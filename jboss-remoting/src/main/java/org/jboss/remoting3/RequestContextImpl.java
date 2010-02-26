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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.SpiUtils;

/**
 *
 */
final class RequestContextImpl<O> implements RequestContext<O> {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cancelLock = new Object();
    private final ReplyHandler replyHandler;
    private final ClientContextImpl clientContext;
    private final AtomicInteger taskCount = new AtomicInteger();

    // @protectedby cancelLock
    private boolean cancelled;
    // @protectedby cancelLock
    private Set<RequestCancelHandler<O>> cancelHandlers;
    private final RequestListenerExecutor interruptingExecutor;
    private final Class<O> replyClass;
    private final ClassLoader serviceClassLoader;

    RequestContextImpl(final ReplyHandler replyHandler, final ClientContextImpl clientContext, final Class<O> replyClass, final ClassLoader serviceClassLoader) {
        this.replyHandler = replyHandler;
        this.clientContext = clientContext;
        this.replyClass = replyClass;
        this.serviceClassLoader = serviceClassLoader;
        final Executor executor = clientContext.getExecutor();
        //noinspection ThisEscapedInObjectConstruction
        interruptingExecutor = new RequestListenerExecutor(executor, this);
    }

    public ClientContext getContext() {
        return clientContext;
    }

    public boolean isCancelled() {
        synchronized (cancelLock) {
            return cancelled;
        }
    }

    public void sendReply(final O reply) throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            final O actualReply;
            try {
                actualReply = replyClass.cast(reply);
            } catch (ClassCastException e) {
                SpiUtils.safeHandleException(replyHandler, new RemoteReplyException("Remote reply was the wrong type", e));
                throw e;
            }
            try {
                replyHandler.handleReply(actualReply);
            } catch (IOException e) {
                SpiUtils.safeHandleException(replyHandler, new RemoteReplyException("Remote reply failed", e));
                throw e;
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendFailure(final String msg, final Throwable cause) throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            replyHandler.handleException(new RemoteExecutionException(msg, cause));
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void sendCancelled() throws IOException, IllegalStateException {
        if (! closed.getAndSet(true)) {
            try {
                replyHandler.handleCancellation();
            } catch (IOException e) {
                // this is highly unlikely to succeed
                SpiUtils.safeHandleException(replyHandler, new RemoteReplyException("Remote cancellation acknowledgement failed", e));
            }
        } else {
            throw new IllegalStateException("Reply already sent");
        }
    }

    public void addCancelHandler(final RequestCancelHandler<O> handler) {
        synchronized (cancelLock) {
            if (cancelled) {
                SpiUtils.safeNotifyCancellation(handler, this);
            } else {
                if (cancelHandlers == null) {
                    cancelHandlers = new HashSet<RequestCancelHandler<O>>();
                }
                cancelHandlers.add(handler);
            }
        }
    }

    public void execute(final Runnable command) {
        interruptingExecutor.execute(new Runnable() {
            public void run() {
                final ClassLoader old;
                final SecurityManager sm = System.getSecurityManager();
                final ClassLoaderAction saveAction = new ClassLoaderAction(serviceClassLoader);
                old = sm != null ? AccessController.doPrivileged(saveAction) : saveAction.run();
                final ClassLoaderAction restoreAction = new ClassLoaderAction(old);
                try {
                    command.run();
                } finally {
                    if (sm != null) {
                        AccessController.doPrivileged(restoreAction);
                    } else {
                        restoreAction.run();
                    }
                }
            }
        });
    }

    private static final class ClassLoaderAction implements PrivilegedAction<ClassLoader> {

        private final ClassLoader classLoader;

        public ClassLoaderAction(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public ClassLoader run() {
            final Thread thread = Thread.currentThread();
            try {
                return thread.getContextClassLoader();
            } finally {
                thread.setContextClassLoader(classLoader);
            }
        }
    }

    protected void cancel() {
        synchronized (cancelLock) {
            if (! cancelled) {
                cancelled = true;
                if (cancelHandlers != null) {
                    for (final RequestCancelHandler<O> handler : cancelHandlers) {
                        interruptingExecutor.execute(new Runnable() {
                            public void run() {
                                SpiUtils.safeNotifyCancellation(handler, RequestContextImpl.this);
                            }
                        });
                    }
                    cancelHandlers = null;
                }
                interruptingExecutor.interruptAll();
            }
        }
    }

    void startTask() {
        taskCount.incrementAndGet();
    }

    void finishTask() {
        if (taskCount.decrementAndGet() == 0 && ! closed.getAndSet(true)) {
            // no response sent!  send back IndeterminateOutcomeException
            SpiUtils.safeHandleException(replyHandler, new IndeterminateOutcomeException("No reply was sent by the request listener"));
        }
    }
}
