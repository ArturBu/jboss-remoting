package org.jboss.cx.remoting;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.core.EndpointImpl;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;

/**
 *
 */
public final class Remoting {
    // lifecycle lock
    private static final Object lifecycle = new Object();

    public static Endpoint createEndpoint(String name) throws IOException {
        synchronized (lifecycle) {
            boolean ok = false;
            final EndpointImpl endpointImpl = new EndpointImpl();
            endpointImpl.setName(name);
            endpointImpl.create();
            try {
                endpointImpl.start();
                ok = true;
                return endpointImpl;
            } finally {
                if (! ok) {
                    endpointImpl.destroy();
                }
            }
        }
    }

    public static void closeEndpoint(Endpoint endpoint) {
        synchronized (lifecycle) {
            if (endpoint instanceof EndpointImpl) {
                final EndpointImpl endpointImpl = (EndpointImpl) endpoint;
                final ConcurrentMap<Object, Object> attributes = endpointImpl.getAttributes();
                endpointImpl.stop();
                endpointImpl.destroy();
            }
        }
    }

    public static <I, O> Client<I, O> createLocalClient(Endpoint endpoint, RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteClientEndpoint<I, O> clientEndpoint = endpoint.createClient(requestListener);
        try {
            return clientEndpoint.getClient();
        } finally {
            clientEndpoint.autoClose();
        }
    }

    public static <I, O> ClientSource<I, O> createLocalClientSource(Endpoint endpoint, RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteServiceEndpoint<I, O> clientEndpoint = endpoint.createService(requestListener);
        try {
            return clientEndpoint.getClientSource();
        } finally {
            clientEndpoint.autoClose();
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
