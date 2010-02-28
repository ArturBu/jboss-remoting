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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Result;
import org.jboss.xnio.channels.ConnectedStreamChannel;

import javax.security.auth.callback.CallbackHandler;

final class ClientOpenListener implements ChannelListener<ConnectedStreamChannel<InetSocketAddress>> {

    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final Result<ConnectionHandlerFactory> factoryResult;
    private final CallbackHandler callbackHandler;

    public ClientOpenListener(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final Result<ConnectionHandlerFactory> factoryResult, final CallbackHandler callbackHandler) {
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.factoryResult = factoryResult;
        this.callbackHandler = callbackHandler;
    }

    public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
        try {
            channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            // ignore
        }
        final RemoteConnection connection = new RemoteConnection(connectionProviderContext.getExecutor(), channel, optionMap);

        // Send client greeting packet...
        final ByteBuffer buffer = connection.allocate();
        try {
            // length placeholder
            buffer.putInt(0);
            // version ID
            GreetingUtils.writeByte(buffer, RemoteProtocol.GREETING_VERSION, RemoteProtocol.VERSION);
            // that's it!
            buffer.flip();
            connection.sendBlocking(buffer);
        } catch (IOException e1) {
            // todo log it
            factoryResult.setException(e1);
            IoUtils.safeClose(connection);
        } finally {
            connection.free(buffer);
        }

        connection.setMessageHandler(new ClientGreetingHandler(connection, factoryResult, callbackHandler));
        // start up the read cycle
        channel.resumeReads();
    }
}