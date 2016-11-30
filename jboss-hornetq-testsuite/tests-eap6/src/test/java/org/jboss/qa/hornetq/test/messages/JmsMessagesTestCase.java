package org.jboss.qa.hornetq.test.messages;

import org.hornetq.core.message.impl.MessageImpl;
import org.hornetq.jms.client.HornetQBytesMessage;
import org.hornetq.jms.client.HornetQObjectMessage;
import org.hornetq.jms.client.HornetQTextMessage;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.apps.impl.AllHeadersClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hornetq.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for creating and manipulating messages.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Tests for creating and manipulating messages.
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmsMessagesTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(JmsMessagesTestCase.class);

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    /**
     * @tpTestDetails Server is started and queue is deployed. Send one message
     * with scheduled delivery time set to 1200 seconds to queue and then try to
     * remove it. Check whether queue contains no messages.
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send one message to queue with scheduled delivery time set to 1200
     * seconds </li>
     * <li>Try to remove send message from queue</li>
     * <li>Check number of messages in queue</li>
     * </ul>
     * @tpPassCrit Queue contains correct number of messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("OneNode")
    public void testRemovingScheduledMessage() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        Message msg = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            Queue testQueue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(testQueue);
            msg = session.createMapMessage();
            long timeout = System.currentTimeMillis() + 1200000;
            msg.setLongProperty(MessageImpl.HDR_SCHEDULED_DELIVERY_TIME.toString(), timeout);
            producer.send(msg);
            producer.close();

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }
        // try to remove this message
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.removeMessageFromQueue(PrepareConstants.QUEUE_NAME, msg.getJMSMessageID());
        long count = jmsOperations.getCountOfMessagesOnQueue(PrepareConstants.QUEUE_NAME);
        jmsOperations.close();

        Assert.assertEquals("There must be 0 messages in queue.", 0, count);
        container(1).stop();

    }

    /**
     * @tpTestDetails Server is started and topic is deployed. Send one large message
     * to topic and then try to
     * to receive it by 2 subscribers. Check there are no errors.
     * @tpProcedure <ul>
     * <li>Start server and deploy topic</li>
     * <li>Send one large message to topic</li>
     * <li>Try to receive it by 2 subscriber</li>
     * <li>Check no error occurs</li>
     * </ul>
     * @tpPassCrit There are no errors or exceptions
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("OneNode")
    public void testLargeMessageReceiveFromTopicTwoSubscribers() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        TextMessage msg = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            Topic inTopic = (Topic) ctx.lookup(PrepareConstants.TOPIC_JNDI);
            connection = cf.createConnection();
            connection.setClientID("myClient");
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            TopicSubscriber sub1 = session.createDurableSubscriber(inTopic, "sub1");
            TopicSubscriber sub2 = session.createDurableSubscriber(inTopic, "sub2");

            MessageProducer producer = session.createProducer(inTopic);
            msg = session.createTextMessage();
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 1024 * 1024; i++) {
                content.append("a");
            }
            msg.setText(content.toString());
            producer.send(msg);
            producer.close();

            sub1.receive(10000);
            sub2.receive(10000);

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }
        container(1).stop();

    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Send one message with
     * scheduled delivery time set to 5 seconds to OriginalQueue. Try to receive
     * message from DivertedQueue and check whether message is delivered to
     * DivertedQueue in correct time range.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send one message to OriginalQueue(scheduled delivery time =
     * 5sec)</li>
     * <li>Check message delivery to DivertedQueue in correct time range</li>
     * </ul>
     * @tpPassCrit Message is delivered to DivertedQueue in correct time range
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "true")
    })
    public void testThatDivertedMessagesIsAlsoScheduledExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(true, false);
    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Send one large message with
     * scheduled delivery time set to 5 seconds to OriginalQueue. Try to receive
     * message from DivertedQueue and check whether message is delivered to
     * DivertedQueue in correct time range.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send one large message to OriginalQueue(scheduled delivery time =
     * 5sec)</li>
     * <li>Check message delivery to DivertedQueue in correct time range</li>
     * </ul>
     * @tpPassCrit Message is delivered to DivertedQueue in correct time range
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "true")
    })
    public void testThatDivertedMessagesIsAlsoScheduledExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(true, true);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Send one message with
     * scheduled delivery time set to 5 seconds to OriginalQueue. Try to receive
     * message from OriginalQueue and DivertedQueue and check whether message is
     * delivered to DivertedQueue and OriginalQueue in correct time range.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send one message to OriginalQueue(scheduled delivery time =
     * 5sec)</li>
     * <li>Check message delivery to OriginalQueue and DivertedQueue in correct
     * time range</li>
     * </ul>
     * @tpPassCrit Message is delivered to both OriginalQueue and DivertedQueue
     * in correct time range
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "false")
    })
    public void testThatDivertedMessagesIsAlsoScheduledNonExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(false, false);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Send one large message with
     * scheduled delivery time set to 5 seconds to OriginalQueue. Try to receive
     * message from OriginalQueue and DivertedQueue and check whether message is
     * delivered to DivertedQueue and OriginalQueue in correct time range.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send one large message to OriginalQueue(scheduled delivery time =
     * 5sec)</li>
     * <li>Check message delivery to OriginalQueue and DivertedQueue in correct
     * time range</li>
     * </ul>
     * @tpPassCrit Message is delivered to both OriginalQueue and DivertedQueue
     * in correct time range
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "false")
    })
    public void testThatDivertedMessagesIsAlsoScheduledNonExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(false, true);
    }

    private void testThatDivertedMessagesIsAlsoScheduled(boolean isExclusive, boolean isLargeMessage) throws Exception {

        container(1).start();

        // send scheduled message
        Context ctx = null;
        Connection connection = null;
        Session session = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            Queue originalQueue = (Queue) ctx.lookup(PrepareConstants.IN_QUEUE_JNDI);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(originalQueue);
            TextMessage msg;
            if (isLargeMessage) {
                msg = (TextMessage) new TextMessageBuilder(1024 * 1024).createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
            } else {
                msg = (TextMessage) new TextMessageBuilder(1).createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
            }

            long timeout = System.currentTimeMillis() + 5000;

            msg.setLongProperty(HDR_SCHEDULED_DELIVERY_TIME.toString(), timeout);

            producer.send(msg);
            log.info("Send message to queue - isExclusive: " + isExclusive + ", isLargeMessage:" + isLargeMessage);
            producer.close();

            Queue divertedQueue = (Queue) ctx.lookup(PrepareConstants.OUT_QUEUE_JNDI);
            MessageConsumer consumerDiverted = session.createConsumer(divertedQueue);
            Message receivedMessage = consumerDiverted.receive(1000);
            Assert.assertNull("This is scheduled message which should not be received so soon.", receivedMessage);

            if (!isExclusive) {
                MessageConsumer consumerOriginal = session.createConsumer(originalQueue);
                receivedMessage = consumerOriginal.receive(1000);
                Assert.assertNull("This is scheduled message which should not be received so soon.", receivedMessage);
                receivedMessage = consumerOriginal.receive(10000);
                Assert.assertNotNull("This is scheduled message which should be received now.", receivedMessage);
            }

            receivedMessage = consumerDiverted.receive(10000);
            Assert.assertNotNull("This is scheduled message which should be received now.", receivedMessage);

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }

        container(1).stop();

    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create message producer
     * with time to live set to 1 second. Send message to OriginalQueue. After 2
     * seconds, create consumer and try to receive message from DivertedQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Create producer(Time to live = 1sec) and send one message to
     * OriginalQueue</li>
     * <li>After 2 seconds try to receive message from DivertedQueue</li>
     * </ul>
     * @tpPassCrit No messages received by consumer. Message is already expired.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE-QUEUE", value = "true")
    })
    public void testThatDivertedMessagesIsAlsoExpiredExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(true, false);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create message producer
     * with time to live set to 1 second. Send message to OriginalQueue. After 2
     * seconds, create consumers and try to receive message from DivertedQueue
     * and OriginalQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Create producer(Time to live = 1sec) and send one message to
     * OriginalQueue</li>
     * <li>After 2 seconds try to receive message from DivertedQueue and
     * OriginalQueue</li>
     * </ul>
     * @tpPassCrit No messages received by consumer. Message is already expired.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "false")
    })
    public void testThatDivertedMessagesIsAlsoExpiredNonExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(false, false);
    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create message producer
     * with time to live set to 1 second. Send large message to OriginalQueue.
     * After 2 seconds, create consumer and try to receive message from
     * DivertedQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Create producer(Time to live = 1sec) and send one large message to
     * OriginalQueue</li>
     * <li>After 2 seconds try to receive message from DivertedQueue</li>
     * </ul>
     * @tpPassCrit No messages received by consumer. Message is already expired.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "true")
    })
    public void testThatDivertedMessagesIsAlsoExpiredExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(true, true);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create message producer
     * with time to live set to 1 second. Send large message to OriginalQueue.
     * After 2 seconds, create consumers and try to receive message from
     * DivertedQueue and OriginalQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Create producer(Time to live = 1sec) and send one large message to
     * OriginalQueue</li>
     * <li>After 2 seconds try to receive message from DivertedQueue and
     * OriginalQueue</li>
     * </ul>
     * @tpPassCrit No messages received by consumer. Message is already expired.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "false")
    })
    public void testThatDivertedMessagesIsAlsoExpiredNonExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(false, true);
    }

    private void testThatDivertedMessagesIsAlsoExpired(boolean isExclusive, boolean isLargeMessage) throws Exception {

        long expireTime = 1000;

        container(1).start();

        // send scheduled message
        Context ctx = null;
        Connection connection = null;
        Session session = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            Queue originalQueue = (Queue) ctx.lookup(PrepareConstants.IN_QUEUE_JNDI);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(originalQueue);
            producer.setTimeToLive(expireTime);
            TextMessage msg;
            if (isLargeMessage) {
                msg = (TextMessage) new TextMessageBuilder(1024 * 1024).createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
            } else {
                msg = (TextMessage) new TextMessageBuilder(1).createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
            }
            producer.send(msg);
            producer.close();

            Thread.sleep(2000);

            Queue divertedQueue = (Queue) ctx.lookup(PrepareConstants.OUT_QUEUE_JNDI);
            Message receivedMessage;
            if (!isExclusive) {
                MessageConsumer consumerOriginal = session.createConsumer(originalQueue);
                receivedMessage = consumerOriginal.receive(1000);
                Assert.assertNull("Message must be null (expired) in original divert address.", receivedMessage);
            }

            MessageConsumer consumerDiverted = session.createConsumer(divertedQueue);
            receivedMessage = consumerDiverted.receive(1000);
            Assert.assertNull("Message must be null (expired) in diverted address.", receivedMessage);

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }

        container(1).stop();

    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create Jms client and sent
     * 100 messages to OriginalQueue. Receive messages from DivertedQueue.
     * Compare send messages to received messages.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send 100 messages to OriginalQueue</li>
     * <li>Receive messages from DivertedQueue</li>
     * <li>Compare send messages to received messages</li>
     * </ul>
     * @tpPassCrit Messages received from DivertedQueue are same messages as
     * producer send to OriginalQueue.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "true")
    })
    public void testThatDivertedMessagesContainsAllHeadersExclusive() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(true, false);
    }

    /**
     * @tpTestDetails Server with configured exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create Jms client and sent
     * 100 large messages to OriginalQueue. Receive messages from DivertedQueue.
     * Compare send messages to received messages.
     * @tpProcedure <ul>
     * <li>Start server with configured exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send 100 large messages to OriginalQueue</li>
     * <li>Receive messages from DivertedQueue</li>
     * <li>Compare send messages to received messages</li>
     * </ul>
     * @tpPassCrit Messages received from DivertedQueue are same messages as
     * producer send to OriginalQueue.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "true")
    })
    public void testThatDivertedMessagesContainsAllHeadersExclusiveLargeMessages() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(true, true);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create Jms client and sent
     * 100 messages to OriginalQueue. Receive messages from DivertedQueue and
     * OriginalQueue. Compare send messages to messages received from
     * DivertedQueue and OriginalQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send 100 messages to OriginalQueue</li>
     * <li>Receive messages from OriginalQueue</li>
     * <li>Receive messages from DivertedQueue</li>
     * <li>Compare send messages to messages received from OriginalQueue</li>
     * <li>Compare send messages to messages received from DivertedQueue</li>
     * </ul>
     * @tpPassCrit Messages received from DivertedQueue are same messages as
     * producer send to OriginalQueue. Messages received from OriginalQueue are
     * same messages as producer send to OriginalQueue.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE-QUEUE", value = "false")
    })
    public void testThatDivertedMessagesContainsAllHeadersNonExclusive() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(false, false);
    }

    /**
     * @tpTestDetails Server with configured non exclusive divert is started and
     * OriginalQueue and DivertedQueue are deployed. Create Jms client and sent
     * 100 large messages to OriginalQueue. Receive messages from DivertedQueue
     * and OriginalQueue. Compare send messages to messages received from
     * DivertedQueue and OriginalQueue.
     * @tpProcedure <ul>
     * <li>Start server with configured non exclusive divert and deploy
     * OriginalQueue and DivertedQueue</li>
     * <li>Send 100 large messages to OriginalQueue</li>
     * <li>Receive messages from OriginalQueue</li>
     * <li>Receive messages from DivertedQueue</li>
     * <li>Compare send messages to messages received from OriginalQueue</li>
     * <li>Compare send messages to messages received from DivertedQueue</li>
     * </ul>
     * @tpPassCrit Messages received from DivertedQueue are same messages as
     * producer send to OriginalQueue. Messages received from OriginalQueue are
     * same messages as producer send to OriginalQueue.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = "DIVERT-A-ORIGIN-QUEUE", value = PrepareConstants.IN_QUEUE_NAME),
            @Param(name = "DIVERT-A-DIVERTED-QUEUE", value = PrepareConstants.OUT_QUEUE_NAME),
            @Param(name = "DIVERT-A-EXCLUSIVE", value = "false")
    })
    public void testThatDivertedMessagesContainsAllHeadersNonExclusiveLargeMessages() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(false, true);
    }

    private void testThatDivertedMessagesContainsAllHeaders(boolean isExclusive, boolean isLargeMessage) throws Exception {

        int numberOfMessages = 100;

        container(1).start();

        SimpleJMSClient clientOriginal = new SimpleJMSClient(container(1), numberOfMessages, Session.AUTO_ACKNOWLEDGE,
                false);
        clientOriginal.setReceiveTimeout(1000);
        MessageBuilder messageBuilder;
        if (isLargeMessage) {
            messageBuilder = new AllHeadersClientMixMessageBuilder(104, 104);
        } else {
            messageBuilder = new AllHeadersClientMixMessageBuilder(1, 1);
        }
        clientOriginal.setMessageBuilder(messageBuilder);
        clientOriginal.sendMessages(PrepareConstants.IN_QUEUE_JNDI);

        if (!isExclusive) {
            clientOriginal.receiveMessages(PrepareConstants.IN_QUEUE_JNDI);
        }

        SimpleJMSClient clientDiverted = new SimpleJMSClient(container(1), numberOfMessages, Session.AUTO_ACKNOWLEDGE,
                false);
        clientDiverted.setReceiveTimeout(1000);
        clientDiverted.receiveMessages(PrepareConstants.OUT_QUEUE_JNDI);

        List<Message> listOfSentMessages = clientOriginal.getListOfSentMesages();
        List<Message> listOfReceivedMessagesOriginal = clientOriginal.getListOfReceivedMessages();
        List<Message> listOfReceivedMessagesDiverted = clientDiverted.getListOfReceivedMessages();
        log.info("####################################################################################################################");
        log.info("List of sent messages.");
        for (Message m : listOfSentMessages) {
            log.info("Sent message: " + m);
        }

        log.info("####################################################################################################################");
        log.info("List of original messages.");
        for (Message m : listOfReceivedMessagesOriginal) {
            log.info("Received message: " + m);
        }
        log.info("####################################################################################################################");
        log.info("List of Diverted messages.");
        for (Message m : listOfReceivedMessagesDiverted) {
            log.info("Received message: " + m);
        }
        log.info("####################################################################################################################");

        // compare all diverted messages with sent messages
        for (int i = 0; i < numberOfMessages; i++) {
            Assert.assertTrue(areSameMessages(listOfSentMessages.get(i), listOfReceivedMessagesDiverted.get(i)));
        }

        // compare all original messages with sent messages
        if (!isExclusive) {
            for (int i = 0; i < numberOfMessages; i++) {
                Assert.assertTrue(areSameMessages(listOfSentMessages.get(i), listOfReceivedMessagesOriginal.get(i)));
            }
        }

        container(1).stop();

    }

    private boolean areSameMessages(Message sentMessage, Message receivedMessage) throws Exception {
        boolean isSame = true;
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        if (!sentMessage.getJMSMessageID().equals(receivedMessage.getJMSMessageID())) {
            log.info("Messages IDs are different - " + sentMessage.getJMSMessageID() + ", " + receivedMessage.getJMSMessageID());
            isSame = false;
        }

        if (sentMessage.getJMSDeliveryMode() != (receivedMessage.getJMSDeliveryMode())) {
            log.info("JMSDeliveryMode is different - " + sentMessage.getJMSDeliveryMode() + ", " + receivedMessage.getJMSDeliveryMode());
            isSame = false;
        }

        if (!sentMessage.getJMSCorrelationID().equals(receivedMessage.getJMSCorrelationID())) {
            log.info("JMSCorrelationIDs are different - " + sentMessage.getJMSCorrelationID() + ", " + receivedMessage.getJMSCorrelationID());
            isSame = false;
        }

        if (!sentMessage.getJMSType().equals(receivedMessage.getJMSType())) {
            log.info("JMSType is different - " + sentMessage.getJMSType() + ", " + receivedMessage.getJMSType());
            isSame = false;
        }

        if (sentMessage.getJMSExpiration() != (receivedMessage.getJMSExpiration())) {
            log.info("JMSExpiration is different - " + sentMessage.getJMSExpiration() + ", " + receivedMessage.getJMSExpiration());
            isSame = false;
        }

        if (sentMessage.getJMSPriority() != (receivedMessage.getJMSPriority())) {
            log.info("JMSPriority is different - " + sentMessage.getJMSPriority() + ", " + receivedMessage.getJMSPriority());
            isSame = false;
        }

        if (!sentMessage.getStringProperty("JMSXUserID").equals(receivedMessage.getStringProperty("JMSXUserID"))) {
            log.info("JMSXUserID IDs are different - " + sentMessage.getStringProperty("JMSXUserID") + ", " + receivedMessage.getStringProperty("JMSXUserID"));
            isSame = false;
        }

        if (!sentMessage.getStringProperty("JMSXAppID").equals(receivedMessage.getStringProperty("JMSXAppID"))) {
            log.info("JMSXAppID IDs are different - " + sentMessage.getStringProperty("JMSXAppID") + ", " + receivedMessage.getStringProperty("JMSXAppID"));
            isSame = false;
        }

        if (!sentMessage.getStringProperty("JMSXGroupID").equals(receivedMessage.getStringProperty("JMSXGroupID"))) {
            log.info("JMSXGroupID IDs are different - " + sentMessage.getStringProperty("JMSXGroupID") + ", " + receivedMessage.getStringProperty("JMSXGroupID"));
            isSame = false;
        }

        if (!sentMessage.getStringProperty(duplicatedHeader).equals(receivedMessage.getStringProperty(duplicatedHeader))) {
            log.info(duplicatedHeader + " IDs are different - " + sentMessage.getStringProperty(duplicatedHeader) + ", " + receivedMessage.getStringProperty(duplicatedHeader));
            isSame = false;
        }

        // compare bodies
        if (sentMessage instanceof TextMessage && receivedMessage instanceof TextMessage
                && !((HornetQTextMessage) sentMessage).getText().equals(((HornetQTextMessage) receivedMessage).getText())) {

            log.info("TextMessage  - There is different body - " + ((TextMessage) sentMessage).getText() + ", " + ((TextMessage) receivedMessage).getText());
            isSame = false;
        }

        if (sentMessage instanceof BytesMessage && receivedMessage instanceof BytesMessage
                && ((HornetQBytesMessage) sentMessage).getBodyLength() != (((HornetQBytesMessage) receivedMessage).getBodyLength())) {

            log.info("BytesMessage - There is different body - " + ((BytesMessage) sentMessage).getBodyLength() + ", " + (((BytesMessage) receivedMessage).getBodyLength()));
            isSame = false;
        }

        if (sentMessage instanceof ObjectMessage && receivedMessage instanceof ObjectMessage
                && !((HornetQObjectMessage) sentMessage).getObject().equals(((HornetQObjectMessage) receivedMessage).getObject())) {

            log.info("ObjectMessage - There is different body - " + sentMessage + ", " + receivedMessage);
            isSame = false;
        }

        if (sentMessage instanceof MapMessage && receivedMessage instanceof MapMessage) {

            Enumeration sentPropertyNames = ((MapMessage) sentMessage).getMapNames();
            while (sentPropertyNames.hasMoreElements()) {
                String sentPropertyName = (String) sentPropertyNames.nextElement();
                if (!((MapMessage) receivedMessage).itemExists(sentPropertyName)) {
                    log.info("MapMessage - does not contain key - " + sentPropertyName + " in the map.");
                    isSame = false;
                }
            }
        }

        return isSame;
    }

    /**
     * @tpTestDetails Start server. Send MapMessage with null in map and receive
     * it.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send MapMessage with null in object and 100 in long</li>
     * <li>Receive message</li>
     * <li>Check message message</li>
     * </ul>
     * @tpPassCrit Received message is MapMessage with correct values
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testMapMessageWithNull() throws Exception {

        container(1).start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        TemporaryQueue testQueue;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            testQueue = session.createTemporaryQueue();

            MessageProducer producer = session.createProducer(testQueue);
            MapMessage msg = session.createMapMessage();
            msg.setObject("obj", null);
            msg.setLong("long", 100);
            producer.send(msg);
            producer.close();

            MessageConsumer consumer = session.createConsumer(testQueue);
            Message receivedMsg = consumer.receive(RECEIVE_TIMEOUT);

            assertTrue("Message should be map type", MapMessage.class.isAssignableFrom(receivedMsg.getClass()));

            MapMessage rm = (MapMessage) receivedMsg;
            assertNull("Object property should be null", receivedMsg.getObjectProperty("obj"));
            assertEquals("Incorrect long property value", 100, rm.getLong("long"));

            consumer.close();

            testQueue.delete();
        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }
        container(1).stop();
    }

}
