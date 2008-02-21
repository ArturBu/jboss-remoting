package org.jboss.cx.remoting.http.spi;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface RemotingHttpSessionContext {
    void queueMessage(IncomingHttpMessage message);

    /**
     * Add a notifier to be called if there is data ready but there are no waiters for {@code getNextRequest}/{@code getNextReply}.
     * The notifier can use the {@code getNext*Immediate} methods to check for the next message.
     *
     * @param notifier the notifier
     */
    void addReadyNotifier(ReadyNotifier notifier);

    OutgoingHttpMessage getNextMessageImmediate();

    OutgoingHttpMessage getNextMessage(long timeoutMillis) throws InterruptedException;

}
