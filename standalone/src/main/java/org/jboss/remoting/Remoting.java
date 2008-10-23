package org.jboss.remoting;

import java.io.IOException;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class Remoting {
    // lifecycle lock
    private static final Object lifecycle = new Object();

    public static Endpoint createEndpoint(String name) throws IOException {
        synchronized (lifecycle) {
            final EndpointImpl endpointImpl = new EndpointImpl();
            endpointImpl.setName(name);
            endpointImpl.start();
            return endpointImpl;
        }
    }

    public static void closeEndpoint(Endpoint endpoint) {
        synchronized (lifecycle) {
            if (endpoint instanceof EndpointImpl) {
                final EndpointImpl endpointImpl = (EndpointImpl) endpoint;
                endpointImpl.stop();
            }
        }
    }

    public static <I, O> Client<I, O> createLocalClient(Endpoint endpoint, RequestListener<I, O> requestListener) throws IOException {
        final Handle<RequestHandler> handle = endpoint.createRequestHandler(requestListener);
        try {
            return endpoint.createClient(handle.getResource());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    public static <I, O> ClientSource<I, O> createLocalClientSource(Endpoint endpoint, RequestListener<I, O> requestListener, final String serviceType, final String groupName) throws IOException {
        final Handle<RequestHandlerSource> handle = endpoint.createRequestHandlerSource(requestListener, serviceType, groupName);
        try {
            return endpoint.createClientSource(handle.getResource());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
