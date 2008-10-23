package org.jboss.remoting;

import org.jboss.remoting.RequestCancelHandler;
import org.jboss.remoting.RequestContext;
import org.jboss.remoting.ClientContext;
import java.io.IOException;

/**
 * A simple delegating wrapper for request context instances.
 *
 * @param <O> the reply type
 */
public class RequestContextWrapper<O> implements RequestContext<O> {
    protected final RequestContext<O> delegate;

    /**
     * Construct a new instance.  Calls will be sent to the given {@code delegate} by default.
     *
     * @param delegate the delegate client instance
     */
    protected RequestContextWrapper(final RequestContext<O> delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public ClientContext getContext() {
        return delegate.getContext();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void sendReply(O reply) throws IOException, IllegalStateException {
        delegate.sendReply(reply);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void sendFailure(String msg, Throwable cause) throws IOException, IllegalStateException {
        delegate.sendFailure(msg, cause);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void sendCancelled() throws IOException, IllegalStateException {
        delegate.sendCancelled();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void addCancelHandler(final RequestCancelHandler<O> requestCancelHandler) {
        delegate.addCancelHandler(requestCancelHandler);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void execute(final Runnable command) {
        delegate.execute(command);
    }
}
