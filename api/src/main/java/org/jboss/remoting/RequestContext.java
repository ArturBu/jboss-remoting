package org.jboss.remoting;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.io.IOException;

/**
 * The context of a single request.  A request listener is obligated to call exactly one of the three {@code send} methods
 * specified in this interface.
 *
 * @param <O> the reply type
 */
public interface RequestContext<O> extends Executor {
    /**
     * Get the context that the request came in on.
     *
     * @return the context
     */
    ClientContext getContext();

    /**
     * Determine whether the current request was cancelled.
     *
     * @return {@code true} if the request was cancelled
     */
    boolean isCancelled();

    /**
     * Send a reply back to the caller.  If transmission fails, an {@code IOException} is thrown from this method
     * and a reply is sent back to the client which will trigger a {@link RemoteReplyException} to be thrown.  If the
     * client connection is interrupted in such a way that the reply cannot reach the client, the client will (eventually)
     * receive an {@link IndeterminateOutcomeException}.
     *
     * @param reply the reply to send
     * @throws IOException if the transmission failed
     * @throws IllegalStateException if this or another of the {@code sendXXX()} methods was already invoked for this request
     */
    void sendReply(O reply) throws IOException, IllegalStateException;

    /**
     * Send a failure message back to the caller.  If the transmission succeeds, the client will receive a
     * {@link RemoteExecutionException} with the message initialized to {@code msg} and the cause initialized to
     * {@code cause}.  If the transmission fails, an {@code IOException} is thrown from this
     * method and the client will (eventually) receive an {@link IndeterminateOutcomeException}.
     *
     * @param msg a message describing the failure, if any (can be {@code null})
     * @param cause the failure cause, if any (can be {@code null})
     *
     * @throws IOException if the transmission failed
     * @throws IllegalStateException if this or another of the {@code sendXXX()} methods was already invoked for this request
     */
    void sendFailure(String msg, Throwable cause) throws IOException, IllegalStateException;

    /**
     * Send a cancellation message back to the client.  If the transmission succeeds, the client result will be an
     * acknowledgement of cancellation.  If the transmission fails, an {@code IOException} is thrown from this
     * method and the client will (eventually) receive an {@link IndeterminateOutcomeException}.
     *
     * @throws IOException if the message could not be sent (the client could not be notified about the cancellation)
     * @throws IllegalStateException if this or another of the {@code sendXXX()} methods was already invoked for this request
     */
    void sendCancelled() throws IOException, IllegalStateException;

    /**
     * Add a notifier to be called if a cancel request is received.  The notifier may be called from the current thread
     * or a different thread.  If the request has already been cancelled, the notifier will be called immediately.
     *
     * @param handler the cancel handler
     */
    void addCancelHandler(RequestCancelHandler<O> handler);

    /**
     * Execute a task in the context of this request.  This method can be used to continue execution of a request.  Any
     * tasks submitted to this executor will be interruptible in the event of cancellation.
     *
     * @param command the task to execute
     */
    void execute(Runnable command) throws RejectedExecutionException;
}
