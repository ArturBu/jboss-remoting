/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
import java.nio.ByteBuffer;

import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageInputStream;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.streams.BufferPipeInputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class InboundMessage {
    final short messageId;
    final RemoteConnectionChannel channel;
    int inboundWindow;
    boolean closed;
    boolean cancelled;

    static final IntIndexer<InboundMessage> INDEXER = new IntIndexer<InboundMessage>() {
        public int getKey(final InboundMessage argument) {
            return argument.messageId & 0xffff;
        }

        public boolean equals(final InboundMessage argument, final int index) {
            return (argument.messageId & 0xffff) == index;
        }
    };

    InboundMessage(final short messageId, final RemoteConnectionChannel channel, int inboundWindow) {
        this.messageId = messageId;
        this.channel = channel;
        this.inboundWindow = inboundWindow;
    }

    final BufferPipeInputStream inputStream = new BufferPipeInputStream(new BufferPipeInputStream.InputHandler() {
        public void acknowledge(final Pooled<ByteBuffer> acked) throws IOException {
            int consumed = acked.getResource().position();
            openInboundWindow(consumed);
            Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_WINDOW_OPEN);
            try {
                ByteBuffer buffer = pooled.getResource();
                buffer.putInt(consumed); // Open window by buffer size
                buffer.flip();
                Channels.sendBlocking(channel.getRemoteConnection().getChannel(), buffer);
            } finally {
                pooled.free();
            }
        }

        public void close() throws IOException {
            sendAsyncClose();
        }
    });

    final MessageInputStream messageInputStream = new MessageInputStream() {
        public int read() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.read();
            }
        }

        public int read(final byte[] bytes, final int offs, final int length) throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.read(bytes, offs, length);
            }
        }

        public long skip(final long l) throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.skip(l);
            }
        }

        public int available() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.available();
            }
        }

        public void close() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                inputStream.close();
            }
        }
    };

    void sendAsyncClose() throws IOException {
        Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_ASYNC_CLOSE);
        channel.freeInboundMessage(messageId);
        try {
            ByteBuffer buffer = pooled.getResource();
            buffer.flip();
            Channels.sendBlocking(channel.getRemoteConnection().getChannel(), buffer);
        } finally {
            pooled.free();
        }
    }

    Pooled<ByteBuffer> allocate(byte protoId) {
        Pooled<ByteBuffer> pooled = channel.allocate(protoId);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort(messageId);
        return pooled;
    }


    void openInboundWindow(int consumed) {
        synchronized (inputStream) {
            inboundWindow += consumed;
        }
    }

    void closeInboundWindow(int produced) {
        synchronized (inputStream) {
            if ((inboundWindow -= produced) < 0) {
                channel.getRemoteConnection().handleException(new IOException("Input overrun"));
            }
        }
    }

    void handleIncoming(Pooled<ByteBuffer> pooledBuffer) {
        boolean eof;
        synchronized (inputStream) {
            if (closed) {
                // ignore
                pooledBuffer.free();
                return;
            }
            if (inboundWindow == 0) {
                pooledBuffer.free();
                // TODO log window overrun
                try {
                    sendAsyncClose();
                } catch (IOException e) {
                    // todo log it
                }
                return;
            }
            ByteBuffer buffer = pooledBuffer.getResource();
            closeInboundWindow(buffer.remaining() - 8);
            buffer.position(buffer.position() - 1);
            byte flags = buffer.get();

            eof = (flags & Protocol.MSG_FLAG_EOF) != 0;
            if (eof) {
                closed = true;
                channel.freeInboundMessage(messageId);
            }
            boolean cancelled = (flags & Protocol.MSG_FLAG_CANCELLED) != 0;
            if (cancelled) {
                this.cancelled = true;
            }
            inputStream.push(pooledBuffer);
            if (eof) {
                inputStream.pushEof();
            }
        }
    }

    void cancel() {
        synchronized (inputStream) {
            if (closed || cancelled) {
                return;
            }
            this.cancelled = true;
            this.closed = true;
            inputStream.pushEof();
        }
    }

    void dumpState(final StringBuilder b) {
        b.append("            ").append(String.format("Inbound message ID %04x, window %d\n", messageId & 0xFFFF, inboundWindow));
        b.append("            ").append("* flags: ");
        if (cancelled) b.append("cancelled ");
        if (closed) b.append("closed ");
        b.append('\n');
    }
}
