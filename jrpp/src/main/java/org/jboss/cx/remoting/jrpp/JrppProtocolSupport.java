package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Executors;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.jrpp.mina.FramingIoFilter;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class JrppProtocolSupport {
    @SuppressWarnings ({"UnusedDeclaration"})
    private final Endpoint endpoint;
    private final ProtocolServerContext serverContext;
    private final IoHandler serverIoHandler = new SingleSessionIoHandlerDelegate(new ServerSessionHandlerFactory());
    private final Set<IoAcceptor> ioAcceptors = CollectionUtil.hashSet();
    private final ProtocolHandlerFactoryImpl protocolHandlerFactory = new ProtocolHandlerFactoryImpl();

    public JrppProtocolSupport(final Endpoint endpoint) throws RemotingException {
        final ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("jrpp").setProtocolHandlerFactory(protocolHandlerFactory);
        final ProtocolRegistration registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
        // todo - add a hook to protocol registration for deregister notification?
        this.endpoint = endpoint;
    }

    public void addServer(final SocketAddress address) throws IOException {
        // todo - make the acceptor managable so it can be started and stopped
        final IoAcceptor ioAcceptor = new NioSocketAcceptor();
        ioAcceptor.setDefaultLocalAddress(address);
        ioAcceptor.setHandler(serverIoHandler);
        ioAcceptor.getFilterChain().addLast("framing filter", new FramingIoFilter());
        ioAcceptor.getFilterChain().addLast("DEBUG", new LoggingFilter());
        ioAcceptor.bind();
        ioAcceptors.add(ioAcceptor);
    }

    public void shutdown() {
        for (IoAcceptor acceptor : ioAcceptors) {
            acceptor.unbind();
        }
        for (IoAcceptor acceptor : ioAcceptors) {
            acceptor.dispose();
        }
        ioAcceptors.clear();
        protocolHandlerFactory.connector.dispose();
    }

    private final class ServerSessionHandlerFactory implements SingleSessionIoHandlerFactory {
        public SingleSessionIoHandler getHandler(IoSession ioSession) throws IOException {
            final JrppConnection connection;
            connection = new JrppConnection(ioSession, serverContext, endpoint.getRemoteCallbackHandler());
            return connection.getIoHandler();
        }
    }

    /**
     * Protocol handler factory implementation.  There will ever only be one of these.
     */
    private final class ProtocolHandlerFactoryImpl implements ProtocolHandlerFactory, SingleSessionIoHandlerFactory {
        private final IoConnector connector;

        public ProtocolHandlerFactoryImpl() {
            final NioProcessor processor = new NioProcessor(Executors.newCachedThreadPool());
            connector = new NioSocketConnector(processor);
            connector.getFilterChain().addLast("framing filter", new FramingIoFilter());
            connector.getFilterChain().addLast("DEBUG", new LoggingFilter());
            connector.setHandler(new SingleSessionIoHandlerDelegate(this));
        }

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, final CallbackHandler clientCallbackHandler, final CallbackHandler serverCallbackHandler) throws IOException {
            // todo - add a connect timeout
            // todo - local connect addr
            final InetSocketAddress socketAddress = new InetSocketAddress(remoteUri.getHost(), remoteUri.getPort());
            final JrppConnection jrppConnection = new JrppConnection(connector, socketAddress, context, clientCallbackHandler);
            if (jrppConnection.waitForUp()) {
                return jrppConnection.getProtocolHandler();
            } else {
                throw new IOException("Failed to initiate a JRPP connection");
            }
        }

        public void close() {
            connector.dispose();
            for (IoAcceptor ioAcceptor : ioAcceptors) {
                ioAcceptor.dispose();
                ioAcceptors.remove(ioAcceptor);
            }
        }

        public SingleSessionIoHandler getHandler(IoSession session) throws Exception {
            return JrppConnection.getHandler(session);
        }
    }
}
