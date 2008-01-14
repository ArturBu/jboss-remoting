package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.http.spi.HttpTransporter;
import org.jboss.cx.remoting.http.spi.HttpRemotingProtocolContext;
import org.jboss.cx.remoting.http.spi.HttpRemotingSessionContext;
import org.jboss.cx.remoting.http.spi.IncomingHttpRequest;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class HttpProtocolSupport {
    private final ProtocolHandlerFactory protocolHandlerFactory = new HttpProtocolHandlerFactory();
    private final List<HttpTransporter> transporters = new ArrayList<HttpTransporter>();

    private final Endpoint endpoint;
    private final ProtocolRegistration registration;
    private final ProtocolServerContext serverContext;

    public HttpProtocolSupport(final Endpoint endpoint) throws RemotingException {
        this.endpoint = endpoint;
        ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("http").setProtocolHandlerFactory(protocolHandlerFactory);
        registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
    }

    public HttpRemotingProtocolContext enrollTransporter(HttpTransporter transporter) {
        transporters.add(transporter);
        return new ProtocolContextImpl(transporter);
    }

    public final class HttpProtocolHandlerFactory implements ProtocolHandlerFactory {

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, CallbackHandler clientCallbackHandler) throws IOException {
            return null;
        }

        public void close() {
        }
    }

    public final class ProtocolContextImpl implements HttpRemotingProtocolContext {
        public HttpRemotingSessionContext locateSession(IncomingHttpRequest request) {
            return null;
        }
    }
}
