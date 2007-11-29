package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.RemotingException;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 *
 */
public interface ProtocolContext {
    /**
     * Handle the close of the session channel from the remote side.
     */
    void closeSession();

    void closeContext(ContextIdentifier remoteContextIdentifier);

    void closeStream(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier);

    void failSession();

    void failContext(ContextIdentifier contextIdentifier);

    void failRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier);

    void failStream(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier);

    void receiveServiceMessage(ServiceIdentifier serviceIdentifier, ServiceNegotiationMessage message);

    void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply);

    void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Request<?> request);

    void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier);

    void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    void receiveStreamData(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier, final Object data);

    Object deserialize(Collection<ByteBuffer> buffers) throws RemotingException;

    Collection<ByteBuffer> serialize(Object object) throws RemotingException;
}
