package org.jboss.cx.remoting.spi.stream;

import java.io.Closeable;
import java.io.IOException;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;

/**
 *
 */
public interface StreamContext extends Closeable {

    /**
     * Write a message.  The message is sent if/when the returned {@code MessageOutput} instance is committed.
     *
     * @return the message output instance
     * @throws IOException if an error occurs
     */
    ObjectMessageOutput writeMessage() throws IOException;

    /**
     * Indicate that this stream is exhausted.
     *
     * @throws IOException if the notification did not succeed
     */
    void close() throws IOException;
}
