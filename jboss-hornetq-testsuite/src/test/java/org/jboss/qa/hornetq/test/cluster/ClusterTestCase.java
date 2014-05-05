package org.jboss.qa.hornetq.test.cluster;

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
import org.jboss.qa.hornetq.apps.impl.GroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;

/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 *
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
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    private static final String MDB_ON_QUEUE1 = "mdbOnQueue1";
    private static final String MDB_ON_QUEUE2 = "mdbOnQueue2";

    private static final String MDB_ON_TOPIC1 = "mdbOnTopic1";
    private static final String MDB_ON_TOPIC2 = "mdbOnTopic2";

    private static final String MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = "mdbOnTopic1WithDifferentSubscriptionName1";


    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";

    // InQueue and OutQueue for mdb
    String inQueueNameForMdb = "InQueue";
    String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    String outQueueNameForMdb = "OutQueue";
    String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;

    // InTopic and OutTopic for mdb
    String inTopicNameForMdb = "InTopic";
    String inTopicJndiNameForMdb = "jms/topic/" + inTopicNameForMdb;
    String outTopicNameForMdb = "OutTopic";
    String outTopicJndiNameForMdb = "jms/topic/" + outTopicNameForMdb;

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

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        Clients queueClients = createClients(Session.CLIENT_ACKNOWLEDGE, false);
        Clients topicClients = createClients(Session.SESSION_TRANSACTED, true);

        queueClients.startClients();
        topicClients.startClients();

        waitForClientsToFinish(queueClients);
        waitForClientsToFinish(topicClients);

        Assert.assertTrue("There are failures detected by clients. More information in log.", queueClients.evaluateResults());
        Assert.assertTrue("There are failures detected by clients. More information in log.", topicClients.evaluateResults());

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

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

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
// give some time for servers to find each other
        waitHornetQToAlive(CONTAINER1_IP, 5445, 60000);
        waitHornetQToAlive(CONTAINER2_IP, 5445, 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, numberOfMessages);
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
//            controller.stop(CONTAINER2);
//        } else {
            killServer(CONTAINER2);
            controller.kill(CONTAINER2);
//        }

        log.info("########################################");
        log.info("Start again - second server");
        log.info("########################################");
        controller.start(CONTAINER2);
        waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 300000);
        log.info("########################################");
        log.info("Second server started");
        log.info("########################################");

        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, inQueueJndiNameForMdb, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);
