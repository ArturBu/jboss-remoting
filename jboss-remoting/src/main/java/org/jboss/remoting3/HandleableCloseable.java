package org.jboss.remoting3;

import java.io.Closeable;
import java.io.IOException;

/**
 * A Remoting resource that can be closed.
 *
 * @param <T> the type that is passed to the close handler
 *
 * @apiviz.exclude
 */
public interface HandleableCloseable<T> extends Closeable {

    /**
     * Close, waiting for any outstanding processing to finish.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;

    /**
     * Add a handler that will be called upon close.  The handler may be called before or after the close acutally
     * takes place.
     *
     * @param handler the close handler
     * @return a key which may be used to later remove this handler
     */
    Key addCloseHandler(CloseHandler<? super T> handler);

    /**
     * A key which may be used to remove this handler.
     *
     * @apiviz.exclude
     */
    interface Key {

        /**
         * Remove the registered handler.  Calling this method more than once has no additional effect.
         */
        void remove();
    }
}
