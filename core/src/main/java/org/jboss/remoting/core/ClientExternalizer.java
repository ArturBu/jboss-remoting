/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.core;

import org.jboss.marshalling.Externalizer;
import org.jboss.marshalling.Creator;
import org.jboss.remoting.spi.RequestHandler;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;

/**
 *
 */
public final class ClientExternalizer implements Externalizer {

    private static final long serialVersionUID = 814228455390899997L;

    private final EndpointImpl endpoint;

    ClientExternalizer(final EndpointImpl endpoint) {
        this.endpoint = endpoint;
    }

    @SuppressWarnings({ "unchecked" })
    public void writeExternal(final Object o, final ObjectOutput output) throws IOException {
        output.writeObject(((ClientImpl)o).getRequestHandlerHandle().getResource());
    }

    @SuppressWarnings({ "unchecked" })
    public Object createExternal(final Class<?> aClass, final ObjectInput input, final Creator creator) throws IOException, ClassNotFoundException {
        final RequestHandler handler = (RequestHandler) input.readObject();
        return new ClientImpl(handler.getHandle(), endpoint.getExecutor());
    }

    public void readExternal(final Object o, final ObjectInput input) throws IOException, ClassNotFoundException {
        // no op
    }
}
