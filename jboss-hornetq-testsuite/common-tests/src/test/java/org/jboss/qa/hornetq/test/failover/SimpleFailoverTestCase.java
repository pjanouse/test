package org.jboss.qa.hornetq.test.failover;


import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.handlers.ifelse.IfHandler;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple failover/failback test case which uses only standard JMS API.
 * This test case should simplify investigation of failover/failback issues.
 */
@RunWith(Arquillian.class)
public class SimpleFailoverTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(SimpleFailoverTestCase.class);

    public static final String QUEUE_NAME = "testQueue";

    public static final String QUEUE_JNDI = "jms/queue/" + QUEUE_NAME;

    public static final String TOPIC_NAME = "testTopic";

    public static final String TOPIC_JNDI = "jms/topic/" + TOPIC_NAME;

    public static long LARGE_MSG_SIZE = 1024 * 1024;

    public static final String clusterConnectionName = "my-cluster";

    @After
    public void stopServers() {
        container(1).stop();
        container(2).stop();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckQueueFailoverTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, false, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckQueueFailoverTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, false, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckQueueFailbackTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, false, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckQueueFailbackTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, false, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckTopicFailoverTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, true, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckTopicFailoverTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, true, 10, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void clientAckTopicFailbackTest() throws Exception {
        failoverTest(Session.CLIENT_ACKNOWLEDGE, true, 10, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void transAckTopicFailbackTest() throws Exception {
        failoverTest(Session.SESSION_TRANSACTED, true, 10, true);
    }

    /**
     * This test simulate simple failover/failback scenario in which live server crash before
     * transaction is committed. After failover client tries to receive the whole bunch of messages again.
     *
     * @param ackMode       acknowledge mode, valid values: {CLIENT_ACKNOWLEDGE, SESSION_TRANSACTED}
     * @param topic         if true the topic is used, otherwise the queue is used
     * @param numMsg        number of messages to be sent and received
     * @param failback      if true the live server is started again after the crash
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Kill before transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Kill before transaction commit is written into journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void failoverTest(int ackMode, boolean topic, int numMsg, boolean failback) throws Exception {
        prepareSimpleDedicatedTopology();
        container(1).start();
        container(2).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Connection connection = cf.createConnection();
        connection.setClientID("client-0");
        Session session;
        Destination destination;

        if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        } else if (ackMode == Session.AUTO_ACKNOWLEDGE) {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } else {
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
        }

        if (topic) {
            destination = (Destination) context.lookup(TOPIC_JNDI);
        } else {
            destination = (Destination) context.lookup(QUEUE_JNDI);
        }


        MessageProducer producer = session.createProducer(destination);
        MessageConsumer consumer;

        if (topic) {
            consumer = session.createDurableSubscriber((Topic) destination, "subscriber-1");
        } else {
            consumer = session.createConsumer(destination);
        }

        connection.start();

        for (int i = 0; i < numMsg; i++) {
            BytesMessage message = session.createBytesMessage();
            for (int j = 0; j < LARGE_MSG_SIZE; j++) {
                message.writeByte(getSampleByte(j));
            }
            message.setStringProperty("id", "message-" + i);
            producer.send(message);
        }
        if (ackMode == Session.SESSION_TRANSACTED) {
            session.commit();
        }

        RuleInstaller.installRule(this.getClass(), container(1));

        Message message = null;

        log.info("Start receive messages");
        for (int i = 0; i < numMsg; i++) {
            message = consumer.receive(5000);
            Assert.assertNotNull(message);
            log.info("Received message " + message.getStringProperty("id"));
            Assert.assertEquals("message-" + i, message.getStringProperty("id"));
        }


        int expectedErrors = 0;
        try {
            if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
                message.acknowledge();
            } else if (ackMode == Session.SESSION_TRANSACTED) {
                session.commit();
            }
        } catch (JMSException e) {
            expectedErrors++;
        }

        Assert.assertEquals(1, expectedErrors);

        if (failback) {
            log.info("FAILBACK: Start live server again.");
            container(1).start();
        }

        int numReceivedMsg;
        int duplicationsDetected;
        while (true) {
            try {
                Set<String> duplicationCache = new HashSet<String>();
                numReceivedMsg = 0;
                duplicationsDetected = 0;
                log.info("Start receive messages");
                for (int i = 0; i < numMsg; ) {
                    Message msg = consumer.receive(60000);
                    if (msg == null) {
                        break;
                    } else {
                        message = msg;
                        numReceivedMsg++;
                    }
                    System.out.println("Received message " + message.getStringProperty("id"));

                    if (duplicationCache.contains(message.getStringProperty("id"))) {
                        duplicationsDetected++;
                        log.warn("Duplication detected " + message.getStringProperty("id"));
                    } else {
                        duplicationCache.add(message.getStringProperty("id"));
                        i++;
                    }
                }

                if (ackMode == Session.CLIENT_ACKNOWLEDGE) {
                    log.info("CALLING message acknowledge");
                    message.acknowledge();
                } else if (ackMode == Session.SESSION_TRANSACTED) {
                    log.info("CALLING session commit");
                    session.commit();
                }
                break;
            }
            catch (JMSException e) {
                log.warn("JMS EXCEPTION CATCHED", e);
                // retry
            }
        }

        log.info("Received messages: " + numReceivedMsg);
        log.info("Duplicated messages: " + duplicationsDetected);
        Assert.assertEquals(numMsg, numReceivedMsg);
        Assert.assertEquals(0, duplicationsDetected);
        Assert.assertNull(consumer.receive(5000));
    }

    public void prepareSimpleDedicatedTopology() throws Exception {
        prepareSimpleDedicatedTopology(JOURNAL_DIRECTORY_A, "ASYNCIO", Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
    }

    public void prepareSimpleDedicatedTopology(String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) throws Exception {
        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareSimpleDedicatedTopologyEAP6(journalDirectory, journalType, connectorType);
        } else {
            prepareSimpleDedicatedTopologyEAP7(journalDirectory, journalType, connectorType);
        }
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopologyEAP6(String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareLiveServerEAP6(container(1), journalDirectory, journalType, connectorType);
        prepareBackupServerEAP6(container(2), journalDirectory, journalType, connectorType);

    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopologyEAP7(String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareLiveServerEAP7(container(1), journalDirectory, journalType, connectorType);
        prepareBackupServerEAP7(container(2), journalDirectory, journalType, connectorType);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO
     */
    protected void prepareLiveServerEAP6(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        // only if connector type is NIO switch to NIO, ignore BIO and HTTP connector(this one does not apply for EAP 6)
        if (Constants.CONNECTOR_TYPE.NETTY_NIO.equals(connectorType)) {
            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String, String> connectorParams = new HashMap<String, String>();
            connectorParams.put("use-nio", "true");
            connectorParams.put("use-nio-global-worker-pool", "true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String, String> acceptorParams = new HashMap<String, String>();
            acceptorParams.put("use-nio", "true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        jmsAdminOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsAdminOperations.close();

        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareLiveServerEAP7(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        setConnectorForClientEAP7(container, connectorType);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreMaster(5000, true);

        jmsAdminOperations.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        jmsAdminOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsAdminOperations.close();

        container.stop();
    }

    protected void setConnectorForClientEAP7(Container container, Constants.CONNECTOR_TYPE connectorType) {

        String messagingGroupSocketBindingForConnector = "messaging";
        String nettyConnectorName = "netty";
        String nettyAcceptorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        int defaultPortForMessagingSocketBinding = 5445;
        String discoveryGroupName = "dg-group1";
        String jgroupsChannel = "activemq-cluster";
        String jgroupsStack = "udp";
        String broadcastGroupName = "bg-group1";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        switch (connectorType) {
            case HTTP_CONNECTOR:
                break;
            case NETTY_BIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, null);
                // add acceptor wtih BIO
                Map<String, String> acceptorParams = new HashMap<String, String>();
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, null);
                jmsAdminOperations.setConnectorOnConnectionFactory(connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            case NETTY_NIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, acceptorParamsNIO);
                jmsAdminOperations.setConnectorOnConnectionFactory(connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            default:
                break;
        }
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);

        jmsAdminOperations.close();
    }


    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO/BIO in connectors for CF or old blocking IO
     */
    protected void prepareBackupServerEAP6(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (Constants.CONNECTOR_TYPE.NETTY_NIO.equals(connectorType)) {            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String, String> connectorParams = new HashMap<String, String>();
            connectorParams.put("use-nio", "true");
            connectorParams.put("use-nio-global-worker-pool", "true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String, String> acceptorParams = new HashMap<String, String>();
            acceptorParams.put("use-nio", "true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }


        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        jmsAdminOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsAdminOperations.close();

        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO, or HTTP connector
     */
    protected void prepareBackupServerEAP7(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setPersistenceEnabled(true);
        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreSlave(true, 5000, true, true, false, null, null, null, null);

        jmsAdminOperations.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        jmsAdminOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsAdminOperations.close();

        container.stop();
    }

    private byte getSampleByte(long i) {
        return (byte) (i % 256);
    }

}
