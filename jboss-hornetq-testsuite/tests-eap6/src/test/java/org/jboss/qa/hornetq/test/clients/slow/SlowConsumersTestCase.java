package org.jboss.qa.hornetq.test.clients.slow;


import java.util.List;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
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
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;


@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class SlowConsumersTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(SlowConsumersTestCase.class);

    private static final String CLIENT_NAME = "test-client";

    private static final int NUMBER_OF_MESSAGES = 10000;

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testSlowNonDurableConsumerKill() throws Exception {

        container(1).start();
        Thread.sleep(3000);

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1);
            slowConsumer.setTimeout(1000); // slow consumer reads only one message per second

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(70000);

            JMSOperations ops = container(1).getJmsOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopicForClient(CLIENT_NAME + "subscriber-2");
            ops.close();

            producer.join();
            fastConsumer.join();
            slowConsumer.join();

            assertEquals("The non-durable subscription should have been removed after killing slow client",
                    0, numberOfSubscribers);
            assertNotNull("Slow client should have been disconnected by the server",
                    slowConsumer.getException());

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            Assert.fail();
        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "NOTIFY"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "5"),
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testSlowConsumerNotification() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        JMXConnector jmxConnector = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            JmxNotificationListener notificationListener = container(1).createJmxNotificationListener();
            JmxUtils jmxUtils = container(1).getJmxUtils();
            jmxConnector = jmxUtils.getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = jmxConnector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(jmxUtils.getObjectNameBuilder(ObjectNameBuilder.class).getHornetQServerObjectName(),
                    notificationListener, null, null);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1);
            slowConsumer.setTimeout(100); // slow consumer reads only one message per second

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(15000);

            JMSOperations ops = container(1).getJmsOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopicForClient(CLIENT_NAME + "subscriber-2");
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
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            Assert.fail();
        } finally {
            JMSTools.cleanupResources(ctx, connection, session);

            if (jmxConnector != null) {
                jmxConnector.close();
            }
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testSlowDurableConsumerKill() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            SubscriberAutoAck fastConsumer = new SubscriberAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, CLIENT_NAME + "subscriber-1", "test-fast-subscriber");
            SubscriberAutoAck slowConsumer = new SubscriberAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, CLIENT_NAME + "subscriber-2", "test-slow-subscriber");
            slowConsumer.setTimeout(1000); // slow consumer reads only one message per second
            slowConsumer.setMaxRetries(1);

            connection.start();
            producer.start();
            fastConsumer.start();
            slowConsumer.start();

            Thread.sleep(70000);

            JMSOperations ops = container(1).getJmsOperations();
            int numberOfSubscribers = ops.getNumberOfDurableSubscriptionsOnTopicForClient(CLIENT_NAME + "subscriber-2");
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
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testSlowReceiverKill() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            ProducerAutoAck producer = new ProducerAutoAck(container(1),
                    PrepareConstants.QUEUE_JNDI, NUMBER_OF_MESSAGES);
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            //ReceiverAutoAck fastReceiver = new ReceiverAutoAck(getHostname(CONTAINER1_NAME_NAME), getJNDIPort(CONTAINER1_NAME_NAME),
            //        QUEUE_JNDI_NAME, 30000, 1);
            ReceiverAutoAck slowReceiver = new ReceiverAutoAck(container(1), PrepareConstants.QUEUE_JNDI);
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
            slowReceiver.join(600000);

            Assert.assertFalse("Slow consumer is running after 5 min. It should be disconnected after 1 min but it did not happen.",
                    slowReceiver.isAlive());

            //LOG.info("Waiting for fast receiver");
            //fastReceiver.join();

            assertNotNull("Slow client should have been disconnected by the server",
                    slowReceiver.getException());

        } finally {
            JMSTools.cleanupResources(ctx, connection, session);
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testSlowConsumerNotKilledWithSlowProducer() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(100); // producer only sends 10 message/second - lower than slow consumer threshold

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1);
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
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore // paging does in fact cause consumer kill (by design)
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testPagingNotKillingFastConsumer() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, 1000, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber fastConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1);
            fastConsumer.setTimeout(30);
            NonDurableTopicSubscriber slowConsumer = new NonDurableTopicSubscriberAutoAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1);
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
            container(1).stop();
        }
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore // long commits do in fact cause consumer kill (by design)
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY, value = "KILL"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_THRESHOLD, value = "20"),
            @Param(name = PrepareParams.SLOW_CONSUMER_POLICY_CHECK_PERIOD, value = "1")
    })
    public void testSlowConsumerNotKilledWhileCommiting() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            PublisherAutoAck producer = new PublisherAutoAck(container(1),
                    PrepareConstants.TOPIC_JNDI, NUMBER_OF_MESSAGES, CLIENT_NAME + "producer");
            producer.setMessageBuilder(new TextMessageBuilder(10));
            producer.setTimeout(0);

            NonDurableTopicSubscriber consumer = new NonDurableTopicSubscriberTransAck(
                    container(1), PrepareConstants.TOPIC_JNDI, 30000, 1000, 1);
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
            container(1).stop();
        }
    }

}
