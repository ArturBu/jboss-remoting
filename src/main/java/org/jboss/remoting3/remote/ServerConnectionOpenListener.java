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
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Sequence;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SslChannel;
import org.xnio.sasl.SaslUtils;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import static org.jboss.remoting3.remote.RemoteAuthLogger.authLog;
import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServerConnectionOpenListener  implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final ServerAuthenticationProvider serverAuthenticationProvider;
    private final OptionMap optionMap;
    private final AtomicInteger retryCount = new AtomicInteger(8);

    ServerConnectionOpenListener(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final ServerAuthenticationProvider serverAuthenticationProvider, final OptionMap optionMap) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.serverAuthenticationProvider = serverAuthenticationProvider;
        this.optionMap = optionMap;
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        boolean ok = false;
        try {
            ByteBuffer sendBuffer = pooled.getResource();
            sendBuffer.put(Protocol.GREETING);
            sendBuffer.flip();
            connection.setReadListener(new Initial());
            connection.send(pooled);
            ok = true;
            return;
        } finally {
            if (! ok) pooled.free();
        }
    }

    final class Initial implements ChannelListener<ConnectedMessageChannel> {
        private final int version;
        private final boolean starttls;
        private final Map<String, ?> propertyMap;
        private final Map<String, SaslServerFactory> allowedMechanisms;

        Initial() {
            // Calculate our capabilities
            version = 0;
            final SslChannel sslChannel = connection.getSslChannel();
            final boolean channelSecure = Channels.getOption(connection.getChannel(), Options.SECURE, false);
            starttls = ! (sslChannel == null || channelSecure);
            final Map<String, ?> propertyMap;
            final Map<String, SaslServerFactory> allowedMechanisms;
            allowedMechanisms = new LinkedHashMap<String, SaslServerFactory>();
            propertyMap = SaslUtils.createPropertyMap(optionMap, channelSecure);
            final Sequence<String> saslMechs = optionMap.get(Options.SASL_MECHANISMS);
            final Set<String> restrictions = saslMechs == null ? null : new HashSet<String>(saslMechs);
            final Sequence<String> saslNoMechs = optionMap.get(Options.SASL_DISALLOWED_MECHANISMS);
            final Set<String> disallowed = saslNoMechs == null ? Collections.<String>emptySet() : new HashSet<String>(saslNoMechs);
            final Enumeration<SaslServerFactory> factories = Sasl.getSaslServerFactories();
            try {
                if ((restrictions == null || restrictions.contains("EXTERNAL")) && ! disallowed.contains("EXTERNAL")) {
                    // only enable external if there is indeed an external auth layer to be had
                    if (sslChannel != null) {
                        final Principal principal = sslChannel.getSslSession().getPeerPrincipal();
                        // only enable external auth if there's a peer principal (else it's just ANONYMOUS)
                        if (principal != null) {
                            allowedMechanisms.put("EXTERNAL", new ExternalSaslServerFactory(principal));
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            }
            while (factories.hasMoreElements()) {
                SaslServerFactory factory = factories.nextElement();
                for (String mechName : factory.getMechanismNames(propertyMap)) {
                    if ((restrictions == null || restrictions.contains(mechName)) && ! disallowed.contains(mechName) && ! allowedMechanisms.containsKey(mechName)) {
                        allowedMechanisms.put(mechName, factory);
                    }
                }
            }
            this.propertyMap = propertyMap;
            this.allowedMechanisms = allowedMechanisms;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                final int res;
                try {
                    res = channel.receive(buffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == 0) {
                    return;
                }
                buffer.flip();
                final byte msgType = buffer.get();
                switch (msgType) {
                    case Protocol.CAPABILITIES: {
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        boolean ok = false;
                        try {
                            ByteBuffer sendBuffer = pooled.getResource();
                            sendBuffer.put(Protocol.CAPABILITIES);
                            ProtocolUtils.writeByte(sendBuffer, Protocol.CAP_VERSION, version);
                            if (starttls) {
                                ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_STARTTLS);
                            }
                            for (String mechName : allowedMechanisms.keySet()) {
                                ProtocolUtils.writeString(sendBuffer, Protocol.CAP_SASL_MECH, mechName);
                            }
                            sendBuffer.flip();
                            connection.send(pooled);
                            ok = true;
                            return;
                        } finally {
                            if (! ok) pooled.free();
                        }
                    }
                    case Protocol.STARTTLS: {
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        boolean ok = false;
                        try {
                            ByteBuffer sendBuffer = pooled.getResource();
                            sendBuffer.put(starttls ? Protocol.STARTTLS : Protocol.NAK);
                            sendBuffer.flip();
                            connection.send(pooled);
                            if (starttls) {
                                try {
                                    connection.getSslChannel().startHandshake();
                                } catch (IOException e) {
                                    connection.handleException(e);
                                }
                            }
                            ok = true;
                            return;
                        } finally {
                            if (! ok) pooled.free();
                        }
                    }
                    case Protocol.AUTH_REQUEST: {
                        if (retryCount.decrementAndGet() < 1) {
                            // no more tries left
                            connection.handleException(new SaslException("Too many authentication failures; connection terminated"), false);
                            return;
                        }
                        final String mechName = Buffers.getModifiedUtf8(buffer);
                        final SaslServerFactory saslServerFactory = allowedMechanisms.get(mechName);
                        final CallbackHandler callbackHandler = serverAuthenticationProvider.getCallbackHandler(mechName);
                        if (saslServerFactory == null || callbackHandler == null) {
                            // reject
                            authLog.rejectedInvalidMechanism(mechName);
                            final Pooled<ByteBuffer> pooled = connection.allocate();
                            final ByteBuffer sendBuffer = pooled.getResource();
                            sendBuffer.put(Protocol.AUTH_REJECTED);
                            sendBuffer.flip();
                            connection.send(pooled);
                            return;
                        }
                        final SaslServer saslServer;
                        try {
                            saslServer = saslServerFactory.createSaslServer(mechName, "remote", connectionProviderContext.getEndpoint().getName(), propertyMap, callbackHandler);
                        } catch (SaslException e) {
                            connection.handleException(e);
                            return;
                        }
                        boolean ok = false;
                        boolean close = false;
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        try {
                            final ByteBuffer sendBuffer = pooled.getResource();
                            int p = sendBuffer.position();
                            try {
                                sendBuffer.put(Protocol.AUTH_COMPLETE);
                                if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer)) {
                                    connectionProviderContext.accept(new ConnectionHandlerFactory() {
                                        public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                            final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection);
                                            connection.setReadListener(new RemoteReadListener(connectionHandler, connection));
                                            return connectionHandler;
                                        }
                                    });
                                } else {
                                    sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                                    connection.setReadListener(new Authentication(saslServer));
                                }
                            } catch (SaslException e) {
                                sendBuffer.put(p, Protocol.AUTH_REJECTED);
                                if (retryCount.decrementAndGet() <= 0) {
                                    close = true;
                                }
                            }
                            sendBuffer.flip();
                            connection.send(pooled, close);
                            ok = true;
                            return;
                        } finally {
                            if (! ok) {
                                pooled.free();
                            }
                        }
                    }
                    default: {
                        log.unknownProtocolId(msgType);
                        break;
                    }
                }
            } finally {
                pooledBuffer.free();
            }
        }
    }

    final class Authentication implements ChannelListener<ConnectedMessageChannel> {

        private final SaslServer saslServer;

        Authentication(final SaslServer saslServer) {
            this.saslServer = saslServer;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                final int res;
                try {
                    res = channel.receive(buffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == 0) {
                    return;
                }
                buffer.flip();
                final byte msgType = buffer.get();
                switch (msgType) {
                    case Protocol.AUTH_RESPONSE: {
                        boolean ok = false;
                        boolean close = false;
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        try {
                            final ByteBuffer sendBuffer = pooled.getResource();
                            int p = sendBuffer.position();
                            try {
                                sendBuffer.put(Protocol.AUTH_COMPLETE);
                                if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer)) {
                                    connectionProviderContext.accept(new ConnectionHandlerFactory() {
                                        public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                            final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection);
                                            connection.setReadListener(new RemoteReadListener(connectionHandler, connection));
                                            return connectionHandler;
                                        }
                                    });
                                } else {
                                    sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                                }
                            } catch (SaslException e) {
                                sendBuffer.put(p, Protocol.AUTH_REJECTED);
                                connection.setReadListener(new Initial());
                            }
                            sendBuffer.flip();
                            connection.send(pooled, close);
                            ok = true;
                            return;
                        } finally {
                            if (! ok) {
                                pooled.free();
                            }
                        }
                    }
                    default: {
                        log.unknownProtocolId(msgType);
                        break;
                    }
                }
            } finally {
                pooledBuffer.free();
            }
        }
    }
}
