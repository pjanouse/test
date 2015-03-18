package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.*;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.test.security.AddressSecuritySettings;
import org.jboss.qa.hornetq.test.security.PermissionGroup;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;
import java.io.File;
import java.util.*;
//TODO
//ClusterTestCase
//        - clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly
//        - clusterTestWithMdbOnTopicDeployAndUndeployTwoServers
//        - clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers
//        - clusterTestWithMdbWithSelectorAndSecurityTwoServers

/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p/>
 * This test also serves
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class ClusterTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusterTestCase.class);

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    private static final String MDB_ON_QUEUE1 = "mdbOnQueue1";
    private static final String MDB_ON_QUEUE2 = "mdbOnQueue2";
    private static final String MDB_ON_QUEUE1_SECURITY = "mdbOnQueue1Security";
    private static final String MDB_ON_QUEUE1_SECURITY2 = "mdbOnQueue1Security2";

    private static final String MDB_ON_TEMPQUEUE1 = "mdbOnTempQueue1";
    private static final String MDB_ON_TEMPQUEUE2 = "mdbOnTempQueue2";

    private static final String MDB_ON_TOPIC1 = "mdbOnTopic1";
    private static final String MDB_ON_TOPIC2 = "mdbOnTopic2";

    private static final String MDB_ON_TEMPTOPIC1 = "mdbOnTempTopic1";
    private static final String MDB_ON_TEMPTOPIC2 = "mdbOnTempTopic2";

    private static final String MDB_ON_QUEUE1_TEMP_QUEUE = "mdbQueue1withTempQueue";

    private static final String MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = "mdbOnTopic1WithDifferentSubscriptionName1";


    static String queueNamePrefix = "testQueue";
    static String topicNamePrefix = "testTopic";
    static String queueJndiNamePrefix = "jms/queue/testQueue";
    static String topicJndiNamePrefix = "jms/topic/testTopic";

    // InQueue and OutQueue for mdb
    static String inQueueNameForMdb = "InQueue";
    static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    static String outQueueNameForMdb = "OutQueue";
    static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;

    // InTopic and OutTopic for mdb
    static String inTopicNameForMdb = "InTopic";
    static String inTopicJndiNameForMdb = "jms/topic/" + inTopicNameForMdb;
    static String outTopicNameForMdb = "OutTopic";
    static String outTopicJndiNameForMdb = "jms/topic/" + outTopicNameForMdb;

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTest() throws Exception {

        prepareServers();

        controller.start(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);

        Clients queueClients = createClients(Session.SESSION_TRANSACTED, false);
        Clients topicClients = createClients(Session.SESSION_TRANSACTED, true);

        queueClients.startClients();
        topicClients.startClients();

        waitForClientsToFinish(queueClients);
        waitForClientsToFinish(topicClients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", queueClients.evaluateResults());
        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", topicClients.evaluateResults());

        stopServer(CONTAINER1_NAME);

        stopServer(CONTAINER2_NAME);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithKillOfServerInCluster() throws Exception {

        prepareServers();

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        waitHornetQToAlive(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), 60000);
        waitHornetQToAlive(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(128));
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();


        log.info("########################################");
        log.info("kill - second server");
        log.info("########################################");

//        if (shutdown)   {
//            controller.stop(CONTAINER2_NAME);
//        } else {
        killServer(CONTAINER2_NAME);
        controller.kill(CONTAINER2_NAME);
//        }

        log.info("########################################");
        log.info("Start again - second server");
        log.info("########################################");
        controller.start(CONTAINER2_NAME);
        waitHornetQToAlive(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 300000);
        log.info("########################################");
        log.info("Second server started");
        log.info("########################################");

        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);
//        printQueueStatus(CONTAINER1_NAME_NAME, inQueueName);
//        printQueueStatus(CONTAINER2_NAME, inQueueName);

        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }

