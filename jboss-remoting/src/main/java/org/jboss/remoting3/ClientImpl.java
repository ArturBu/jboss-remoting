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

package org.jboss.remoting3;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.LocalReplyHandler;
import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class ClientImpl<I, O> extends AbstractHandleableCloseable<Client<I, O>> implements Client<I, O> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.client");

    private final RemoteRequestHandler handler;
    private final Class<I> requestClass;
    private final Class<O> replyClass;
    private final ClassLoader clientClassLoader;
    private final Attachments attachments = new AttachmentsImpl();

    private ClientImpl(final RemoteRequestHandler handler, final Executor executor, final Class<I> requestClass, final Class<O> replyClass, final ClassLoader clientClassLoader) {
        super(executor);
        this.handler = handler;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
        this.clientClassLoader = clientClassLoader;
    }

    static <I, O> ClientImpl<I, O> create(final RemoteRequestHandler handler, final Executor executor, final Class<I> requestClass, final Class<O> replyClass, final ClassLoader clientClassLoader) {
        final ClientImpl<I, O> ci = new ClientImpl<I, O>(handler, executor, requestClass, replyClass, clientClassLoader);
        handler.addCloseHandler(SpiUtils.closingCloseHandler(ci));
        return ci;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    protected void closeAction() throws IOException {
        handler.close();
    }

    public O invoke(final I request) throws IOException {
        return invoke(request, replyClass);
    }

    public <T extends O> T invoke(final I request, final Class<T> replyClass) throws IOException, CancellationException {
       if (! isOpen()) {
           throw new IOException("Client is not open");
       }
       log.trace("Client.invoke() sending request \"%s\"", request);
       final I actualRequest = castRequest(request);
       final QueueExecutor executor = new QueueExecutor();
       final FutureReplyImpl<T> futureReply = new FutureReplyImpl<T>(executor, replyClass, clientClassLoader);
       final LocalReplyHandler replyHandler = futureReply.getReplyHandler();
       final Cancellable requestContext = handler.receiveRequest(actualRequest, replyHandler);
       futureReply.setRemoteRequestContext(requestContext);
       futureReply.addNotifier(IoUtils.attachmentClosingNotifier(), executor);
       executor.runQueue();
       try {
           final T reply = futureReply.getInterruptibly();
           log.trace("Client.invoke() received reply \"%s\"", reply);
           return reply;
       } catch (InterruptedException e) {
           try {
               futureReply.cancel();
               throw new IndeterminateOutcomeException("The current thread was interrupted before the result could be read");
           } finally {
               Thread.currentThread().interrupt();
           }
       }
    }

    public <T extends O> T invokeTyped(final TypedRequest<? extends I, T> typedRequest) throws IOException, CancellationException {
        if (! isOpen()) {
            throw new IOException("Client is not open");
        }
        log.trace("Client.invoke() sending request \"%s\"", typedRequest);
        final I actualRequest = castRequest(typedRequest);
        final QueueExecutor executor = new QueueExecutor();
        final FutureReplyImpl<T> futureReply = new FutureReplyImpl<T>(executor, typedRequest, clientClassLoader);
        final LocalReplyHandler replyHandler = futureReply.getReplyHandler();
        final Cancellable requestContext = handler.receiveRequest(actualRequest, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        futureReply.addNotifier(IoUtils.attachmentClosingNotifier(), executor);
        executor.runQueue();
        try {
            final T reply = futureReply.getInterruptibly();
            log.trace("Client.invoke() received reply \"%s\"", reply);
            return reply;
        } catch (InterruptedException e) {
            try {
                futureReply.cancel();
                throw new IndeterminateOutcomeException("The current thread was interrupted before the result could be read");
            } finally {
                Thread.currentThread().interrupt();
            }
        }
    }

    public IoFuture<? extends O> send(final I request) throws IOException {
        return send(request, replyClass);
    }

    public <T extends O> IoFuture<? extends T> send(final I request, final Class<T> replyClass) throws IOException {
        if (! isOpen()) {
            throw new IOException("Client is not open");
        }
        log.trace("Client.send() sending request \"%s\"", request);
        final I actualRequest = castRequest(request);
        final FutureReplyImpl<T> futureReply = new FutureReplyImpl<T>(getExecutor(), replyClass, clientClassLoader);
        final LocalReplyHandler replyHandler = futureReply.getReplyHandler();
        final Cancellable requestContext = handler.receiveRequest(actualRequest, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        return futureReply;
    }

    public <T extends O> IoFuture<? extends T> sendTyped(final TypedRequest<? extends I, T> typedRequest) throws IOException {
        if (! isOpen()) {
            throw new IOException("Client is not open");
        }
        log.trace("Client.send() sending request \"%s\"", typedRequest);
        final I actualRequest = castRequest(typedRequest);
        final FutureReplyImpl<T> futureReply = new FutureReplyImpl<T>(getExecutor(), typedRequest, clientClassLoader);
        final LocalReplyHandler replyHandler = futureReply.getReplyHandler();
        final Cancellable requestContext = handler.receiveRequest(actualRequest, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        return futureReply;
    }

    /*
     * Since type is erased, it's possible that the wrong type was passed.
     */
    private I castRequest(final Object request) throws RemoteRequestException {
        try {
            return requestClass.cast(request);
        } catch (ClassCastException e) {
            throw new RemoteRequestException("Invalid request type sent (got <" + request.getClass().getName() + ">, expected <? extends " + requestClass.getName() + ">");
        }
    }

    public String toString() {
        return "client instance <" + Integer.toHexString(hashCode()) + ">";
    }

    RemoteRequestHandler getRequestHandler() {
        return handler;
    }

    Class<I> getRequestClass() {
        return requestClass;
    }

    Class<O> getReplyClass() {
        return replyClass;
    }
}
