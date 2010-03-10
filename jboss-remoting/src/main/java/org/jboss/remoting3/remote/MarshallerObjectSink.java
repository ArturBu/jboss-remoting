/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3.remote;

import java.io.IOException;
import org.jboss.marshalling.Marshaller;
import org.jboss.remoting3.stream.ObjectSink;

final class MarshallerObjectSink<T> implements ObjectSink<T> {
    private final Marshaller marshaller;

    MarshallerObjectSink(final Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void accept(final T instance) throws IOException {
        marshaller.writeByte(RemoteProtocol.OSINK_OBJECT);
        marshaller.writeObject(instance);
    }

    public void flush() throws IOException {
        marshaller.writeByte(RemoteProtocol.OSINK_FLUSH);
        marshaller.flush();
    }

    public void close() throws IOException {
        marshaller.writeByte(RemoteProtocol.OSINK_CLOSE);
        marshaller.close();
    }
}
