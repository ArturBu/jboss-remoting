package org.jboss.remoting3;

/**
 * A simple request listener implementation that implements all methods with no-operation implementations.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public abstract class AbstractRequestListener<I, O> implements RequestListener<I, O> {

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientOpen(final ClientContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceOpen(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceClose(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientClose(final ClientContext context) {
    }
}
