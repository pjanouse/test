package org.jboss.qa.hornetq.apps.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Interceptor on server side which check if incoming interceptor is called before outgoing interceptor.
 */
public class IncomingOutgoingMessagePacketInterceptor implements Interceptor {

    public static final String CHECK_PROP = "RECEIVED_AND_SENT_PROP";

    public static final String CHECK_VALUE = "received_and_sent";

    private static final String RECEIVED_PROP = "RECEIVED_PROP";

    private static final String RECEIVED_VALUE = "received";

    @Override
    public boolean intercept(Packet packet, RemotingConnection remotingConnection) throws ActiveMQException {
        if (packet instanceof SessionSendMessage) {
            SessionSendMessage msgPacket = (SessionSendMessage) packet;
            msgPacket.getMessage().putStringProperty(RECEIVED_PROP, RECEIVED_VALUE);
        } else if (packet instanceof SessionSendLargeMessage) {
            SessionSendLargeMessage msgPacket = (SessionSendLargeMessage) packet;
            msgPacket.getLargeMessage().putStringProperty(RECEIVED_PROP, RECEIVED_VALUE);
        } else if (packet instanceof SessionReceiveMessage) {
            SessionReceiveMessage msgPacket = (SessionReceiveMessage) packet;
            if (RECEIVED_VALUE.equals(msgPacket.getMessage().getStringProperty(RECEIVED_PROP))) {
                msgPacket.getMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
            }
        } else if (packet instanceof SessionReceiveLargeMessage) {
            SessionReceiveLargeMessage msgPacket = (SessionReceiveLargeMessage) packet;
            if (RECEIVED_VALUE.equals(msgPacket.getLargeMessage().getStringProperty(RECEIVED_PROP))) {
                msgPacket.getLargeMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
            }
        }
        return true;
    }
}
