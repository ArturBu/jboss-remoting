package org.jboss.cx.remoting.spi;

import java.io.IOException;
import java.io.ObjectInput;

/**
 * A readable message.
 */
public interface ObjectMessageInput extends DataMessageInput, ObjectInput {
    /**
     * Read an object using the current context classloader, or, if there is no such classloader, the classloader
     * which loaded this interface.
     *
     * @return the object from the message
     * @throws ClassNotFoundException if the class of the object could not be resolved by the classloader
     * @throws IOException if an I/O error occurs
     */
    Object readObject() throws ClassNotFoundException, IOException;

    /**
     * Read an object using the given classloader.
     *
     * @param loader the classloader to use
     * @return the object from the message
     * @throws ClassNotFoundException if the class of the object could not be resolved by the classloader
     * @throws IOException if an I/O error occurs
     */
    Object readObject(ClassLoader loader) throws ClassNotFoundException, IOException;
}
