package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class NetworkFailuresCoreBridgesWithJGroupsPrepare extends NetworkFailuresCoreBridgesPrepare {

    public static final String GOSSHIP_ROUTER_ADDRESS = "0.0.0.0";
    public static final int GOSSHIP_ROUTER_PORT = 12001;

    @Override
    @PrepareMethod(value = "NetworkFailuresCoreBridgesWithJGroupsPrepare", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "NetworkFailuresCoreBridgesWithJGroupsPrepare", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    protected void afterPrepareContainer1(Map<String, Object> params, Container container) throws Exception {
        boolean messageGrouping = PrepareUtils.getBoolean(params, MESSAGE_GROUPING, false);

        JMSOperations jmsOperations = container.getJmsOperations();

        prepareProxyConnector(params, jmsOperations, PROXY_21_PORT);

        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, "udp", "udp", 2000, PROXY_CONNECTOR_NAME);

        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, 10000, "udp", "udp");

        // update UDP stack to use
//        <transport type="TUNNEL" shared="false">
//        <property name="enable_bundling">false</property>
//        <property name="gossip_router_hosts">0.0.0.0[12001]</property>
//        </transport>
        jmsOperations.addTransportToJGroupsStack("udp", "TUNNEL", GOSSHIP_ROUTER_ADDRESS, GOSSHIP_ROUTER_PORT, false);

        jmsOperations.close();

        if (messageGrouping) {
            prepareMessageGrouping(container, "LOCAL");
        }
    }

    protected void afterPrepareContainer2(Map<String, Object> params, Container container) throws Exception {
        boolean messageGrouping = PrepareUtils.getBoolean(params, MESSAGE_GROUPING, false);

        JMSOperations jmsOperations = container.getJmsOperations();

        prepareProxyConnector(params, jmsOperations, PROXY_12_PORT);

        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, "udp", "udp", 2000, PROXY_CONNECTOR_NAME);

        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, 10000, "udp", "udp");

        // update UDP stack to use
//        <transport type="TUNNEL" shared="false">
//        <property name="enable_bundling">false</property>
//        <property name="gossip_router_hosts">0.0.0.0[12001]</property>
//        </transport>
        jmsOperations.addTransportToJGroupsStack("udp", "TUNNEL", GOSSHIP_ROUTER_ADDRESS, GOSSHIP_ROUTER_PORT, false);

        jmsOperations.close();

        if (messageGrouping) {
            prepareMessageGrouping(container, "REMOTE");
        }
    }

}
