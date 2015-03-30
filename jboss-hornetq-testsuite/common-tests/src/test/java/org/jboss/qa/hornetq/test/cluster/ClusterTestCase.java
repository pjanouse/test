package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerResp;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SecurityClient;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.GroupColoredMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.GroupMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MixMessageGroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToQueueWithSelectorAndSecurity;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTempQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicToTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
//TODO
//ClusterTestCase
//        - clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly
//        - clusterTestWithMdbOnTopicDeployAndUndeployTwoServers
//        - clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers
//        - clusterTestWithMdbWithSelectorAndSecurityTwoServers

/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones: export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2 export MCAST_ADDR=$MCAST_ADDRIPV6
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

    private final JavaArchive MDB_ON_QUEUE1 = createDeploymentMdbOnQueue1();
    private final JavaArchive MDB_ON_QUEUE2 = createDeploymentMdbOnQueue2();
    private final JavaArchive MDB_ON_QUEUE1_SECURITY = createDeploymentMdbOnQueueWithSecurity();
    private final JavaArchive MDB_ON_QUEUE1_SECURITY2 = createDeploymentMdbOnQueueWithSecurity2();

    private final JavaArchive MDB_ON_TEMPQUEUE1 = createDeploymentMdbOnTempQueue1();
    private final JavaArchive MDB_ON_TEMPQUEUE2 = createDeploymentMdbOnTempQueue2();

    private final JavaArchive MDB_ON_TOPIC1 = createDeploymentMdbOnTopic1();
    private final JavaArchive MDB_ON_TOPIC2 = createDeploymentMdbOnTopic2();

    private final JavaArchive MDB_ON_TEMPTOPIC1 = createDeploymentMdbOnTempTopic1();
    private final JavaArchive MDB_ON_TEMPTOPIC2 = createDeploymentMdbOnTempTopic2();

    private final JavaArchive MDB_ON_QUEUE1_TEMP_QUEUE = createDeploymentMdbOnQueue1Temp();

    private final JavaArchive MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = createDeploymentMdbOnTopicWithSub1();

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
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTest() throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        Clients queueClients = createClients(Session.SESSION_TRANSACTED, false);
        Clients topicClients = createClients(Session.SESSION_TRANSACTED, true);

        queueClients.startClients();
        topicClients.startClients();
        JMSTools.waitForClientsToFinish(queueClients);
        JMSTools.waitForClientsToFinish(topicClients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.",
                queueClients.evaluateResults());
        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.",
                topicClients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithKillOfServerInCluster() throws Exception {

        prepareServers();

        container(1).start();
        container(2).start();

        JMSTools.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        JMSTools.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
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

        // if (shutdown) {
        // controller.stop(CONTAINER2_NAME);
        // } else {
        container(2).kill();
        // }

        log.info("########################################");
        log.info("Start again - second server");
        log.info("########################################");
        container(2).start();
        JMSTools.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000);
        log.info("########################################");
        log.info("Second server started");
        log.info("########################################");

        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), inQueueJndiNameForMdb, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);
        // printQueueStatus(CONTAINER1_NAME_NAME, inQueueName);
        // printQueueStatus(CONTAINER2_NAME, inQueueName);

        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }

    // THIS WAS COMMENTED BECAUSE clusterTest() IS BASICALLY TESTING THE SAME SCENARIO
    // /**
    // * This test will start two servers A and B in cluster.
    // * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
    // * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitDuplicateId() throws Exception {
    //
    // prepareServers();
    //
    // controller.start(CONTAINER2_NAME);
    //
    // controller.start(CONTAINER1_NAME_NAME);
    //
    // ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME),
    // getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
    // MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
    // messageBuilder.setAddDuplicatedHeader(true);
    // producer1.setMessageBuilder(messageBuilder);
    // producer1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
    // producer1.start();
    // producer1.join();
    //
    // ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME),
    // getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, 2000, 10, 10);
    // receiver1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
    // receiver1.start();
    // receiver1.join();
    //
    // stopServer(CONTAINER1_NAME_NAME);
    //
    // stopServer(CONTAINER2_NAME);
    //
    // }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithoutDuplicateIdWithInterruption() throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        // send messages without dup id -> load-balance to node 2
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
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

            context = container(1).getContext();

            cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());

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

        // receive some of them from first server and kill receiver -> only some of them gets back to
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), inQueueJndiNameForMdb, 10000, 10, 10);
        receiver2.start();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver2.getListOfReceivedMessages().size());
        container(1).stop();
        container(2).stop();

    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueue() throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_QUEUE1);

        container(2).deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: "
                + producer.getListOfSentMessages().size() + "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size(), producer
                .getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(),
                receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(),
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        container(1).undeploy(MDB_ON_QUEUE1);

        container(2).undeploy(MDB_ON_QUEUE2);

        container(1).stop();

        container(2).stop();

    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueueDeployAndUndeploy() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        int numberOfMessages = 30;
        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_TEMPQUEUE1);

        container(2).deploy(MDB_ON_TEMPQUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        producer.start();
        producer.join();
        log.info("Removing MDB ond secnod server and adding it back ");
        container(2).undeploy(MDB_ON_TEMPQUEUE2);
        container(2).deploy(MDB_ON_TEMPQUEUE2);
        log.info("Trying to read from newly deployed OutQueue");

        receiver.start();
        receiver.join();

        Assert.assertEquals("Number of messages in OutQueue does not match", numberOfMessages, receiver
                .getListOfReceivedMessages().size());
        container(1).undeploy(MDB_ON_TEMPQUEUE1);
        container(2).undeploy(MDB_ON_TEMPQUEUE2);

        container(1).stop();

        container(2).stop();

    }

    /**
     * This test will start one server A. Start producers/publishers connected to A with client/transaction acknowledge on
     * queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicDeployAleradyExistingTopic() throws Exception {
        log.info("PREPARING SERVERS");
        boolean passed = false;
        prepareServers(true);
        container(1).start();
        try {
            container(1).deploy(MDB_ON_TEMPTOPIC1);
            passed = true;

        } catch (Exception e) {
            // this is correct behavior
        }
        Assert.assertFalse("Deployment of already existing topic didn't failed", passed);
        container(1).undeploy(MDB_ON_TEMPTOPIC1);

        container(1).stop();
    }

    /**
     * This test will start one server A. Start producers/publishers connected to A with client/transaction acknowledge on
     * queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        container(1).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), inTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        subscriber.start();
        subscriber.join();
        container(1).undeploy(MDB_ON_TEMPTOPIC1);
        container(1).deploy(MDB_ON_TEMPTOPIC1);

        Assert.assertEquals("Number of subscriptions is not 0", 0,
                jmsAdminOperations.getNumberOfDurableSubscriptionsOnTopic("mySubscription"));
        container(1).undeploy(MDB_ON_TEMPTOPIC1);
        container(1).stop();
    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(container(1), false);
        prepareServer(container(2), false);
        container(1).start();
        container(2).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), outTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();
        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        container(1).stop();
        container(2).stop();
    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(container(1), true);
        prepareServer(container(2), false);
        container(1).start();
        container(2).start();
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), inTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();

        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        container(1).stop();
        container(2).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTempQueueonOtherNodes() throws Exception {
        prepareServers(true);
        container(2).start();
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        JMSOperations jmsAdminOperationsContainer1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperationsContainer2 = container(2).getJmsOperations();
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
        container(1).start();
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = container(1).getContext();
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
        container(1).kill();
        container(1).start();
        // Thread.sleep(80000);
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
        prepareServer(container(1), false);
        container(1).start();
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, false);
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = container(1).getContext();
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
        container(1).kill();
        container(1).start();
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
        container(1).start();
        try {
            Context context = container(1).getContext();
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
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, 1, 300);
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
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, 1, 300);
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
        prepareServer(container(1), false);
        container(1).start();
        container(1).deploy(MDB_ON_TEMPQUEUE1);
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(1), outQueueJndiNameForMdb, 10000, 10, 10);
        producer.start();
        producer.join();
        container(1).deploy(MDB_ON_TEMPQUEUE1);
        receiver.start();
        receiver.join();
        Assert.assertEquals("Number of messages does not match", NUMBER_OF_MESSAGES_PER_PRODUCER, receiver
                .getListOfReceivedMessages().size());
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGrouping() throws Exception {
        prepareServer(container(1), true);
        prepareServer(container(2), true);
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

        // setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);
        jmsAdminOperations1.addLoggerCategory("org.jboss", "INFO");
        jmsAdminOperations1.addLoggerCategory("org.jboss.qa.hornetq.apps.mdb", "TRACE");
        jmsAdminOperations1.seRootLoggingLevel("TRACE");

        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        UsersSettings.forEapServer(container(1).getServerHome()).withUser("user", "user.1234", "user").create();
        UsersSettings.forEapServer(container(2).getServerHome()).withUser("user", "user.1234", "user").create();

        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        // restart servers to changes take effect
        container(1).stop();
        container(2).stop();

        container(1).start();
        container(2).start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueueJndiNameForMdb, 10000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        // setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));
        ProducerClientAck producerRedG2 = new ProducerClientAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG2.setMessageBuilder(new GroupColoredMessageBuilder("g2", "RED"));
        ProducerClientAck producerBlueG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerBlueG1.setMessageBuilder(new GroupColoredMessageBuilder("g2", "BLUE"));

        container(1).deploy(MDB_ON_QUEUE1_SECURITY);

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

        jmsAdminOperations1 = container(1).getJmsOperations();
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue on server1: "
                + jmsAdminOperations1.getNumberOfConsumersOnQueue("InQueue"));
        jmsAdminOperations2 = container(2).getJmsOperations();
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue server2: "
                + jmsAdminOperations2.getNumberOfConsumersOnQueue("InQueue"));

        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        Assert.assertEquals("Number of received messages does not match on receiver1", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver1.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of received messages does not match on receiver2", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver2.getListOfReceivedMessages().size());
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

        container(1).stop();
        container(2).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGroupingToFail() throws Exception {
        prepareServer(container(1), true);
        prepareServer(container(2), true);
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

        // setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);

        UsersSettings.forEapServer(container(1).getServerHome()).withUser("user", "user.1234", "user").create();

        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();

        // restart servers to changes take effect
        container(1).stop();
        container(1).kill();
        container(2).stop();
        container(2).kill();
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_SECURITY);

        // setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));

        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        producerRedG1.start();
        receiver2.start();

        producerRedG1.join();
        receiver2.join();

        Assert.assertEquals("Number of received messages does not match on receiver2", 0, receiver2.getListOfReceivedMessages()
                .size());
        container(1).stop();
        container(2).stop();
    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithoutDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(false);
    }

    /**
     * This test will start two servers A and B in cluster. Start producers/publishers connected to A with client/transaction
     * acknowledge on queue/topic. Start consumers/subscribers connected to B with client/transaction acknowledge on
     * queue/topic.
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

        container(2).start();

        container(1).start();

        if (mdbsWithDifferentSubscriptions) {

            container(1).deploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the
            // same
            container(1).deploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);
        } else {

            container(1).deploy(MDB_ON_TOPIC1);
            container(2).deploy(MDB_ON_TOPIC2);
        }

        // give it some time - mdbs to subscribe
        Thread.sleep(1000);

        // Send messages into input topic and read from out topic
        log.info("Start publisher and consumer.");
        PublisherClientAck publisher = new PublisherClientAck(container(1), inTopicJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        publisher.start();
        receiver.start();

        publisher.join();
        receiver.join();

        if (mdbsWithDifferentSubscriptions) {
            Assert.assertEquals(
                    "Number of sent and received messages is different. There should be twice as many received messages"
                            + "than sent. Sent: " + publisher.getListOfSentMessages().size() + "Received: "
                            + receiver.getListOfReceivedMessages().size(), 2 * publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size());
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + 2
                    * NUMBER_OF_MESSAGES_PER_PRODUCER + " Received: " + receiver.getListOfReceivedMessages().size(), receiver
                    .getListOfReceivedMessages().size(), 2 * NUMBER_OF_MESSAGES_PER_PRODUCER);

        } else {
            Assert.assertEquals(
                    "Number of sent and received messages is not correct. There should be as many received messages as"
                            + " sent. Sent: " + publisher.getListOfSentMessages().size() + "Received: "
                            + receiver.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size(), receiver
                            .getListOfReceivedMessages().size());
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: "
                    + NUMBER_OF_MESSAGES_PER_PRODUCER + " Received: " + receiver.getListOfReceivedMessages().size(), receiver
                    .getListOfReceivedMessages().size(), NUMBER_OF_MESSAGES_PER_PRODUCER);
        }

        Assert.assertFalse("Producer did not sent any messages. Sent: " + publisher.getListOfSentMessages().size(), publisher
                .getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(),
                receiver.getListOfReceivedMessages().size() == 0);

        if (mdbsWithDifferentSubscriptions) {

            container(1).deploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the
            // same
            container(1).deploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);

        } else {

            container(1).deploy(MDB_ON_TOPIC1);
            container(2).deploy(MDB_ON_TOPIC2);
        }

        container(1).stop();

        container(2).stop();

    }

    // /**
    // * - Start 4 servers - local and remotes (1,2,3)
    // * - Send messages to remote 1 (message group ids 4 types)
    // * - Start consumer on remote 2
    // * - Crash remote 3
    // * - Send messages to remnote 1 (message group ids 4 types)
    // * - restart remote 3
    // * - chekc consumer whether it received all messages
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumer() throws Exception {
    // clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4_NAME, 20000);
    // }

    /**
     * - Start 4 servers - local and remotes (1,2,3) - Send messages to remote 1 (message group ids 4 types) - Start consumer on
     * remote 2 - Crash remote 3 - Send messages to remote 1 (message group ids 4 types) - restart remote 3 - chekc consumer
     * whether it received all messages
     */
    // TODO see this
    // http://documentation-devel.engineering.redhat.com/site/documentation/en-US/JBoss_Enterprise_Application_Platform/6.4/html/Administration_and_Configuration_Guide/Clustered_Grouping.html
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumerLongRestart() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(container(4), 20000);
    }

    /**
     * - Start 4 servers - local and remotes (1,2,3) - Send messages to remote 1 (message group ids 4 types) - Start consumer on
     * remote 2 - Crash local - Send messages to remote 1 (message group ids 4 types) - restart local - chekc consumer whether
     * it received all messages
     */

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingCrashLocalWithNoConsumer() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(container(1), 20000);
    }

    public void clusterWitMessageGroupingCrashServerWithNoConsumer(Container serverToKill, long timeBetweenKillAndRestart)
            throws Exception {
        testMessageGrouping(serverToKill, timeBetweenKillAndRestart, container(3));
    }

    public void testMessageGrouping(Container serverToKill, long timeBetweenKillAndRestart, Container serverWithConsumer)
            throws Exception {

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = -1;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();

        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        groupMessageVerifiers.add(messageVerifier);

        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(serverWithConsumer, inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.setMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(3 * 1000);

        log.info("Kill server - " + serverToKill);
        // kill remote 3
        serverToKill.kill();

        Thread.sleep(3 * 1000);

        log.info("Killed server - " + serverToKill);

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), inQueueJndiNameForMdb, numberOfMessages);
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
        serverToKill.start();
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

        Assert.assertEquals("There is different number of sent and received messages: ", messageVerifier.getSentMessages()
                .size(), messageVerifier.getReceivedMessages().size());

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMessageGroupingRestartOfRemote() throws Exception {

        Container serverToKill = container(2);
        long timeBetweenKillAndRestart = 20000;

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<String> groups = new ArrayList<String>();

        Context context = container(1).getContext();
        ConnectionFactory factory = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
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

        Container serverToConnect = null;
        for (String group : groups) {
            if (groups.lastIndexOf(group) % 4 == 0) {
                serverToConnect = container(1);

            } else if (groups.lastIndexOf(group) % 4 == 1) {
                serverToConnect = container(2);
            } else if (groups.lastIndexOf(group) % 4 == 2) {
                serverToConnect = container(3);
            } else {
                serverToConnect = container(4);
            }

            ProducerTransAck producerToInQueue1 = new ProducerTransAck(serverToConnect, inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, group));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
            ReceiverTransAck receiver = new ReceiverTransAck(serverToConnect, inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.start();
            receivers.add(receiver);
        }

        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 300, 120000);

        log.info("Kill server - " + serverToKill);
        // kill remote 1
        serverToKill.kill();
        Thread.sleep(3 * 1000);
        log.info("Killed server - " + serverToKill);

        log.info("Wait for - " + timeBetweenKillAndRestart);
        Thread.sleep(timeBetweenKillAndRestart);
        log.info("Finished waiting for - " + timeBetweenKillAndRestart);

        // start killed server
        log.info("Start server - " + serverToKill);
        serverToKill.start();
        log.info("Started server - " + serverToKill);

        // wait timeout time to get messages redistributed to the other node
        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 600, 120000);

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

        Assert.assertEquals("Number of send and received messages is different: ", numberOfSendMessages + 500,
                numberOfReceivedMessages);

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

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
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);

        container(2).start();
        container(1).start();
        Thread.sleep(5000);
        FinalTestMessageVerifier verifier = new TextMessageVerifier();
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 30000, 100, 10);
        receiver.setMessageVerifier(verifier);
        receiver.start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue2 = new ProducerTransAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue1.start();
        producerToInQueue2.start();

        producerToInQueue1.join();
        producerToInQueue2.join();

        // kill both of the servers
        container(1).kill();

        // start 1st server
        container(1).start();
        Thread.sleep(5000);

        ProducerTransAck producerToInQueue3 = new ProducerTransAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue4 = new ProducerTransAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
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
        Assert.assertEquals("Number of sent and received messages is not correct. There should be " + 4
                * NUMBER_OF_MESSAGES_PER_PRODUCER + " received but it's : " + receiver.getListOfReceivedMessages().size(),
                4 * NUMBER_OF_MESSAGES_PER_PRODUCER, receiver.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

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
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);

        container(2).start();
        container(1).start();

        log.info("Send messages to first server.");
        sendMessages(container(1), inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        log.info("Send messages to first server - done.");

        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertTrue(
                "Number received messages must be 0 as producer was not up in parallel with consumer. There should be 0"
                        + " received but it's: " + receiver.getListOfReceivedMessages().size(), receiver
                        .getListOfReceivedMessages().size() == 0);

        container(1).stop();
        container(2).stop();

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
     * Start 2 servers in cluster - Create divert which sends message to queue - exclusive + non-exclusive - Check that messages
     * gets load-balanced
     */
    private void clusterTestWithDiverts(boolean isExclusive, MessageBuilder messageBuilder) throws Exception {

        int numberOfMessages = 100;

        prepareServers();

        String divertName = "myDivert";
        String divertAddress = "jms.queue." + inQueueNameForMdb;
        String forwardingAddress = "jms.queue." + outQueueNameForMdb;

        createDivert(container(1), divertName, divertAddress, forwardingAddress, isExclusive, null, null, null);
        createDivert(container(2), divertName, divertAddress, forwardingAddress, isExclusive, null, null, null);

        container(1).start();
        container(2).start();

        // start client
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.start();

        ReceiverClientAck receiverOriginalAddress = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 30000, 100, 10);
        receiverOriginalAddress.start();

        ReceiverClientAck receiverDivertedAddress = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 30000, 100, 10);
        receiverDivertedAddress.start();

        producerToInQueue1.join(60000);
        receiverOriginalAddress.join(60000);
        receiverDivertedAddress.join(60000);

        // if exclusive check that messages are just in diverted address
        if (isExclusive) {
            Assert.assertEquals(
                    "In exclusive mode there must be messages just in diverted address only but there are messages in "
                            + "original address.", 0, receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In exclusive mode there must be messages in diverted address.", numberOfMessages,
                    receiverDivertedAddress.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("In non-exclusive mode there must be messages in " + "original address.", numberOfMessages,
                    receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In non-exclusive mode there must be messages in diverted address.", numberOfMessages,
                    receiverDivertedAddress.getListOfReceivedMessages().size());
        }

        container(1).stop();
        container(2).stop();

    }

    private void createDivert(Container container, String divertName, String divertAddress, String forwardingAddress,
            boolean isExclusive, String filter, String routingName, String transformerClassName) {

        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.addDivert(divertName, divertAddress, forwardingAddress, isExclusive, filter, routingName,
                transformerClassName);
        jmsOperations.close();
        container.stop();
    }

    /**
     * Start just local and send messages with different groupids. Start consumers on local and check messages.
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingTestSingleLocalServer() throws Exception {
        clusterTestWitMessageGroupingTestSingleServer(container(1));
    }

    // /**
    // * Now stop local and start remote server and send messages to it
    // * Start consumers on remote and check messages.
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitMessageGroupingTestSingleRemoteServer() throws Exception {
    // clusterTestWitMessageGroupingTestSingleServer(CONTAINER2_NAME);
    // }

    public void clusterTestWitMessageGroupingTestSingleServer(Container testedContainer) throws Exception {

        int numberOfMessages = 10;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        Container serverWithLocalHandler = container(1);
        Container serverWithRemoteHandler = container(2);
        // set local grouping-handler on 1st node
        addMessageGrouping(serverWithLocalHandler, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(serverWithRemoteHandler, name, "REMOTE", address, timeout);

        // first try just local
        testedContainer.start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();
        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(testedContainer, inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }
        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(testedContainer, inQueueJndiNameForMdb, 10000, 100, 10);
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

        if (serverWithLocalHandler.equals(testedContainer)) {
            Assert.assertEquals("There is different number of sent and recevied messages.", numberOfSentMessages,
                    numberOfReceivedMessages);
        } else if (serverWithRemoteHandler.equals(testedContainer)) {
            Assert.assertEquals("There must be 0 messages recevied.", 0, numberOfReceivedMessages);
        }

        testedContainer.stop();
    }

    private SecurityClient createConsumerReceiveAndRollback(Container cont, String queue) throws Exception {

        SecurityClient securityClient = new SecurityClient(cont, queue, NUMBER_OF_MESSAGES_PER_PRODUCER, null, null);
        securityClient.initializeClient();
        securityClient.consumeAndRollback(200);
        return securityClient;
    }

    private void sendMessages(Container cont, String queue, MessageBuilder messageBuilder) throws InterruptedException {

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(cont, queue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        log.info("Start producer to send messages to node " + cont.getName() + " to destination: " + queue);
        producerToInQueue1.start();
        producerToInQueue1.join();

    }

    private void addMessageGrouping(Container container, String name, String type, String address, long timeout) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, 500, 750);
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Create org.jboss.qa.hornetq.apps.clients with the given acknowledge mode on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic true for topic
     * @return org.jboss.qa.hornetq.apps.clients
     * @throws Exception
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
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
        prepareServer(container(1), createDestinations);
        prepareServer(container(2), createDestinations);
        prepareServer(container(3), createDestinations);
        prepareServer(container(4), createDestinations);
    }

    /**
     * Prepares server for topology. Destination queues and topics will be created.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareServer(Container container) {
        prepareServer(container, true);
    }

    /**
     * Prepares server for topology.
     *
     * @param container The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    private void prepareServer(Container container, boolean createDestinations) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        // jmsAdminOperations.setLoggingLevelForConsole("INFO");
        // jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

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
        container.stop();
    }

    @Before
    @After
    public void stopAllServers() {

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    public static JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

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

    public static JavaArchive createDeploymentMdbOnQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnTempQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(getHornetqJmsXmlWithQueues()), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueueWithSecurity() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueueWithSecurity2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security2.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
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
    public static JavaArchive createDeploymentMdbOnTopicWithSub1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopicWithSub1.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue and sends reply back to sender via
     * tempQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1withTempQueue.jar");
        mdbJar.addClass(LocalMdbFromQueueToTempQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    // jms/queue/InQueue
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
            // if some file exists in main paging directory skip it
            if (dir.isDirectory()) {
                ArrayList<File> files = new ArrayList<File>(Arrays.asList(dir.listFiles()));
                for (File f : files) {
                    // name of file can not contain "address" because its file which doesn't contain paging data nad still
                    // exists in paging directories
                    if (f.isFile() && !f.getName().contains("address")) {
                        out.add(f);
                    }

                }

            }

        }

        return out;
    }

}