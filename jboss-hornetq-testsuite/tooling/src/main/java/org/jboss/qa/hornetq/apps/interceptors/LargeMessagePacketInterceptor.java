package org.jboss.qa.hornetq.apps.interceptors;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Interceptor;
import org.hornetq.core.protocol.core.Packet;
import org.hornetq.spi.core.protocol.RemotingConnection;

/**
 * Created by mnovak on 3/17/15.
 */
public interface LargeMessagePacketInterceptor extends Interceptor {

    public static final String SENT_AS_LARGE_MSG_PROP = "sent-as-large-message";

    public static final String RECEIVED_AS_LARGE_MSG_PROP = "received-as-large-message";

    @Override
    boolean intercept(Packet packet, RemotingConnection connection) throws HornetQException;
}
