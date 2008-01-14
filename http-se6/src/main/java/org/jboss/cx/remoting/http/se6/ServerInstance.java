package org.jboss.cx.remoting.http.se6;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.InetAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.BasicAuthenticator;

import org.jboss.cx.remoting.http.spi.HttpRemotingSessionContext;
import org.jboss.cx.remoting.http.spi.IncomingHttpRequest;
import org.jboss.cx.remoting.http.spi.OutgoingHttpReply;
import org.jboss.cx.remoting.Header;

/**
 *
 */
public final class ServerInstance {
    private final HttpServer httpServer;

    public ServerInstance(String context, HttpServer httpServer) {
        this.httpServer = httpServer;
        final HttpContext httpContext = httpServer.createContext(context, new MyHttpHandler());
        httpContext.setAuthenticator(new BasicAuthenticator("Remote Access") {
            public boolean checkCredentials(final String user, final String password) {
                final char[] passwordChars = password.toCharArray();
                
                // todo - use endpoint callbacks
                return false;
            }
        });
    }

    public ServerInstance(String context, InetSocketAddress address, Executor executor) throws IOException {
        this(context, HttpServer.create(address, 0));
        httpServer.setExecutor(executor);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        // todo - magic #
        httpServer.stop(30);
    }

    private class MyHttpHandler implements HttpHandler {
        public void handle(final HttpExchange httpExchange) throws IOException {
            final URI requestURI = httpExchange.getRequestURI();
            final Headers requestHeaders = httpExchange.getRequestHeaders();
            final InetSocketAddress inetSocketAddress = httpExchange.getRemoteAddress();
            final InetAddress remoteAddress = inetSocketAddress.getAddress();
            final int remotePort = inetSocketAddress.getPort();
            HttpRemotingSessionContext sessionContext = null; // todo locate
            sessionContext.queueRequest(new IncomingHttpRequest() {
                public InetAddress getRemoteAddress() {
                    return remoteAddress;
                }

                public int getRemotePort() {
                    return remotePort;
                }

                public List<Header> getAllHeaders() {
                    return null;
                }

                public List<Header> getHeaders(String name) {
                    return null;
                }

                public Iterable<String> getHeaderNames() {
                    return httpExchange.getRequestHeaders().keySet();
                }

                public String getCharacterEncoding() {
                    return null;
                }

                public String getContentLength() {
                    return null;
                }

                public URI getRequestUri() {
                    return requestURI;
                }

                public String getMethod() {
                    return httpExchange.getRequestMethod();
                }

                public String getUserName() {
                    return null;
                }
            });
            // todo - WAIT untit the input stream is consumed? or - just don't close the output until the input is done
            // todo - consume all of input stream
            OutgoingHttpReply httpReply = null;
            try {
                httpReply = sessionContext.getNextReply(8000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (httpReply == null) {
                // send empty OK
            } else {
                // send reply
                final Headers responseHeaders = httpExchange.getResponseHeaders();
                httpExchange.sendResponseHeaders(200, 0); // todo - preset response size?
                final OutputStream outputStream = httpExchange.getResponseBody();
                httpReply.writeTo(outputStream);
            }
            httpExchange.close();
        }
    }
}
