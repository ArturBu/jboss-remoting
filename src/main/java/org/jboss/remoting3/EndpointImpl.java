/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.regex.Pattern;
import org.jboss.remoting3.security.PasswordClientCallbackHandler;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Result;
import org.jboss.logging.Logger;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").infof("JBoss Remoting version %s", Version.VERSION);
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private static final RemotingPermission REGISTER_SERVICE_PERM = new RemotingPermission("registerService");
    private static final RemotingPermission CONNECT_PERM = new RemotingPermission("connect");
    private static final RemotingPermission ADD_CONNECTION_PROVIDER_PERM = new RemotingPermission("addConnectionProvider");
    private static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE_PERM = new RemotingPermission("getConnectionProviderInterface");
    private static final int CLOSED_FLAG = 0x80000000;
    private static final int COUNT_MASK = ~(CLOSED_FLAG);

    private final Set<ConnectionImpl> connections = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ConnectionImpl, Boolean>()));

    private final Attachments attachments = new Attachments();

    private final ConcurrentMap<String, ConnectionProvider> connectionProviders = new UnlockedReadHashMap<String, ConnectionProvider>();
    private final ConcurrentMap<String, OpenListener> registeredServices = new UnlockedReadHashMap<String, OpenListener>();

    private final Xnio xnio;
    private final XnioWorker worker;

    private final ThreadPoolExecutor executor;

    private static final AtomicIntegerFieldUpdater<EndpointImpl> resourceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(EndpointImpl.class, "resourceCount");

    @SuppressWarnings("unused")
    private volatile int resourceCount = 0;

    private static final Pattern VALID_SERVICE_PATTERN = Pattern.compile("[-.:a-zA-Z_0-9]+");

    /**
     * The name of this endpoint.
     */
    private final String name;
    @SuppressWarnings("unused")
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final CloseHandler<Object> resourceCloseHandler = new CloseHandler<Object>() {
        public void handleClose(final Object closed, final IOException exception) {
            closeTick1(closed);
        }
    };
    private final EndpointImpl.ConnectionCloseHandler connectionCloseHandler = new EndpointImpl.ConnectionCloseHandler();

    private EndpointImpl(final Pool executor, final Xnio xnio, final String name, final OptionMap optionMap) throws IOException {
        super(executor);
        executor.stopTask = new Runnable() {
            public void run() {
                log.tracef("Finished final shutdown of %s", EndpointImpl.this);
                closeComplete();
            }
        };
        executor.allowCoreThreadTimeOut(true);
        this.executor = executor;
        if (xnio == null) {
            throw new IllegalArgumentException("xnio is null");
        }
        if (optionMap == null) {
            throw new IllegalArgumentException("optionMap is null");
        }
        this.xnio = xnio;
        this.name = name;
        final String workerName = name == null ? "Remoting (anonymous)" : "Remoting \"" + name + "\"";
        this.optionMap = OptionMap.builder().addAll(optionMap).set(Options.WORKER_NAME, workerName).getMap();
        // initialize CPC
        connectionProviderContext = new ConnectionProviderContextImpl();
        // add default connection providers
        connectionProviders.put("local", new LocalConnectionProvider(connectionProviderContext, executor));
        // get XNIO worker
        worker = xnio.createWorker(optionMap);
        log.tracef("Completed open of %s", this);
    }

    private EndpointImpl(final int poolSize, final Xnio xnio, final String name, final OptionMap optionMap) throws IOException {
        this(new Pool(poolSize, poolSize, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new EndpointThreadFactory(name)), xnio, name, optionMap);
    }

    EndpointImpl(final Xnio xnio, final String name, final OptionMap optionMap) throws IOException {
        this(optionMap.get(RemotingOptions.TASK_THREAD_POOL_SIZE, 4), xnio, name, optionMap);
    }

    protected Executor getExecutor() {
        return executor;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String getName() {
        return name;
    }

    private void closeTick1(Object c) {
        int res = resourceCountUpdater.decrementAndGet(this);
        if (res == CLOSED_FLAG) {
            // this was the last phase 1 resource.
            finishPhase1();
        } else if ((res & CLOSED_FLAG) != 0) {
            // shutdown is currently in progress.
            if (log.isTraceEnabled()) {
                log.logf(EndpointImpl.class.getName(), Logger.Level.TRACE, null, "Phase 1 shutdown count %08x of %s (closed %s)", Integer.valueOf(res & COUNT_MASK), this, c);
            }
        } else {
            if (log.isTraceEnabled()) {
                log.logf(EndpointImpl.class.getName(), Logger.Level.TRACE, null, "Resource closed count %08x of %s (closed %s)", Integer.valueOf(res & COUNT_MASK), this, c);
            }
        }
    }

    private void finishPhase1() {
        // all our original resources were closed; now move on to stage two (thread pools)
        log.tracef("Finished phase 1 shutdown of %s", this);
        IoUtils.safeClose(worker);
        executor.shutdown();
        return;
    }

    void resourceUntick(Object opened) throws NotOpenException {
        int old;
        do {
            old = resourceCountUpdater.get(this);
            if ((old & CLOSED_FLAG) != 0) {
                throw new NotOpenException("Endpoint is not open");
            }
        } while (! resourceCountUpdater.compareAndSet(this, old, old + 1));
        if (log.isTraceEnabled()) {
            log.tracef("Allocated tick to %d of %s (opened %s)", Integer.valueOf(old + 1), this, opened);
        }
    }

    protected void closeAction() throws IOException {
        // Commence phase one shutdown actions
        int res;
        do {
            res = resourceCount;
        } while (! resourceCountUpdater.compareAndSet(this, res, res | CLOSED_FLAG));
        if (res == 0) {
            finishPhase1();
        } else {
            for (Object connection : connections.toArray()) {
                ((ConnectionImpl)connection).closeAsync();
            }
            for (ConnectionProvider connectionProvider : connectionProviders.values()) {
                connectionProvider.closeAsync();
            }
        }
    }

    public Registration registerService(final String serviceType, final OpenListener openListener, final OptionMap optionMap) throws ServiceRegistrationException {
        if (! VALID_SERVICE_PATTERN.matcher(serviceType).matches()) {
            throw new IllegalArgumentException("Service type must match " + VALID_SERVICE_PATTERN);
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        final OpenListener existing = registeredServices.putIfAbsent(serviceType, openListener);
        if (existing != null) {
            throw new ServiceRegistrationException("Service type '" + serviceType + "' is already registered");
        }
        final MapRegistration<OpenListener> registration = new MapRegistration<OpenListener>(registeredServices, serviceType, openListener) {
            protected void closeAction() throws IOException {
                try {
                    openListener.registrationTerminated();
                } finally {
                    super.closeAction();
                }
            }
        };
        // automatically close the registration when the endpoint is closed
        final Key key = addCloseHandler(SpiUtils.closingCloseHandler(registration));
        registration.addCloseHandler(new CloseHandler<Registration>() {
            public void handleClose(final Registration closed, final IOException exception) {
                key.remove();
            }
        });
        return registration;
    }

    public IoFuture<Connection> connect(final URI destination) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder();
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null));
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null));
    }

    private IoFuture<Connection> doConnect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        boolean ok = false;
        resourceUntick("Connection to " + destination);
        try {
            final String scheme = destination.getScheme();
            final ConnectionProvider connectionProvider = connectionProviders.get(scheme);
            if (connectionProvider == null) {
                throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
            }
            final FutureResult<Connection> futureResult = new FutureResult<Connection>(getExecutor());
            // Mark the stack because otherwise debugging connect problems can be incredibly tough
            final StackTraceElement[] mark = Thread.currentThread().getStackTrace();
            final Cancellable connect = connectionProvider.connect(destination, connectOptions, new Result<ConnectionHandlerFactory>() {
                private final AtomicBoolean called = new AtomicBoolean();

                public boolean setResult(final ConnectionHandlerFactory result) {
                    if (called.getAndSet(true)) {
                        return false;
                    }
                    final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, result, connectionProviderContext);
                    connections.add(connection);
                    connection.getConnectionHandler().addCloseHandler(SpiUtils.asyncClosingCloseHandler(connection));
                    connection.addCloseHandler(resourceCloseHandler);
                    connection.addCloseHandler(connectionCloseHandler);
                    return futureResult.setResult(connection);
                }

                public boolean setException(final IOException exception) {
                    if (called.getAndSet(true)) {
                        return false;
                    }
                    closeTick1("a failed connection (2)");
                    SpiUtils.glueStackTraces(exception, mark, 1, "asynchronous invocation");
                    return futureResult.setException(exception);
                }

                public boolean setCancelled() {
                    if (called.getAndSet(true)) {
                        return false;
                    }
                    closeTick1("a cancelled connection");
                    return futureResult.setCancelled();
                }
            }, callbackHandler);
            ok = true;
            futureResult.addCancelHandler(connect);
            return futureResult.getIoFuture();
        } finally {
            if (! ok) {
                closeTick1("a failed connection (1)");
            }
        }
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, callbackHandler);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final String actualUserName = userName != null ? userName : uriUserName != null ? uriUserName : connectOptions.get(RemotingOptions.AUTHORIZE_ID);
        final String actualUserRealm = realmName != null ? realmName : uriUserRealm != null ? uriUserRealm : connectOptions.get(RemotingOptions.AUTH_REALM);
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (actualUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, actualUserName);
        if (actualUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, actualUserRealm);
        final OptionMap finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(actualUserName, actualUserRealm, password));
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        boolean ok = false;
        resourceUntick("Connection provider for " + uriScheme);
        try {
            final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl();
            final ConnectionProvider provider = providerFactory.createInstance(context, optionMap);
            try {
                if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
                    throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
                }
                // add a resource count for close
                log.tracef("Adding connection provider registration named '%s': %s", uriScheme, provider);
                final Registration registration = new MapRegistration<ConnectionProvider>(connectionProviders, uriScheme, provider) {
                    protected void closeAction() throws IOException {
                        try {
                            provider.closeAsync();
                        } finally {
                            super.closeAction();
                        }
                    }
                };
                provider.addCloseHandler(new CloseHandler<ConnectionProvider>() {
                    public void handleClose(final ConnectionProvider closed, final IOException exception) {
                        registration.closeAsync();
                        closeTick1(closed);
                    }
                });
                ok = true;
                return registration;
            } finally {
                if (! ok) {
                    provider.close();
                }
            }
        } finally {
            if (! ok) {
                closeTick1("Connection provider for " + uriScheme);
            }
        }
    }

    public <T> T getConnectionProviderInterface(final String uriScheme, final Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(GET_CONNECTION_PROVIDER_INTERFACE_PERM);
        }
        if (! expectedType.isInterface()) {
            throw new IllegalArgumentException("Interface expected");
        }
        final ConnectionProvider provider = connectionProviders.get(uriScheme);
        if (provider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + uriScheme + "\" is installed");
        }
        return expectedType.cast(provider.getProviderInterface());
    }

    public boolean isValidUriScheme(final String uriScheme) {
        return connectionProviders.containsKey(uriScheme);
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("endpoint ");
        if (name != null) {
            b.append('"').append(name).append('"');
        } else {
            b.append("(anonymous)");
        }
        b.append(" <").append(Integer.toHexString(hashCode())).append(">");
        return b.toString();
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static String uriDecode(String encoded) {
        final char[] chars = encoded.toCharArray();
        final int olen = chars.length;
        final byte[] buf = new byte[olen];
        int c = 0;
        for (int i = 0; i < olen; i++) {
            final char ch = chars[i];
            if (ch == '%') {
                buf[c++] = (byte) (Character.digit(chars[++i], 16) << 4 | Character.digit(chars[++i], 16));
            } else if (ch < 32 || ch > 127) {
                // skip it
            } else {
                buf[c++] = (byte) ch;
            }
        }
        return new String(buf, 0, c, UTF_8);
    }

    static final class UserAndRealm {
        private final String user;
        private final String realm;

        UserAndRealm(final String user, final String realm) {
            this.user = user;
            this.realm = realm;
        }

        public String getUser() {
            return user;
        }

        public String getRealm() {
            return realm;
        }
    }

    private static final UserAndRealm EMPTY = new UserAndRealm(null, null);

    private UserAndRealm getUserAndRealm(URI uri) {
        final String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            return EMPTY;
        }
        int i = userInfo.indexOf(';');
        if (i == -1) {
            return new UserAndRealm(uri.getUserInfo(), null);
        } else {
            return new UserAndRealm(uriDecode(userInfo.substring(0, i)), uriDecode(userInfo.substring(i + 1)));
        }
    }

    private class MapRegistration<T> extends AbstractHandleableCloseable<Registration> implements Registration {

        private final ConcurrentMap<String, T> map;
        private final String key;
        private final T value;

        private MapRegistration(final ConcurrentMap<String, T> map, final String key, final T value) {
            super(executor, false);
            this.map = map;
            this.key = key;
            this.value = value;
        }

        protected void closeAction() throws IOException {
            map.remove(key, value);
            closeComplete();
        }

        public void close() {
            try {
                super.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public String toString() {
            return String.format("Registration of '%s': %s", key, value);
        }
    }

    final class LocalConnectionContext implements ConnectionHandlerContext {
        private final ConnectionProviderContext connectionProviderContext;
        private final Connection connection;

        LocalConnectionContext(final ConnectionProviderContext connectionProviderContext, final Connection connection) {
            this.connectionProviderContext = connectionProviderContext;
            this.connection = connection;
        }

        public ConnectionProviderContext getConnectionProviderContext() {
            return connectionProviderContext;
        }

        public OpenListener getServiceOpenListener(final String serviceType) {
            return registeredServices.get(serviceType);
        }

        public Connection getConnection() {
            return connection;
        }

        public void remoteClosed() {
            connection.closeAsync();
        }
    }

    private final class ConnectionProviderContextImpl implements ConnectionProviderContext {

        private ConnectionProviderContextImpl() {
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            try {
                resourceUntick("an inbound connection");
            } catch (NotOpenException e) {
                throw new IllegalStateException("Accept after endpoint close", e);
            }
            boolean ok = false;
            try {
                final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this);
                connections.add(connection);
                connection.getConnectionHandler().addCloseHandler(SpiUtils.asyncClosingCloseHandler(connection));
                connection.addCloseHandler(connectionCloseHandler);
                connection.addCloseHandler(resourceCloseHandler);
                ok = true;
            } finally {
                if (! ok) closeTick1("a failed inbound connection");
            }
        }

        public Endpoint getEndpoint() {
            return EndpointImpl.this;
        }

        public Xnio getXnio() {
            return xnio;
        }

        public Executor getExecutor() {
            return executor;
        }

        public XnioWorker getXnioWorker() {
            return worker;
        }
    }

    static final class EndpointThreadFactory implements ThreadFactory {
        private final String name;
        @SuppressWarnings("unused")
        private volatile int threadId = 1;

        private static final AtomicIntegerFieldUpdater<EndpointThreadFactory> threadIdUpdater = AtomicIntegerFieldUpdater.newUpdater(EndpointThreadFactory.class, "threadId");

        EndpointThreadFactory(final String name) {
            this.name = name;
        }

        public Thread newThread(final Runnable r) {
            final int id = threadIdUpdater.getAndIncrement(this);
            final String threadName = name == null ? String.format("Remoting (anonymous) task-%d", Integer.valueOf(id)) : String.format("Remoting \"%s\" task-%d", name, Integer.valueOf(id));
            final Thread thread = new Thread(r, threadName);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    log.errorf(e, "Uncaught exception in thread %s", t);
                }
            });
            return thread;
        }
    }

    static final class Pool extends ThreadPoolExecutor {
        volatile Runnable stopTask;

        Pool(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        protected void terminated() {
            final Runnable task = stopTask;
            if (task != null) task.run();
        }
    }

    private class ConnectionCloseHandler implements CloseHandler<Connection> {

        public void handleClose(final Connection closed, final IOException exception) {
            connections.remove(closed);
        }
    }
}
