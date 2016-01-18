package org.jboss.qa.hornetq.apps.interceptors;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendLargeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Incoming interceptor on server side.
 */
public class IncomingMessagePacketInterceptor implements Interceptor {

    public static final String CHECK_PROP = "RECEIVED_PROP";

    public static final String CHECK_VALUE = "received";

    @Override
    public boolean intercept(Packet packet, RemotingConnection remotingConnection) throws ActiveMQException {
        if (packet instanceof SessionSendMessage) {
            SessionSendMessage msgPacket = (SessionSendMessage) packet;
            msgPacket.getMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
        } else if (packet instanceof SessionSendLargeMessage) {
            SessionSendLargeMessage msgPacket = (SessionSendLargeMessage) packet;
            msgPacket.getLargeMessage().putStringProperty(CHECK_PROP, CHECK_VALUE);
        }
        return true;
    }
}
