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

import junit.framework.TestCase;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import org.jboss.remoting.AbstractRequestListener;
import org.jboss.remoting.RequestContext;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Client;
import org.jboss.remoting.test.support.LoggingHelper;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class EndpointTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    private static void safeStop(EndpointImpl endpoint) {
        try {
            endpoint.stop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void testCreate() throws Throwable {
        final EndpointImpl endpoint = new EndpointImpl();
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            endpoint.setExecutor(executorService);
            endpoint.start();
            endpoint.stop();
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testLocalClientInvoke() throws Throwable {
        final AtomicBoolean clientEndpointClosed = new AtomicBoolean(false);
        final AtomicBoolean clientClosed = new AtomicBoolean(false);
        final EndpointImpl endpoint = new EndpointImpl();
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final Object requestObj = new Object();
        final Object replyObj = new Object();
        try {
            endpoint.setExecutor(executorService);
            endpoint.start();
            try {
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        try {
                            context.sendReply(replyObj);
                        } catch (IOException e) {
                            try {
                                context.sendFailure(e.getMessage(), e);
                            } catch (IOException e1) {
                                fail("double fault");
                            }
                        }
                    }
                });
                final RequestHandler requestHandler = handle.getResource();
                try {
                    requestHandler.addCloseHandler(new CloseHandler<RequestHandler>() {
                        public void handleClose(final RequestHandler closed) {
                            clientEndpointClosed.set(true);
                        }
                    });
                    final Client<Object,Object> client = endpoint.createClient(requestHandler);
                    try {
                        client.addCloseHandler(new CloseHandler<Client<Object, Object>>() {
                            public void handleClose(final Client<Object, Object> closed) {
                                clientClosed.set(true);
                            }
                        });
                        assertEquals(replyObj, client.invoke(requestObj));
                        client.close();
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(requestHandler);
                }
            } finally {
                safeStop(endpoint);
            }
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
            assertTrue(clientEndpointClosed.get());
            assertTrue(clientClosed.get());
        } finally {
            executorService.shutdownNow();
        }
    }

    public void testLocalClientSend() throws Throwable {
        final AtomicBoolean clientEndpointClosed = new AtomicBoolean(false);
        final AtomicBoolean clientClosed = new AtomicBoolean(false);
        final EndpointImpl endpoint = new EndpointImpl();
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final Object requestObj = new Object();
        final Object replyObj = new Object();
        try {
            endpoint.setExecutor(executorService);
            endpoint.start();
            try {
                final Handle<RequestHandler> handle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                    public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                        assertEquals(request, requestObj);
                        try {
                            context.sendReply(replyObj);
                        } catch (IOException e) {
                            try {
                                context.sendFailure(e.getMessage(), e);
                            } catch (IOException e1) {
                                fail("double fault");
                            }
                        }
                    }
                });
                final RequestHandler requestHandler = handle.getResource();
                try {
                    requestHandler.addCloseHandler(new CloseHandler<RequestHandler>() {
                        public void handleClose(final RequestHandler closed) {
                            clientEndpointClosed.set(true);
                        }
                    });
                    final Client<Object,Object> client = endpoint.createClient(requestHandler);
                    try {
                        client.addCloseHandler(new CloseHandler<Client<Object, Object>>() {
                            public void handleClose(final Client<Object, Object> closed) {
                                clientClosed.set(true);
                            }
                        });
                        assertEquals(replyObj, client.send(requestObj).get());
                        client.close();
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(requestHandler);
                }
            } finally {
                safeStop(endpoint);
            }
            executorService.shutdown();
            assertTrue(executorService.awaitTermination(1L, TimeUnit.SECONDS));
            assertTrue(clientEndpointClosed.get());
            assertTrue(clientClosed.get());
        } finally {
            executorService.shutdownNow();
        }
    }
}
