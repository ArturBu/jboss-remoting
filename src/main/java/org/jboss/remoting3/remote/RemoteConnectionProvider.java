/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelThreadPool;
import org.xnio.ConnectionChannelThread;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.Result;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;

import javax.security.auth.callback.CallbackHandler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteConnectionProvider implements ConnectionProvider {

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final RemoteConnectionProvider.AcceptListener acceptListener = new RemoteConnectionProvider.AcceptListener();
    private final Xnio xnio;
    private final ChannelThreadPool<ReadChannelThread> readThreadPool;
    private final ChannelThreadPool<WriteChannelThread> writeThreadPool;
    private final ChannelThreadPool<ConnectionChannelThread> connectThreadPool;
    private final Pool<ByteBuffer> bufferPool;

    public RemoteConnectionProvider(final Xnio xnio, final ChannelThreadPool<ReadChannelThread> readThreadPool, final ChannelThreadPool<WriteChannelThread> writeThreadPool, final Pool<ByteBuffer> pool, final ChannelThreadPool<ConnectionChannelThread> threadPool) {
        this.xnio = xnio;
        this.readThreadPool = readThreadPool;
        this.writeThreadPool = writeThreadPool;
        bufferPool = pool;
        connectThreadPool = threadPool;
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        boolean secure = connectOptions.get(Options.SECURE, false);
        final InetSocketAddress destination;
        try {
            destination = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());
        } catch (UnknownHostException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        ChannelListener<ConnectedStreamChannel> openListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, bufferPool.allocate(), bufferPool.allocate());
                final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(new RemoteConnection(bufferPool, messageChannel, connectOptions), callbackHandler, AccessController.getContext());
                openListener.handleEvent(messageChannel);
            }
        };
        if (secure) {
            try {
                return xnio.connectSsl(destination, connectThreadPool.getThread(), readThreadPool.getThread(), writeThreadPool.getThread(), openListener, connectOptions);
            } catch (NoSuchProviderException e) {
                result.setException(new IOException(e));
                return IoUtils.nullCancellable();
            } catch (NoSuchAlgorithmException e) {
                result.setException(new IOException(e));
                return IoUtils.nullCancellable();
            }
        } else {
            return xnio.connectStream(destination, connectThreadPool.getThread(), readThreadPool.getThread(), writeThreadPool.getThread(), openListener, null, connectOptions);
        }
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    private final class ProviderInterface implements NetworkServerProvider {

        public ChannelListener<AcceptingChannel<ConnectedStreamChannel>> getServerListener(final OptionMap optionMap, final ServerAuthenticationProvider authenticationProvider) {
            return acceptListener;
        }
    }

    private final class AcceptListener implements ChannelListener<AcceptingChannel<ConnectedStreamChannel>> {

        public void handleEvent(final AcceptingChannel<ConnectedStreamChannel> channel) {
            ConnectedStreamChannel accepted = null;
            try {
                accepted = channel.accept(readThreadPool.getThread(), writeThreadPool.getThread());
            } catch (IOException e) {
                // todo log the exception
            }
            if (accepted == null) {
                channel.resumeAccepts();
                return;
            }
            // todo send server greeting
//            accepted.getReadSetter().set(new ServerConnectionGreetingListener());
            accepted.resumeReads();
        }
    }
}
