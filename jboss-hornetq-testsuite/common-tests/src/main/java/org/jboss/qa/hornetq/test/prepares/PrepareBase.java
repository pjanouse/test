package org.jboss.qa.hornetq.test.prepares;

import org.jboss.logging.Logger;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SlowConsumerPolicy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PrepareBase {

    public static final Logger log = Logger.getLogger(PrepareBase.class);

    public static final String TOPIC_NAME = "testTopic";

    public static final String TOPIC_JNDI =  "jms/topic/" + TOPIC_NAME;

    public static final String QUEUE_NAME = "testQueue";

    public static final String QUEUE_JNDI =  "jms/queue/" + QUEUE_NAME;

    public static final String IN_QUEUE_NAME = "InQueue";

    public static final String IN_QUEUE_JNDI =  "jms/queue/" + IN_QUEUE_NAME;

    public static final String OUT_QUEUE_NAME = "OutQueue";

    public static final String OUT_QUEUE_JNDI =  "jms/queue/" + OUT_QUEUE_NAME;

    public static final String IN_TOPIC_NAME = "InTopic";

    public static final String IN_TOPIC_JNDI =  "jms/topic/" + IN_TOPIC_NAME;

    public static final String OUT_TOPIC_NAME = "OutTopic";

    public static final String OUT_TOPIC_JNDI =  "jms/topic/" + OUT_TOPIC_NAME;

    public static final String TOPIC_NAME_PREFIX = "testTopic";

    public static final String TOPIC_JNDI_PREFIX =  "jms/topic/" + TOPIC_NAME_PREFIX;

    public static final String QUEUE_NAME_PREFIX = "testQueue";

    public static final String QUEUE_JNDI_PREFIX =  "jms/queue/" + QUEUE_NAME_PREFIX;

    public static final String JMS_BRIDGE_NAME = "myBridge";

    public static final String CLUSTER_NAME = "my-cluster";

    public static final String REMOTE_CONNECTION_FACTORY_NAME = "RemoteConnectionFactory";

    public static final String INVM_CONNECTION_FACTORY_NAME = "InVmConnectionFactory";

    public static final String POOLED_CONNECTION_FACTORY_NAME_EAP6 = "hornetq-ra";

    public static final String POOLED_CONNECTION_FACTORY_NAME_EAP7 = "activemq-ra";

    public static final String DISCOVERY_GROUP_NAME = "dg-group1";

    public static final String BROADCAST_GROUP_NAME = "bg-group1";

    public static final String CONNECTOR_NAME_EAP7 = "http-connector";

    public static final String CONNECTOR_NAME_EAP6 = "netty";

    public static final String CONNECTOR_NAME = "connector";

    public static final String INVM_CONNECTOR_NAME = "in-vm";

    public static final String ACCEPTOR_NAME_EAP6 = "netty";

    public static final String ACCEPTOR_NAME_EAP7 = "http-acceptor";

    public static final String ACCEPTOR_NAME = "acceptor";

    public static final String JGROUPS_CHANNEL = "activemq-cluster";

    public static final String SERVER_NAME = "default";

    public static final String BACKUP_SERVER_NAME = "backup";

    public static final String MULTICAST_SOCKET_BINDING_NAME = "messaging-group";

    public static final String MESSAGING_SOCKET_BINDING_NAME = "messaging";

    public static final String MESSAGING_SOCKET_BINDING_NAME_BACKUP = "messaging-backup";

    public static final String USER_NAME = "user";

    public static final String USER_PASS = "useruser";

    public static final String ADMIN_NAME = "admin";

    public static final String ADMIN_PASS = "adminadmin";

    public Container getContainer(Map<String, Object> params, int i) {
        return PrepareUtils.get(params, "container" + i, Container.class);
    }

    public Map<String, Object> getParamsForContainer(Map<String, Object> params, int i) {
        Map<String, Object> newParams = new HashMap<String, Object>(params);
        String prefix = "" + i + ".";

        for (String key : params.keySet()) {
            if (key.startsWith(prefix)) {
                newParams.put(key.substring(prefix.length()), newParams.get(key));
            }
        }
        return newParams;
    }

    protected void prepareAddressSettings(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareAddressSettings(params, jmsOperations, SERVER_NAME);
    }

    protected void prepareAddressSettings(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
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

    protected void prepareDestinations(Map<String, Object> params, JMSOperations jmsOperations) {
        boolean prepareDestinations = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_DESTINATIONS, true);
        boolean prepareQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_QUEUE, true);
        boolean prepareTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_TOPIC, true);
        boolean prepareInQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_IN_QUEUE, true);
        boolean prepareInTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_IN_TOPIC, true);
        boolean prepareOutQueue = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_OUT_QUEUE, true);
        boolean prepareOutTopic = PrepareUtils.getBoolean(params, PrepareParams.PREPARE_OUT_TOPIC, true);
        int destinationCount = PrepareUtils.getInteger(params, PrepareParams.DESTINATION_COUNT, 10);

        if (!prepareDestinations) {
            return;
        }

        if (prepareQueue) {
            jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        }
        if (prepareTopic) {
            jmsOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);
        }
        if (prepareInQueue) {
            jmsOperations.createQueue(IN_QUEUE_NAME, IN_QUEUE_JNDI);
        }
        if (prepareOutQueue) {
            jmsOperations.createQueue(OUT_QUEUE_NAME, OUT_QUEUE_JNDI);
        }
        if (prepareInTopic) {
            jmsOperations.createTopic(IN_TOPIC_NAME, IN_TOPIC_JNDI);
        }
        if (prepareOutTopic) {
            jmsOperations.createTopic(OUT_TOPIC_NAME, OUT_TOPIC_JNDI);
        }

        for (int i = 0; i < destinationCount; i++) {
            jmsOperations.createTopic(TOPIC_NAME_PREFIX + i, TOPIC_JNDI_PREFIX + i);
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i);
        }
    }

    protected void prepareDiverts(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareDiverts(params, jmsOperations, SERVER_NAME);
    }

    /**
     * Accept params in the form:
     * DIVERT-A-ORIGIN-QUEUE=originQueue
     * DIVERT-A-DIVERTED-QUEUE=divertedQueue
     * DIVERT-A-EXCLUSIVE=false
     * DIVERT-B-ORIGIN-TOPIC=originTopic
     * DIVERT-B-DIVERTED-TOPIC=divertedTopic
     * DIVERT-B-EXCLUSIVE=false
     * @param params
     * @param jmsOperations
     */
    protected void prepareDiverts(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
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

    protected void prepareConnectionFactory(Map<String, Object> params, JMSOperations jmsOperations) {
        boolean rcfHa = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_HA, true);
        boolean rcfBlockOnAck = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_BLOCK_ON_ACK, true);
        long rcfRetryInterval = PrepareUtils.getLong(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RETRY_INTERVAL, 2000l);
        double rcfRetryIntervalMultiplier = PrepareUtils.getDouble(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RETRY_INTERVAL_MULTIPLIER, 1.0);
        int rcfReconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.REMOTE_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, -1);
        boolean rcfCompression = PrepareUtils.getBoolean(params, PrepareParams.REMOTE_CONNECTION_FACTORY_COMPRESSION, false);

        boolean pcfHa = PrepareUtils.getBoolean(params, PrepareParams.POOLED_CONNECTION_FACTORY_HA, false);
        boolean pcfBlockOnAck = PrepareUtils.getBoolean(params, PrepareParams.POOLED_CONNECTION_FACTORY_BLOCK_ON_ACK, false);
        long pcfRetryInterval = PrepareUtils.getLong(params, PrepareParams.POOLED_CONNECTION_FACTORY_RETRY_INTERVAL, 2000l);
        double pcfRetryIntervalMultiplier = PrepareUtils.getDouble(params, PrepareParams.POOLED_CONNECTION_FACTORY_RETRY_INTERVAL_MULTIPLIER, 1.0);
        int pcfReconnectAttempts = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, -1);
        int pcfMinPoolSize = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, -1);
        int pcfMaxPoolSize = PrepareUtils.getInteger(params, PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, -1);

        jmsOperations.setHaForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfHa);
        jmsOperations.setBlockOnAckForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfBlockOnAck);
        jmsOperations.setRetryIntervalForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfRetryInterval);
        jmsOperations.setRetryIntervalMultiplierForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfRetryIntervalMultiplier);
        jmsOperations.setReconnectAttemptsForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfReconnectAttempts);
        jmsOperations.setCompressionOnConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, rcfCompression);

        String pooledConnectionFactoryName = ContainerUtils.isEAP6(getContainer(params, 1)) ? POOLED_CONNECTION_FACTORY_NAME_EAP6 : POOLED_CONNECTION_FACTORY_NAME_EAP7;

        jmsOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, pcfHa);
        jmsOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, pcfBlockOnAck);
        jmsOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, pcfRetryInterval);
        jmsOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, pcfRetryIntervalMultiplier);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, pcfReconnectAttempts);
        jmsOperations.setMinPoolSizeOnPooledConnectionFactory(pooledConnectionFactoryName, pcfMinPoolSize);
        jmsOperations.setMaxPoolSizeOnPooledConnectionFactory(pooledConnectionFactoryName, pcfMaxPoolSize);
    }

    protected void prepareConnectorEAP6(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareConnectorEAP6(params, jmsOperations, SERVER_NAME);
    }

    protected void prepareConnectorEAP6(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
        Constants.CONNECTOR_TYPE connectorType = Constants.CONNECTOR_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CONNECTOR_TYPE, "NETTY_BIO"));

        switch (connectorType) {
            case HTTP_CONNECTOR:
                throw new RuntimeException("Unsupported configuration");
            case NETTY_BIO:
                // add connector with BIO
                jmsOperations.removeRemoteConnector(serverName, CONNECTOR_NAME_EAP6);
                jmsOperations.createRemoteConnector(serverName, CONNECTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, null);
                // add acceptor wtih BIO
                jmsOperations.removeRemoteAcceptor(serverName, ACCEPTOR_NAME_EAP6);
                jmsOperations.createRemoteAcceptor(serverName, ACCEPTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, null);
                jmsOperations.setConnectorOnConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnClusterGroup(serverName, CLUSTER_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnBroadcastGroup(serverName, BROADCAST_GROUP_NAME, Arrays.asList(CONNECTOR_NAME));
                break;
            case NETTY_NIO:
                // add connector with NIO
                jmsOperations.removeRemoteConnector(serverName, CONNECTOR_NAME_EAP6);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsOperations.createRemoteConnector(serverName, CONNECTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsOperations.removeRemoteAcceptor(serverName, ACCEPTOR_NAME_EAP6);
                jmsOperations.createRemoteAcceptor(serverName, ACCEPTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, acceptorParamsNIO);
                jmsOperations.setConnectorOnConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnClusterGroup(serverName, CLUSTER_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnBroadcastGroup(serverName, BROADCAST_GROUP_NAME, Arrays.asList(CONNECTOR_NAME));
                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType.name());
        }
        jmsOperations.setHaForConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, true);
        jmsOperations.setBlockOnAckForConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, true);
        jmsOperations.setRetryIntervalForConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, 1000L);
        jmsOperations.setRetryIntervalMultiplierForConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, 1.0);
        jmsOperations.setReconnectAttemptsForConnectionFactory(REMOTE_CONNECTION_FACTORY_NAME, -1);
    }

    protected void prepareConnectorEAP7(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareConnectorEAP7(params, jmsOperations, SERVER_NAME);
    }

    protected void prepareConnectorEAP7(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
        Constants.CONNECTOR_TYPE connectorType = Constants.CONNECTOR_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CONNECTOR_TYPE, "HTTP_CONNECTOR"));

        jmsOperations.removeHttpAcceptor(serverName, ACCEPTOR_NAME_EAP7);
        jmsOperations.removeHttpConnector(serverName, CONNECTOR_NAME_EAP7);

        switch (connectorType) {
            case HTTP_CONNECTOR:
                Map<String, String> acceptorParams = new HashMap<String, String>();
                acceptorParams.put("batch-delay", "50");
                acceptorParams.put("direct-deliver", "false");

                jmsOperations.createHttpAcceptor(serverName, ACCEPTOR_NAME, "default", acceptorParams);
                jmsOperations.createHttpConnector(serverName, CONNECTOR_NAME, "http", acceptorParams, ACCEPTOR_NAME);

                jmsOperations.setConnectorOnConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnClusterGroup(serverName, CLUSTER_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnBroadcastGroup(serverName, BROADCAST_GROUP_NAME, Arrays.asList(CONNECTOR_NAME));
                break;
            case NETTY_BIO:
                jmsOperations.createSocketBinding(MESSAGING_SOCKET_BINDING_NAME, Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7);

                // add connector with BIO
                jmsOperations.createRemoteConnector(serverName, CONNECTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, null);
                // add acceptor wtih BIO
                jmsOperations.createRemoteAcceptor(serverName, ACCEPTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, null);
                jmsOperations.setConnectorOnConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnClusterGroup(serverName, CLUSTER_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnBroadcastGroup(serverName, BROADCAST_GROUP_NAME, Arrays.asList(CONNECTOR_NAME));
                break;
            case NETTY_NIO:
                jmsOperations.createSocketBinding(MESSAGING_SOCKET_BINDING_NAME, Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7);
                // add connector with NIO
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsOperations.createRemoteConnector(serverName, CONNECTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsOperations.createRemoteAcceptor(serverName, ACCEPTOR_NAME, MESSAGING_SOCKET_BINDING_NAME, acceptorParamsNIO);
                jmsOperations.setConnectorOnConnectionFactory(serverName, REMOTE_CONNECTION_FACTORY_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnClusterGroup(serverName, CLUSTER_NAME, CONNECTOR_NAME);
                jmsOperations.setConnectorOnBroadcastGroup(serverName, BROADCAST_GROUP_NAME, Arrays.asList(CONNECTOR_NAME));
                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType.name());
        }
    }

    protected void prepareCluster(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareCluster(params, jmsOperations, SERVER_NAME);
    }

    protected void prepareCluster(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE, "DEFAULT"));
        long callTimeout = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_CALL_TIMEOUT, 30000l);
        long checkPeriod = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_CHECK_PERIOD, 30000l);
        long ttl = PrepareUtils.getLong(params, PrepareParams.CLUSTER_CONNECTION_TTL, 60000l);
        Boolean forwardWhenNoConsumer = PrepareUtils.getBoolean(params, PrepareParams.FORWARD_WHEN_NO_CONSUMER);

        if (clusterType == Constants.CLUSTER_TYPE.DEFAULT) {
            Container container = getContainer(params, 1);
            if (ContainerUtils.isEAP6(container)) {
                clusterType = Constants.CLUSTER_TYPE.MULTICAST;
            } else {
                clusterType = Constants.CLUSTER_TYPE.JGROUPS_DISCOVERY;
            }
        }

        jmsOperations.setClusterConnectionCallTimeout(serverName, CLUSTER_NAME, callTimeout);
        jmsOperations.setClusterConnectionCheckPeriod(serverName, CLUSTER_NAME, checkPeriod);
        jmsOperations.setClusterConnectionTTL(serverName, CLUSTER_NAME, ttl);

        if (forwardWhenNoConsumer != null) {
            jmsOperations.setForwardWhenNoConsumers(serverName, CLUSTER_NAME, forwardWhenNoConsumer);
        }

        switch (clusterType) {
            case MULTICAST:
                jmsOperations.removeBroadcastGroup(serverName, BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, BROADCAST_GROUP_NAME, MULTICAST_SOCKET_BINDING_NAME, 2000, CONNECTOR_NAME, "");
                jmsOperations.removeDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME, MULTICAST_SOCKET_BINDING_NAME, 10000);
                break;
            case JGROUPS_DISCOVERY:
                jmsOperations.removeBroadcastGroup(serverName, BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, BROADCAST_GROUP_NAME, "udp", JGROUPS_CHANNEL, 5000, CONNECTOR_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME, 10000, "udp", JGROUPS_CHANNEL);
                break;
            case JGROUPS_DISCOVERY_TCP:
                jmsOperations.removeBroadcastGroup(serverName, BROADCAST_GROUP_NAME);
                jmsOperations.setBroadCastGroup(serverName, BROADCAST_GROUP_NAME, "tcp", JGROUPS_CHANNEL, 5000, CONNECTOR_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME);
                jmsOperations.setDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME, 10000, "tcp", JGROUPS_CHANNEL);
                break;
            case NONE:
                jmsOperations.removeBroadcastGroup(serverName, BROADCAST_GROUP_NAME);
                jmsOperations.removeDiscoveryGroup(serverName, DISCOVERY_GROUP_NAME);
                jmsOperations.removeClusteringGroup(serverName, CLUSTER_NAME);
                break;
            default: throw new IllegalArgumentException("Unsupported cluster type: " + clusterType.name());
        }
    }

    protected void prepareSecurity(Map<String, Object> params, Container container) throws Exception {
        prepareSecurity(params, container, SERVER_NAME);
    }

    protected void prepareSecurity(Map<String, Object> params, Container container, String serverName) throws Exception {
        boolean securityEnabled = PrepareUtils.getBoolean(params, PrepareParams.ENABLE_SECURITY, false);

        JMSOperations jmsOperations = container.getJmsOperations();

        if (!securityEnabled) {
            jmsOperations.disableSecurity(serverName);
            jmsOperations.close();
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

        jmsOperations.close();

        UsersSettings.forDefaultEapServer()
                .withUser(USER_NAME, USER_PASS, "users")
                .withUser(ADMIN_NAME, ADMIN_PASS, "admin")
                .create();
    }

    protected void prepareMisc(Map<String, Object> params, JMSOperations jmsOperations) {
        prepareMisc(params, jmsOperations, SERVER_NAME);
    }

    protected void prepareMisc(Map<String, Object> params, JMSOperations jmsOperations, String serverName) {
        String journalType = PrepareUtils.getString(params, PrepareParams.JOURNAL_TYPE, "ASYNCIO");
        long journalFileSize = PrepareUtils.getLong(params, PrepareParams.JOURNAL_FILE_SIZE, 10 * 1024 * 1024l);
        long transactionTimeout = PrepareUtils.getLong(params, PrepareParams.TRANSACTION_TIMEOUT, 300000l);
        boolean jmxManagementEnabled = PrepareUtils.getBoolean(params, PrepareParams.JMX_MANAGEMENT_ENABLED, false);
        boolean autoCreateJMSQueues = PrepareUtils.getBoolean(params, PrepareParams.AUTO_CREATE_JMS_QUEUES, false);
        boolean autoDeleteJMSQueues = PrepareUtils.getBoolean(params, PrepareParams.AUTO_DELETE_JMS_QUEUES, false);

        jmsOperations.setJournalType(serverName, journalType);
        jmsOperations.setJournalFileSize(serverName, journalFileSize);
        jmsOperations.setTransactionTimeout(serverName, transactionTimeout);
        jmsOperations.setJmxManagementEnabled(jmxManagementEnabled);
        jmsOperations.setAutoCreateJMSQueue(autoCreateJMSQueues);
        jmsOperations.setAutoDeleteJMSQueue(autoDeleteJMSQueues);
    }

}
