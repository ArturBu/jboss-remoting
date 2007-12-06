package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public final class JrppServiceActivateMessage extends JrppServiceMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppServiceActivateMessage(final ServiceIdentifier serviceIdentifier) {
        super(serviceIdentifier);
    }

    protected JrppServiceActivateMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
