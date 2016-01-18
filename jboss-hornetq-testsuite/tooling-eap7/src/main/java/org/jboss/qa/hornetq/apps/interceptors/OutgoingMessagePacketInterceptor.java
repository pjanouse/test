package org.jboss.qa.hornetq.apps.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Outgoing interceptor on server side
 */
public class OutgoingMessagePacketInterceptor implements Interceptor {

    public static final String CHECK_PROP = "SENT_PROP";

    public static final String CHECK_VALUE = "sent";

    @Override
    public boolean intercept(Packet packet, RemotingConnection remotingConnection) throws ActiveMQException {
        if (packet instanceof SessionReceiveMessage) {
            SessionReceiveMessage msgPacket = (SessionReceiveMessage) packet;
            msgPacket.getMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
        } else if (packet instanceof SessionReceiveLargeMessage) {
            SessionReceiveLargeMessage msgPacket = (SessionReceiveLargeMessage) packet;
            msgPacket.getLargeMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
        }
        return true;
    }
}
