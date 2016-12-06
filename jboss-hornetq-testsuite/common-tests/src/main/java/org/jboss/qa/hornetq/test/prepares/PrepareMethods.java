package org.jboss.qa.hornetq.test.prepares;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DBAllocatorUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.JdbcUtils;
import org.jboss.qa.hornetq.tools.ModuleUtils;
import org.jboss.qa.hornetq.tools.SlowConsumerPolicy;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PrepareMethods extends PrepareBase {

    public static final String PREPARE_ADDRESS_SETTINGS = "prepare-address-settings";
    public static final String PREPARE_DESTINATIONS = "prepare-destinations";
    public static final String PREPARE_DIVERTS = "prepare-diverts";
    public static final String PREPARE_CONNECTION_FACTORY = "prepare-connection-factory";
    public static final String PREPARE_CONNECTOR = "prepare-connector";
    public static final String PREPARE_CLUSTER = "prepare-cluster";
    public static final String PREPARE_SECURITY = "prepare-security";
    public static final String PREPARE_MISC = "prepare-misc";
    public static final String PREPARE_DATABASE = "prepare-database";
    public static final String PREPARE_JOURNALS_DIRECTORY = "prepare-journals-directory";
    public static final String PREPARE_HA = "prepare-ha";
    public static final String PREPARE_COLOCATED_BACKUP = "prepare-colocated-backup";

    @PrepareMethod(value = PREPARE_ADDRESS_SETTINGS, labels = {"EAP6", "EAP7"})
    public void prepareAddressSettings(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        String address = PrepareUtils.getString(params, PrepareParams.ADDRESS, "#");
        String addressFullPolicy = PrepareUtils.getString(params, PrepareParams.ADDRESS_FULL_POLICY, "PAGE");
        int maxSizeBytes = PrepareUtils.getInteger(params, PrepareParams.MAX_SIZE_BYTES, 10485760);
        long pageSizeBytes = PrepareUtils.getLong(params, PrepareParams.PAGE_SIZE_BYTES, 2097152l);
        int redeliveryDelay = PrepareUtils.getInteger(params, PrepareParams.REDELIVERY_DELAY, 0);
        int redistributionDelay = PrepareUtils.getInteger(params, PrepareParams.REDISTRIBUTION_DELAY, 1000);
        String expiryQueue = PrepareUtils.getString(params, PrepareParams.EXPIRY_QUEUE, "jms.queue.ExpiryQueue");
        String deadLetterQueue = PrepareUtils.getString(params, PrepareParams.DEAD_LETTER_QUEUE, "jms.queue.DLQ");
        int maxDeliveryAttempts = PrepareUtils.getInteger(params, PrepareParams.MAX_DELIVERY_ATTEMPTS, 10);
        boolean lastValueQueue = PrepareUtils.getBoolean(params, PrepareParams.LAST_VALUE_QUEUE, false);

        SlowConsumerPolicy slowConsumerPolicy = PrepareUtils.getEnum(params, PrepareParams.SLOW_CONSUMER_POLICY, SlowConsumerPolicy.class);
        Integer slowConsumerPolicyThreshold = PrepareUtils.getInteger(params, PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD);
        Integer slowConsumerPolicyCheckPeriod = PrepareUtils.getInteger(params, PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD);

        jmsOperations.removeAddressSettings(serverName, address);
        jmsOperations.addAddressSettings(serverName, address, addressFullPolicy, maxSizeBytes, redeliveryDelay, redistributionDelay, pageSizeBytes, expiryQueue, deadLetterQueue, maxDeliveryAttempts, lastValueQueue);

        if (slowConsumerPolicy != null) {
            jmsOperations.setSlowConsumerPolicy(serverName, address, slowConsumerPolicyThreshold, slowConsumerPolicy, slowConsumerPolicyCheckPeriod);
        }
    }

    /**
     * Accept additional queues and topics params in the form:
     * PREPARE_ADDITIONAL_TOPICS=myTopic1;myTopic2
     * PREPARE_ADDITIONAL_QUEUES=myQueue1;myQueue2
     *
     */
    @PrepareMethod(value = PREPARE_DESTINATIONS, labels = {"EAP6", "EAP7"})
    public void prepareDestinations(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);

        boolean prepareDestinations = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_DESTINATIONS, true);
        boolean prepareQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_QUEUE, true);
        boolean prepareTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_TOPIC, true);
        boolean prepareInQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_IN_QUEUE, true);
        boolean prepareInTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_IN_TOPIC, true);
        boolean prepareOutQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_OUT_QUEUE, true);
        boolean prepareOutTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_OUT_TOPIC, true);
        int destinationCount = PrepareUtils.getInteger(params, PrepareParams.DESTINATION_COUNT, 10);

        String[] additionalQueues = PrepareUtils.getString(params, PrepareParams.PREPARE_ADDITIONAL_QUEUES, "").split(";");
        String[] additionalTopics = PrepareUtils.getString(params, PrepareParams.PREPARE_ADDITIONAL_TOPICS, "").split(";");

        if (!prepareDestinations) {
            return;
        }

        if (prepareQueue) {
            jmsOperations.createQueue(PrepareConstants.QUEUE_NAME, PrepareConstants.QUEUE_JNDI);
        }
        if (prepareTopic) {
            jmsOperations.createTopic(PrepareConstants.TOPIC_NAME, PrepareConstants.TOPIC_JNDI);
        }
        if (prepareInQueue) {
            jmsOperations.createQueue(PrepareConstants.IN_QUEUE_NAME, PrepareConstants.IN_QUEUE_JNDI);
        }
        if (prepareOutQueue) {
            jmsOperations.createQueue(PrepareConstants.OUT_QUEUE_NAME, PrepareConstants.OUT_QUEUE_JNDI);
        }
        if (prepareInTopic) {
            jmsOperations.createTopic(PrepareConstants.IN_TOPIC_NAME, PrepareConstants.IN_TOPIC_JNDI);
        }
        if (prepareOutTopic) {
            jmsOperations.createTopic(PrepareConstants.OUT_TOPIC_NAME, PrepareConstants.OUT_TOPIC_JNDI);
        }

        for (int i = 0; i < destinationCount; i++) {
            jmsOperations.createTopic(PrepareConstants.TOPIC_NAME_PREFIX + i, PrepareConstants.TOPIC_JNDI_PREFIX + i);
            jmsOperations.createQueue(PrepareConstants.QUEUE_NAME_PREFIX + i, PrepareConstants.QUEUE_JNDI_PREFIX + i);
        }

        for(int i = 0; i < additionalQueues.length; i++) {
            if (!additionalQueues[i].isEmpty())
                jmsOperations.createQueue(additionalQueues[i] , "jms/queue/" + additionalQueues[i]);
        }

        for(int i=0; i< additionalTopics.length; i++) {
            if (!additionalTopics[i].isEmpty())
                jmsOperations.createTopic(additionalTopics[i], "jms/topic/" + additionalTopics[i]);
        }
    }

    /**
     * Accept params in the form:
     * DIVERT-A-ORIGIN-QUEUE=originQueue
     * DIVERT-A-DIVERTED-QUEUE=divertedQueue
     * DIVERT-A-EXCLUSIVE=false
     * DIVERT-B-ORIGIN-TOPIC=originTopic
     * DIVERT-B-DIVERTED-TOPIC=divertedTopic
     * DIVERT-B-EXCLUSIVE=false
     */
    @PrepareMethod(value = PREPARE_DIVERTS, labels = {"EAP6", "EAP7"})
    public void prepareDiverts(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        final String divertPrefix = "DIVERT-";
        final String originQueue = "ORIGIN-QUEUE";
        final String originTopic = "ORIGIN-TOPIC";
        final String divertedQueue = "DIVERTED-QUEUE";
        final String divertedTopic = "DIVERTED-TOPIC";
        final String exclusive = "EXCLUSIVE";

        Set<String> prefixes = new HashSet<String>();

        for (String key : params.keySet()) {
            if (key.startsWith(divertPrefix)) {
                int index = key.indexOf("-", key.indexOf("-") + 1);
                prefixes.add(key.substring(0, index + 1));
            }
        }

        for (String prefix : prefixes) {
            String originDestination = null;
            String divertedDestination = null;

            if (params.containsKey(prefix + originQueue)) {
                originDestination = "jms.queue." + PrepareUtils.getString(params, prefix + originQueue);
            }
            if (params.containsKey(prefix + originTopic)) {
                originDestination = "jms.topic." + PrepareUtils.getString(params, prefix + originTopic);
            }
            if (params.containsKey(prefix + divertedQueue)) {
                divertedDestination = "jms.queue." + PrepareUtils.getString(params, prefix + divertedQueue);
            }
            if (params.containsKey(prefix + divertedTopic)) {
                divertedDestination = "jms.topic." + PrepareUtils.getString(params, prefix + divertedTopic);
            }

            boolean isExclusive = PrepareUtils.getBoolean(params, prefix + exclusive);

            jmsOperations.addDivert(serverName, prefix, originDestination, divertedDestination, isExclusive, null, prefix, null);
        }
    }

    @PrepareMethod(value = PREPARE_CONNECTION_FACTORY, labels = {"EAP6", "EAP7"})
    public void prepareConnectionFactory(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);

        String invmType = PrepareUtils.getString(params, PrepareParams.INVM_CONNECTION_FACTORY_TYPE);

        boolean rcfHa = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_HA, true);
        boolean rcfBlockOnAck = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_BLOCK_ON_ACK, true);
        long rcfRetryInterval = PrepareUtils.getLong(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RETRY_INTERVAL, 2000l);
        double rcfRetryIntervalMultiplier = PrepareUtils.getDouble(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RETRY_INTERVAL_MULTIPLIER, 1.0);
        int rcfReconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, -1);
        boolean rcfCompression = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, false);
        long rfcMinLargeMessageSize = PrepareUtils.getLong(params, PrepareParams.REMOTE_CONNECTION_FACTORY_MIN_LARGE_MESSAGE_SIZE, 102400l);
        String rfcType = PrepareUtils.getString(params, PrepareParams.REMOTE_CONNECTION_FACTORY_TYPE);

        boolean pcfHa = PrepareUtils.getBoolean(params, PrepareParams.POOLED_CONNECTION_FACTORY_HA, false);
        boolean pcfBlockOnAck = PrepareUtils.getBoolean(params, PrepareParams.POOLED_CONNECTION_FACTORY_BLOCK_ON_ACK, false);
        long pcfRetryInterval = PrepareUtils.getLong(params, PrepareParams.POOLED_CONNECTION_FACTORY_RETRY_INTERVAL, 2000l);
        double pcfRetryIntervalMultiplier = PrepareUtils.getDouble(params, PrepareParams.POOLED_CONNECTION_FACTORY_RETRY_INTERVAL_MULTIPLIER, 1.0);
        int pcfReconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, -1);
        int pcfMinPoolSize = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, -1);
        int pcfMaxPoolSize = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, -1);
        long pcfMinLargeMessageSize = PrepareUtils.getLong(params, PrepareParams.POOLED_CONNECTION_FACTORY_MIN_LARGE_MESSAGE_SIZE, 102400l);

        if (invmType != null) {
            jmsOperations.setFactoryType(PrepareConstants.INVM_CONNECTION_FACTORY_NAME, invmType);
        }

        jmsOperations.setHaForConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfHa);
        jmsOperations.setBlockOnAckForConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfBlockOnAck);
        jmsOperations.setRetryIntervalForConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfRetryInterval);
        jmsOperations.setRetryIntervalMultiplierForConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfRetryIntervalMultiplier);
        jmsOperations.setReconnectAttemptsForConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfReconnectAttempts);
        jmsOperations.setCompressionOnConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rcfCompression);
        jmsOperations.setMinLargeMessageSizeOnConnectionFactory(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rfcMinLargeMessageSize);

        if (rfcType != null) {
            jmsOperations.setFactoryType(PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, rfcType);
        }

        String pooledConnectionFactoryName = ContainerUtils.isEAP6(getContainer(params, 1)) ? PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6 : PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7;

        jmsOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, pcfHa);
        jmsOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, pcfBlockOnAck);
        jmsOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, pcfRetryInterval);
        jmsOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, pcfRetryIntervalMultiplier);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, pcfReconnectAttempts);
        jmsOperations.setMinPoolSizeOnPooledConnectionFactory(pooledConnectionFactoryName, pcfMinPoolSize);
        jmsOperations.setMaxPoolSizeOnPooledConnectionFactory(pooledConnectionFactoryName, pcfMaxPoolSize);
        jmsOperations.setMinLargeMessageSizeOnPooledConnectionFactory(pooledConnectionFactoryName, pcfMinLargeMessageSize);
    }

    @PrepareMethod(value = PREPARE_CONNECTOR, labels = {"EAP6"})
    public void prepareConnectorEAP6(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        Constants.CONNECTOR_TYPE connectorType = Constants.CONNECTOR_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CONNECTOR_TYPE, "NETTY_BIO"));
        String acceptorName = PrepareUtils.getString(params, PrepareParams.ACCEPTOR_NAME, PrepareConstants.ACCEPTOR_NAME);
        String socketBindingName = PrepareUtils.getString(params, PrepareParams.SOCKET_BINDING_NAME, PrepareConstants.MESSAGING_SOCKET_BINDING_NAME);
        int socketBindingPort = PrepareUtils.getInteger(params, PrepareParams.SOCKET_BINDING_PORT, PrepareConstants.MESSAGING_PORT);

        if (!PrepareConstants.MESSAGING_SOCKET_BINDING_NAME.equals(socketBindingName)) {
            jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);
        }

        switch (connectorType) {
            case HTTP_CONNECTOR:
                throw new RuntimeException("Unsupported configuration");
            case NETTY_BIO:
                // add connector with BIO
                jmsOperations.removeRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME_EAP6);
                jmsOperations.createRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME, socketBindingName, null);
                // add acceptor wtih BIO
                jmsOperations.removeRemoteAcceptor(serverName, PrepareConstants.ACCEPTOR_NAME_EAP6);
                jmsOperations.createRemoteAcceptor(serverName, acceptorName, socketBindingName, null);
                break;
            case NETTY_NIO:
                // add connector with NIO
                jmsOperations.removeRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME_EAP6);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsOperations.createRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME, socketBindingName, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsOperations.removeRemoteAcceptor(serverName, PrepareConstants.ACCEPTOR_NAME_EAP6);
                jmsOperations.createRemoteAcceptor(serverName, acceptorName, socketBindingName, acceptorParamsNIO);
                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType.name());
        }

        if (PrepareConstants.SERVER_NAME.equals(serverName)) {
            jmsOperations.setConnectorOnConnectionFactory(serverName, PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setConnectorOnClusterGroup(serverName, PrepareConstants.CLUSTER_NAME, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setConnectorOnBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, Arrays.asList(PrepareConstants.CONNECTOR_NAME));
        } else {
            // If the serverName is not a default, these elements likely won't exist
            jmsOperations.setBroadCastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, "udp", PrepareConstants.JGROUPS_CHANNEL, 2000, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME, 1000, "udp", PrepareConstants.JGROUPS_CHANNEL);
            jmsOperations.setClusterConnections(serverName, PrepareConstants.CLUSTER_NAME, "jms", PrepareConstants.DISCOVERY_GROUP_NAME, false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME);
        }
    }

    @PrepareMethod(value = PREPARE_CONNECTOR, labels = {"EAP7"})
    public void prepareConnectorEAP7(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        Constants.CONNECTOR_TYPE connectorType = PrepareUtils.getEnum(params, PrepareParams.CONNECTOR_TYPE, Constants.CONNECTOR_TYPE.class, Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
        String acceptorName = PrepareUtils.getString(params, PrepareParams.ACCEPTOR_NAME, PrepareConstants.ACCEPTOR_NAME);
        String socketBindingName = PrepareUtils.getString(params, PrepareParams.SOCKET_BINDING_NAME, PrepareConstants.MESSAGING_SOCKET_BINDING_NAME);
        int socketBindingPort = PrepareUtils.getInteger(params, PrepareParams.SOCKET_BINDING_PORT, PrepareConstants.MESSAGING_PORT);

        jmsOperations.removeHttpAcceptor(serverName, PrepareConstants.ACCEPTOR_NAME_EAP7);
        jmsOperations.removeHttpConnector(serverName, PrepareConstants.CONNECTOR_NAME_EAP7);

        switch (connectorType) {
            case HTTP_CONNECTOR:
                Map<String, String> acceptorParams = new HashMap<String, String>();
                acceptorParams.put("batch-delay", "50");
                acceptorParams.put("direct-deliver", "false");

                jmsOperations.createHttpAcceptor(serverName, acceptorName, PrepareConstants.HTTP_LISTENER, acceptorParams);
                jmsOperations.createHttpConnector(serverName, PrepareConstants.CONNECTOR_NAME, PrepareConstants.HTTP_SOCKET_BINDING, acceptorParams, acceptorName);
                break;
            case NETTY_BIO:
                jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);

                // add connector with BIO
                jmsOperations.createRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME, socketBindingName, null);
                // add acceptor wtih BIO
                jmsOperations.createRemoteAcceptor(serverName, acceptorName, socketBindingName, null);
                break;
            case NETTY_NIO:
                jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);
                // add connector with NIO
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsOperations.createRemoteConnector(serverName, PrepareConstants.CONNECTOR_NAME, socketBindingName, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsOperations.createRemoteAcceptor(serverName, acceptorName, socketBindingName, acceptorParamsNIO);

                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType.name());
        }

        if (PrepareConstants.SERVER_NAME.equals(serverName)) {
            jmsOperations.setConnectorOnConnectionFactory(serverName, PrepareConstants.REMOTE_CONNECTION_FACTORY_NAME, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setConnectorOnClusterGroup(serverName, PrepareConstants.CLUSTER_NAME, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setConnectorOnBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, Arrays.asList(PrepareConstants.CONNECTOR_NAME));
        } else {
            // If the serverName is not a default, these elements likely won't exist
            jmsOperations.setBroadCastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, "udp", PrepareConstants.JGROUPS_CHANNEL, 2000, PrepareConstants.CONNECTOR_NAME);
            jmsOperations.setDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME, 1000, "udp", PrepareConstants.JGROUPS_CHANNEL);
            jmsOperations.setClusterConnections(serverName, PrepareConstants.CLUSTER_NAME, "jms", PrepareConstants.DISCOVERY_GROUP_NAME, false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME);
        }
    }

    @PrepareMethod(value = PREPARE_CLUSTER, labels = {"EAP6", "EAP7"})
    public void prepareCluster(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        Constants.CLUSTER_TYPE clusterType = PrepareUtils.getEnum(params, PrepareParams.CLUSTER_TYPE, Constants.CLUSTER_TYPE.class, Constants.CLUSTER_TYPE.DEFAULT);
        long callTimeout = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_CALL_TIMEOUT, 30000l);
        long checkPeriod = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_CHECK_PERIOD, 30000l);
        long ttl = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_TTL, 60000l);
        Boolean forwardWhenNoConsumer = PrepareUtils.getBoolean(params, PrepareParams.FORWARD_WHEN_NO_CONSUMER);

        if (clusterType == Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            // Do nothing
            return;
        }

        if (clusterType == Constants.CLUSTER_TYPE.DEFAULT) {
            Container container = getContainer(params, 1);
            if (ContainerUtils.isEAP6(container)) {
                clusterType = Constants.CLUSTER_TYPE.MULTICAST;
            } else {
                clusterType = Constants.CLUSTER_TYPE.JGROUPS_DISCOVERY;
            }
        }

        jmsOperations.setClusterConnectionCallTimeout(serverName, PrepareConstants.CLUSTER_NAME, callTimeout);
        jmsOperations.setClusterConnectionCheckPeriod(serverName, PrepareConstants.CLUSTER_NAME, checkPeriod);
        jmsOperations.setClusterConnectionTTL(serverName, PrepareConstants.CLUSTER_NAME, ttl);

        if (forwardWhenNoConsumer != null) {
            jmsOperations.setForwardWhenNoConsumers(serverName, PrepareConstants.CLUSTER_NAME, forwardWhenNoConsumer);
        }

        switch (clusterType) {
            case MULTICAST:
                jmsOperations.removeBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, 2000, PrepareConstants.CONNECTOR_NAME, "");
                jmsOperations.removeDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME, PrepareConstants.MULTICAST_SOCKET_BINDING_NAME, 10000);
                break;
            case JGROUPS_DISCOVERY:
                jmsOperations.removeBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, "udp", PrepareConstants.JGROUPS_CHANNEL, 2000, PrepareConstants.CONNECTOR_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME, 10000, "udp", PrepareConstants.JGROUPS_CHANNEL);
                break;
            case JGROUPS_DISCOVERY_TCP:
                jmsOperations.removeBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME, "tcp", PrepareConstants.JGROUPS_CHANNEL, 2000, PrepareConstants.CONNECTOR_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME, 10000, "tcp", PrepareConstants.JGROUPS_CHANNEL);
                break;
            case NONE:
                jmsOperations.removeBroadcastGroup(serverName, PrepareConstants.BROADCAST_GROUP_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, PrepareConstants.DISCOVERY_GROUP_NAME);
                jmsOperations.removeClusteringGroup(serverName, PrepareConstants.CLUSTER_NAME);
                break;
            default: throw new IllegalArgumentException("Unsupported cluster type: " + clusterType.name());
        }
    }

    @PrepareMethod(value = PREPARE_SECURITY, labels = {"EAP6", "EAP7"})
    public void prepareSecurity(Map<String, Object> params) throws Exception {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        boolean securityEnabled = PrepareUtils.getBoolean(params, PrepareParams.ENABLE_SECURITY, false);

        if (!securityEnabled) {
            jmsOperations.disableSecurity(serverName);
            return;
        }

        boolean guestCreateDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_CREATE_DURABLE_QUEUE, false);
        boolean guestCreateNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_CREATE_NON_DURABLE_QUEUE, false);
        boolean guestDeleteDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_DELETE_DURABLE_QUEUE, false);
        boolean guestDeleteNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_DELETE_NON_DURABLE_QUEUE, false);
        boolean guestManage = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_MANAGE, false);
        boolean guestSend = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_SEND, true);
        boolean guestConsume = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_GUEST_CONSUME, false);

        boolean adminCreateDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_CREATE_DURABLE_QUEUE, true);
        boolean adminCreateNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_CREATE_NON_DURABLE_QUEUE, true);
        boolean adminDeleteDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_DELETE_DURABLE_QUEUE, true);
        boolean adminDeleteNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_DELETE_NON_DURABLE_QUEUE, true);
        boolean adminManage = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_MANAGE, true);
        boolean adminSend = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_SEND, false);
        boolean adminConsume = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_ADMIN_CONSUME, false);

        boolean usersCreateDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_CREATE_DURABLE_QUEUE, false);
        boolean usersCreateNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_CREATE_NON_DURABLE_QUEUE, true);
        boolean usersDeleteDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_DELETE_DURABLE_QUEUE, false);
        boolean usersDeleteNonDurableQueue = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_DELETE_NON_DURABLE_QUEUE, true);
        boolean usersManage = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_MANAGE, false);
        boolean usersSend = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_SEND, true);
        boolean usersConsume = PrepareUtils.getBoolean(params, PrepareParams.SECURITY_USERS_CONSUME, true);

        jmsOperations.setSecurityEnabled(true);
        jmsOperations.setAuthenticationForNullUsers(true);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsOperations.rewriteLoginModule("Remoting", opts);
        jmsOperations.rewriteLoginModule("RealmDirect", opts);

        // set security persmissions for roles admin,users - user is already there
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", guestCreateDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", guestCreateNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", guestDeleteDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", guestDeleteNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", guestManage);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", guestSend);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", guestConsume);

        jmsOperations.addRoleToSecuritySettings("#", "admin");
        jmsOperations.addRoleToSecuritySettings("#", "users");

        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", adminCreateDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", adminCreateNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", adminDeleteDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", adminDeleteNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", adminManage);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", adminSend);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", adminConsume);

        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", usersCreateDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", usersCreateNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", usersDeleteDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", usersDeleteNonDurableQueue);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", usersManage);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", usersSend);
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", usersConsume);

        UsersSettings.forDefaultEapServer()
                .withUser(PrepareConstants.USER_NAME, PrepareConstants.USER_PASS, "users")
                .withUser(PrepareConstants.ADMIN_NAME, PrepareConstants.ADMIN_PASS, "admin")
                .create();
    }

    @PrepareMethod(value = PREPARE_MISC, labels = {"EAP6", "EAP7"})
    public void prepareMisc(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        String journalType = PrepareUtils.getString(params, PrepareParams.JOURNAL_TYPE, "ASYNCIO");
        long journalFileSize = PrepareUtils.getLong(params, PrepareParams.JOURNAL_FILE_SIZE, 10 * 1024 * 1024l);
        long transactionTimeout = PrepareUtils.getLong(params, PrepareParams.TRANSACTION_TIMEOUT, 300000l);
        boolean jmxManagementEnabled = PrepareUtils.getBoolean(params, PrepareParams.JMX_MANAGEMENT_ENABLED, false);
        Boolean autoCreateJMSQueues = PrepareUtils.getBoolean(params, PrepareParams.AUTO_CREATE_JMS_QUEUES);
        Boolean autoDeleteJMSQueues = PrepareUtils.getBoolean(params, PrepareParams.AUTO_DELETE_JMS_QUEUES);

        jmsOperations.setJournalType(serverName, journalType);
        jmsOperations.setJournalFileSize(serverName, journalFileSize);
        jmsOperations.setTransactionTimeout(serverName, transactionTimeout);
        jmsOperations.setJmxManagementEnabled(jmxManagementEnabled);

        if (autoCreateJMSQueues != null) {
            jmsOperations.setAutoCreateJMSQueue(autoCreateJMSQueues);
        }
        if (autoDeleteJMSQueues != null) {
            jmsOperations.setAutoDeleteJMSQueue(autoDeleteJMSQueues);
        }
    }

    @PrepareMethod(value = PREPARE_DATABASE, labels = {"EAP6", "EAP7"})
    public void prepareDatabase(Map<String, Object> params) throws Exception {
        Container container = getContainer(params);
        String serverName = getServerName(params);

        String database = PrepareUtils.getString(params, PrepareParams.DATABASE);

        if (database == null) {
            return;
        }

        String journalBindingsTable = PrepareUtils.getString(params, PrepareParams.JOURNAL_BINDINGS_TABLE);
        String journalMessagesTable = PrepareUtils.getString(params, PrepareParams.JOURNAL_MESSAGES_TABLE);
        String journalLargeMessagesTable = PrepareUtils.getString(params, PrepareParams.JOURNAL_LARGE_MESSAGES_TABLE);

        JdbcUtils.downloadJdbcDriver(container, database);

        Thread.sleep(10000);

        Map<String, String> properties = DBAllocatorUtils.allocateDatabase(database);

        JMSOperations jmsOperations = container.getJmsOperations();

        String moduleName = "test.sql-provider-factory";
        String jndiName = "java:jboss/datasources/messaging";
        String poolName = "messaging";
        String driver = "ojdbc7.jar";
        String connectionUrl = properties.get("db.jdbc_url");
        String userName = properties.get("db.username");
        String password = properties.get("db.password");

        URL sqlProviderFactory = getClass().getResource("/artemis-sql-provider-factory-oracle.jar");
        ModuleUtils.registerModule(container, moduleName, sqlProviderFactory, Arrays.asList("org.apache.activemq.artemis"));

        jmsOperations.createDataSource(jndiName, poolName, driver, connectionUrl, userName, password);
        jmsOperations.setJournalDataSource(serverName, poolName);
        jmsOperations.setJournalSqlProviderFactoryClass(serverName, "com.oracle.OracleSQLProviderFactory", moduleName);

        if (journalBindingsTable != null) {
            jmsOperations.setJournalBindingsTable(serverName, journalBindingsTable);
        }
        if (journalMessagesTable != null) {
            jmsOperations.setJournalMessagesTable(serverName, journalMessagesTable);
        }
        if (journalLargeMessagesTable != null) {
            jmsOperations.setJournalLargeMessagesTable(serverName, journalLargeMessagesTable);
        }

        jmsOperations.close();
    }

    @PrepareMethod(value = PREPARE_JOURNALS_DIRECTORY, labels = {"EAP6"})
    public void prepareJournalsDirectoryEAP6(Map<String, Object> params) throws Exception {
        Container container = getContainer(params);
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);
        String journalsDirectory = PrepareUtils.getString(params, PrepareParams.JOURNALS_DIRECTORY);

        if (journalsDirectory == null) {
            return;
        }

        jmsOperations.setBindingsDirectory(serverName, journalsDirectory);
        jmsOperations.setPagingDirectory(serverName, journalsDirectory);
        jmsOperations.setLargeMessagesDirectory(serverName, journalsDirectory);
        jmsOperations.setJournalDirectory(serverName, journalsDirectory);

        jmsOperations.close();
        container.restart();
        Thread.sleep(10000);
        setJMSOperations(params, container.getJmsOperations());
    }

    @PrepareMethod(value = PREPARE_JOURNALS_DIRECTORY, labels = {"EAP7"})
    public void prepareJournalsDirectoryEAP7(Map<String, Object> params) throws Exception {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);
        String journalsDirectory = PrepareUtils.getString(params, PrepareParams.JOURNALS_DIRECTORY);

        if (journalsDirectory == null) {
            return;
        }

        jmsOperations.setBindingsDirectory(serverName, journalsDirectory);
        jmsOperations.setPagingDirectory(serverName, journalsDirectory);
        jmsOperations.setLargeMessagesDirectory(serverName, journalsDirectory);
        jmsOperations.setJournalDirectory(serverName, journalsDirectory);
    }

    @PrepareMethod(value = PREPARE_HA, labels = {"EAP6"})
    public void prepareHAEAP6(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        Constants.HA_TYPE haType = PrepareUtils.getEnum(params, PrepareParams.HA_TYPE, Constants.HA_TYPE.class, Constants.HA_TYPE.NONE);
        int maxSavedReplicatedJournalSize = PrepareUtils.getInteger(params, PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, 2);
        String replicationGroupName = PrepareUtils.getString(params, PrepareParams.REPLICATION_GROUP_NAME);

        switch (haType) {
            case NONE:
                break;
            case SHARED_STORE_MASTER:
                jmsOperations.setSharedStore(serverName, true);
                jmsOperations.setFailoverOnShutdown(true, serverName);
                break;

            case SHARED_STORE_SLAVE:
                jmsOperations.setBackup(serverName, true);
                jmsOperations.setSharedStore(serverName, true);
                jmsOperations.setFailoverOnShutdown(true, serverName);
                jmsOperations.setAllowFailback(serverName, true);
                break;

            case REPLICATION_MASTER:
                jmsOperations.setSharedStore(serverName, false);
                jmsOperations.setFailoverOnShutdown(true, serverName);
                jmsOperations.setCheckForLiveServer(true, serverName);
                jmsOperations.setAllowFailback(serverName, true);
                jmsOperations.setMaxSavedReplicatedJournals(serverName, maxSavedReplicatedJournalSize);
                jmsOperations.setBackupGroupName(replicationGroupName, serverName);
                break;

            case REPLICATION_SLAVE:
                jmsOperations.setBackup(serverName, true);
                jmsOperations.setSharedStore(serverName, false);
                jmsOperations.setFailoverOnShutdown(true, serverName);
                jmsOperations.setCheckForLiveServer(true, serverName);
                jmsOperations.setAllowFailback(serverName, true);
                jmsOperations.setMaxSavedReplicatedJournals(serverName, maxSavedReplicatedJournalSize);
                jmsOperations.setBackupGroupName(replicationGroupName, serverName);
                break;
        }
    }

    @PrepareMethod(value = PREPARE_HA, labels = {"EAP7"})
    public void prepareHAEAP7(Map<String, Object> params) {
        JMSOperations jmsOperations = getJMSOperations(params);
        String serverName = getServerName(params);

        Constants.HA_TYPE haType = PrepareUtils.getEnum(params, PrepareParams.HA_TYPE, Constants.HA_TYPE.class, Constants.HA_TYPE.NONE);
        int maxSavedReplicatedJournalSize = PrepareUtils.getInteger(params, PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, 2);
        String replicationGroupName = PrepareUtils.getString(params, PrepareParams.REPLICATION_GROUP_NAME);

        switch (haType) {
            case NONE:
                break;
            case SHARED_STORE_MASTER:
                jmsOperations.addHAPolicySharedStoreMaster(serverName, 0, true);
                break;
            case SHARED_STORE_SLAVE:
                jmsOperations.addHAPolicySharedStoreSlave(serverName, true, 0, true, true, false, null, null, null, null);
                break;

            case REPLICATION_MASTER:
                jmsOperations.addHAPolicyReplicationMaster(serverName, true, PrepareConstants.CLUSTER_NAME, replicationGroupName);
                break;

            case REPLICATION_SLAVE:
                jmsOperations.addHAPolicyReplicationSlave(serverName, true, PrepareConstants.CLUSTER_NAME, 0, replicationGroupName, maxSavedReplicatedJournalSize, true, false, null, null, null, null);
                break;
        }
    }

    @PrepareMethod(value = PREPARE_COLOCATED_BACKUP, labels = {"EAP6", "EAP7"})
    public void prepareColocatedBackup(Map<String, Object> params, PrepareContext ctx) throws Exception {
        JMSOperations jmsOperations = getJMSOperations(params);

        boolean prepareColocatedBackup = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_COLOCATED_BACKUP, false);

        if (!prepareColocatedBackup) {
            return;
        }

        Map<String, Object> paramsForBackup = getParamsForServer(params, PrepareConstants.BACKUP_SERVER_NAME);
        paramsForBackup.put(PrepareParams.ACCEPTOR_NAME, PrepareConstants.ACCEPTOR_NAME_BACKUP);
        paramsForBackup.put(PrepareParams.SOCKET_BINDING_NAME, PrepareConstants.MESSAGING_SOCKET_BINDING_NAME_BACKUP);
        paramsForBackup.put(PrepareParams.SOCKET_BINDING_PORT, PrepareConstants.MESSAGING_PORT_BACKUP);

        jmsOperations.addMessagingSubsystem(PrepareConstants.BACKUP_SERVER_NAME);

        ctx.invokeMethod(PrepareMethods.PREPARE_CONNECTOR, paramsForBackup);
        ctx.invokeMethod(PrepareMethods.PREPARE_SECURITY, paramsForBackup);
        ctx.invokeMethod(PrepareMethods.PREPARE_MISC, paramsForBackup);
        ctx.invokeMethod(PrepareMethods.PREPARE_ADDRESS_SETTINGS, paramsForBackup);
        ctx.invokeMethod(PrepareMethods.PREPARE_CLUSTER, paramsForBackup);

    }

}
