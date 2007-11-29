package org.jboss.cx.remoting.spi.protocol;

import java.net.URI;
import java.io.IOException;

/**
 *
 */
public interface ProtocolHandlerFactory {
    /**
     * Determine whether the given URI refers to the current endpoint.
     *
     * todo - revisit this - maybe it should mean "in the same VM"
     *
     * @param uri a URI whose scheme matches this handler factory
     * @return {@code true} if the URI refers to this local endpoint
     */
    boolean isLocal(URI uri);

    /**
     * Create a protocol handler instance which is associated with a single {@code Session} instance.  The returned
     * handler should be connected to the remote side.
     *
     * @param context the protocol context to use for inbound data
     * @param remoteUri the URI of the remote side
     * @return the protocol handler for outbound data
     * @throws IOException if the handler could not be created
     */
    ProtocolHandler createHandler(ProtocolContext context, URI remoteUri) throws IOException;

    /**
     * Signifies that this protocol has been unregistered from the endpoint.
     */
    void close();
}
