package org.jboss.qa.hornetq.test.messages;


import org.apache.log4j.Logger;
import org.hornetq.core.message.impl.MessageImpl;
import org.hornetq.jms.client.HornetQBytesMessage;
import org.hornetq.jms.client.HornetQObjectMessage;
import org.hornetq.jms.client.HornetQTextMessage;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.apps.impl.AllHeadersClientMixMessageBuilder;
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
import java.util.Enumeration;
import java.util.List;
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

    private static final Logger log = Logger.getLogger(JmsMessagesTestCase.class);

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
            if (isLargeMessage) {
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

        stopServer(CONTAINER1);

    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoExpiredExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoExpiredNonExclusive() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoExpiredExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesIsAlsoExpiredNonExclusiveLargeMessage() throws Exception {
        testThatDivertedMessagesIsAlsoExpired(false, true);
    }

    private void testThatDivertedMessagesIsAlsoExpired(boolean isExclusive, boolean isLargeMessage) throws Exception {

        long expireTime = 1000;

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
            producer.setTimeToLive(expireTime);
            TextMessage msg;
            if (isLargeMessage) {
                msg = (TextMessage) new TextMessageBuilder(1024 * 1024).createMessage(session);
            } else {
                msg = (TextMessage) new TextMessageBuilder(1).createMessage(session);
            }
            producer.send(msg);
            producer.close();

            Thread.sleep(2000);

            Queue divertedQueue = (Queue) ctx.lookup(outQueueJndiName);
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

        stopServer(CONTAINER1);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesContainsAllHeadersExclusive() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesContainsAllHeadersExclusiveLargeMessages() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesContainsAllHeadersNonExclusive() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testThatDivertedMessagesContainsAllHeadersNonExclusiveLargeMessages() throws Exception {
        testThatDivertedMessagesContainsAllHeaders(false, true);
    }


    private void testThatDivertedMessagesContainsAllHeaders(boolean isExclusive, boolean isLargeMessage) throws Exception {

        int numberOfMessages = 100;

        controller.start(CONTAINER1);

        prepareServerWithDivert(CONTAINER1, inQueue, outQueue, isExclusive);

        SimpleJMSClient clientOriginal = new SimpleJMSClient(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), numberOfMessages, Session.AUTO_ACKNOWLEDGE,
                false);
        clientOriginal.setReceiveTimeout(1000);
        MessageBuilder messageBuilder;
        if (isLargeMessage) {
            messageBuilder = new AllHeadersClientMixMessageBuilder(104, 104);
        } else {
            messageBuilder = new AllHeadersClientMixMessageBuilder(1, 1);
        }
        clientOriginal.setMessageBuilder(messageBuilder);
        clientOriginal.sendMessages(inQueueJndiName);

        if (!isExclusive) {
            clientOriginal.receiveMessages(inQueueJndiName);
        }

        SimpleJMSClient clientDiverted = new SimpleJMSClient(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), numberOfMessages, Session.AUTO_ACKNOWLEDGE,
                false);
        clientDiverted.setReceiveTimeout(1000);
        clientDiverted.receiveMessages(outQueueJndiName);

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
        for (int i = 0; i < numberOfMessages; i++)  {
            Assert.assertTrue(areSameMessages(listOfSentMessages.get(i), listOfReceivedMessagesDiverted.get(i)));
        }

        // compare all original messages with sent messages
        if (!isExclusive) {
            for (int i = 0; i < numberOfMessages; i++) {
                Assert.assertTrue(areSameMessages(listOfSentMessages.get(i), listOfReceivedMessagesOriginal.get(i)));
            }
        }

        stopServer(CONTAINER1);

    }

    private boolean areSameMessages(Message sentMessage, Message receivedMessage) throws Exception {
        boolean isSame = true;

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

        if (!sentMessage.getStringProperty("_HQ_DUPL_ID").equals(receivedMessage.getStringProperty("_HQ_DUPL_ID"))) {
            log.info("_HQ_DUPL_ID IDs are different - " + sentMessage.getStringProperty("_HQ_DUPL_ID") + ", " + receivedMessage.getStringProperty("_HQ_DUPL_ID"));
            isSame = false;
        }

        // compare bodies
        if (sentMessage instanceof TextMessage && receivedMessage instanceof TextMessage
                && !((HornetQTextMessage) sentMessage).getText().equals(((HornetQTextMessage) receivedMessage).getText())) {

            log.info("TextMessage  - There is different body - " +  ((TextMessage) sentMessage).getText() + ", " + ((TextMessage) receivedMessage).getText());
            isSame = false;
        }

        if (sentMessage instanceof BytesMessage && receivedMessage instanceof BytesMessage
                && ((HornetQBytesMessage) sentMessage).getBodyLength() != (((HornetQBytesMessage) receivedMessage).getBodyLength())) {

            log.info("BytesMessage - There is different body - " +  ((BytesMessage) sentMessage).getBodyLength() + ", " + (((BytesMessage) receivedMessage).getBodyLength()));
            isSame = false;
        }

        if (sentMessage instanceof ObjectMessage && receivedMessage instanceof ObjectMessage
                && !((HornetQObjectMessage) sentMessage).getObject().equals(((HornetQObjectMessage) receivedMessage).getObject())) {

            log.info("ObjectMessage - There is different body - " +  sentMessage + ", " + receivedMessage);
            isSame = false;
        }

        if (sentMessage instanceof MapMessage && receivedMessage instanceof MapMessage) {

            Enumeration sentPropertyNames = ((MapMessage) sentMessage).getMapNames();
            while (sentPropertyNames.hasMoreElements())  {
                String sentPropertyName = (String) sentPropertyNames.nextElement();
                if (!((MapMessage) receivedMessage).itemExists(sentPropertyName)) {
                    log.info("MapMessage - does not contain key - " +  sentPropertyName + " in the map.");
                    isSame = false;
                }
            }
        }

        return isSame;
    }

    private void prepareServer(String container) {

        JMSOperations jmsOperations = getJMSOperations(container);

        jmsOperations.createQueue(inQueue, inQueueJndiName);

        jmsOperations.close();


    }

    private void prepareServerWithDivert(String containerName, String originalQueue, String divertedQueue, boolean isExclusive) {
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
