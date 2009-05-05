/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 *
 * @apiviz.landmark
 */
public final class Remoting {

    private static final Logger log = Logger.getLogger("org.jboss.remoting");

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of 10 threads.
     *
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name) throws IOException {
        return createEndpoint(name, 10);
    }

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of {@code maxThreads} threads.
     *
     * You must have the {@link org.jboss.remoting3.EndpointPermission createEndpoint EndpointPermission} to invoke this method.
     *
     * @param name the name of the endpoint
     * @param maxThreads the maximum thread count
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name, final int maxThreads) throws IOException {
        final CloseableExecutor executor = createExecutor(maxThreads);
        final Endpoint endpoint = createEndpoint(executor, name);
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(executor);
            }
        });
        return endpoint;
    }

    private static final ThreadFactory OUR_THREAD_FACTORY = new ThreadFactory() {
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

        public Thread newThread(final Runnable r) {
            final Thread thread = defaultThreadFactory.newThread(r);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    log.error(e, "Uncaught exception in thread %s", t);
                }
            });
            return thread;
        }
    };

    /**
     * Create a simple thread pool that is compatible with Remoting.  The thread pool will have a maximum of {@code maxThreads}
     * threads.
     *
     * @param maxThreads the maximum thread count
     * @return a closeable executor
     */
    public static CloseableExecutor createExecutor(final int maxThreads) {
        return IoUtils.closeableExecutor(new ThreadPoolExecutor(1, maxThreads, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(50), OUR_THREAD_FACTORY, new ThreadPoolExecutor.CallerRunsPolicy()), 30L, TimeUnit.SECONDS);
    }

    /**
     * Create an endpoint using the given {@code Executor} to execute tasks.
     *
     * @param executor the executor to use
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final Executor executor, final String name) throws IOException {
        return new EndpointImpl(executor, name);
    }

    /**
     * Create a local client from a request listener.  The client will retain the sole reference to the request listener,
     * so when the client is closed, the listener will also be closed (unless the client is sent to a remote endpoint).
     *
     * @param endpoint the endpoint to bind the request listener to
     * @param requestListener the request listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new client
     * @throws IOException if an error occurs
     */
    public static <I, O> Client<I, O> createLocalClient(final Endpoint endpoint, final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        final RequestHandler requestHandler = endpoint.createLocalRequestHandler(requestListener, requestClass, replyClass);
        try {
            return endpoint.createClient(requestHandler, requestClass, replyClass);
        } finally {
            IoUtils.safeClose(requestHandler);
        }
    }

    /**
     * Convenience method to rethrow the cause of a {@code RemoteExecutionException} as a specific type, in order
     * to simplify application exception handling.
     * <p/>
     * A typical usage might look like this:
     * <pre>
     *   try {
     *     client.invoke(request);
     *   } catch (RemoteExecutionException ree) {
     *     Remoting.rethrowAs(IOException.class, ree);
     *     Remoting.rethrowAs(RuntimeException.class, ree);
     *     Remoting.rethrowUnexpected(ree);
     *   }
     * </pre>
     * <p/>
     * Note that if the nested exception is an {@link InterruptedException}, the type that will actually be thrown
     * will be {@link RemoteInterruptedException}.
     *
     * @param type the class of the exception
     * @param original the remote execution exception
     * @param <T> the exception type
     * @throws T the exception, if it matches the given type
     */
    public static <T extends Throwable> void rethrowAs(Class<T> type, RemoteExecutionException original) throws T {
        final Throwable cause = original.getCause();
        if (cause == null) {
            return;
        }
        if (type.isAssignableFrom(cause.getClass())) {
            if (cause instanceof InterruptedException) {
                throw new RemoteInterruptedException(cause.getMessage(), cause.getCause());
            }
            throw type.cast(cause);
        }
        return;
    }

    public static void rethrowUnexpected(RemoteExecutionException original) throws IllegalStateException {
        Throwable cause = original.getCause();
        if (cause instanceof InterruptedException) {
            cause = new RemoteInterruptedException(cause.getMessage(), cause.getCause());
        }
        throw new IllegalStateException("Unexpected remote exception occurred", cause);
    }

    private Remoting() { /* empty */ }
}
