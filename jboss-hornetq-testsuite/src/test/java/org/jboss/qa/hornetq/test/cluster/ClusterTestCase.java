package org.jboss.qa.hornetq.test.cluster;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
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
//@RestoreConfigAfterTest
public class ClusterTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusterTestCase.class);

    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    private static final String MDB_ON_QUEUE1 = "mdbOnQueue1";
    private static final String MDB_ON_QUEUE2 = "mdbOnQueue2";

    private static final String MDB_ON_TOPIC1 = "mdbOnTopic1";
    private static final String MDB_ON_TOPIC2 = "mdbOnTopic2";

    private static final String MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = "mdbOnTopic1WithDifferentSubscriptionName1";

    private static boolean topologyCreated = false;



    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

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
//    @CleanUpAfterTest
    public void clusterTest() throws Exception {

//        prepareServers();

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
//    @CleanUpAfterTest
    public void clusterTestWithMdbOnQueue() throws Exception {

//        prepareServers();

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
     * Wait for clients to finish.
     *
     * @param clients clients
     * @throws InterruptedException
     */
    private void waitForClientsToFinish(Clients clients) throws InterruptedException {

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

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

        return clients;
    }

    @Before
    public void prepareServers() {
        if (!topologyCreated) {
            prepareServer(CONTAINER1);
            prepareServer(CONTAINER2);

            // deploy destinations 
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1);
            stopServer(CONTAINER1);
            controller.start(CONTAINER2);
            deployDestinations(CONTAINER2);
            stopServer(CONTAINER2);
            topologyCreated = true;
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.createQueue(serverName, inQueueNameForMdb, inQueueJndiNameForMdb, true);
        jmsAdminOperations.createQueue(serverName, outQueueNameForMdb, outQueueJndiNameForMdb, true);
        jmsAdminOperations.createTopic(serverName, inTopicNameForMdb, inTopicJndiNameForMdb);
        jmsAdminOperations.createTopic(serverName, outTopicNameForMdb, outTopicJndiNameForMdb);
        jmsAdminOperations.close();
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
//        jmsAdminOperations.setLoopBackAddressType("public", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("unsecure", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("management", bindingAddress);
//        jmsAdminOperations.setInetAddress("public", bindingAddress);
//        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
//        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
//        jmsAdminOperations.setBindingsDirectory(journalDirectory);
//        jmsAdminOperations.setPagingDirectory(journalDirectory);
//        jmsAdminOperations.setJournalDirectory(journalDirectory);
//        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
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
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
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
    @TargetsContainer(CONTAINER2)
    public static JavaArchive createDeploymentMdbOnQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/mdbOnQueue2.jar");
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
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

}