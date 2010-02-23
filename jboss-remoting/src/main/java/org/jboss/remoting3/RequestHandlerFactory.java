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

import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.RequestHandler;

/**
 * A factory which creates request handlers corresponding to request listeners for a given client listener.
 */
final class RequestHandlerFactory<I, O> {
    private final Executor executor;
    private final ClientListener<? super I, ? extends O> clientListener;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    RequestHandlerFactory(final Executor executor, final ClientListener<? super I, ? extends O> clientListener, final Class<I> requestClass, final Class<O> replyClass) {
        this.executor = executor;
        this.clientListener = clientListener;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
    }

    static <I, O> RequestHandlerFactory<I, O> create(final Executor executor, final ClientListener<? super I, ? extends O> clientListener, final Class<I> requestClass, final Class<O> replyClass) {
        return new RequestHandlerFactory<I, O>(executor, clientListener, requestClass, replyClass);
    }

    RequestHandler createRequestHandler(final Connection connection) {
        final ClientContextImpl context = new ClientContextImpl(executor, connection);
        return new LocalRequestHandler<I, O>(executor, clientListener.handleClientOpen(context), context, requestClass, replyClass);
    }

    Class<I> getRequestClass() {
        return requestClass;
    }

    Class<O> getReplyClass() {
        return replyClass;
    }
}
