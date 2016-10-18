package org.jboss.qa.artemis.test.clients.clients.slow;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.log4j.Logger;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.mdb.LocalSlowMdbFromTopic;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SlowConsumerPolicy;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.remote.JMXConnector;
import java.util.List;


import static org.junit.Assert.*;

/**
 * @tpChapter Functional testing
 * @tpSubChapter PAGING - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails This test case simulates slow consumers connected to the
 * server. There is only one server and slow and fast consumers consume messages
 * from topic. Tests are focused on proper disconnection of slow consumers.
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class SlowConsumersTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(SlowConsumersTestCase.class);

    private static final String IN_QUEUE_NAME = "InQueue";
    private static final String IN_QUEUE_JNDI_NAME = "jms/queue/" + IN_QUEUE_NAME;

    private static final String OUT_QUEUE_NAME = "OutQueue";
    private static final String OUT_QUEUE_JNDI_NAME = "jms/queue/" + OUT_QUEUE_NAME;

    private static final String TOPIC_NAME = "InTopic";
    private static final String TOPIC_JNDI_NAME = "jms/topic/" + TOPIC_NAME;

    private static final String CLIENT_NAME = "test-client";

    private static final int NUMBER_OF_MESSAGES = 10000;

    @Before
    @After
    public void shutdownServerBeforeAfterTest() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * published to topic on server. There are two non durable subscribers, one
     * slow, one fast. Let them process messages and check whether the slow
     * consumer got disconnected and subscription was removed.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Connect to the server with publisher and non durable subscribers(fast,slow), send and receive messages.</li>
     * <li>Check slow client got disconnected and subscription was removed.</li>
     * </ul>
     * @tpPassCrit Slow client is disconnected from server and its subscription
     * is removed.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowNonDurableConsumerKill() throws Exception {
        prepareServerForKills(10);

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(0);

        NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME);
        NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME, 10000, 1);
        slowConsumer.setTimeout(500); // slow consumer reads only 5 message per second

        fastConsumer.start();
        slowConsumer.start();
        Thread.sleep(3000);
        producer.start();

        long startTime = System.currentTimeMillis();

        //wait max 100sec, once slow is disconnected continue
        while (slowConsumer.getException() == null) {
            Thread.sleep(1000);
            if (startTime + 100000 < System.currentTimeMillis()) {
                LOG.error("Slow consumer was not disconnected in 100sec timeout.");
                break;
            }
        }

        JMSOperations ops = container(1).getJmsOperations();
        int numberOfSubscribers = ops.getNumberOfNonDurableSubscriptionsOnTopic(TOPIC_NAME);
        ops.close();
        LOG.info("number of subscribers on InTopic :" + numberOfSubscribers);


        //if producer is still active, secend subscriber should still receive messages, otherwise its subscription is removed
        int numberOfSubscribersExpected = producer.getCount() < NUMBER_OF_MESSAGES ? 1 : 0;
        LOG.info("number of expected subscribers on InTopic :" + numberOfSubscribersExpected);

        producer.stopSending();
        producer.join();
        fastConsumer.setTimeout(0);
        slowConsumer.setTimeout(0);
        fastConsumer.join();
        slowConsumer.join();

        LOG.info("Verify fast consumers messages");
        FinalTestMessageVerifier finalTestMessageVerifier1 = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        finalTestMessageVerifier1.addSendMessages(producer.getListOfSentMessages());
        finalTestMessageVerifier1.addReceivedMessages(fastConsumer.getListOfReceivedMessages());
        finalTestMessageVerifier1.verifyMessages();

        assertEquals("Fast consumer should receive all messages", producer.getCount(), fastConsumer.getCount());

        assertEquals("The non-durable subscription should have been removed after killing slow client",
                numberOfSubscribersExpected, numberOfSubscribers);
        assertNotNull("Slow client should have been disconnected by the server",
                slowConsumer.getException());
        assertFalse("Slow consumer should be finished", slowConsumer.isAlive());
        assertFalse("Fast consumer should be finished", fastConsumer.isAlive());
        assertFalse("Producer should be finished", producer.isAlive());
    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * publish to topic on server. There are two non durable subscribers, one
     * slow, one fast. Let them process messages and check whether there
     * are some Jmx notifications related to the slow consumer.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Connect to the server with publisher and non durable subscribers(fast,slow), send and receive messages.</li>
     * <li>Check notifications related to slow consumer and its connection to server</li>
     * </ul>
     * @tpPassCrit There is at least one slow consumer JMX notification and slow
     * client is not disconnected by the server.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowConsumerNotification() throws Exception {
        prepareServerForNotifications(10);
        JMXConnector jmxConnector;
        JmxNotificationListener notificationListener = container(1).createJmxNotificationListener();
        jmxConnector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
        MBeanServerConnection mbeanServer = jmxConnector.getMBeanServerConnection();
        mbeanServer.addNotificationListener(ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(),
                "default", true).getActiveMQServerObjectName(), notificationListener, null, null);

        PublisherAutoAck producer1 = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer1");
        producer1.setMessageBuilder(new TextMessageBuilder(10));
        producer1.setTimeout(0);
        PublisherAutoAck producer2 = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer2");
        producer2.setMessageBuilder(new TextMessageBuilder(10));
        producer2.setTimeout(0);

        NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME);
        NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME, 10000, 1);
        fastConsumer.setTimeout(10);
        slowConsumer.setTimeout(500); // slow consumer reads only 2 message per second

        fastConsumer.start();
        slowConsumer.start();
        Thread.sleep(5000);
        producer1.start();
        producer2.start();

        long startTime = System.currentTimeMillis();

        while (!hasConsumerSlowNotification(notificationListener)) {
            Thread.sleep(1000);
            if (startTime + 100000 < System.currentTimeMillis()) {
                LOG.error("There should be at least one JMX SLOW CLIENT notification.");
                break;
            }
        }

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();
        fastConsumer.setTimeout(0);
        slowConsumer.setTimeout(0);
        fastConsumer.join();
        slowConsumer.join();

        JMSOperations ops = container(1).getJmsOperations();
        int numberOfSubscribers = ops.getNumberOfNonDurableSubscriptionsOnTopic(TOPIC_NAME);
        ops.close();
        LOG.info("number of subscribers on InTopic :" + numberOfSubscribers);

        LOG.info("Verify fast consumers messages");
        FinalTestMessageVerifier finalTestMessageVerifier1 = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        finalTestMessageVerifier1.addSendMessages(producer1.getListOfSentMessages());
        finalTestMessageVerifier1.addSendMessages(producer2.getListOfSentMessages());
        finalTestMessageVerifier1.addReceivedMessages(fastConsumer.getListOfReceivedMessages());
        finalTestMessageVerifier1.verifyMessages();

        LOG.info("Verify slow consumers messages");
        FinalTestMessageVerifier finalTestMessageVerifier2 = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        finalTestMessageVerifier2.addSendMessages(producer1.getListOfSentMessages());
        finalTestMessageVerifier2.addSendMessages(producer2.getListOfSentMessages());
        finalTestMessageVerifier2.addReceivedMessages(slowConsumer.getListOfReceivedMessages());
        finalTestMessageVerifier2.verifyMessages();


        assertEquals("Fast consumer should receive all messages", producer1.getCount() + producer2.getCount(), fastConsumer.getCount());
        assertEquals("Slow consumer should receive all messages", producer1.getCount() + producer2.getCount(), slowConsumer.getCount());
        assertTrue("There should be at least one JMX SLOW CLIENT notification", hasConsumerSlowNotification(notificationListener));
        assertNull("Slow client should not have been disconnected by the server",
                slowConsumer.getException());

        assertFalse("Slow consumer should be finished", slowConsumer.isAlive());
        assertFalse("Fast consumer should be finished", fastConsumer.isAlive());
        assertFalse("Producer1 should be finished", producer1.isAlive());
        assertFalse("Producer2 consumer should be finished", producer2.isAlive());
    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * published to topic on server. There are two durable subscribers, one
     * slow, one fast. Let them process messages and check whether the slow
     * consumer got disconnected and its subscription is preserved.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Connect to the server with publisher and durable subscribers(fast,slow), send and receive messages.</li>
     * <li>Check slow client got disconnected and its subscription is preserved.</li>
     * </ul>
     * @tpPassCrit Slow client is disconnected by the server and its subscription
     * is preserved.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowDurableConsumerKill() throws Exception {
        prepareServerForKills(10);

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(0);

        SubscriberAutoAck fastConsumer = new SubscriberAutoAck(container(1),
                TOPIC_JNDI_NAME, CLIENT_NAME + "subscriber-1", "test-fast-subscriber");
        SubscriberAutoAck slowConsumer = new SubscriberAutoAck(container(1),
                TOPIC_JNDI_NAME, CLIENT_NAME + "subscriber-2", "test-slow-subscriber");
        slowConsumer.setTimeout(500); // slow consumer reads only 2 messages per second
        slowConsumer.setMaxRetries(1);

        fastConsumer.start();
        slowConsumer.start();
        Thread.sleep(5000);
        producer.start();

        long startTime = System.currentTimeMillis();
        //wait max 100sec, once slow is disconnected continue
        while (slowConsumer.getException() == null) {
            Thread.sleep(1000);
            if (startTime + 100000 < System.currentTimeMillis()) {
                LOG.error("Slow consumer was not disconnected in 100sec timeout.");
                break;
            }
        }

        JMSOperations ops = container(1).getJmsOperations();
        int numberOfSlowSubscriberSubscriptions = ops.getNumberOfDurableSubscriptionsOnTopicForClient(CLIENT_NAME + "subscriber-2");
        int numberOfSubscriptionsOnTopic = ops.getNumberOfDurableSubscriptionsOnTopic(TOPIC_NAME);
        ops.close();
        LOG.info("number of slow consumers subscriptions on InTopic :" + numberOfSlowSubscriberSubscriptions);
        LOG.info("number of subscriptions on InTopic :" + numberOfSubscriptionsOnTopic);

        producer.stopSending();
        producer.join();
        fastConsumer.setTimeout(0);
        slowConsumer.setTimeout(0);
        fastConsumer.join();
        slowConsumer.join();

        LOG.info("Verify fast consumers messages");
        FinalTestMessageVerifier finalTestMessageVerifier1 = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        finalTestMessageVerifier1.addSendMessages(producer.getListOfSentMessages());
        finalTestMessageVerifier1.addReceivedMessages(fastConsumer.getListOfReceivedMessages());
        finalTestMessageVerifier1.verifyMessages();

        assertEquals("Fast consumer should receive all messages", producer.getCount(), fastConsumer.getCount());

        // subscriber was durable, subscription must survive disconnection
        assertEquals("The durable subscription of slow client should have been preserved after killing slow client",
                1, numberOfSlowSubscriberSubscriptions);
        assertEquals("Slow and fast client should preserve their subscriptions",
                2, numberOfSubscriptionsOnTopic);
        assertNotNull("Slow client should have been disconnected by the server",
                slowConsumer.getException());
        assertFalse("Slow consumer should be finished", slowConsumer.isAlive());
        assertFalse("Fast consumer should be finished", fastConsumer.isAlive());
        assertFalse("Producer should be finished", producer.isAlive());

    }

    /**
     * @tpTestDetails Single server with deployed queue is started. Messages are
     * send to queue on server. There is one slow receiver which receives
     * messages from the queue. Wait for clients finish and check disconnection
     * of slow receiver.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed</li>
     * <li>Start producer and send messages to the queue</li>
     * <li>Start slow receiver and receive messages form the queue</li>
     * <li>Wait for finish of producer and receiver</li>
     * <li>Check slow client have been disconnected by the server</li>
     * </ul>
     * @tpPassCrit Slow client is disconnected by the server.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowReceiverKill() throws Exception {
        prepareServerForKills();

        ProducerAutoAck producer = new ProducerAutoAck(container(1),
                IN_QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES);
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(0);

        //ReceiverAutoAck fastReceiver = new ReceiverAutoAck(getHostname(CONTAINER1_NAME_NAME), getJNDIPort(CONTAINER1_NAME_NAME),
        //        IN_QUEUE_JNDI_NAME, 30000, 1);
        ReceiverAutoAck slowReceiver = new ReceiverAutoAck(container(1),
                IN_QUEUE_JNDI_NAME);
        slowReceiver.setTimeout(100); // slow consumer reads only 10 message per second
        slowReceiver.setMaxRetries(1);

        LOG.info("Starting producer");
        producer.start();
        //LOG.info("Starting fast receiver");
        //fastReceiver.start();
        LOG.info("Starting slow receiver");
        slowReceiver.start();

        Thread.sleep(30000);

        LOG.info("Waiting for producer");
        producer.join();
        LOG.info("Waiting for slow receiver");
        slowReceiver.join();
        //LOG.info("Waiting for fast receiver");
        //fastReceiver.join();

        assertNotNull("Slow client should have been disconnected by the server",
                slowReceiver.getException());

    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * send to topic on server by slow producer which sends only 10 messages per
     * second (which is lower than slow consumer threshold). There are two non
     * durable subscribers(fast, slow) which receive messages from the topic.
     * Wait for clients finish and check disconnection of slow subscriber.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Start slow producer and start sending messages to the topic.</li>
     * <li>Start two non durable subscribers(fast,slow), start receiving messages from the topic.</li>
     * <li>Wait for finish of producer and subscribers.</li>
     * <li>Check slow consumer connection to the server.</li>
     * </ul>
     * @tpPassCrit Slow client is not disconnected by the server.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowConsumerNotKilledWithSlowProducer() throws Exception {
        prepareServerForKills();

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(100); // producer only sends 10 message/second - lower than slow consumer threshold

        NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME);
        NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME, 30000, 1);
        slowConsumer.setTimeout(100); // slow consumer reads only 10 messages per second

        producer.start();
        fastConsumer.start();
        slowConsumer.start();

        Thread.sleep(60000);

        producer.join();
        fastConsumer.join();
        slowConsumer.join();

        assertNull("Slow client should not have been disconnected by the server with slow producer",
                slowConsumer.getException());

    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * published to topic on server.Server has lowered the paging threshold and
     * is forced into paging mode. There are two non durable subscribers, one
     * slow, one fast. Wait for clients finish and check whether the slow client is
     * disconnected and fast is still connected.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Connect to the server with publisher and non durable subscribers(fast,slow),send and receive messages</li>
     * <li>Check slow client got disconnected and fast client is still connected.</li>
     * </ul>
     * @tpPassCrit Slow client is disconnected by the server. Fast client is connected to server.
     * @tpInfo Test is ignored because paging does in fact cause consumer kill (by design)
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore // paging does in fact cause consumer kill (by design)
    public void testPagingNotKillingFastConsumer() throws Exception {
        prepareServerForKills();

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(0);

        NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME, 30000, 1);
        fastConsumer.setTimeout(30);
        NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                container(1), TOPIC_JNDI_NAME, 30000, 1);
        slowConsumer.setTimeout(100); // slow consumer reads only 10 messages per second

        producer.start();
        fastConsumer.start();
        slowConsumer.start();

        Thread.sleep(60000);

        producer.join();
        fastConsumer.join();
        slowConsumer.join();

        assertNull("Fast client should not have been disconnected by the server with paging on the topic",
                fastConsumer.getException());
        assertNotNull("Slow client should have been disconnected by the server",
                slowConsumer.getException());

    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * published to topic on server. There is one non durable subscriber. Wait
     * for client finish and check client connection to the server.
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Connect to the server with publisher and non durable subscribers(fast,slow),send and receive messages</li>
     * <li>Check clients connection to server</li>
     * </ul>
     * @tpPassCrit Client is not disconnected by the server while commiting
     * @tpInfo Test is ignored because long commits do in fact cause consumer kill (by design)
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore("long commits do in fact cause consumer kill (by design)")
    public void testSlowConsumerNotKilledWhileCommiting() throws Exception {
        prepareServerForKills();

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(10));
        producer.setTimeout(0);

        NonDurableTopicSubscriber consumer = new NonDurableTopicSubscriberTransAck(
                container(1), TOPIC_JNDI_NAME, 30000, 1000, 1);
        consumer.setTimeout(25);

        producer.start();
        consumer.start();

        Thread.sleep(60000);

        producer.join();
        consumer.join();

        assertNull("Fast client should not have been disconnected by the server while commiting",
                consumer.getException());

    }

    /**
     * @tpTestDetails Single server with deployed topic is started. Messages are
     * published to topic on server.There is one slow MDB deployed. Send messages to topic and deploy MDB.
     * MDB should be disconnected by KILL policy
     * @tpProcedure <ul>
     * <li>Start server with single topic deployed.</li>
     * <li>Deploy slow MDB</li>
     * <li>Check MDB is disconnected</li>
     * </ul>
     * @tpPassCrit MDB doesn`t have subscription on topic
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowMDBKill() throws Exception {

        prepareServerForKills(10);

        JavaArchive deployment = createDeploymentMdbSlow();
        container(1).deploy(deployment);

        PublisherAutoAck producer = new PublisherAutoAck(container(1),
                TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
        producer.setMessageBuilder(new TextMessageBuilder(1));
        producer.setTimeout(0);
        producer.start();

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), OUT_QUEUE_JNDI_NAME);
        receiver.start();
        receiver.join();

        producer.stopSending();
        producer.join();

        JMSOperations ops = container(1).getJmsOperations();
        int topicSubscriptions = ops.getNumberOfDurableSubscriptionsOnTopic(TOPIC_NAME);
        ops.close();

        assertEquals("MDBs subscription should be removed", 0, topicSubscriptions);
        assertFalse("Producer should be finished", producer.isAlive());
        assertFalse("Consumer should be finished", receiver.isAlive());

        container(1).undeploy(deployment);
    }
    
    private void prepareServerForKills() throws Exception {
        prepareServerForKills(20);
    }

    private void prepareServerForKills(int slowConsumerThreshold) throws Exception {
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        // disable clustering
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 1024);
        ops.setSlowConsumerPolicy("#", slowConsumerThreshold, SlowConsumerPolicy.KILL, 1);
        ops.setReconnectAttemptsForConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, 0);
        ops.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 0);
        ops.setSetupAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 0);
        ops.setInitialConnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1);

        ops.createQueue(IN_QUEUE_NAME, IN_QUEUE_JNDI_NAME);
        ops.createQueue(OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME);
        ops.createTopic(TOPIC_NAME, TOPIC_JNDI_NAME);
        ops.close();

        container(1).restart();
    }

    private void prepareServerForNotifications(int slowConsumerThreshold) throws Exception {
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        ops.setJmxManagementEnabled(true);

        // disable clustering
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 1024);
        ops.setSlowConsumerPolicy("#", slowConsumerThreshold, SlowConsumerPolicy.NOTIFY, 5);
        ops.setReconnectAttemptsForConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, 0);

        ops.createQueue(IN_QUEUE_NAME, IN_QUEUE_JNDI_NAME);
        ops.createQueue(OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME);
        ops.createTopic(TOPIC_NAME, TOPIC_JNDI_NAME);
        ops.close();

        container(1).restart();
    }

    private boolean hasConsumerSlowNotification(JmxNotificationListener notificationListener) {
        List<Notification> jmxNotifications = notificationListener.getCaughtNotifications();
        for (Notification n : jmxNotifications) {
            if ("CONSUMER_SLOW".equals(n.getType())) {
                return true;
            }
        }
        return false;
    }

    public JavaArchive createDeploymentMdbSlow() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbSlow.jar");
        mdbJar.addClass(LocalSlowMdbFromTopic.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        LOG.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }
}
