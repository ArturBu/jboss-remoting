package org.jboss.cx.remoting;

import java.util.concurrent.Executor;
import java.io.IOException;

/**
 * The context of a single request.
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
     * Send a reply back to the caller.
     *
     * @param reply the reply to send
     * @throws IOException if the transmission failed
     * @throws IllegalStateException if a reply was already sent
     */
    void sendReply(O reply) throws IOException, IllegalStateException;

    /**
     * Send a failure message back to the caller.
     *
     * @param msg a message describing the failure, if any (can be {@code null})
     * @param cause the failure cause, if any (can be {@code null})
     *
     * @throws IOException if the transmission failed
     * @throws IllegalStateException if a reply was already sent
     */
    void sendFailure(String msg, Throwable cause) throws IOException, IllegalStateException;

    /**
     * Send a cancellation message back to the client.
     *
     * @throws IOException if the message could not be sent (the client could not be notified about the cancellation)
     * @throws IllegalStateException if a reply was already sent
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
    void execute(Runnable command);
}
