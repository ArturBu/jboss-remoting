package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import org.jboss.cx.remoting.spi.ByteMessageInput;

/**
 *
 */
public interface IncomingHttpMessage extends HttpMessage {
    ByteMessageInput getMessageData() throws IOException;
}
