package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class ColocatedClusterFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ColocatedClusterFailoverTestCase.class);
    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;
    protected static final int BYTEMAN_PORT = 9091;

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";

    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(40,200);
//    MessageBuilder messageBuilder = new TextMessageBuilder(1024);
    Clients clients = null;

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

    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, false);
    }

    public void testFailoverWithShutDown(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, true);
    }

    @Before @After
    public void makeSureAllClientsAreDead() throws InterruptedException {
        if (clients != null) {
            clients.stopClients();
            waitForClientsToFinish(clients, 600000);
        }

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
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {

        prepareColocatedTopologyInCluster();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // give some time for servers to find each other
        Thread.sleep(10000);

        messageBuilder.setAddDuplicatedHeader(true);

        clients = createClients(acknowledge, topic, messageBuilder);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(100);

        clients.startClients();

        waitForReceiversUntil(clients.getConsumers(), 120, 300000);

        logger.info("########################################");
        logger.info("kill - first server");
        logger.info("########################################");
        if (shutdown)   {
            controller.stop(CONTAINER1);
        } else {
            // install rule to first server
            RuleInstaller.installRule(this.getClass(), CONTAINER1_IP, BYTEMAN_PORT);
//            killServer(CONTAINER1);
            controller.kill(CONTAINER1);
        }

        waitForReceiversUntil(clients.getConsumers(), 500, 300000);
        Assert.assertTrue("Backup on second server did not start - failover failed.", waitHornetQToAlive(CONTAINER2_IP, 5446, 300000));

        if (failback) {
            logger.info("########################################");
            logger.info("failback - Start first server again ");
            logger.info("########################################");
            controller.start(CONTAINER1);
            Assert.assertTrue("Live on server 1 did not start again after failback - failback failed.", waitHornetQToAlive(CONTAINER1_IP, 5445, 300000));
            Thread.sleep(10000);
            logger.info("########################################");
            logger.info("failback - Stop second server to be sure that failback occurred");
            logger.info("########################################");
            stopServer(CONTAINER2);
        }
        Thread.sleep(20000); // give some time to clients

        clients.stopClients();

        waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by clients. More information in log.", clients.evaluateResults());

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    // TODO UNCOMMENT WHEN IS FIXED: https://bugzilla.redhat.com/show_bug.cgi?id=1019378

//    /**
//     * Start simple failover test with client_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverWithMdbsKill() throws Exception {
//
//        testFailWithMdbs(false);
//    }
//
//    /**
//     * Start simple failover test with client_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverWithMdbsShutdown() throws Exception {
//
//        testFailWithMdbs(true);
//    }

    public void testFailWithMdbs(boolean shutdown) throws Exception {

        prepareColocatedTopologyInCluster();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // give some time for servers to find each other
        waitHornetQToAlive(CONTAINER1_IP, 5445, 60000);
        waitHornetQToAlive(CONTAINER2_IP, 5445, 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(CONTAINER1_IP, getJNDIPort(), inQueue, numberOfMessages);
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        deployer.deploy("mdb2");
        deployer.deploy("mdb1");

        JMSOperations jmsOperations1 = getJMSOperations(CONTAINER1);
        JMSOperations jmsOperations2 = getJMSOperations(CONTAINER2);
        long startTime = System.currentTimeMillis();
        long timeout = 300000;
        double numberOfMessagesForFailover = numberOfMessages * 0.7;

        while (jmsOperations1.getCountOfMessagesOnQueue(inQueueName) + jmsOperations2.getCountOfMessagesOnQueue(inQueueName) > numberOfMessagesForFailover
                && System.currentTimeMillis() - startTime < timeout) {
            logger.info("Waiting for mdbs to process: " + numberOfMessagesForFailover + " from InQueue.");
        }
        jmsOperations1.close();
        jmsOperations2.close();

        printQueueStatus(CONTAINER1, inQueueName);
        printQueueStatus(CONTAINER1, outQueueName);
        printQueueStatus(CONTAINER2, inQueueName);
        printQueueStatus(CONTAINER1, outQueueName);

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown)   {
            controller.stop(CONTAINER2);
        } else {
            killServer(CONTAINER2);
            controller.kill(CONTAINER2);
        }

        Assert.assertTrue("Backup on first server did not start - failover failed.", waitHornetQToAlive(CONTAINER1_IP, 5446, 300000));
        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 300000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();

        List<Client> listOfReceiverClientAckList = new ArrayList<Client>();
        waitForReceiversUntil(listOfReceiverClientAckList, numberOfMessages - numberOfMessages/10, 300000);

        printQueueStatus(CONTAINER1, inQueueName);
        printQueueStatus(CONTAINER1, outQueueName);

        logger.info("########################################");
        logger.info("Start again - second server");
        logger.info("########################################");
        controller.start(CONTAINER2);
        logger.info("########################################");
        logger.info("Second server started");
        logger.info("########################################");

        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        deployer.undeploy("mdb1");
        deployer.undeploy("mdb2");

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }


    // TODO UNCOMMENT WHEN https://bugzilla.redhat.com/show_bug.cgi?id=1019378 IS FIXED
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testKillInClusterSmallMessages() throws Exception {
//        testFailInCluster(false, new TextMessageBuilder(10));
//    }
//
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testShutdowonInClusterSmallMessages() throws Exception {
//        testFailInCluster(true, new TextMessageBuilder(10));
//    }
//
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testKillInClusterMixMessages() throws Exception {
//        testFailInCluster(false, new ClientMixMessageBuilder(10, 200));
//    }
//
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testShutdowonInClusterMixMessages() throws Exception {
//        testFailInCluster(true, new ClientMixMessageBuilder(10, 200));
//    }

    public void testFailInCluster(boolean shutdown, MessageBuilder messageBuilder) throws Exception {


        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        prepareLiveServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_B);

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // give some time for servers to find each other
        waitHornetQToAlive(CONTAINER1_IP, 5445, 60000);
        waitHornetQToAlive(CONTAINER2_IP, 5445, 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(CONTAINER1_IP, getJNDIPort(), inQueue, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown)   {
            controller.stop(CONTAINER2);
        } else {
            killServer(CONTAINER2);
            controller.kill(CONTAINER2);
        }

        logger.info("########################################");
        logger.info("Start again - second server");
        logger.info("########################################");
        controller.start(CONTAINER2);
        logger.info("########################################");
        logger.info("Second server started");
        logger.info("########################################");

        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, inQueue, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);
        printQueueStatus(CONTAINER1, inQueueName);
        printQueueStatus(CONTAINER2, inQueueName);

        receiver1.start();
        List<Client> listOfReceiverClientAckList = new ArrayList<Client>();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }

    private void printQueueStatus(String containerName, String queueCoreName) {

        JMSOperations jmsOperations1 = getJMSOperations(CONTAINER1);

        long count = jmsOperations1.getCountOfMessagesOnQueue(queueCoreName);

        logger.info("########################################");
        logger.info("Status of queue - " + queueCoreName + " - on node " + containerName + " is: " + count);
        logger.info("########################################");

        jmsOperations1.close();

    }

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh1Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER2)
    public static JavaArchive createLodh1Deployment2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh2");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb2.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    protected Clients createClients(int acknowledgeMode, boolean topic, MessageBuilder messageBuilder) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(10);
        clients.setReceivedMessagesAckCommitAfter(5);

        return clients;
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckQueue() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false);
//    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailoverClientAckQueueShutDown() throws Exception {

        testFailoverWithShutDown(Session.CLIENT_ACKNOWLEDGE, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailbackTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckTopic() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false, true);
//    }

    /**
     * Start simple failover test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * Start simple failover test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailoverTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true);
    }

//    /**
//     * Start simple failback test with auto acknowledge on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailbackAutoAckTopic() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, true, true);
//    }

    /**
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailbackClientAckTopicShutdown() throws Exception {
        testFailoverWithShutDown(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * Start simple failback test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testFailbackTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true);
    }


    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     *
     */
    @Before
    @After
    public void stopAllServers()  {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    /**
     * Prepare two servers in colocated topology in cluster.
     *
     */
    public void prepareColocatedTopologyInCluster() {

            prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
            prepareColocatedBackupServer(CONTAINER1, CONTAINER1_IP, "backup", JOURNAL_DIRECTORY_B);

            prepareLiveServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_B);
            prepareColocatedBackupServer(CONTAINER2, CONTAINER2_IP, "backup", JOURNAL_DIRECTORY_A);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";


        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalFileSize(10 * 1024 * 1024);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

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
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param containerName        Name of the arquilian container.
     * @param ipAddress            On which IP address to broadcast and where is containers management ip address.
     * @param backupServerName     Name of the new HornetQ backup server.
     * @param journalDirectoryPath Absolute or relative path to journal directory.
     */
    protected void prepareColocatedBackupServer(String containerName, String ipAddress,
                                             String backupServerName, String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = 5446;
        String messagingGroupSocketBindingName = "messaging-group";


        controller.start(containerName);
        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.setBackup(backupServerName, true);
        jmsAdminOperations.setSharedStore(backupServerName, true);
        jmsAdminOperations.setJournalFileSize(backupServerName, 10 * 1024 * 1024);
        jmsAdminOperations.setFailoverOnShutdown(true, backupServerName);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setAllowFailback(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, "ASYNCIO");

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

//        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
//            jmsAdminOperations.createQueue(backupServerName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
//        }
//
//        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
//            jmsAdminOperations.createTopic(backupServerName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
//        }
//        jmsAdminOperations.createQueue(backupServerName, inQueueName, inQueue, true);
//        jmsAdminOperations.createQueue(backupServerName, outQueueName, outQueue, true);

        jmsAdminOperations.close();

        controller.stop(containerName);
    }

}