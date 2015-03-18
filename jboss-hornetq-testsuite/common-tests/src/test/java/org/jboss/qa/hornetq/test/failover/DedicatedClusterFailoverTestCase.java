package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
// TODO fix and run this test - consumer first node, producer 3rd node
@Ignore
public class DedicatedClusterFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedClusterFailoverTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;
    private static final int BYTEMAN_PORT = 9091;
    static boolean topologyCreated = false;
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test fail back
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback) throws Exception {

        testFailover(acknowledge, failback, false);

    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>333",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {

        prepareDedicatedTopologyInCluster();

        controller.start(CONTAINER3_NAME);
        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER1_NAME);

        // install rule to first server
        RuleInstaller.installRule(this.getClass(), getHostname(CONTAINER1_NAME), getBytemanPort(CONTAINER1_NAME));

        Clients clients = createClients(acknowledge, topic);

        clients.startClients();

        waitForReceiversUntil(clients.getConsumers(), 50, 60000);

        controller.kill(CONTAINER1_NAME);

        // wait for backup to wake up
        waitHornetQToAlive(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 60000);

        waitForReceiversUntil(clients.getConsumers(), 300, 60000);

        if (failback) {
            logger.info("########################################");
            logger.info("failback - Start live server again ");
            logger.info("########################################");
            controller.start(CONTAINER1_NAME);
            waitHornetQToAlive(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), 60000);
            waitForReceiversUntil(clients.getConsumers(), 500, 60000);
//            logger.info("########################################");
//            logger.info("failback - Stop backup server");
//            logger.info("########################################");
//            stopServer(CONTAINER2_NAME);
//            Thread.sleep(5000); // give some time to org.jboss.qa.hornetq.apps.clients to do failback

            // check that backup is dead
            Assert.assertFalse("Backup should deactivate after failback.", checkThatServerIsReallyUp(getHostname(
                    CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME)));
        }

        clients.stopClients();

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);

    }

    @After
    public void stopServers() {
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);
    }

    public void prepareDedicatedTopologyInCluster() {

        prepareLiveServer(CONTAINER1_NAME, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2_NAME, JOURNAL_DIRECTORY_A);

        prepareLiveServer(CONTAINER3_NAME, JOURNAL_DIRECTORY_B);
    }

    /**
     * Create org.jboss.qa.hornetq.apps.clients with the given acknowledge mode on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE,
     *                        Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic           true for topic
     * @return org.jboss.qa.hornetq.apps.clients
     * @throws Exception
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients = null;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        return clients;
    }


    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }


    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }

    /**
     * Start simple failover test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * Start simple failover test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true);
    }

    /**
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * Start simple failback test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true);
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(String containerName, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";


        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

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

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }

        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareBackupServer(String containerName, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);
        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setJmxDomainName("org.hornetq.backup");
        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

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

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }

        controller.stop(containerName);
    }

}