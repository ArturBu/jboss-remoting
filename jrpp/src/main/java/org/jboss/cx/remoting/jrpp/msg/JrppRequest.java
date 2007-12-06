package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.Header;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppRequest extends JrppRequestBodyMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public JrppRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object body, final Header[] headers) {
        super(contextIdentifier, requestIdentifier, body, headers);
    }

    protected JrppRequest(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