//        printQueueStatus(CONTAINER1, inQueueName);
//        printQueueStatus(CONTAINER2, inQueueName);

        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void clusterTestWitDuplicateId() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(new ClientMixMessageBuilder(10,100));
        producer1.start();
        producer1.join();

        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, 10000, 10, 10);
        receiver1.start();
        receiver1.join();

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void clusterTestWitoutDuplicateIdWithInterruption() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // send messages without dup id -> load-balance to node 2
        SoakProducerClientAck producer1 = new SoakProducerClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10,100);
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

            context = getContext(CONTAINER1_IP, getJNDIPort());

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

        } catch (JMSException ex)   {
            log.error("Error occurred during receiving.", ex);
        } finally {
            if (conn != null)   {
                conn.close();
            }
            if (context != null)    {
                context.close();
            }

        }

        // receive  some of them from first server and kill receiver -> only some of them gets back to
        SoakReceiverClientAck receiver2 = new SoakReceiverClientAck(getCurrentContainerForTest(), CONTAINER2_IP, getJNDIPort(), inQueueJndiNameForMdb, 100000, 10, 10);
        receiver2.start();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                NUMBER_OF_MESSAGES_PER_PRODUCER, receiver2.getCount());
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueue() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        deployer.deploy(MDB_ON_QUEUE1);

        deployer.deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER2_IP, getJNDIPort(), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: " + producer.getListOfSentMessages().size()
                + "Received: " + receiver.getListOfReceivedMessages().size(), producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size()
                , producer.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size()
                , receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                , NUMBER_OF_MESSAGES_PER_PRODUCER);

        deployer.undeploy(MDB_ON_QUEUE1);

        deployer.undeploy(MDB_ON_QUEUE2);

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }


    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
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
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(true);
    }

    public void clusterTestWithMdbOnTopic(boolean mdbsWithDifferentSubscriptions) throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

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
        PublisherClientAck publisher = new PublisherClientAck(CONTAINER1_IP, getJNDIPort(), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER2_IP, getJNDIPort(), outQueueJndiNameForMdb, 10000, 10, 10);

        publisher.start();
        receiver.start();

        publisher.join();
        receiver.join();

        if (mdbsWithDifferentSubscriptions) {
            Assert.assertEquals("Number of sent and received messages is different. There should be twice as many received messages"
                    + "than sent. Sent: " + publisher.getListOfSentMessages().size()
                    + "Received: " + receiver.getListOfReceivedMessages().size(), 2 * publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size());
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + 2 * NUMBER_OF_MESSAGES_PER_PRODUCER
                    + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                    , 2 * NUMBER_OF_MESSAGES_PER_PRODUCER);

        } else {
            Assert.assertEquals("Number of sent and received messages is not correct. There should be as many received messages as"
                    + " sent. Sent: " + publisher.getListOfSentMessages().size()
                    + "Received: " + receiver.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size());
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

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producer connected to A to queue and send some messages with message grouping id1.
     * Start producer connected to B to queue and send some messages with message grouping id2.
     * Kill server with local message handler - A
     * Kill the other server - B
     * Start the server with local message handler
     * Start producer connected to A to queue and send some messages with message grouping id1.
     * Start producer connected to A to queue and send some messages with message grouping id2 -> this will print error
     * Read messages
     */
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
        addMessageGrouping(CONTAINER1, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(CONTAINER2, name, "REMOTE", address, timeout);

        controller.start(CONTAINER2);
        controller.start(CONTAINER1);



        SecurityClient client1 = createConsumer(CONTAINER1_IP, inQueueJndiNameForMdb);
        SecurityClient client2 = createConsumer(CONTAINER2_IP, inQueueJndiNameForMdb);

        sendMesages(CONTAINER1_IP, inQueueJndiNameForMdb, new GroupMessageBuilder("id1"));
        sendMesages(CONTAINER2_IP, inQueueJndiNameForMdb, new GroupMessageBuilder("id2"));

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * timeout);

        client1.close();
        client2.close();

        // kill both of the servers
        killServer(CONTAINER1);
        controller.kill(CONTAINER1);
        killServer(CONTAINER2);
        controller.kill(CONTAINER2);

        // start 1st server
        controller.start(CONTAINER1);

        // send messages to 1st node
        sendMesages(CONTAINER1_IP, inQueueJndiNameForMdb, new GroupMessageBuilder("id1"));
        sendMesages(CONTAINER1_IP, inQueueJndiNameForMdb, new GroupMessageBuilder("id2"));

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * timeout);

        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER1_IP, 4447, inQueueJndiNameForMdb, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of sent and received messages is not correct. There should be " + 3 * NUMBER_OF_MESSAGES_PER_PRODUCER
                + " recieved but it's : " + receiver.getListOfReceivedMessages().size(), 3 * NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver.getListOfReceivedMessages().size());

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }

    private SecurityClient createConsumer(String containerIp, String queue) throws Exception {

        SecurityClient securityClient = new SecurityClient(containerIp, 4447, queue, NUMBER_OF_MESSAGES_PER_PRODUCER, null, null);
        securityClient.initializeClient();
        return securityClient;
    }

    private void sendMesages(String containerIp, String queue, MessageBuilder messageBuilder) throws InterruptedException {

        // send messages to 1st node
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), containerIp, getJNDIPort(),
                queue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        log.info("Start producer to send messages to node " + containerIp + " to destination: " + queue);
        producerToInQueue1.start();
        producerToInQueue1.join();

    }

    private void addMessageGrouping(String containerName, String name, String type, String address, long timeout) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.addMessageGrouping(name, type, address, timeout);

        jmsAdminOperations.close();

        stopServer(containerName);

    }

    /**
     * Deploys mdb with given name
     * @param nameOfMdb nameOfMdb
     *
     */
    public void deployMdb(String nameOfMdb) {
        deployer.deploy(nameOfMdb);
    }

    /**
     * Deploys mdb with given name
     * @param nameOfMdb nameOfMdb
     *
     */
    public void undeployMdb(String nameOfMdb) {
        deployer.undeploy(nameOfMdb);
    }

    /**
     * Create clients with the given acknowledge mode on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic           true for topic
     * @return clients
     * @throws Exception
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        clients.setProducedMessagesCommitAfter(100);
        clients.setReceivedMessagesAckCommitAfter(100);

        return clients;
    }

    public void prepareServers() {

            prepareServer(CONTAINER1);
            prepareServer(CONTAINER2);

    }



    /**
     * Prepares server for topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareServer(String containerName) {

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
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);
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

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE1)
    @TargetsContainer(CONTAINER1)
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
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE2)
    @TargetsContainer(CONTAINER2)
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
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TOPIC1)
    @TargetsContainer(CONTAINER1)
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
    @Deployment(managed = false, testable = false, name = MDB_ON_TOPIC2)
    @TargetsContainer(CONTAINER2)
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
    @TargetsContainer(CONTAINER1)
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

}