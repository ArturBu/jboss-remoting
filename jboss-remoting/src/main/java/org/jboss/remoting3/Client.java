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
import java.io.ObjectStreamException;
import java.util.concurrent.CancellationException;
import org.jboss.xnio.IoFuture;

/**
 * A communications client.  The client may be associated with state maintained by the local and/or remote side.
 * <p/>
 * This interface is part of the Remoting public API.  It is intended to be consumed by Remoting applications; it is
 * not intended to be implemented by them.  Methods may be added to this interface in future minor releases without
 * advance notice.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @apiviz.landmark
 */
public interface Client<I, O> extends HandleableCloseable<Client<I, O>> {
    /**
     * Send a request and block until a reply is received.  If the remote side manipulates a stream, the
     * current thread may be used to handle it.
     * <p/>
     * If the operation is cancelled asynchronously, a {@code CancellationException} is thrown.  This exception indicates
     * that the request was received and was executed, but a cancellation request was received and handled before the
     * reply was able to be sent.  The remote service will have cleanly cancelled the operation.  This exception type
     * is a {@code RuntimeException}; thus direct handling of this exception is optional (depending on your use case).
     * <p/>
     * If the request is sent but the remote side sends an exception back, a {@code RemoteExecutionException} is thrown
     * with the cause and message initialized by the remote service.  This exception indicates an error in the execution
     * of the service's {@code RequestListener}.  The service will have cleanly recovered from such an exception.
     * <p/>
     * If the request is sent and the remote side tries to reply, but sending the reply fails, a
     * {@code RemoteReplyException} is thrown, possibly with the cause initialized to the reason of the failure.  Typically
     * this exception is thrown when serialization of the reply failed for some reason.  This exception type extends
     * {@code RemoteExecutionException} and can be treated similarly in most cases.
     * <p/>
     * If the request is sent and the remote side sends the reply successfully but there is an error reading the reply
     * locally, a {@code ReplyException} is thrown.  In this case the operation is known to have completed without error
     * but the actual detailed reply cannot be known.  In cases where the reply would be ignored anyway, this exception
     * type may be safely ignored (possibly logging it for informational purposes).  This exception is typically caused
     * by an {@code ObjectStreamException} thrown while unmarshalling the reply, though other causes are also possible.
     * <p/>
     * If the result of the operation is known to be impossible to ascertain, then an {@code IndeterminateOutcomeException}
     * is thrown.  Possible causes of this condition include (but are not limited to) the connection to the remote side
     * being unexpectedly broken, or the current thread being interrupted before the reply can be read.  In the latter
     * case, a best effort is automatically made to attempt to cancel the outstanding operation, though there is no
     * guarantee.
     * <p/>
     * If the request cannot be sent, some other {@code IOException} will be thrown with the reason, including (but not limited to)
     * attempting to call this method on a closed client, or {@code ObjectStreamException}s related to marshalling the
     * request locally or unmarshalling it remotely.  Such an exception indicates that the remote side did not receive
     * the request.
     * <p/>
     * All these exceptions (apart from {@code CancellationException}) extend {@code IOException} which makes it easier
     * to selectively catch only those exceptions that you need to implement special policy for, while relegating the
     * rest to common handlers.
     *
     * @param request the request to send
     *
     * @return the result of the request
     *
     * @throws CancellationException if the operation was cancelled asynchronously
     * @throws RemoteExecutionException if the remote handler threw an exception
     * @throws RemoteReplyException if the remote side was unable to send the response
     * @throws ReplyException if the operation succeeded but the reply cannot be read for some reason
     * @throws IndeterminateOutcomeException if the result of the operation cannot be ascertained
     * @throws ObjectStreamException if marshalling or unmarshalling some part of the request failed
     * @throws IOException if some other I/O error occurred while sending the request
     */
    O invoke(I request) throws IOException, CancellationException;

    /**
     * Send a reqest and block until a reply is received, requiring the reply to be of a specific type.
     * Otherwise this method functions identically to {@link #invoke(Object) invoke(I)}.
     *
     * @param request the reqest to send
     * @param expectedResultType the expected result type
     * @return the result of the request
     * @throws IOException if an I/O error occurred while sending the request
     * @throws CancellationException if the operation was cancelled asynchronously
     * @see #invoke(Object) invoke(I)
     */
    <T extends O> T invoke(I request, Class<T> expectedResultType) throws IOException, CancellationException;

    /**
     * Send a typed request and block until a reply is received.  If, for some reason, the given typed request object
     * is not a subtype of {@code <I>}, a {@code ClassCastException} is thrown.  Otherwise this method functions
     * identically to {@link #invoke(Object) invoke(I)}.
     *
     * @param request the request
     * @param <T> the specific reply subtype
     * @return the result of the request
     * @throws IOException if an I/O error occurred while sending the request
     * @throws CancellationException if the operation was cancelled asynchronously
     */
    <T extends O> T invokeTyped(TypedRequest<? extends I, T> request) throws IOException, CancellationException, ClassCastException;

    /**
     * Send a request asynchronously.  If the remote side manipulates a stream, it
     * may use a local policy to assign one or more thread(s) to handle the local end of that stream, or it may
     * fail with an exception (e.g. if this method is called on a client with no threads to handle streaming).
     * <p/>
     * This method <b>may</b> block until the request is sent; however once the request is sent, the rest of the request
     * delivery and processing is fully asynchronous.  The returned {@code IoFuture} object can be queried at a later time
     * to determine the result of the operation.  If the operation fails, one of the conditions described on the
     * {@link #invoke(Object) invoke(I)} method will result.  This condition can be determined by reading the status of
     * the {@code IoFuture} object or by attempting to read the result.
     *
     * @param request the request to send
     *
     * @return a future representing the result of the request
     *
     * @throws ObjectStreamException if marshalling some part of the request failed
     * @throws IOException if some other I/O error occurred while sending the request
     */
    IoFuture<? extends O> send(I request) throws IOException;

    /**
     * Send a request asynchronously, requiring the reply to be of a specific result type.
     * Otherwise this method functions identically to {@link #send(Object) send(I)}.
     *
     * @param request the request to send
     * @param expectedResultType the expected result type class
     * @param <T> the expected result type
     * @return a future representing the result of the request
     * @throws ObjectStreamException if marshalling some part of the request failed
     * @throws IOException if some other I/O error occurred while sending the request
     * @see #send(Object) send(I)
     */
    <T extends O> IoFuture<? extends T> send(I request, Class<T> expectedResultType) throws IOException;

    /**
     * Send a typed request asynchronously.  If, for some reason, the given typed request object
     * is not a subtype of {@code <I>}, a {@code ClassCastException} is thrown.  Otherwise
     * this method functions identically to {@link #send(Object) send(I)}.
     *
     * @param request the request to send
     * @param <T> the expected result type
     * @return a future representing the result of the request
     * @throws ObjectStreamException if marshalling some part of the request failed
     * @throws IOException if some other I/O error occurred while sending the request
     * @see #send(Object) send(I)
     */
    <T extends O> IoFuture<? extends T> sendTyped(TypedRequest<? extends I, T> request) throws IOException;
}
