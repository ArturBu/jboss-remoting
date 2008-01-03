package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.stream.DefaultStreamDetector;
import org.jboss.cx.remoting.core.util.AtomicStateMachine;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.protocol.ByteInput;
import org.jboss.cx.remoting.spi.protocol.ByteOutput;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.spi.protocol.MessageOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamDetector;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;

import javax.security.auth.callback.CallbackHandler;


/**
 * Three execution contexts:
 *
 * - Inbound protocol handler - controlled by server/network handler
 * - Context client context - controlled by user/container
 * - Local work handler - ExecutorService provided to Endpoint
 */
public final class CoreSession {
    private static final Logger log = Logger.getLogger(CoreSession.class);

    private final ProtocolContextImpl protocolContext = new ProtocolContextImpl();
    private final UserSession userSession = new UserSession();

    // stream serialization detectors - immutable
    private final List<StreamDetector> streamDetectors;

    // clients - weak reference, to clean up if the user leaks
    private final ConcurrentMap<ContextIdentifier, WeakReference<CoreOutboundContext>> contexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, WeakReference<CoreOutboundService>> services = CollectionUtil.concurrentMap();

    // servers - strong refereces, only clean up if we hear it from the other end
    private final ConcurrentMap<ContextIdentifier, CoreInboundContext> serverContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, CoreInboundService> serverServices = CollectionUtil.concurrentMap();

    // streams - strong references, only clean up if a close message is sent or received
    private final ConcurrentMap<StreamIdentifier, StreamSerializer> streams = CollectionUtil.concurrentMap();

