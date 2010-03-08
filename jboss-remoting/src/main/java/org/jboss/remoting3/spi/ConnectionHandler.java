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

package org.jboss.remoting3.spi;

import java.io.Closeable;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

/**
 * A connection to a foreign endpoint.  This interface is implemented by the protocol implementation.
 */
public interface ConnectionHandler extends Closeable {

    /**
     * Open a request handler.
     *
     * @param serviceType the service type string
     * @param groupName the group name string
     * @param result the result for the connected request handler
     * @param classLoader the class loader to use for replies
     * @param optionMap the options for this service
     * @return a handle which may be used to cancel the pending operation
     */
    Cancellable open(String serviceType, String groupName, Result<RemoteRequestHandler> result, ClassLoader classLoader, OptionMap optionMap);

    /**
     * Create a connector which may be used to communicate with the given local RequestHandler.  The connector
     * should only produce a result once it has passed to the remote side of this connection.
     *
     * @param localHandler the local handler
     * @return the connector
     */
    RequestHandlerConnector createConnector(LocalRequestHandler localHandler);
}
