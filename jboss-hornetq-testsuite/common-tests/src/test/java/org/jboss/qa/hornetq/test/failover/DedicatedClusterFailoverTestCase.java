package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.jms.Session;

/**
 * @author mnovak@redhat.com
 * tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH SHARED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-nfs/
 * @tpTcmsLink TBD
 * @tpTestCaseDetails This test case checks if loadbalancing in cluster works fine even if clients have to make failover on backup
 */
@RunWith(Arquillian.class)
// TODO fix and run this test - consumer first node, producer 3rd node. Update documentation when it's complete
@Ignore
public class DedicatedClusterFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedClusterFailoverTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 1;

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    /**
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test fail back
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback) throws Exception {

        testFailover(acknowledge, failback, false);

    }

    /**

     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Hornetq Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Hornetq Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Hornetq Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>333",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Artemis Setup counter for PostOfficeImpl",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Artemis Info messages and counter for PostOfficeImpl",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Artemis Kill server when a number of messages were received",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>333",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")

    })
    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {

        prepareDedicatedTopologyInCluster();

        container(3).start();
        container(2).start();
        container(1).start();

        // install rule to first server
        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());

        Clients clients = createClients(acknowledge, topic);

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 50, 60000);

        container(1).kill();

        // wait for backup to wake up
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 300, 60000);

        if (failback) {
            logger.info("########################################");
            logger.info("failback - Start live server again ");
            logger.info("########################################");
            container(1).start();
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
            ClientUtils.waitForReceiversUntil(clients.getConsumers(), 500, 60000);
//            logger.info("########################################");
//            logger.info("failback - Stop backup server");
//            logger.info("########################################");
//            stopServer(CONTAINER2_NAME);
//            Thread.sleep(5000); // give some time to org.jboss.qa.hornetq.apps.clients to do failback

            // check that backup is dead
            Assert.assertFalse("Backup should deactivate after failback.", CheckServerAvailableUtils.checkThatServerIsReallyUp(container(2).getHostname(),
                    container(2).getHornetqPort()));
        }

        clients.stopClients();

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();
        container(2).stop();
        container(3).stop();

    }

    @After
    @Before
    public void stopServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
    }

    public void prepareDedicatedTopologyInCluster() {

        prepareLiveServer(container(1), JOURNAL_DIRECTORY_A);
        prepareBackupServer(container(2), JOURNAL_DIRECTORY_A);

        prepareLiveServer(container(3), JOURNAL_DIRECTORY_B);
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

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        return clients;
    }


    /**
     * Start simple failover test with client_ack on queues
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }


    /**
     * Start simple failover test with client_ack on queues
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>wait until clients make failover</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>producer and consumer continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with client_ack on queues
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>wait until clients make failover</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>producer and consumer continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }

    /**
     * Start simple failover test with client_ack on queues
     * @tpTestDetails This test scenario tests failover of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * Start simple failover test with client_ack on queues
     * @tpTestDetails This test scenario tests failover of clients connected to topic (using SESSION_TRANSACTED session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailoverTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>wait until clients make failover</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>producer and consumer continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using SESSION_TRANSACTED session)
     * on node which is killed in dedicated cluster topology.
     * @tpProcedure <ul>
     *     <li>start three nodes in colocated cluster topology. Node-1 and node-3 are live servers, node-2 is backup for node-1</li>
     *     <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>wait until clients make failover</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>producer and consumer continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
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
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(Container container, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareBackupServer(Container container, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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

        container.stop();
    }

}