package org.jboss.cx.remoting;

/**
 * A handler which is notified of a resource close.
 *
 * @param <T> the type of resource
 */
public interface CloseHandler<T> {

    /**
     * Receive a notification that the resource was closed.
     *
     * @param closed the closed resource
     */
    void handleClose(T closed);
}
