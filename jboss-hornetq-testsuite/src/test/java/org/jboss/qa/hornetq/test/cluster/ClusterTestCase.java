package org.jboss.qa.hornetq.test.cluster;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.*;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    private static final String MDB_ON_QUEUE1 = "mdbOnQueue1";
    private static final String MDB_ON_QUEUE2 = "mdbOnQueue2";

    private static final String MDB_ON_TEMPQUEUE1 = "mdbOnTempQueue1";
    private static final String MDB_ON_TEMPQUEUE2 = "mdbOnTempQueue2";

    private static final String MDB_ON_TOPIC1 = "mdbOnTopic1";
    private static final String MDB_ON_TOPIC2 = "mdbOnTopic2";

    private static final String MDB_ON_TEMPTOPIC1 = "mdbOnTempTopic1";
    private static final String MDB_ON_TEMPTOPIC2 = "mdbOnTempTopic2";

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
        waitHornetQToAlive(getHostname(CONTAINER1), getHornetqPort(CONTAINER1), 60000);
        waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, numberOfMessages);
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

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, 30000, 1000, 10);
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
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitDuplicateId() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(new ClientMixMessageBuilder(10, 100));
        producer1.start();
        producer1.join();

        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, 10000, 10, 10);
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
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitoutDuplicateIdWithInterruption() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // send messages without dup id -> load-balance to node 2
        SoakProducerClientAck producer1 = new SoakProducerClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
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

            context = getContext(getHostname(CONTAINER1), getJNDIPort(CONTAINER1));

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
        SoakReceiverClientAck receiver2 = new SoakReceiverClientAck(getCurrentContainerForTest(), getHostname(CONTAINER2), getJNDIPort(CONTAINER2), inQueueJndiNameForMdb, 100000, 10, 10);
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
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueue() throws Exception {

        prepareServers();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        deployer.deploy(MDB_ON_QUEUE1);

        deployer.deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2), getJNDIPort(CONTAINER2), outQueueJndiNameForMdb, 10000, 10, 10);

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

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     *
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueueDeployAndUndeploy() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        int numberOfMessages=30;
        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        deployer.deploy(MDB_ON_TEMPQUEUE1);

        deployer.deploy(MDB_ON_TEMPQUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, numberOfMessages);
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2), getJNDIPort(CONTAINER2), outQueueJndiNameForMdb, 10000, 10, 10);



        producer.start();
        producer.join();
        log.info("Removing MDB ond secnod server and adding it back ");
        deployer.undeploy(MDB_ON_TEMPQUEUE2);
        deployer.deploy(MDB_ON_TEMPQUEUE2);
        log.info("Trying to read from newly deployed OutQueue");

        receiver.start();
        receiver.join();

        Assert.assertEquals("Number of messages in OutQueue does not match", numberOfMessages,receiver.getListOfReceivedMessages().size());
        deployer.undeploy(MDB_ON_TEMPQUEUE1);
        deployer.undeploy(MDB_ON_TEMPQUEUE2);


        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

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
        boolean passed=false;
        prepareServers(true);
        controller.start(CONTAINER1);
        try {
            deployer.deploy(MDB_ON_TEMPTOPIC1);
            passed=true;

        }catch(Exception e){
            //this is correct behavior
        }
        Assert.assertFalse("Deployment of already existing topic didn't failed",passed);


        stopServer(CONTAINER1);
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
    public void clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        controller.start(CONTAINER1);
        deployer.deploy(MDB_ON_TEMPTOPIC1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        SubscriberAutoAck subscriber= new SubscriberAutoAck(CONTAINER1, getHostname(CONTAINER1), 4447, inTopicJndiNameForMdb , "subscriber1", "subscription1");
        subscriber.start();
        subscriber.join();
        deployer.undeploy(MDB_ON_TEMPTOPIC1);
        deployer.deploy(MDB_ON_TEMPTOPIC1);

        Assert.assertEquals("Number of subscriptions is not 0",0,jmsAdminOperations.getNumberOfDurableSubscriptionsOnTopic("mySubscription"));

       stopServer(CONTAINER1);
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
    public void clusterTestWithMdbOnTopicDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        deployer.deploy(MDB_ON_TEMPTOPIC1);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = this.getJMSOperations(CONTAINER1);
        JMSOperations jmsAdminOperations2 = this.getJMSOperations(CONTAINER2);
        SubscriberAutoAck subscriber= new SubscriberAutoAck(CONTAINER2, getHostname(CONTAINER2), getJNDIPort(CONTAINER2), inTopicJndiNameForMdb , "subscriber1", "subscription1");
        subscriber.start();
        subscriber.join();
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER1",0,jmsAdminOperations1.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        deployer.undeploy(MDB_ON_TEMPTOPIC2);
        deployer.deploy(MDB_ON_TEMPTOPIC2);

        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER1",0,jmsAdminOperations1.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2",0,jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
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
    public void clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(CONTAINER1, true);
        prepareServer(CONTAINER2, false);
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = this.getJMSOperations(CONTAINER1);
        JMSOperations jmsAdminOperations2 = this.getJMSOperations(CONTAINER2);
        SubscriberAutoAck subscriber= new SubscriberAutoAck(CONTAINER2, getHostname(CONTAINER2), getJNDIPort(CONTAINER2), inTopicJndiNameForMdb , "subscriber1", "subscription1");
        subscriber.start();
        subscriber.join();
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER1",0,jmsAdminOperations1.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        deployer.undeploy(MDB_ON_TEMPTOPIC2);
        deployer.deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER1",0,jmsAdminOperations1.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2",0,jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
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
        PublisherClientAck publisher = new PublisherClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER2), getJNDIPort(CONTAINER2), outQueueJndiNameForMdb, 10000, 10, 10);

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

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

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
//        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4, 20000);
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
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumerLongRestart() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4, 20000);
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
        clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER1, 20000);
    }


    public void clusterWitMessageGroupingCrashServerWithNoConsumer(String serverToKill, long timeBetweenKillAndRestart) throws Exception {
        testMessageGrouping(serverToKill, timeBetweenKillAndRestart, CONTAINER3, false);
    }

    // TODO uncomment the test when fixed
