package org.jboss.cx.remoting.samples.simple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Remoting;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.security.sasl.Provider;
import org.jboss.cx.remoting.jrpp.JrppServer;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class JrppStreamExampleMain {

    public static void main(String[] args) throws IOException, RemoteExecutionException, URISyntaxException {
        Security.addProvider(new Provider());
        final StreamingRot13RequestListener listener = new StreamingRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple");
        try {
            final JrppServer jrppServer = Remoting.addJrppServer(endpoint, new InetSocketAddress(12345), listener, AttributeMap.EMPTY);
            try {
                Session session = endpoint.openSession(new URI("jrpp://localhost:12345"), AttributeMap.EMPTY, listener);
                try {
                    final Client<Reader,Reader> client = session.getRootClient();
                    try {
                        final String original = "The Secret Message\n";
                        final StringReader originalReader = new StringReader(original);
                        try {
                            final Reader reader = client.send(originalReader).get();
                            try {
                                final BufferedReader bufferedReader = new BufferedReader(reader);
                                try {
                                    final String secretLine = bufferedReader.readLine();
                                    System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), secretLine);
                                } finally {
                                    IoUtils.safeClose(bufferedReader);
                                }
                            } finally {
                                IoUtils.safeClose(reader);
                            }
                        } finally {
                            IoUtils.safeClose(originalReader);
                        }
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(session);
                }
            } finally {
                jrppServer.stop();
                jrppServer.destroy();
            }
        } finally {
            Remoting.closeEndpoint(endpoint);
        }

    }
}
