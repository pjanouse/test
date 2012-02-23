package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Basic tests for transfer messages over core-bridge
 * <p/>
 *
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
public class TransferOverBridgeTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        deleteDataFolderForJBoss1();
        deleteDataFolderForJBoss2();
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void normalMessagesTest() throws InterruptedException {
        testLogic(10, new ByteMessageBuilder(30), null);
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeByteMessagesTest() throws InterruptedException {
        testLogic(10, new ByteMessageBuilder(1024), null);
    }

    /**
     * Large message, text message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeTextMessagesTest() throws InterruptedException {
        final int SIZE = 1024;
        testLogic(10, new TextMessageBuilder(SIZE), new MessageVerifier() {
            @Override
            public void verifyMessage(Message message) throws Exception {
                assertTrue(message instanceof TextMessage);
                assertTrue(((TextMessage) message).getText().length() == SIZE);
            }
        });
    }

    /**
     * Starts target server later
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void startTargetServerLaterTest() throws InterruptedException {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
        final int messages = 100;

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP, 9999);

        // Create queue
        jmsAdminContainer1.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer1.removeRemoteConnector("bridge-connector");
        jmsAdminContainer1.removeBridge("myBridge");
        jmsAdminContainer1.removeRemoteSocketBinding("messaging-bridge");

        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        controller.start(CONTAINER1);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        client1.sendMessages(TEST_QUEUE_JNDI);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore it
        }
        controller.start(CONTAINER2);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore it
        }

        // Receive messages from the output node
        SimpleJMSClient client2 = new SimpleJMSClient(CONTAINER2_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        client2.receiveMessages(TEST_QUEUE_JNDI);
        assertEquals(messages, client2.getReceivedMessages());

        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE));
        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }

    /**
     * Starts source server later
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void startSourceServerLaterTest() throws InterruptedException {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
        final int messages = 100;

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP, 9999);

        // Create queue
        jmsAdminContainer1.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer1.removeRemoteConnector("bridge-connector");
        jmsAdminContainer1.removeBridge("myBridge");
        jmsAdminContainer1.removeRemoteSocketBinding("messaging-bridge");

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        client1.sendMessages(TEST_QUEUE_JNDI);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore it
        }
        controller.start(CONTAINER2);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore it
        }

        // Receive messages from the output node
        SimpleJMSClient client2 = new SimpleJMSClient(CONTAINER2_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        client2.receiveMessages(TEST_QUEUE_JNDI);
        assertEquals(messages, client2.getReceivedMessages());

        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE));
        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }

    //============================================================================================================
    //============================================================================================================
    // Private methods
    //============================================================================================================
    //============================================================================================================

    /**
     * Implementation of the basic test scenario
     *
     * @param messages        number of messages used for the test
     * @param messageBuilder  instance of the message builder
     * @param messageVerifier instance of the messages verifier
     */
    private void testLogic(int messages, MessageBuilder messageBuilder, MessageVerifier messageVerifier) {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP, 9999);

        // Create queue
        jmsAdminContainer1.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer1.removeRemoteConnector("bridge-connector");
        jmsAdminContainer1.removeBridge("myBridge");
        jmsAdminContainer1.removeRemoteSocketBinding("messaging-bridge");

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        if (messageBuilder != null) {
            client1.setMessageBuilder(messageBuilder);
        }
        client1.sendMessages(TEST_QUEUE_JNDI);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore it
        }
        // Receive messages from the output node
        SimpleJMSClient client2 = new SimpleJMSClient(CONTAINER2_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        if (messageVerifier != null) {
            client2.setMessageVerifier(messageVerifier);
        }
        client2.receiveMessages(TEST_QUEUE_JNDI);
        assertEquals(messages, client2.getReceivedMessages());

        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE));
        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }
}
