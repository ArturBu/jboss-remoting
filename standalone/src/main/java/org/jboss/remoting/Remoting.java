package org.jboss.remoting;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.Collection;
import java.util.Iterator;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 */
public final class Remoting {

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of 10 threads.
     *
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name) {
        return createEndpoint(name, 10);
    }

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of {@code maxThreads} threads.
     *
     * @param name the name of the endpoint
     * @param maxThreads the maximum thread count
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name, final int maxThreads) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, maxThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new AlwaysBlockingQueue<Runnable>(new SynchronousQueue<Runnable>()), new ThreadPoolExecutor.AbortPolicy());
        final EndpointImpl endpoint = new EndpointImpl(executor, name);
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                executor.shutdown();
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        return endpoint;
    }

    /**
     * Create an endpoint using the given {@code Executor} to execute tasks.
     *
     * @param executor the executor to use
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final Executor executor, final String name) {
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
        final Handle<RequestHandler> handle = endpoint.createRequestHandler(requestListener, requestClass, replyClass);
        try {
            return endpoint.createClient(handle.getResource(), requestClass, replyClass);
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    /**
     * Create a local client source from a local service configuration.  The client source will be registered on the endpoint.
     *
     * @param endpoint the endpoint to bind the service to
     * @param config the service configuration
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new client source
     * @throws IOException if an error occurs
     */
    public static <I, O> ClientSource<I, O> createLocalClientSource(final Endpoint endpoint, final LocalServiceConfiguration<I, O> config) throws IOException {
        final Handle<RequestHandlerSource> handle = endpoint.registerService(config);
        try {
            return endpoint.createClientSource(handle.getResource(), config.getRequestClass(), config.getReplyClass());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    private static class AlwaysBlockingQueue<E> implements BlockingQueue<E> {
        private final BlockingQueue<E> delegate;

        public AlwaysBlockingQueue(final BlockingQueue<E> delegate) {
            this.delegate = delegate;
        }

        public boolean offer(final E o) {
            try {
                delegate.put(o);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public boolean offer(final E o, final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.offer(o, timeout, unit);
        }

        public E poll(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.poll(timeout, unit);
        }

        public E take() throws InterruptedException {
            return delegate.take();
        }

        public void put(final E o) throws InterruptedException {
            delegate.put(o);
        }

        public int remainingCapacity() {
            return delegate.remainingCapacity();
        }

        public boolean add(final E o) {
            return delegate.add(o);
        }

        public int drainTo(final Collection<? super E> c) {
            return delegate.drainTo(c);
        }

        public int drainTo(final Collection<? super E> c, final int maxElements) {
            return delegate.drainTo(c, maxElements);
        }

        public E poll() {
            return delegate.poll();
        }

        public E remove() {
            return delegate.remove();
        }

        public E peek() {
            return delegate.peek();
        }

        public E element() {
            return delegate.element();
        }

        public int size() {
            return delegate.size();
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public boolean contains(final Object o) {
            return delegate.contains(o);
        }

        public Iterator<E> iterator() {
            return delegate.iterator();
        }

        public Object[] toArray() {
            return delegate.toArray();
        }

        public <T> T[] toArray(final T[] a) {
            //noinspection SuspiciousToArrayCall
            return delegate.toArray(a);
        }

        public boolean remove(final Object o) {
            return delegate.remove(o);
        }

        public boolean containsAll(final Collection<?> c) {
            return delegate.containsAll(c);
        }

        public boolean addAll(final Collection<? extends E> c) {
            return delegate.addAll(c);
        }

        public boolean removeAll(final Collection<?> c) {
            return delegate.removeAll(c);
        }

        public boolean retainAll(final Collection<?> c) {
            return delegate.retainAll(c);
        }

        public void clear() {
            delegate.clear();
        }

        public boolean equals(final Object o) {
            return delegate.equals(o);
        }

        public int hashCode() {
            return delegate.hashCode();
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