// THIS WAS COMMENTED BECAUSE clusterTest() IS BASICALLY TESTING THE SAME SCENARIO
//    /**
//     * This test will start two servers A and B in cluster.
//     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
//     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitDuplicateId() throws Exception {
//
//        prepareServers();
//
//        controller.start(CONTAINER2_NAME);
//
//        controller.start(CONTAINER1_NAME_NAME);
//
//        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME), getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
//        messageBuilder.setAddDuplicatedHeader(true);
//        producer1.setMessageBuilder(messageBuilder);
//        producer1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
//        producer1.start();
//        producer1.join();
//
//        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME), getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, 2000, 10, 10);
//        receiver1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
//        receiver1.start();
//        receiver1.join();
//
//        stopServer(CONTAINER1_NAME_NAME);
//
//        stopServer(CONTAINER2_NAME);
//
//    }


    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithoutDuplicateIdWithInterruption() throws Exception {

        prepareServers();

        controller.start(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);

        // send messages without dup id -> load-balance to node 2
        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.start();
        producer1.join();

        // receive more then half message so some load-balanced messages gets back
        Context context = null;
        ConnectionFactory cf;
        Connection conn = null;
        Session session;
        Queue queue;

        try {

            context = getContext(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME));

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(inQueueJndiNameForMdb);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiver = session.createConsumer(queue);

            int count = 0;
            while (count < NUMBER_OF_MESSAGES_PER_PRODUCER) {
                receiver.receive(10000);
                count++;
                log.info("Receiver got message: " + count);
            }
            session.rollback();

        } catch (JMSException ex) {
            log.error("Error occurred during receiving.", ex);
        } finally {
            if (conn != null) {
                conn.close();
            }
            if (context != null) {
                context.close();
            }

        }

        // receive  some of them from first server and kill receiver -> only some of them gets back to
        ReceiverTransAck receiver2 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER2_NAME), getJNDIPort(


                CONTAINER2_NAME), inQueueJndiNameForMdb, 10000, 10, 10);
        receiver2.start();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                NUMBER_OF_MESSAGES_PER_PRODUCER, receiver2.getListOfReceivedMessages().size());
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }


    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueue() throws Exception {

        prepareServers();

        controller.start(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);

        deployer.deploy(MDB_ON_QUEUE1);

        deployer.deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: " + producer.getListOfSentMessages().size()
                        + "Received: " + receiver.getListOfReceivedMessages().size(), producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size()
        );
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size()
                , producer.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size()
                , receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                , NUMBER_OF_MESSAGES_PER_PRODUCER);

        deployer.undeploy(MDB_ON_QUEUE1);

        deployer.undeploy(MDB_ON_QUEUE2);

        stopServer(CONTAINER1_NAME);

        stopServer(CONTAINER2_NAME);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueueDeployAndUndeploy() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        int numberOfMessages = 30;
        controller.start(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);

        deployer.deploy(MDB_ON_TEMPQUEUE1);

        deployer.deploy(MDB_ON_TEMPQUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, numberOfMessages);
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiNameForMdb, 10000, 10, 10);


        producer.start();
        producer.join();
        log.info("Removing MDB ond secnod server and adding it back ");
        deployer.undeploy(MDB_ON_TEMPQUEUE2);
        deployer.deploy(MDB_ON_TEMPQUEUE2);
        log.info("Trying to read from newly deployed OutQueue");

        receiver.start();
        receiver.join();

        Assert.assertEquals("Number of messages in OutQueue does not match", numberOfMessages, receiver.getListOfReceivedMessages().size());
        deployer.undeploy(MDB_ON_TEMPQUEUE1);
        deployer.undeploy(MDB_ON_TEMPQUEUE2);


        stopServer(CONTAINER1_NAME);

        stopServer(CONTAINER2_NAME);

    }

    /**
     * This test will start one server A.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicDeployAleradyExistingTopic() throws Exception {
        log.info("PREPARING SERVERS");
        boolean passed = false;
        prepareServers(true);
        controller.start(CONTAINER1_NAME);
        try {
            deployer.deploy(MDB_ON_TEMPTOPIC1);
            passed = true;

        } catch (Exception e) {
            //this is correct behavior
        }
        Assert.assertFalse("Deployment of already existing topic didn't failed", passed);


        stopServer(CONTAINER1_NAME);
    }

    /**
     * This test will start one server A.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_TEMPTOPIC1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1_NAME);
        SubscriberAutoAck subscriber = new SubscriberAutoAck(CONTAINER1_NAME, getHostname(CONTAINER1_NAME), 4447, inTopicJndiNameForMdb, "subscriber1", "subscription1");
        subscriber.start();
        subscriber.join();
        deployer.undeploy(MDB_ON_TEMPTOPIC1);
        deployer.deploy(MDB_ON_TEMPTOPIC1);

        Assert.assertEquals("Number of subscriptions is not 0", 0, jmsAdminOperations.getNumberOfDurableSubscriptionsOnTopic("mySubscription"));

        stopServer(CONTAINER1_NAME);
    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(CONTAINER1_NAME, false);
        prepareServer(CONTAINER2_NAME, false);
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        deployer.deploy(MDB_ON_TEMPTOPIC1);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = this.getJMSOperations(CONTAINER1_NAME);
        JMSOperations jmsAdminOperations2 = this.getJMSOperations(CONTAINER2_NAME);
        SubscriberAutoAck subscriber = new SubscriberAutoAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outTopicJndiNameForMdb, "subscriber1", "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();
        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        deployer.undeploy(MDB_ON_TEMPTOPIC2);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0, jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(CONTAINER1_NAME, true);
        prepareServer(CONTAINER2_NAME, false);
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = this.getJMSOperations(CONTAINER1_NAME);
        JMSOperations jmsAdminOperations2 = this.getJMSOperations(CONTAINER2_NAME);
        SubscriberAutoAck subscriber = new SubscriberAutoAck(
                CONTAINER2_NAME, getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), inTopicJndiNameForMdb, "subscriber1", "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();

        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        deployer.undeploy(MDB_ON_TEMPTOPIC2);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0, jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTempQueueonOtherNodes() throws Exception {
        prepareServers(true);
        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        JMSOperations jmsAdminOperationsContainer1 = getJMSOperations(CONTAINER1_NAME);
        JMSOperations jmsAdminOperationsContainer2 = getJMSOperations(CONTAINER2_NAME);
        responsiveProducer.start();
        // Wait fro creating connections and send few messages
        Thread.sleep(1000);
        cont1Count = jmsAdminOperationsContainer1.getNumberOfTempQueues();
        cont2Count = jmsAdminOperationsContainer2.getNumberOfTempQueues();
        responsiveProducer.join();
        Assert.assertEquals("Invalid number of temp queues on CONTAINER1_NAME", 1, cont1Count);
        Assert.assertEquals("Invalid number of temp queues on CONTAINER2_NAME", 0, cont2Count);


    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestPagingAfterFailOverTempQueue() throws Exception {
        prepareServers(true);
        controller.start(CONTAINER1_NAME);
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = getJMSOperations(CONTAINER1_NAME);
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = getContext(CONTAINER1_NAME);
        ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        MessageProducer producer = session.createProducer(tempQueue);
        TextMessage message = session.createTextMessage("message");
        for (int i = 0; i < 10000; i++) {
            producer.send(message);
        }

        Assert.assertEquals("No paging files was found", false, getAllPagingFiles(pagingPath).size() == 0);
        killServer(CONTAINER1_NAME);
        controller.kill(CONTAINER1_NAME);
        controller.start(CONTAINER1_NAME);
        //  Thread.sleep(80000);
        for (File f : getAllPagingFiles(pagingPath)) {
            log.error("FILE: " + f.getName());
        }
        Assert.assertEquals("Too many paging files was found", 0, getAllPagingFiles(pagingPath).size());
        stopAllServers();


    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestPagingAfterFailOverNonDurableQueue() throws Exception {
        prepareServer(CONTAINER1_NAME, false);
        controller.start(CONTAINER1_NAME);
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = getJMSOperations(CONTAINER1_NAME);
        jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, false);
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = getContext(CONTAINER1_NAME);
        Queue inqueue = (Queue) context.lookup(inQueueJndiNameForMdb);
        ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);

        MessageProducer producer = session.createProducer(inqueue);
        TextMessage message = session.createTextMessage("message");
        for (int i = 0; i < 10000; i++) {
            producer.send(message);
        }

        Assert.assertEquals("No paging files was found", false, getAllPagingFiles(pagingPath).size() == 0);
        killServer(CONTAINER1_NAME);
        controller.kill(CONTAINER1_NAME);
        controller.start(CONTAINER1_NAME);
        Assert.assertEquals("Too many paging files was found", 0, getAllPagingFiles(pagingPath).size());


        stopAllServers();


    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestReadMessageFromDifferentConnection() throws Exception {
        prepareServers(true);
        boolean failed = false;
        controller.start(CONTAINER1_NAME);
        try {
            Context context = getContext(CONTAINER1_NAME);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
            Connection connection1 = cf.createConnection();
            Connection connection2 = cf.createConnection();
            Session session1 = connection1.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            Session session2 = connection2.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            TemporaryQueue tempQueue = session1.createTemporaryQueue();
            MessageProducer producer = session1.createProducer(tempQueue);
            producer.send(session1.createTextMessage("message"));
            MessageConsumer consumer = session2.createConsumer(tempQueue);
            consumer.receive(100);
        } catch (JMSException e) {
            failed = true;
        } catch (Exception e) {
            throw e;
        } finally {
            stopAllServers();
        }
        Assert.assertEquals("Sending message didn't failed", true, failed);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTemQueueMessageExpiration() throws Exception {
        prepareServers(true);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(CONTAINER1_NAME, getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, 1, 300);
        responsiveProducer.start();
        responsiveProducer.join();

        Assert.assertEquals("Number of received messages don't match", 0, responsiveProducer.getRecievedCount());


        stopAllServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTemQueueMessageExpirationLM() throws Exception {
        prepareServers(true);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(CONTAINER1_NAME, getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, 1, 300);
        responsiveProducer.setMessageBuilder(new TextMessageBuilder(110 * 1024));
        responsiveProducer.start();
        responsiveProducer.join();

        Assert.assertEquals("Number of received messages don't match", 0, responsiveProducer.getRecievedCount());


        stopAllServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbHqXmlOnQueueRedeploy() throws Exception {
        prepareServer(CONTAINER1_NAME, false);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_TEMPQUEUE1);
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiNameForMdb, 10000, 10, 10);
        producer.start();
        producer.join();
        deployer.deploy(MDB_ON_TEMPQUEUE1);
        receiver.start();
        receiver.join();
        Assert.assertEquals("Number of messages does not match", NUMBER_OF_MESSAGES_PER_PRODUCER, receiver.getListOfReceivedMessages().size());
        stopServer(CONTAINER1_NAME);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGrouping() throws Exception {
        prepareServer(CONTAINER1_NAME, true);
        prepareServer(CONTAINER2_NAME, true);
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        JMSOperations jmsAdminOperations1 = getJMSOperations(CONTAINER1_NAME);
        JMSOperations jmsAdminOperations2 = getJMSOperations(CONTAINER2_NAME);


        //setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);
        jmsAdminOperations1.addLoggerCategory("org.jboss","INFO");
        jmsAdminOperations1.addLoggerCategory("org.jboss.qa.hornetq.apps.mdb","TRACE");
        jmsAdminOperations1.seRootLoggingLevel("TRACE");

        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        UsersSettings.forEapServer(getJbossHome(CONTAINER1_NAME)).withUser("user", "user.1234", "user").create();
        UsersSettings.forEapServer(getJbossHome(CONTAINER2_NAME)).withUser("user", "user.1234", "user").create();


        AddressSecuritySettings.forContainer(this, CONTAINER1_NAME).forAddress("jms.queue.InQueue").givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest").create();
        AddressSecuritySettings.forContainer(this, CONTAINER1_NAME).forAddress("jms.queue.OutQueue").givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();


        AddressSecuritySettings.forContainer(this, CONTAINER2_NAME).forAddress("jms.queue.InQueue").givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest").create();
        AddressSecuritySettings.forContainer(this, CONTAINER2_NAME).forAddress("jms.queue.OutQueue").givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();


        //restart servers to changes take effect
        stopServer(CONTAINER1_NAME);
        controller.kill(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        controller.kill(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);


        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiNameForMdb, 10000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiNameForMdb, 10000, 10, 10);


        //setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));
        ProducerClientAck producerRedG2 = new ProducerClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(
                CONTAINER2_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG2.setMessageBuilder(new GroupColoredMessageBuilder("g2", "RED"));
        ProducerClientAck producerBlueG1 = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerBlueG1.setMessageBuilder(new GroupColoredMessageBuilder("g2", "BLUE"));



        deployer.deploy(MDB_ON_QUEUE1_SECURITY);



        receiver1.start();
        receiver2.start();

        producerBlueG1.start();
        Thread.sleep(2000);
        producerRedG1.start();
        producerRedG2.start();


       // producerBlueG1.join();
        producerRedG1.join();
        producerRedG2.join();
        receiver1.join();
        receiver2.join();


        jmsAdminOperations1 = getJMSOperations(CONTAINER1_NAME);
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue on server1: " + jmsAdminOperations1.getNumberOfConsumersOnQueue("InQueue"));
        jmsAdminOperations2 = getJMSOperations(CONTAINER2_NAME);
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue server2: " + jmsAdminOperations2.getNumberOfConsumersOnQueue("InQueue"));

        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        Assert.assertEquals("Number of received messages does not match on receiver1", NUMBER_OF_MESSAGES_PER_PRODUCER, receiver1.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of received messages does not match on receiver2", NUMBER_OF_MESSAGES_PER_PRODUCER, receiver2.getListOfReceivedMessages().size());
        ArrayList<String> receiver1GroupiIDs = new ArrayList<String>();
        ArrayList<String> receiver2GroupiIDs = new ArrayList<String>();
        for (Map<String, String> m : receiver1.getListOfReceivedMessages()) {
            if (m.containsKey("JMSXGroupID") && !receiver1GroupiIDs.contains(m.get("JMSXGroupID"))) {
                receiver1GroupiIDs.add(m.get("JMSXGroupID"));
            }
        }
        for (Map<String, String> m : receiver2.getListOfReceivedMessages()) {
            if (m.containsKey("JMSXGroupID") && !receiver1GroupiIDs.contains(m.get("JMSXGroupID"))) {
                receiver2GroupiIDs.add(m.get("JMSXGroupID"));
            }
        }
        for (String gId : receiver1GroupiIDs) {
            if (receiver2GroupiIDs.contains(gId)) {
                Assert.assertEquals("GroupIDs was mixed", false, true);
            }

        }



        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGroupingToFail() throws Exception {
        prepareServer(CONTAINER1_NAME, true);
        prepareServer(CONTAINER2_NAME, true);
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        JMSOperations jmsAdminOperations1 = getJMSOperations(CONTAINER1_NAME);
        JMSOperations jmsAdminOperations2 = getJMSOperations(CONTAINER2_NAME);

        //setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);

        UsersSettings.forEapServer(getJbossHome(CONTAINER1_NAME)).withUser("user", "user.1234", "user").create();


        AddressSecuritySettings.forContainer(this, CONTAINER1_NAME).forAddress("jms.queue.InQueue").givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest").create();
        AddressSecuritySettings.forContainer(this, CONTAINER1_NAME).forAddress("jms.queue.OutQueue").givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();

        AddressSecuritySettings.forContainer(this, CONTAINER2_NAME).forAddress("jms.queue.InQueue").givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest").create();
        AddressSecuritySettings.forContainer(this, CONTAINER2_NAME).forAddress("jms.queue.OutQueue").givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();


        //restart servers to changes take effect
        stopServer(CONTAINER1_NAME);
        controller.kill(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        controller.kill(CONTAINER2_NAME);
        controller.start(CONTAINER1_NAME);
        deployer.deploy(MDB_ON_QUEUE1_SECURITY);

        //setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));

        ReceiverClientAck receiver2 = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiNameForMdb, 10000, 10, 10);

        producerRedG1.start();
        receiver2.start();

        producerRedG1.join();
        receiver2.join();


        Assert.assertEquals("Number of received messages does not match on receiver2", 0, receiver2.getListOfReceivedMessages().size());
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }


    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithoutDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(false);
    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(true);
    }

    public void clusterTestWithMdbOnTopic(boolean mdbsWithDifferentSubscriptions) throws Exception {

        prepareServers();

        controller.start(CONTAINER2_NAME);

        controller.start(CONTAINER1_NAME);

        if (mdbsWithDifferentSubscriptions) {

            deployer.deploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the same
            deployer.deploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);
        } else {

            deployer.deploy(MDB_ON_TOPIC1);
            deployer.deploy(MDB_ON_TOPIC2);
        }

        // give it some time - mdbs to subscribe
        Thread.sleep(1000);

        // Send messages into input topic and read from out topic
        log.info("Start publisher and consumer.");
        PublisherClientAck publisher = new PublisherClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiNameForMdb, 10000, 10, 10);

        publisher.start();
        receiver.start();

        publisher.join();
        receiver.join();

        if (mdbsWithDifferentSubscriptions) {
            Assert.assertEquals("Number of sent and received messages is different. There should be twice as many received messages"
                            + "than sent. Sent: " + publisher.getListOfSentMessages().size()
                            + "Received: " + receiver.getListOfReceivedMessages().size(), 2 * publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size()
            );
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + 2 * NUMBER_OF_MESSAGES_PER_PRODUCER
                    + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                    , 2 * NUMBER_OF_MESSAGES_PER_PRODUCER);

        } else {
            Assert.assertEquals("Number of sent and received messages is not correct. There should be as many received messages as"
                            + " sent. Sent: " + publisher.getListOfSentMessages().size()
                            + "Received: " + receiver.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size()
            );
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                    + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                    , NUMBER_OF_MESSAGES_PER_PRODUCER);
        }

        Assert.assertFalse("Producer did not sent any messages. Sent: " + publisher.getListOfSentMessages().size()
                , publisher.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size()
                , receiver.getListOfReceivedMessages().size() == 0);

        if (mdbsWithDifferentSubscriptions) {

            deployer.undeploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the same
            deployer.undeploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);

        } else {

            deployer.undeploy(MDB_ON_TOPIC1);
            deployer.undeploy(MDB_ON_TOPIC2);
        }

        stopServer(CONTAINER1_NAME);

        stopServer(CONTAINER2_NAME);

    }

//    /**
//     * - Start 4 servers - local and remotes (1,2,3)
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - Start consumer on remote 2
//     * - Crash remote 3
//     * - Send messages to remnote 1 (message group ids 4 types)
//     * - restart remote 3
//     * - chekc consumer whether it received all messages
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumer() throws Exception {
//        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4_NAME, 20000);
//    }

    /**
     * - Start 4 servers - local and remotes (1,2,3)
     * - Send messages to remote 1 (message group ids 4 types)
     * - Start consumer on remote 2
     * - Crash remote 3
     * - Send messages to remote 1 (message group ids 4 types)
     * - restart remote 3
     * - chekc consumer whether it received all messages
     */
    //TODO see this http://documentation-devel.engineering.redhat.com/site/documentation/en-US/JBoss_Enterprise_Application_Platform/6.4/html/Administration_and_Configuration_Guide/Clustered_Grouping.html
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumerLongRestart() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4_NAME, 20000);
    }

    /**
     * - Start 4 servers - local and remotes (1,2,3)
     * - Send messages to remote 1 (message group ids 4 types)
     * - Start consumer on remote 2
     * - Crash local
     * - Send messages to remote 1 (message group ids 4 types)
     * - restart local
     * - chekc consumer whether it received all messages
     */

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingCrashLocalWithNoConsumer() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER1_NAME, 20000);
    }


    public void clusterWitMessageGroupingCrashServerWithNoConsumer(String serverToKill, long timeBetweenKillAndRestart) throws Exception {
        testMessageGrouping(serverToKill, timeBetweenKillAndRestart, CONTAINER3_NAME);
    }

    public void testMessageGrouping(String serverToKill, long timeBetweenKillAndRestart, String serverWithConsumer) throws Exception {

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = -1;

        // set local grouping-handler on 1st node
        addMessageGrouping(CONTAINER1_NAME, name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(CONTAINER2_NAME, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER3_NAME, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER4_NAME, name, "REMOTE", address, timeout);

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER3_NAME);
        controller.start(CONTAINER4_NAME);

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();


        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        groupMessageVerifiers.add(messageVerifier);

        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(getHostname(serverWithConsumer), getJNDIPort(serverWithConsumer), inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.setMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER2_NAME), getJNDIPort(
                    CONTAINER2_NAME), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(3 * 1000);

        log.info("Kill server - " + serverToKill);
        // kill remote 3
        killServer(serverToKill);
        controller.kill(serverToKill);

        Thread.sleep(3 * 1000);

        log.info("Killed server - " + serverToKill);

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER2_NAME), getJNDIPort(
                    CONTAINER2_NAME), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        log.info("Wait for - " + timeBetweenKillAndRestart);
        Thread.sleep(timeBetweenKillAndRestart);
        log.info("Finished waiting for - " + timeBetweenKillAndRestart);


        // start killed server
        log.info("Start server - " + serverToKill);
        controller.start(serverToKill);
        log.info("Started server - " + serverToKill);

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * 1000);

        // stop producers
        for (ProducerTransAck p : producers) {
            p.stopSending();
            p.join();
            // we need to add messages manually!!!
            for (FinalTestMessageVerifier groupMessageVerifier : groupMessageVerifiers) {
                groupMessageVerifier.addSendMessages(p.getListOfSentMessages());
            }
        }
        // check consumers
        for (ReceiverTransAck r : receivers) {
            r.join();
            messageVerifier.addReceivedMessages(r.getListOfReceivedMessages());
        }

        // now message verifiers
        for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
            verifier.verifyMessages();
        }

        Assert.assertEquals("There is different number of sent and received messages: ",
                messageVerifier.getSentMessages().size(), messageVerifier.getReceivedMessages().size());

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER4_NAME);

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMessageGroupingRestartOfRemote() throws Exception {

        String serverToKill = CONTAINER2_NAME;
        long timeBetweenKillAndRestart = 20000;

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(CONTAINER1_NAME, name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(CONTAINER2_NAME, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER3_NAME, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER4_NAME, name, "REMOTE", address, timeout);

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER3_NAME);
        controller.start(CONTAINER4_NAME);

        List<String> groups = new ArrayList<String>();

        Context context = getContext(CONTAINER1_NAME);
        ConnectionFactory factory = (ConnectionFactory) context.lookup(HornetQTestCaseConstants.CONNECTION_FACTORY_JNDI_EAP6);
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(inQueueJndiNameForMdb);
        MessageProducer producer = session.createProducer(queue);
        Message m = null;

        for (int i = 0; i < 500; i++) {
            m = session.createTextMessage();
            String group = UUID.randomUUID().toString();
            m.setStringProperty("JMSXGroupID", group);
            producer.send(m);
            if (i % 100 == 0) {
                groups.add(group);
            }
        }

        // start producers and consumers
        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>(); // create 500 groups
        List<Client> receivers = new ArrayList<Client>();

        String serverToConnect = null;
        for (String group : groups) {
            if (groups.lastIndexOf(group) % 4 == 0) {
                serverToConnect = CONTAINER1_NAME;

            } else if (groups.lastIndexOf(group) % 4 == 1) {
                serverToConnect = CONTAINER2_NAME;
            } else if (groups.lastIndexOf(group) % 4 == 2) {
                serverToConnect = CONTAINER3_NAME;
            } else {
                serverToConnect = CONTAINER4_NAME;
            }

            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(serverToConnect),
                    getJNDIPort(serverToConnect), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, group));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
            ReceiverTransAck receiver = new ReceiverTransAck(getHostname(serverToConnect), getJNDIPort(serverToConnect),
                    inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.start();
            receivers.add(receiver);
        }

        waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 300, 120000);

        log.info("Kill server - " + serverToKill);
        // kill remote 1
        killServer(serverToKill);
        controller.kill(serverToKill);
        Thread.sleep(3 * 1000);
        log.info("Killed server - " + serverToKill);

        log.info("Wait for - " + timeBetweenKillAndRestart);
        Thread.sleep(timeBetweenKillAndRestart);
        log.info("Finished waiting for - " + timeBetweenKillAndRestart);

        // start killed server
        log.info("Start server - " + serverToKill);
        controller.start(serverToKill);
        log.info("Started server - " + serverToKill);

        // wait timeout time to get messages redistributed to the other node
        waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 600, 120000);

        int numberOfSendMessages = 0;
        int numberOfReceivedMessages = 0;

        // stop producers
        for (ProducerTransAck p : producers) {
            p.stopSending();
            p.join();
            numberOfSendMessages += p.getListOfSentMessages().size();
        }

        // wait for consumers to finish
        for (Client r : receivers) {
            r.join();
            numberOfReceivedMessages += ((ReceiverTransAck) r).getListOfReceivedMessages().size();
        }

        Assert.assertEquals("Number of send and received messages is different: ", numberOfSendMessages+500, numberOfReceivedMessages);

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER4_NAME);

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGrouping() throws Exception {

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(CONTAINER1_NAME, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(CONTAINER2_NAME, name, "REMOTE", address, timeout);

        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER1_NAME);
        Thread.sleep(5000);
        FinalTestMessageVerifier verifier = new TextMessageVerifier();
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), inQueueJndiNameForMdb, 30000, 100, 10);
        receiver.setMessageVerifier(verifier);
        receiver.start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME),inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue2 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(
                CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME),inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue1.start();
        producerToInQueue2.start();


        producerToInQueue1.join();
        producerToInQueue2.join();




        // kill both of the servers
        killServer(CONTAINER1_NAME);
            controller.kill(CONTAINER1_NAME);

        // start 1st server
        controller.start(CONTAINER1_NAME);
        Thread.sleep(5000);


        ProducerTransAck producerToInQueue3 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME),inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue4 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(
                CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME),inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue3.start();
        producerToInQueue4.start();

        producerToInQueue3.join();
        producerToInQueue4.join();

        receiver.join();

        verifier.addSendMessages(producerToInQueue1.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue2.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue3.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue4.getListOfSentMessages());

        verifier.verifyMessages();


        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of sent and received messages is not correct. There should be " + 4 * NUMBER_OF_MESSAGES_PER_PRODUCER
                        + " received but it's : " + receiver.getListOfReceivedMessages().size(), 4 * NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver.getListOfReceivedMessages().size()
        );

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingSimple() throws Exception {

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 0;

        // set local grouping-handler on 1st node
        addMessageGrouping(CONTAINER1_NAME, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(CONTAINER2_NAME, name, "REMOTE", address, timeout);

        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER1_NAME);

        log.info("Send messages to first server.");
        sendMessages(CONTAINER1_NAME, inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        log.info("Send messages to first server - done.");


        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), inQueueJndiNameForMdb, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertTrue("Number received messages must be 0 as producer was not up in parallel with consumer. There should be 0"
                + " received but it's: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size() == 0);

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsExclusiveSmallMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(1, 1));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsNotExclusiveSmallMessages() throws Exception {
        clusterTestWithDiverts(false, new ClientMixMessageBuilder(1, 1));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsExclusiveLargeMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(120, 120));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsNotExclusiveLargeMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(120, 120));
    }

    /**
     * Start 2 servers in cluster
     * - Create divert which sends message to queue - exclusive + non-exclusive
     * - Check that messages gets load-balanced
     */
    private void clusterTestWithDiverts(boolean isExclusive, MessageBuilder messageBuilder) throws Exception {

        int numberOfMessages = 100;

        prepareServers();

        String divertName = "myDivert";
        String divertAddress = "jms.queue." + inQueueNameForMdb;
        String forwardingAddress = "jms.queue." + outQueueNameForMdb;

        createDivert(CONTAINER1_NAME, divertName, divertAddress, forwardingAddress, isExclusive, null, null, null);
        createDivert(CONTAINER2_NAME, divertName, divertAddress, forwardingAddress, isExclusive, null, null, null);

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        // start client
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiNameForMdb, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.start();

        ReceiverClientAck receiverOriginalAddress = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(
                CONTAINER2_NAME), inQueueJndiNameForMdb, 30000, 100, 10);
        receiverOriginalAddress.start();

        ReceiverClientAck receiverDivertedAddress = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(
                CONTAINER2_NAME), outQueueJndiNameForMdb, 30000, 100, 10);
        receiverDivertedAddress.start();

        producerToInQueue1.join(60000);
        receiverOriginalAddress.join(60000);
        receiverDivertedAddress.join(60000);

        // if exclusive check that messages are just in diverted address
        if (isExclusive) {
            Assert.assertEquals("In exclusive mode there must be messages just in diverted address only but there are messages in " +
                    "original address.", 0, receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In exclusive mode there must be messages in diverted address.",
                    numberOfMessages, receiverDivertedAddress.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("In non-exclusive mode there must be messages in " +
                    "original address.", numberOfMessages, receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In non-exclusive mode there must be messages in diverted address.",
                    numberOfMessages, receiverDivertedAddress.getListOfReceivedMessages().size());
        }

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }

    private void createDivert(String containerName, String divertName, String divertAddress, String forwardingAddress, boolean isExclusive,
                              String filter, String routingName, String transformerClassName) {

        controller.start(containerName);
        JMSOperations jmsOperations = getJMSOperations(containerName);
        jmsOperations.addDivert(divertName, divertAddress, forwardingAddress, isExclusive, filter, routingName, transformerClassName);
        jmsOperations.close();
        stopServer(containerName);

    }

    /**
     * Start just local and send messages with different groupids.
     * Start consumers on local and check messages.
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingTestSingleLocalServer() throws Exception {
        clusterTestWitMessageGroupingTestSingleServer(CONTAINER1_NAME);
    }

//    /**
//     * Now stop local and start remote server and send messages to it
//     * Start consumers on remote and check messages.
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitMessageGroupingTestSingleRemoteServer() throws Exception {
//        clusterTestWitMessageGroupingTestSingleServer(CONTAINER2_NAME);
//    }

    public void clusterTestWitMessageGroupingTestSingleServer(String nameOfTestedContainerName) throws Exception {

        int numberOfMessages = 10;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        String serverWithLocalHandler = CONTAINER1_NAME;
        String serverWithRemoteHandler = CONTAINER2_NAME;
        // set local grouping-handler on 1st node
        addMessageGrouping(serverWithLocalHandler, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(serverWithRemoteHandler, name, "REMOTE", address, timeout);

        // first try just local
        controller.start(nameOfTestedContainerName);

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();
        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(nameOfTestedContainerName), getJNDIPort(nameOfTestedContainerName), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }
        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(getHostname(nameOfTestedContainerName), getJNDIPort(nameOfTestedContainerName), inQueueJndiNameForMdb, 10000, 100, 10);
            receiver.setMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        int numberOfSentMessages = 0;
        for (ProducerTransAck p : producers) {
            p.join();
            for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
                verifier.addSendMessages(p.getListOfSentMessages());
            }
            numberOfSentMessages = numberOfSentMessages + p.getListOfSentMessages().size();
        }

        int numberOfReceivedMessages = 0;
        for (ReceiverTransAck r : receivers) {
            r.join();
            numberOfReceivedMessages = numberOfReceivedMessages + r.getListOfReceivedMessages().size();
        }

        for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
            verifier.verifyMessages();
        }

        if (serverWithLocalHandler.equals(nameOfTestedContainerName)) {
            Assert.assertEquals("There is different number of sent and recevied messages.", numberOfSentMessages, numberOfReceivedMessages);
        } else if (serverWithRemoteHandler.equals((nameOfTestedContainerName))) {
            Assert.assertEquals("There must be 0 messages recevied.", 0, numberOfReceivedMessages);
        }

        stopServer(nameOfTestedContainerName);

    }


    private SecurityClient createConsumerReceiveAndRollback(String containerName, String queue) throws Exception {

        SecurityClient securityClient = new SecurityClient(getHostname(containerName), getJNDIPort(containerName), queue, NUMBER_OF_MESSAGES_PER_PRODUCER, null, null);
        securityClient.initializeClient();
        securityClient.consumeAndRollback(200);
        return securityClient;
    }

    private void sendMessages(String containerName, String queue, MessageBuilder messageBuilder) throws InterruptedException {


        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(containerName), getJNDIPort(containerName),
                queue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        log.info("Start producer to send messages to node " + containerName + " to destination: " + queue);
        producerToInQueue1.start();
        producerToInQueue1.join();

    }

    private void addMessageGrouping(String containerName, String name, String type, String address, long timeout) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, 500, 750);

        jmsAdminOperations.close();

        stopServer(containerName);

    }

    /**
     * Deploys mdb with given name
     *
     * @param nameOfMdb nameOfMdb
     */
    public void deployMdb(String nameOfMdb) {
        deployer.deploy(nameOfMdb);
    }

    /**
     * Deploys mdb with given name
     *
     * @param nameOfMdb nameOfMdb
     */
    public void undeployMdb(String nameOfMdb) {
        deployer.undeploy(nameOfMdb);
    }

    /**
     * Create org.jboss.qa.hornetq.apps.clients with the given acknowledge mode on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic           true for topic
     * @return org.jboss.qa.hornetq.apps.clients
     * @throws Exception
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

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
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
        messageBuilder.setAddDuplicatedHeader(true);
        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(100);
        clients.setReceivedMessagesAckCommitAfter(100);

        return clients;
    }

    /**
     * Prepares several servers for topology. Destinations will be created.
     */
    public void prepareServers() {
        prepareServers(true);
    }

    /**
     * Prepares several servers for topology.
     *
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    public void prepareServers(boolean createDestinations) {
        prepareServer(CONTAINER1_NAME, createDestinations);
        prepareServer(CONTAINER2_NAME, createDestinations);
        prepareServer(CONTAINER3_NAME, createDestinations);
        prepareServer(CONTAINER4_NAME, createDestinations);
    }

    /**
     * Prepares server for topology. Destination queues and topics will be created.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareServer(String containerName) {
        prepareServer(containerName, true);
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName      Name of the container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    private void prepareServer(String containerName, boolean createDestinations) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

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
//        jmsAdminOperations.setLoggingLevelForConsole("INFO");
//        jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        if (createDestinations) {
            for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
                jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
            }

            for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
                jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
            }

            jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
            jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);
            jmsAdminOperations.createTopic(inTopicNameForMdb, inTopicJndiNameForMdb);
            jmsAdminOperations.createTopic(outTopicNameForMdb, outTopicJndiNameForMdb);
        }

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER4_NAME);

    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue defined in hornetq-jms.xml
     * Queues are part of deployment
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPQUEUE1)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnTempQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(getHornetqJmsXmlWithQueues()), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/mdbOnQueue1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE2)
    @TargetsContainer(CONTAINER2_NAME)
    public static JavaArchive createDeploymentMdbOnQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue2.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue defined in hornetq-jms.xml
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPQUEUE2)
    @TargetsContainer(CONTAINER2_NAME)
    public static JavaArchive createDeploymentMdbOnTempQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(getHornetqJmsXmlWithQueues()), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue2.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1_SECURITY)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnQueueWithSecurity() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1_SECURITY2)
    @TargetsContainer(CONTAINER2_NAME)
    public static JavaArchive createDeploymentMdbOnQueueWithSecurity2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security2.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TOPIC1)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnTopic1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPTOPIC1)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnTempTopic1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopicToTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(getHornetqJmsXmlWithTopic()), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPTOPIC2)
    @TargetsContainer(CONTAINER2_NAME)
    public static JavaArchive createDeploymentMdbOnTempTopic2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopicToTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(getHornetqJmsXmlWithTopic()), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TOPIC2)
    @TargetsContainer(CONTAINER2_NAME)
    public static JavaArchive createDeploymentMdbOnTopic2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnTopicWithSub1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopicWithSub1.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        //      Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue and sends reply back to sender via tempQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1_TEMP_QUEUE)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1withTempQueue.jar");
        mdbJar.addClass(LocalMdbFromQueueToTempQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    //jms/queue/InQueue
    public static String getHornetqJmsXmlWithQueues() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">\n");
        sb.append("<hornetq-server>\n");
        sb.append("<jms-destinations>\n");
        sb.append("<jms-queue name=\"");
        sb.append(inQueueNameForMdb);
        sb.append("\">\n");
        sb.append("<entry name=\"java:jboss/exported/");
        sb.append(inQueueJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("<entry name=\"");
        sb.append(inQueueJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("</jms-queue>\n");
        sb.append("<jms-queue name=\"");
        sb.append(outQueueNameForMdb);
        sb.append("\">\n");
        sb.append("<entry name=\"java:jboss/exported/");
        sb.append(outQueueJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("<entry name=\"");
        sb.append(outQueueJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("</jms-queue>\n");
        sb.append("</jms-destinations>\n");
        sb.append("</hornetq-server>\n");
        sb.append("</messaging-deployment>\n");
        log.info(sb.toString());
        return sb.toString();
    }

    public static String getHornetqJmsXmlWithTopic() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">\n");
        sb.append("<hornetq-server>\n");
        sb.append("<jms-destinations>\n");
        sb.append("<jms-topic name=\"");
        sb.append(inTopicNameForMdb);
        sb.append("\">\n");
        sb.append("<entry name=\"java:jboss/exported/");
        sb.append(inTopicJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("<entry name=\"");
        sb.append(inTopicJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("</jms-topic>\n");
        sb.append("<jms-topic name=\"");
        sb.append(outTopicNameForMdb);
        sb.append("\">\n");
        sb.append("<entry name=\"java:jboss/exported/");
        sb.append(outTopicJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("<entry name=\"");
        sb.append(outTopicJndiNameForMdb);
        sb.append("\"/>\n");
        sb.append("</jms-topic>\n");
        sb.append("</jms-destinations>\n");
        sb.append("</hornetq-server>\n");
        sb.append("</messaging-deployment>\n");
        log.info(sb.toString());
        return sb.toString();
    }

    public ArrayList<File> getAllPagingFiles(String pagingDirectoryPath) {
        File mainPagingDirectoryPath = new File(pagingDirectoryPath);
        ArrayList<File> out = new ArrayList<File>();
        // get all paging directories in main paging directory
        ArrayList<File> pagingDirectories = new ArrayList<File>(Arrays.asList(mainPagingDirectoryPath.listFiles()));
        for (File dir : pagingDirectories) {
            //if  some file exists in main paging directory skip it
            if (dir.isDirectory()) {
                ArrayList<File> files = new ArrayList<File>(Arrays.asList(dir.listFiles()));
                for (File f : files) {
                    // name of file can not contain "address" because its file which doesn't contain paging data nad still exists in paging directories
                    if (f.isFile() && !f.getName().contains("address")) {
                        out.add(f);
                    }

                }

            }

        }

        return out;
    }

}