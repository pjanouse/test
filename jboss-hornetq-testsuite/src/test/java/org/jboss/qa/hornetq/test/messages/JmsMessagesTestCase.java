package org.jboss.qa.hornetq.test.messages;


import org.hornetq.core.message.impl.MessageImpl;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;


/**
 * Tests for creating and manipulating messages.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmsMessagesTestCase extends HornetQTestCase {

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private String inQueue = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueue;
    private String outQueue = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueue;

    @Before
    public void startTestContainer() {
        this.controller.start(CONTAINER1);
    }


    @After
    public void stopTestContainer() {
        this.controller.stop(CONTAINER1);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testRemovingScheduledMessage() throws Exception {

        controller.start(CONTAINER1);
        prepareServer(CONTAINER1);

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        Message msg = null;
        try {
            ctx = this.getContext(CONTAINER1);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryName());
            Queue testQueue = (Queue) ctx.lookup(inQueueJndiName);
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
        JMSOperations jmsOperations = getJMSOperations(CONTAINER1);
        jmsOperations.removeMessageFromQueue(inQueue, msg.getJMSMessageID());
        long count = jmsOperations.getCountOfMessagesOnQueue(inQueue);
        jmsOperations.close();

        Assert.assertEquals("There must be 0 messages in queue.", 0, count);
        stopServer(CONTAINER1);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoScheduledExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(true, false);
    }
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoScheduledExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoScheduledNonExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoScheduledNonExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoScheduled(false, true);
    }

    private void testThatDivertedMessagesIsAlsoScheduled(boolean isExclusive, boolean isLargeMessage) throws Exception {

        controller.start(CONTAINER1);

        prepareServerWithDivert(CONTAINER1, inQueue, outQueue, isExclusive);

        // send scheduled message
        Context ctx = null;
        Connection connection = null;
        Session session = null;
        try {
            ctx = this.getContext(CONTAINER1);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryName());
            Queue originalQueue = (Queue) ctx.lookup(inQueueJndiName);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(originalQueue);
            TextMessage msg;
            if (isLargeMessage)  {
                msg = (TextMessage) new TextMessageBuilder(1024 * 1024).createMessage(session);
            } else {
                msg = (TextMessage) new TextMessageBuilder(1).createMessage(session);
            }

            long timeout = System.currentTimeMillis() + 5000;
            msg.setLongProperty(MessageImpl.HDR_SCHEDULED_DELIVERY_TIME.toString(), timeout);

            producer.send(msg);
            producer.close();

            Queue divertedQueue = (Queue) ctx.lookup(outQueueJndiName);
            MessageConsumer consumerDiverted = session.createConsumer(divertedQueue);
            Message receivedMessage = consumerDiverted.receive(1000);
            Assert.assertNull("This is scheduled message which should not be received so soon.", receivedMessage);

            if (!isExclusive)   {
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

        stopServer(CONTAINER1);

    }

    private void prepareServer(String container) {

        JMSOperations jmsOperations = getJMSOperations(container);

        jmsOperations.createQueue(inQueue, inQueueJndiName);

        jmsOperations.close();


    }

    private void prepareServerWithDivert(String containerName, String originalQueue, String divertedQueue, boolean isExclusive)  {
        JMSOperations jmsOperations = getJMSOperations(containerName);

        jmsOperations.createQueue(inQueue, inQueueJndiName);
        jmsOperations.createQueue(outQueue, outQueueJndiName);
        jmsOperations.addDivert("myDivert", "jms.queue." + originalQueue, "jms.queue." + divertedQueue, isExclusive, null, null, null);

        jmsOperations.close();

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testMapMessageWithNull() throws Exception {
        Context ctx = null;
        Connection connection = null;
        Session session = null;
        TemporaryQueue testQueue = null;

        try {
            ctx = this.getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryName());
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
    }

}
