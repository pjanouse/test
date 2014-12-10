package org.jboss.qa.hornetq.test.clients.slow;


import java.util.List;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import org.apache.log4j.Logger;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.NonDurableTopicSubscriber;
import org.jboss.qa.hornetq.apps.clients.NonDurableTopicSubscriberAutoAck;
import org.jboss.qa.hornetq.apps.clients.NonDurableTopicSubscriberTransAck;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SlowConsumerPolicy;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;


@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class SlowConsumersTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(SlowConsumersTestCase.class);

    private static final String QUEUE_NAME = "InQueue";
    private static final String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;

    private static final String TOPIC_NAME = "InTopic";
    private static final String TOPIC_JNDI_NAME = "jms/topic/" + TOPIC_NAME;

    private static final String CLIENT_NAME = "test-client";

    private static final int NUMBER_OF_MESSAGES = 10000;

    private static final String HOSTNAME = getHostname(CONTAINER1);
    private static final int JNDI_PORT = getJNDIPort(CONTAINER1);

    @After
    public void shutdownServerAfterTest() {
        stopServer(CONTAINER1);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowNonDurableConsumerKill() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1);
            slowConsumer.setTimeout(1000); // slow consumer reads only one message per second

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(70000);

            JMSOperations ops = getJMSOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopic(CLIENT_NAME + "subscriber-2");
            ops.close();

            producer.join();
            fastConsumer.join();
            slowConsumer.join();

            assertEquals("The non-durable subscription should have been removed after killing slow client",
                    0, numberOfSubscribers);
            assertNotNull("Slow client should have been disconnected by the server",
                    slowConsumer.getException());

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowConsumerNotification() throws Exception {
        prepareServerForNotifications();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        JMXConnector jmxConnector = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            JmxNotificationListener notificationListener = new JmxNotificationListener();
            jmxConnector = JmxUtils.getJmxConnectorForEap(CONTAINER1_INFO);
            MBeanServerConnection mbeanServer = jmxConnector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getHornetQServerObjectName(),
                    notificationListener, null, null);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1);
            slowConsumer.setTimeout(100); // slow consumer reads only one message per second

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(15000);

            JMSOperations ops = getJMSOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopic(CLIENT_NAME + "subscriber-2");
            ops.close();

            producer.join();
            fastConsumer.join();
            slowConsumer.join();

            List<Notification> jmxNotifications = notificationListener.getCaughtNotifications();
            boolean hasConsumerSlowNotification = false;
            for (Notification n : jmxNotifications) {
                if ("CONSUMER_SLOW".equals(n.getType())) {
                    hasConsumerSlowNotification = true;
                    break;
                }
            }

            assertTrue("There should be at least one slow consumer JMX notification", hasConsumerSlowNotification);
            assertNull("Slow client should not have been disconnected by the server",
                    slowConsumer.getException());
        } finally {
            JMSTools.cleanupResources(ctx, connection, session);

            if (jmxConnector != null) {
                jmxConnector.close();
            }
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowDurableConsumerKill() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            SubscriberAutoAck fastConsumer = new SubscriberAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, CLIENT_NAME + "subscriber-1", "test-fast-subscriber");
            SubscriberAutoAck slowConsumer = new SubscriberAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, CLIENT_NAME + "subscriber-2", "test-slow-subscriber");
            slowConsumer.setTimeout(1000); // slow consumer reads only one message per second
            slowConsumer.setMaxRetries(1);

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(70000);

            JMSOperations ops = getJMSOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopic(CLIENT_NAME + "subscriber-2");
            ops.close();

            producer.join();
            fastConsumer.join();
            slowConsumer.join();

            // subscriber was durable, subscription must survive disconnection
            assertEquals("The durable subscription should have been preserved after killing slow client",
                    1, numberOfSubscribers);
            assertNotNull("Slow client should have been disconnected by the server",
                    slowConsumer.getException());

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowReceiverKill() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            ProducerAutoAck producer = new ProducerAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES);
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            //ReceiverAutoAck fastReceiver = new ReceiverAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
            //        QUEUE_JNDI_NAME, 30000, 1);
            ReceiverAutoAck slowReceiver = new ReceiverAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    QUEUE_JNDI_NAME);
            slowReceiver.setTimeout(1000); // slow consumer reads only one message per second
            slowReceiver.setMaxRetries(1);

            connection.start();
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

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSlowConsumerNotKilledWithSlowProducer() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(100); // producer only sends 10 message/second - lower than slow consumer threshold

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1);
            slowConsumer.setTimeout(100); // slow consumer reads only 10 messages per second

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(60000);

            producer.join();
            fastConsumer.join();
            slowConsumer.join();

            assertNull("Slow client should not have been disconnected by the server with slow producer",
                    slowConsumer.getException());

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore // paging does in fact cause consumer kill (by design)
    public void testPagingNotKillingFastConsumer() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1);
            fastConsumer.setTimeout(30);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1);
            slowConsumer.setTimeout(1000); // slow consumer reads only 10 messages per second

            connection.start();
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

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore // long commits do in fact cause consumer kill (by design)
    public void testSlowConsumerNotKilledWhileCommiting() throws Exception {
        prepareServerForKills();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                    TOPIC_JNDI_NAME, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber consumer = new NonDurableTopicSubscriberTransAck(
                    HOSTNAME, JNDI_PORT, TOPIC_JNDI_NAME, 30000, 1000, 1);
            consumer.setTimeout(25);

            connection.start();
            producer.start();
            consumer.start();

            Thread.sleep(60000);

            producer.join();
            consumer.join();

            assertNull("Fast client should not have been disconnected by the server while commiting",
                    consumer.getException());

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
        }
    }

    private void prepareServerForKills() throws Exception {
        controller.start(CONTAINER1);
        JMSOperations ops = getJMSOperations();

        // disable clustering
        ops.setClustered(false);
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 1024);
        ops.setSlowConsumerPolicy("#", 20, SlowConsumerPolicy.KILL, 1);

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.createTopic(TOPIC_NAME, TOPIC_JNDI_NAME);
        ops.close();

        stopServer(CONTAINER1);
        controller.start(CONTAINER1);
    }

    private void prepareServerForNotifications() throws Exception {
        controller.start(CONTAINER1);
        JMSOperations ops = getJMSOperations();

        ops.setJmxManagementEnabled(true);

        // disable clustering
        ops.setClustered(false);
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 1024);
        ops.setSlowConsumerPolicy("#", 20, SlowConsumerPolicy.NOTIFY, 5);

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.createTopic(TOPIC_NAME, TOPIC_JNDI_NAME);
        ops.close();

        stopServer(CONTAINER1);
        controller.start(CONTAINER1);
    }

}
