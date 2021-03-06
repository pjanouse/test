//TODO refactor for EAP 7
package org.jboss.qa.hornetq.apps.interceptors;


import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.kohsuke.MetaInfServices;


/**
 * Listener for intercepting send-message and receive-message packets and adding property sent-as-large-message or
 * received-as-large-message.
 * <p/>
 * Property is set to true for large messages and false to normal messages. Normal message in this context
 * can be large message that got compressed to size below min-large-message-size. Interceptor is used to test
 * large messages being sent/received as normal size messages if compressions allows.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@MetaInfServices
public class LargeMessagePacketInterceptorImplEAP7 implements LargeMessagePacketInterceptor {

    @Override
    public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
        if (packet instanceof SessionSendMessage) {
            SessionSendMessage msgPacket = (SessionSendMessage) packet;
            msgPacket.getMessage().putBooleanProperty(SENT_AS_LARGE_MSG_PROP, false);
        } else if (packet instanceof SessionSendLargeMessage) {
            SessionSendMessage msgPacket = (SessionSendMessage) packet;
            SessionSendLargeMessage largeMsgPacket = (SessionSendLargeMessage) packet;
            largeMsgPacket.getLargeMessage().putBooleanProperty(SENT_AS_LARGE_MSG_PROP, true);
        } else if (packet instanceof SessionReceiveMessage) {
            SessionReceiveMessage msgPacket = (SessionReceiveMessage) packet;
            msgPacket.getMessage().putBooleanProperty(RECEIVED_AS_LARGE_MSG_PROP, false);
        } else if (packet instanceof SessionReceiveLargeMessage) {
            SessionReceiveLargeMessage largeMsgPacket = (SessionReceiveLargeMessage) packet;
            largeMsgPacket.getLargeMessage().putBooleanProperty(RECEIVED_AS_LARGE_MSG_PROP, true);
        }

        // always let the packet go through
        return true;
    }

}

