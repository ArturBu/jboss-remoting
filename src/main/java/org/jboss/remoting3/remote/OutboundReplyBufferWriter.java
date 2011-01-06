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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.marshalling.NioByteOutput;
import org.jboss.xnio.Pool;
import org.jboss.xnio.log.Logger;

final class OutboundReplyBufferWriter implements NioByteOutput.BufferWriter {

    private final AtomicBoolean first = new AtomicBoolean(true);
    private final int id;
    private final boolean exception;
    private final InboundRequest inboundRequest;
    private static final Logger log = Loggers.main;

    OutboundReplyBufferWriter(final InboundRequest inboundRequest, final int id, final boolean exception) {
        this.inboundRequest = inboundRequest;
        this.id = id;
        this.exception = exception;
    }

    public ByteBuffer getBuffer() {
        final RemoteConnectionHandler connectionHandler = inboundRequest.getRemoteConnectionHandler();
        final Pool<ByteBuffer> bufferPool = connectionHandler.getBufferPool();
        final ByteBuffer buffer = bufferPool.allocate();
        log.trace("Allocated buffer %s for %s", buffer, this);
        buffer.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
        buffer.put(exception ? RemoteProtocol.REPLY_EXCEPTION : RemoteProtocol.REPLY);
        buffer.putInt(id);
        final boolean isFirst = first.getAndSet(false);
        if (isFirst) {
            buffer.put((byte) RemoteProtocol.MSG_FLAG_FIRST);
        } else {
            buffer.put((byte)0);
        }
        log.trace("Prepopulated buffer %s for %s", buffer, this);
        return buffer;
    }

    public void accept(final ByteBuffer buffer, final boolean eof) throws IOException {
        final RemoteConnectionHandler connectionHandler = inboundRequest.getRemoteConnectionHandler();
        try {
            inboundRequest.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
        try {
            if (eof) {
                buffer.put(7, (byte) (buffer.get(3) | RemoteProtocol.MSG_FLAG_LAST));
            }
            log.trace("Sending buffer %s for %s", buffer, this);
            connectionHandler.getRemoteConnection().sendBlocking(buffer, eof);
        } finally {
            connectionHandler.getBufferPool().free(buffer);
        }
    }

    public void flush() throws IOException {
        inboundRequest.getRemoteConnectionHandler().getRemoteConnection().flushBlocking();
    }

    public String toString() {
        return "Outbound reply buffer writer for " + inboundRequest;
    }
}
