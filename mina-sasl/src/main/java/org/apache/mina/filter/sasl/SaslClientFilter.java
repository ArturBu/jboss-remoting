package org.apache.mina.filter.sasl;

import java.io.IOException;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.IoSession;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * An {@code IoFilter} implementation for handling the client side of SASL.  The filter directly handles
 * the wrapping and unwrapping of messages that are encrypted or otherwise processed by a SASL mechanism.
 * Also, the filter indirectly handles the negotiation of protocol messages.
 * </p>
 * Since SASL often encodes the negotiation messages in the higher-level protocol, when a SASL challenge
 * message comes in, it is up to the protocol handler to decode these messages.  Once a challenge message
 * is decoded, it can be sent directly into this filter via the
 * {@link #handleSaslChallenge(org.apache.mina.common.IoSession, byte[])} method.  This method will evaluate
 * the challenge and, if necessary, send a response using the provided {@link org.apache.mina.filter.sasl.SaslMessageSender}
 * instance.
 * </p>
 * The completion of the negotiation may be tested with the {@link #isComplete(org.apache.mina.common.IoSession)} method.
 *
 * @see javax.security.sasl.SaslClient
 */
public final class SaslClientFilter extends AbstractSaslFilter {
    private static final AttributeKey SASL_CLIENT_KEY = new AttributeKey(SaslClientFilter.class, "saslClient");

    /**
     * Construct a new SASL client filter.
     *
     * @param messageSender the message sender, used to send response messages
     */
    public SaslClientFilter(final SaslMessageSender messageSender) {
        super(messageSender);
    }

    /**
     * Get the {@code SaslClient} instance for the given session.
     *
     * @param ioSession the session
     * @return the SASL client instance
     */
    public SaslClient getSaslClient(IoSession ioSession) {
        return (SaslClient) ioSession.getAttribute(SASL_CLIENT_KEY);
    }

    /**
     * Set the {@code SaslClient} instance for the given session.
     *
     * @param ioSession the session
     * @param saslClient the SASL client instance
     */
    public void setSaslClient(IoSession ioSession, SaslClient saslClient) {
        ioSession.setAttribute(SASL_CLIENT_KEY, saslClient);
    }

    /**
     * Handle a received (and decoded) SASL challenge message.  This method is called when the upper-level
     * protocol receives a complete SASL challenge message.  If a response is produced, it will be sent via the
     * provided {@link org.apache.mina.filter.sasl.SaslMessageSender}.
     *
     * @param ioSession the session
     * @param challengeData the received challenge data
     *
     * @throws IOException if an error occurs during processing of the message, or during the transmission of the response
     */
    public void handleSaslChallenge(IoSession ioSession, byte[] challengeData) throws IOException {
        final SaslClient client = getSaslClient(ioSession);
        final byte[] response = client.evaluateChallenge(challengeData);
        if (response != null) {
            sendSaslMessage(ioSession, response);
        }
    }

    /**
     * Send an initial response to the server to get things rolling.  Useful for protocols where the client initiates
     * authentication.
     *
     * @param ioSession the session
     * @throws IOException if an error occurs during transmission of the response
     */
    public void sendInitialResponse(IoSession ioSession) throws IOException {
        final SaslClient client = getSaslClient(ioSession);
        final byte[] response;
        if (client.hasInitialResponse()) {
            response = client.evaluateChallenge(new byte[0]);
        } else {
            response = new byte[0];
        }
        if (response != null) {
            sendSaslMessage(ioSession, response);
        }
    }

    /**
     * Determine whether SASL negotiation is complete for a session.
     *
     * @param ioSession the session
     * @return {@code true} if negotiation is complete
     *
     * @throws IOException if the completeness could not be determined
     *
     * @see javax.security.sasl.SaslClient#isComplete()
     */
    public boolean isComplete(IoSession ioSession) throws IOException {
        return getSaslClient(ioSession).isComplete();
    }

    /**
     * Get the quality of protection negotiated by this SASL session.
     *
     * @param ioSession the session
     * @return the negotiated quality of protection
     *
     * @see javax.security.sasl.Sasl#QOP
     */
    protected String getQop(final IoSession ioSession) {
        return (String) getSaslClient(ioSession).getNegotiatedProperty(Sasl.QOP);
    }

    protected byte[] wrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException {
        return getSaslClient(ioSession).wrap(data, offs, len);
    }

    protected byte[] unwrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException {
        return getSaslClient(ioSession).unwrap(data, offs, len);
    }
}