    // don't GC the endpoint while a session lives
    private final CoreEndpoint endpoint;
    private final ProtocolHandler protocolHandler;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.UP);

    // Constructors

    CoreSession(final CoreEndpoint endpoint, final ProtocolHandler protocolHandler) {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        this.endpoint = endpoint;
        this.protocolHandler = protocolHandler;
        streamDetectors = java.util.Collections.<StreamDetector>singletonList(new DefaultStreamDetector());
    }

    CoreSession(final CoreEndpoint endpoint, final ProtocolHandlerFactory factory, final EndpointLocator endpointLocator) throws IOException {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        if (factory == null) {
            throw new NullPointerException("factory is null");
        }
        if (endpointLocator == null) {
            throw new NullPointerException("endpointLocator is null");
        }
        this.endpoint = endpoint;
        final CallbackHandler locatorCallbackHandler = endpointLocator.getClientCallbackHandler();
        final Endpoint userEndpoint = endpoint.getUserEndpoint();
        protocolHandler = factory.createHandler(protocolContext, endpointLocator.getEndpointUri(), locatorCallbackHandler == null ? userEndpoint.getLocalCallbackHandler() : locatorCallbackHandler, userEndpoint.getRemoteCallbackHandler());
        streamDetectors = java.util.Collections.<StreamDetector>singletonList(new DefaultStreamDetector());
    }

    // Outbound protocol messages

    void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Request<?> request) throws RemotingException {
        try {
            protocolHandler.sendRequest(contextIdentifier, requestIdentifier, request);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the request: " + e);
        }
    }

    boolean sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        try {
            protocolHandler.sendCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
        } catch (IOException e) {
            log.trace("Failed to send a cancel request: %s", e);
            return false;
        }
        return true;
    }

    void sendServiceRequest(final ServiceIdentifier serviceIdentifier, final ServiceLocator<?,?> locator) throws RemotingException {
        try {
            protocolHandler.sendServiceRequest(serviceIdentifier, locator);
        } catch (IOException e) {
            throw new RemotingException("Failed to send a service request: " + e);
        }
    }

    void sendServiceActivate(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        try {
            protocolHandler.sendServiceActivate(serviceIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to send a service activate: " + e);
        }
    }

    void sendReply(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Reply<?> reply) throws RemotingException {
        try {
            protocolHandler.sendReply(contextIdentifier, requestIdentifier, reply);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the reply: " + e);
        }
    }

    void sendException(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws RemotingException {
        try {
            protocolHandler.sendException(contextIdentifier, requestIdentifier, exception);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the exception: " + e);
        }
    }

    void sendCancelAcknowledge(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier) throws RemotingException {
        try {
            protocolHandler.sendCancelAcknowledge(contextIdentifier, requestIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to send cancel acknowledgement: " + e);
        }
    }

    // Inbound protocol messages are in the ProtocolContextImpl

    // Other protocol-related

    RequestIdentifier openRequest(final ContextIdentifier contextIdentifier) throws RemotingException {
        try {
            return protocolHandler.openRequest(contextIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to open a request: " + e);
        }
    }

    void closeService(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        try {
            protocolHandler.closeService(serviceIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to close service: " + e);
        }
    }

    // Getters

    ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    Session getUserSession() {
        return userSession;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    // Thread-local instance

    private static final ThreadLocal<CoreSession> instance = new ThreadLocal<CoreSession>();

    static CoreSession getInstance() {
        return instance.get();
    }

    private void setInstance() {
        instance.set(this);
    }

    private void clearInstance() {
        instance.remove();
    }

    // State mgmt

    private enum State {
        DOWN,
        UP,
        STOPPING,
    }

    protected void shutdown() {
        if (state.transition(State.UP, State.STOPPING)) {
            for (Map.Entry<ContextIdentifier,WeakReference<CoreOutboundContext>> entry : contexts.entrySet()) {
                final CoreOutboundContext context = entry.getValue().get();
                if (context != null) {
                    context.receiveCloseContext();
                }
            }
            for (Map.Entry<ContextIdentifier,CoreInboundContext> entry : serverContexts.entrySet()) {
                entry.getValue().shutdown();
            }
            state.requireTransition(State.STOPPING, State.DOWN);
        }
    }

    // Context mgmt

    <I, O> CoreOutboundContext<I, O> createContext(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        state.requireHold(State.UP);
        try {
            final ContextIdentifier contextIdentifier;
            try {
                contextIdentifier = protocolHandler.openContext(serviceIdentifier);
            } catch (IOException e) {
                RemotingException rex = new RemotingException("Failed to open context: " + e.getMessage());
                rex.setStackTrace(e.getStackTrace());
                throw rex;
            }
            final CoreOutboundContext<I, O> context = new CoreOutboundContext<I, O>(this, contextIdentifier);
            log.trace("Adding new context, ID = %s", contextIdentifier);
            contexts.put(contextIdentifier, new WeakReference<CoreOutboundContext>(context));
            return context;
        } finally {
            state.release();
        }
    }

    <I, O> CoreInboundContext<I, O> createServerContext(final ServiceIdentifier remoteServiceIdentifier, final ContextIdentifier remoteContextIdentifier, final RequestListener<I, O> requestListener) {
        if (remoteServiceIdentifier == null) {
            throw new NullPointerException("remoteServiceIdentifier is null");
        }
        if (remoteContextIdentifier == null) {
            throw new NullPointerException("remoteContextIdentifier is null");
        }
        state.requireHold(State.UP);
        try {
            final CoreInboundContext<I, O> context = new CoreInboundContext<I, O>(remoteContextIdentifier, this, requestListener, null);
            log.trace("Adding new server (inbound) context, ID = %s", remoteContextIdentifier);
            serverContexts.put(remoteContextIdentifier, context);
            return context;
        } finally {
            state.release();
        }
    }

    CoreOutboundContext getContext(final ContextIdentifier contextIdentifier) {
        if (contextIdentifier == null) {
            throw new NullPointerException("contextIdentifier is null");
        }
        final WeakReference<CoreOutboundContext> weakReference = contexts.get(contextIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    CoreInboundContext getServerContext(final ContextIdentifier remoteContextIdentifier) {
        if (remoteContextIdentifier == null) {
            throw new NullPointerException("remoteContextIdentifier is null");
        }
        final CoreInboundContext context = serverContexts.get(remoteContextIdentifier);
        return context;
    }

    void removeContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        contexts.remove(identifier);
    }

    void removeServerContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        serverContexts.remove(identifier);
    }

    // Service mgmt

    <I, O> CoreOutboundService<I, O> createService(final ServiceLocator<I, O> locator) throws RemotingException {
        if (locator == null) {
            throw new NullPointerException("locator is null");
        }
        state.requireHold(State.UP);
        try {
            final ServiceIdentifier serviceIdentifier;
            try {
                serviceIdentifier = protocolHandler.openService();
            } catch (IOException e) {
                throw new RemotingException("Failed to open service: " + e.toString());
            }
            final CoreOutboundService<I, O> service = new CoreOutboundService<I, O>(this, serviceIdentifier, locator);
            log.trace("Adding new client service, ID = %s", serviceIdentifier);
            services.put(serviceIdentifier, new WeakReference<CoreOutboundService>(service));
            return service;
        } finally {
            state.release();
        }
    }

    <I, O> CoreInboundService<I, O> createServerService(final ServiceIdentifier serviceIdentifier, final ServiceLocator<I, O> locator) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        if (locator == null) {
            throw new NullPointerException("locator is null");
        }
        state.requireHold(State.UP);
        try {
            final CoreInboundService<I, O> service;
            try {
                service = new CoreInboundService<I, O>(endpoint, this, serviceIdentifier, locator);
            } catch (RemotingException e) {
                try {
                    sendServiceTerminate(serviceIdentifier);
                } catch (RemotingException e1) {
                    log.trace("Failed to notify client of service termination: %s", e);
                }
                return null;
            }
            try {
                sendServiceActivate(serviceIdentifier);
            } catch (RemotingException e) {
                log.trace("Failed to notify client of service activation: %s", e);
                return null;
            }
            log.trace("Adding new server service, ID = %s", serviceIdentifier);
            serverServices.put(serviceIdentifier, service);
            return service;
        } finally {
            state.release();
        }
    }

    private void sendServiceTerminate(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        try {
            protocolHandler.sendServiceTerminate(serviceIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to send service terminate: " + e.toString());
        }
    }

    CoreOutboundService getService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        final WeakReference<CoreOutboundService> weakReference = services.get(serviceIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    CoreInboundService getServerService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        return serverServices.get(serviceIdentifier);
    }

    void removeServerService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        serverServices.remove(serviceIdentifier);
    }


    // User session impl

    public final class UserSession implements Session {
        private UserSession() {}

        private final ConcurrentMap<Object, Object> sessionMap = CollectionUtil.concurrentMap();

        public void close() throws RemotingException {
            shutdown();
            try {
                protocolHandler.closeSession();
            } catch (IOException e) {
                throw new RemotingException("Unable to close session: " + e.toString());
            }
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return sessionMap;
        }

        public String getEndpointName() {
            return endpoint.getUserEndpoint().getName();
        }

        public <I, O> ContextSource<I, O> openService(ServiceLocator<I, O> locator) throws RemotingException {
            if (locator == null) {
                throw new NullPointerException("locator is null");
            }
            if (locator.getServiceType() == null) {
                throw new NullPointerException("locator.getServiceType() is null");
            }
            state.requireHold(State.UP);
            try {
                final CoreOutboundService<I, O> service = createService(locator);
                service.sendServiceRequest();
                service.await();
                return service.getUserContextSource();
            } finally {
                state.release();
            }
        }
    }

    // Protocol context

    public final class ProtocolContextImpl implements ProtocolContext {

        public void closeSession() {
            shutdown();
        }

        public MessageOutput getMessageOutput(ByteOutput target) throws IOException {
            return new MessageOutputImpl(target, streamDetectors);
        }

        public MessageInput getMessageInput(ByteInput source) throws IOException {
            return new MessageInputImpl(source);
        }

        public void closeContext(ContextIdentifier remoteContextIdentifier) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            if (context != null) {
                context.shutdown();
            }
        }

        public void closeStream(StreamIdentifier streamIdentifier) {
            streams.remove(streamIdentifier);
        }

        public void closeService(ServiceIdentifier serviceIdentifier) {
            // todo
        }

        public void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier) {
            final CoreInboundService service = getServerService(remoteServiceIdentifier);
            if (service != null) {
                service.receivedOpenedContext(remoteContextIdentifier);
            }
        }

        @SuppressWarnings({"unchecked"})
        public void receiveServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) {
            createServerService(serviceIdentifier, locator);
        }

        public void receiveServiceActivate(ServiceIdentifier serviceIdentifier) {
            final CoreOutboundService service = getService(serviceIdentifier);
            if (service != null) {
                service.receiveServiceActivate();
            } else {
                log.trace("Got service activate for an unknown service (%s)", serviceIdentifier);
            }
        }

        public void receiveServiceTerminate(ServiceIdentifier serviceIdentifier) {
            final CoreOutboundService service = getService(serviceIdentifier);
            if (service != null) {
                service.receiveServiceTerminate();
            } else {
                log.trace("Got service terminate for an unknown service (%s)", serviceIdentifier);
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveReply(requestIdentifier, reply);
            } else {
                log.trace("Got a reply for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveException(requestIdentifier, exception);
            } else {
                log.trace("Got a request exception for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveCancelAcknowledge(requestIdentifier);
            } else {
                log.trace("Got a cancel acknowledge for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            context.receiveCancelRequest(requestIdentifier, mayInterrupt);
        }

        public void receiveStreamData(StreamIdentifier streamIdentifier, MessageInput data) {
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            if (context != null) {
                context.receiveRequest(requestIdentifier, request);
            } else {
                log.trace("Got a request on an unknown context identifier (%s)", remoteContextIdentifier);
                try {
                    protocolHandler.sendException(remoteContextIdentifier, requestIdentifier, new RemoteExecutionException("Received a request on an invalid context"));
                } catch (IOException e) {
                    log.trace("Failed to send exception: %s", e.getMessage());
                }
            }
        }

        public <T> Reply<T> createReply(T body) {
            return new ReplyImpl<T>(body);
        }

        public <T> Request<T> createRequest(T body) {
            return new RequestImpl<T>(body);
        }
    }

    // message output

    private final class MessageOutputImpl extends ObjectOutputStream implements MessageOutput {
        private final ByteOutput target;
        private final List<StreamDetector> streamDetectors;

        private MessageOutputImpl(final ByteOutput target, final List<StreamDetector> streamDetectors) throws IOException {
            super(new OutputStream() {
                public void write(int b) throws IOException {
                    target.write(b);
                }

                public void write(byte b[]) throws IOException {
                    target.write(b);
                }

                public void write(byte b[], int off, int len) throws IOException {
                    target.write(b, off, len);
                }

                public void flush() throws IOException {
                    target.flush();
                }

                public void close() throws IOException {
                    target.close();
                }
            });
            enableReplaceObject(true);
            this.target = target;
            this.streamDetectors = streamDetectors;
        }

        public void commit() throws IOException {
            close();
            target.commit();
        }

        protected void writeObjectOverride(Object obj) throws IOException {
            setInstance();
            super.writeObjectOverride(obj);
            clearInstance();
        }

        protected Object replaceObject(Object obj) throws IOException {
            final Object testObject = super.replaceObject(obj);
            for (StreamDetector detector : streamDetectors) {
                final StreamSerializerFactory factory = detector.detectStream(testObject);
                if (factory != null) {
                    final StreamIdentifier streamIdentifier = protocolHandler.openStream();
                    final StreamContextImpl streamContext = new StreamContextImpl(streamIdentifier);
                    final StreamSerializer streamSerializer = factory.getLocalSide(streamContext, testObject);
                    if (streams.putIfAbsent(streamIdentifier, streamSerializer) != null) {
                        throw new IOException("Duplicate stream identifier encountered: " + streamIdentifier);
                    }
                    return new StreamMarker(CoreSession.this, factory.getClass(), streamIdentifier);
                }
            }
            return testObject;
        }
    }

    // message input

    private final class ObjectInputImpl extends ObjectInputStream {

        public ObjectInputImpl(final InputStream is) throws IOException {
            super(is);
            super.enableResolveObject(true);
        }

        public ObjectInputImpl(final InputStream is, final ClassLoader loader) throws IOException {
//            super(is, loader);
            super(is);
            super.enableResolveObject(true);
        }

        protected Object resolveObject(Object obj) throws IOException {
            log.trace("In resolveObject");
            final Object testObject = super.resolveObject(obj);
            if (testObject instanceof StreamMarker) {
                StreamMarker marker = (StreamMarker) testObject;
                final StreamIdentifier streamIdentifier = marker.getStreamIdentifier();
                final StreamSerializerFactory streamSerializerFactory;
                try {
                    streamSerializerFactory = marker.getFactoryClass().newInstance();
                } catch (InstantiationException e) {
                    throw new IOException("Failed to instantiate a stream: " + e);
                } catch (IllegalAccessException e) {
                    throw new IOException("Failed to instantiate a stream: " + e);
                }
                final RemoteStreamSerializer streamSerializer = streamSerializerFactory.getRemoteSide(new StreamContextImpl(streamIdentifier));
                if (streams.putIfAbsent(streamIdentifier, streamSerializer) != null) {
                    throw new IOException("Duplicate stream received");
                }
                return streamSerializer.getRemoteInstance();
            } else {
                return testObject;
            }
        }
    }

    private final class MessageInputImpl extends DelegatingObjectInput implements MessageInput {
        private CoreSession.ObjectInputImpl objectInput;

        private MessageInputImpl(final ObjectInputImpl objectInput) throws IOException {
            super(objectInput);
            this.objectInput = objectInput;
        }

        private MessageInputImpl(final ByteInput source) throws IOException {
            this(new ObjectInputImpl(new InputStream() {
                public int read(byte b[]) throws IOException {
                    return source.read(b);
                }

                public int read(byte b[], int off, int len) throws IOException {
                    return source.read(b, off, len);
                }

                public int read() throws IOException {
                    return source.read();
                }

                public void close() throws IOException {
                    source.close();
                }

                public int available() throws IOException {
                    return source.remaining();
                }
            }));
        }

        public Object readObject() throws ClassNotFoundException, IOException {
            setInstance();
            try {
                return super.readObject();
            } finally {
                clearInstance();
            }
        }

        public int remaining() {
            try {
                return objectInput.available();
            } catch (IOException e) {
                throw new IllegalStateException("Available failed", e);
            }
        }
    }

    // stream context

    private final class StreamContextImpl implements StreamContext {
        private final StreamIdentifier streamIdentifier;

        private StreamContextImpl(final StreamIdentifier streamIdentifier) {
            this.streamIdentifier = streamIdentifier;
        }

        public MessageOutput writeMessage() throws IOException {
            return protocolHandler.sendStreamData(streamIdentifier);
        }

        public void close() throws IOException {
            try {
                protocolHandler.closeStream(streamIdentifier);
            } finally {
                streams.remove(streamIdentifier);
            }
        }
    }

}