//    /**
//     * Test scenario
//     * - Start 4 servers - local and remotes (1,2,3)
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - Initialize consumer on remote 2
//     * - Crash remote 2
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - start consumer on local - check consumer whether it received all messages
//     *
//     * @throws Exception
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitMessageGroupingCrashNodeWithConsumer() throws Exception {
//        testMessageGrouping(CONTAINER3, 20000, CONTAINER2, true);
//    }
//
//    /**
//     * Test scenario
//     * - Start 4 servers - local and remotes (1,2,3)
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - Initialize consumer on remote 4
//     * - Crash remote 1
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - start consumer on remote 4 - check consumer whether it received all messages
//     *
//     * @throws Exception
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitMessageGroupingCrashRemoteNodeWithConsumer() throws Exception {
//        testMessageGrouping(CONTAINER2, 20000, CONTAINER2, true);
//    }
//
//    /**
//     * Test scenario
//     * - Start 4 servers - local and remotes (1,2,3)
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - Initialize consumer on remote 2
//     * - Crash local
//     * - Send messages to remote 1 (message group ids 4 types)
//     * - start local
//     * - start consumer on local - check consumer whether it received all messages
//     *
//     * @throws Exception
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void clusterTestWitMessageGroupingCrashLocalNodeWithConsumer() throws Exception {
//        testMessageGrouping(CONTAINER1, 20000, CONTAINER3, true);
//    }


    public void testMessageGrouping(String serverToKill, long timeBetweenKillAndRestart, String serverWithConsumer, boolean justInitializeConsumer) throws Exception {

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(CONTAINER1, name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(CONTAINER2, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER3, name, "REMOTE", address, timeout);
        addMessageGrouping(CONTAINER4, name, "REMOTE", address, timeout);

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        controller.start(CONTAINER3);
        controller.start(CONTAINER4);

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER2), getJNDIPort(CONTAINER2), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        groupMessageVerifiers.add(messageVerifier);

        if (justInitializeConsumer) {
            createConsumer(serverWithConsumer, inQueueJndiNameForMdb);
            createConsumer(serverWithConsumer, inQueueJndiNameForMdb);
        } else {
            for (int i = 0; i < 2; i++) {
                GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
                groupMessageVerifiers.add(groupMessageVerifier);
                ReceiverTransAck receiver = new ReceiverTransAck(getHostname(serverWithConsumer), getJNDIPort(serverWithConsumer), inQueueJndiNameForMdb, 10000, 100, 10);
                receiver.setMessageVerifier(groupMessageVerifier);
                receiver.start();
                receivers.add(receiver);
            }
        }

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * timeout);

        log.info("Kill server - " + serverToKill);
        // kill remote 3
        killServer(serverToKill);
        controller.kill(serverToKill);

        // THIS IS IMPORTANT - KEEP THE SERVER KILLED MORE THAN 2 * MESASGE_GROUP_TIMEMOUT
        Thread.sleep(3 * timeout);

        log.info("Killed server - " + serverToKill);

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER2), getJNDIPort(CONTAINER2), inQueueJndiNameForMdb, numberOfMessages);
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
        Thread.sleep(2 * timeout);

        if (justInitializeConsumer) {
            for (int i = 0; i < 2; i++) {
                GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();
                groupMessageVerifiers.add(groupMessageVerifier);
                // receive timout must be more than ttl for checking connection
                ReceiverTransAck receiver = new ReceiverTransAck(getHostname(serverWithConsumer), getJNDIPort(serverWithConsumer), inQueueJndiNameForMdb, 60000, 100, 10);
                receiver.setMessageVerifier(groupMessageVerifier);
                receiver.start();
                receivers.add(receiver);
            }
        }

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

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);

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

        SecurityClient client1 = createConsumer(CONTAINER1, inQueueJndiNameForMdb);
        SecurityClient client2 = createConsumer(CONTAINER2, inQueueJndiNameForMdb);

        sendMesages(CONTAINER1, inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        sendMesages(CONTAINER2, inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id2"));

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
        sendMesages(CONTAINER1, inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        sendMesages(CONTAINER1, inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id2"));

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * timeout);

        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueueJndiNameForMdb, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of sent and received messages is not correct. There should be " + 3 * NUMBER_OF_MESSAGES_PER_PRODUCER
                        + " recieved but it's : " + receiver.getListOfReceivedMessages().size(), 3 * NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver.getListOfReceivedMessages().size()
        );

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

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
        clusterTestWitMessageGroupingTestSingleServer(CONTAINER1);
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
//        clusterTestWitMessageGroupingTestSingleServer(CONTAINER2);
//    }

    public void clusterTestWitMessageGroupingTestSingleServer(String nameOfTestedContainerName) throws Exception {

        int numberOfMessages = 10;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        String serverWithLocalHandler = CONTAINER1;
        String serverWithRemoteHandler = CONTAINER2;
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


    private SecurityClient createConsumer(String containerName, String queue) throws Exception {

        SecurityClient securityClient = new SecurityClient(getHostname(containerName), getJNDIPort(containerName), queue, NUMBER_OF_MESSAGES_PER_PRODUCER, null, null);
        securityClient.initializeClient();
        securityClient.consumeAndRollback(200);
        return securityClient;
    }

    private void sendMesages(String containerName, String queue, MessageBuilder messageBuilder) throws InterruptedException {

        // send messages to 1st node
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

        jmsAdminOperations.addMessageGrouping(name, type, address, timeout);

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
                clients = new TopicClientsAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        clients.setProducedMessagesCommitAfter(100);
        clients.setReceivedMessagesAckCommitAfter(100);

        return clients;
    }

    /**
     *Prepares several servers for topology. Destinations will be created.
     *
     */
    public void prepareServers(){
        prepareServers(true);
    }

    /**
     *Prepares several servers for topology.
     *
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    public void prepareServers(boolean createDestinations) {
        prepareServer(CONTAINER1,createDestinations);
        prepareServer(CONTAINER2,createDestinations);
        prepareServer(CONTAINER3,createDestinations);
        prepareServer(CONTAINER4,createDestinations);
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
     * @param containerName Name of the container - defined in arquillian.xml
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

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);

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
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue defined in hornetq-jms.xml
     * Queues are part of deployment
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPQUEUE1)
    @TargetsContainer(CONTAINER1)
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
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue defined in hornetq-jms.xml
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPQUEUE2)
    @TargetsContainer(CONTAINER2)
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
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPTOPIC1)
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeploymentMdbOnTempTopic1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
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
    @Deployment(managed = false, testable = false, name = MDB_ON_TEMPTOPIC2)
    @TargetsContainer(CONTAINER2)
    public static JavaArchive createDeploymentMdbOnTempTopic2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
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

}