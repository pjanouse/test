package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.TwoNodes;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class NetworkFailuresCoreBridgesPrepare extends TwoNodes {

    public static final int PROXY_12_PORT = 43812;
    public static final int PROXY_21_PORT = 43821;

    public static final String BROADCAST_GROUP_ADDRESS_A = "233.1.2.1";
    public static final int BROADCAST_GROUP_PORT_A = 9876;

    public static final String BROADCAST_GROUP_ADDRESS_B = "233.1.2.2";
    public static final int BROADCAST_GROUP_PORT_B = 9876;

    public static final String DISCOVERY_GROUP_ADDRESS_A = "233.1.2.3";
    public static final int DISCOVERY_GROUP_PORT_A = 9876;

    public static final String DISCOVERY_GROUP_ADDRESS_B = "233.1.2.4";
    public static final int DISCOVERY_GROUP_PORT_B = 9876;

    public static final String MESSAGE_GROUPING = "MESSAGE_GROUPING";

    public static final String PROXY_CONNECTOR_NAME = "connector-to-proxy-directing-to-this-server";

    public static final String PROXY_SOCKET_BINDING_NAME = "binding-connect-to-this-server-through-remote-proxy";

    @Override
    @PrepareMethod(value = "NetworkFailuresCoreBridgesPrepare", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "NetworkFailuresCoreBridgesPrepare", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer1(params, container);
    }

    @Override
    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer1(params, container);
    }

    @Override
    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer2(params, container);
    }

    @Override
    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        afterPrepareContainer2(params, container);
    }

    protected void afterPrepareContainer1(Map<String, Object> params, Container container) throws Exception {
        boolean messageGrouping = PrepareUtils.getBoolean(params, MESSAGE_GROUPING, false);

        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingNameForDiscovery = messagingGroupSocketBindingName + "-" + container.getName();

        JMSOperations jmsOperations = container.getJmsOperations();

        prepareProxyConnector(params, jmsOperations, PROXY_21_PORT);

        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000, "connector-to-proxy-directing-to-this-server", "");

        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, messagingGroupSocketBindingNameForDiscovery, 10000);

        jmsOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, BROADCAST_GROUP_ADDRESS_A);
        jmsOperations.setMulticastPortOnSocketBinding(messagingGroupSocketBindingName, BROADCAST_GROUP_PORT_A);

        jmsOperations.createSocketBinding(messagingGroupSocketBindingNameForDiscovery, "public", DISCOVERY_GROUP_ADDRESS_A, DISCOVERY_GROUP_PORT_A);
        jmsOperations.setIdCacheSize(20000);

        jmsOperations.close();

        if (messageGrouping) {
            prepareMessageGrouping(container, "LOCAL");
        }
    }

    protected void afterPrepareContainer2(Map<String, Object> params, Container container) throws Exception {
        boolean messageGrouping = PrepareUtils.getBoolean(params, MESSAGE_GROUPING, false);

        String messagingGroupSocketBindingNameForDiscovery = MULTICAST_SOCKET_BINDING_NAME + "-" + container.getName();

        JMSOperations jmsOperations = container.getJmsOperations();

        prepareProxyConnector(params, jmsOperations, PROXY_12_PORT);

        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, MULTICAST_SOCKET_BINDING_NAME, 2000, "connector-to-proxy-directing-to-this-server", "");

        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, messagingGroupSocketBindingNameForDiscovery, 10000);

        jmsOperations.setMulticastAddressOnSocketBinding(MULTICAST_SOCKET_BINDING_NAME, BROADCAST_GROUP_ADDRESS_B);
        jmsOperations.setMulticastPortOnSocketBinding(MULTICAST_SOCKET_BINDING_NAME, BROADCAST_GROUP_PORT_B);

        jmsOperations.createSocketBinding(messagingGroupSocketBindingNameForDiscovery, "public", DISCOVERY_GROUP_ADDRESS_B, DISCOVERY_GROUP_PORT_B);
        jmsOperations.setIdCacheSize(20000);

        jmsOperations.close();

        if (messageGrouping) {
            prepareMessageGrouping(container, "REMOTE");
        }
    }

    protected void prepareProxyConnector(Map<String, Object> params, JMSOperations jmsOperations, int proxyPortIn) {
        int reconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.RECONNECT_ATTEMPTS);

        // every can connect to this server through proxy on 127.0.0.1:proxyPortIn
        jmsOperations.addRemoteSocketBinding(PROXY_SOCKET_BINDING_NAME, "127.0.0.1", proxyPortIn);

        if (ContainerUtils.isEAP7(getContainer(params, 1))) {
            jmsOperations.createHttpConnector(PROXY_CONNECTOR_NAME, PROXY_SOCKET_BINDING_NAME, null);
        } else {
            jmsOperations.createRemoteConnector(PROXY_CONNECTOR_NAME, PROXY_SOCKET_BINDING_NAME, null);
        }

        jmsOperations.removeClusteringGroup(CLUSTER_NAME);
        jmsOperations.setClusterConnections(CLUSTER_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000, true, PROXY_CONNECTOR_NAME);
        jmsOperations.setReconnectAttemptsForClusterConnection(CLUSTER_NAME, reconnectAttempts);

        jmsOperations.setHaForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, true);
        jmsOperations.setBlockOnAckForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, true);
        jmsOperations.setRetryIntervalForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, 1000L);
        jmsOperations.setRetryIntervalMultiplierForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, 1.0);
        jmsOperations.setReconnectAttemptsForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, -1);
    }

    protected void prepareMessageGrouping(Container container, String handlerType) {
        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;
        long groupTimeout = 500;
        long reaperPeriod = 750;

        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.addMessageGrouping("default", name, handlerType, address, timeout, groupTimeout, reaperPeriod);
        jmsOperations.close();
    }
}
