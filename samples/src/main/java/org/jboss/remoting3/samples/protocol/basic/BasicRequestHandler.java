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

package org.jboss.remoting3.samples.protocol.basic;

import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RemoteRequestContext;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.marshalling.Marshaller;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.Executor;
import java.util.Queue;
import java.io.IOException;

/**
 *
 */
final class BasicRequestHandler extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.basic");

    private final AtomicInteger requestSequence;
    private final Lock reqLock;
    private final Marshaller marshaller;
    private final Queue<ReplyHandler> replyQueue;
    private final StreamChannel streamChannel;

    public BasicRequestHandler(final Lock reqLock, final Marshaller marshaller, final Queue<ReplyHandler> replyQueue, final StreamChannel streamChannel, final Executor executor) {
        super(executor);
        this.reqLock = reqLock;
        this.marshaller = marshaller;
        this.replyQueue = replyQueue;
        this.streamChannel = streamChannel;
        requestSequence = new AtomicInteger();
    }

    public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler replyHandler) {
        reqLock.lock();
        try {
            marshaller.write(2);
            marshaller.writeObject(request);
            marshaller.flush();
            final int id = requestSequence.getAndIncrement();
            replyQueue.add(replyHandler);
            return new RemoteRequestContext() {
                public void cancel() {
                    reqLock.lock();
                    try {
                        marshaller.write(3);
                        marshaller.writeInt(id);
                        marshaller.flush();
                    } catch (IOException e) {
                        log.error(e, "Error writing cancel request");
                        IoUtils.safeClose(BasicRequestHandler.this);
                    } finally {
                        reqLock.unlock();
                    }
                }
            };
        } catch (IOException e) {
            SpiUtils.safeHandleException(replyHandler, e);
            IoUtils.safeClose(this);
            return SpiUtils.getBlankRemoteRequestContext();
        } finally {
            reqLock.unlock();
        }
    }

    protected void closeAction() throws IOException {
        streamChannel.close();
    }

    public String toString() {
        return "basic protocol handler <" + Integer.toHexString(hashCode()) + ">";
    }
}
