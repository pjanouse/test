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
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
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
    @RestoreConfigAfterTest
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
    @RestoreConfigAfterTest
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
    @RestoreConfigAfterTest
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
    @RestoreConfigAfterTest
    public void startTargetServerLaterTest() throws InterruptedException {
        testLogicForTargetServerLaterStart(null);
    }

    /**
     * Starts target server later - large messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    public void startTargetServerLaterWithLargeMessagesTest() throws InterruptedException {
        testLogicForTargetServerLaterStart(new ByteMessageBuilder(10 * 1024 * 1024));
    }

    /**
     * Starts source server later
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    public void startSourceServerLaterTest() throws InterruptedException {
        testLogicForSourceServerLaterStart(null);
    }

    /**
     * Starts source server later - large message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    public void startSourceServerLaterWithLargeMessagesTest() throws InterruptedException {
        testLogicForSourceServerLaterStart(new ByteMessageBuilder(10 * 1024 * 1024));
    }

    /**
     * Kills source server - normal messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @BMRules(
            {
                    @BMRule(name = "Initialization of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            action = "createCounter(\"counter\")"),
                    @BMRule(name = "Incrementation of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            action = "incrementCounter(\"counter\"); " +
                                    "System.out.println(\"Current counter - \" + readCounter(\"counter\"));"),
                    @BMRule(name = "Killing server rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            condition = "readCounter(\"counter\")>5",
                            action = "System.out.println(\"!!! Killing server!!!\"); " +
                                    "createCounter(\"counter\");" +
                                    "killJVM();")
            })
    @RunAsClient
    @RestoreConfigAfterTest
    public void killSourceServerTest() throws InterruptedException {
        testLogicForTestWithByteman(10, CONTAINER1, CONTAINER1_IP, BYTEMAN_CONTAINER1_PORT, null);
    }

    /**
     * Kills target server - normal messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @BMRules(
            {
                    @BMRule(name = "Initialization of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            action = "createCounter(\"counter\")"),
                    @BMRule(name = "Incrementation of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            action = "incrementCounter(\"counter\"); " +
                                    "System.out.println(\"Current counter - \" + readCounter(\"counter\"));"),
                    @BMRule(name = "Killing server rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            condition = "readCounter(\"counter\")>5",
                            action = "System.out.println(\"!!! Killing server!!!\"); " +
                                    "killJVM();")
            })
    @RunAsClient
    @RestoreConfigAfterTest
    public void killTargetServerTest() throws InterruptedException {
        testLogicForTestWithByteman(10, CONTAINER2, CONTAINER2_IP, BYTEMAN_CONTAINER2_PORT, null);
    }


    /**
     * Kills source server - large messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @BMRules(
            {
                    @BMRule(name = "Initialization of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            action = "createCounter(\"counter\")"),
                    @BMRule(name = "Incrementation of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            action = "incrementCounter(\"counter\"); " +
                                    "System.out.println(\"Current counter - \" + readCounter(\"counter\"));"),
                    @BMRule(name = "Killing server rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "deleteMessage",
                            condition = "readCounter(\"counter\")>5",
                            action = "System.out.println(\"!!! Killing server!!!\"); " +
                                    "createCounter(\"counter\");" +
                                    "killJVM();")
            })
    @RunAsClient
    @RestoreConfigAfterTest
    public void killSourceServerWithLargeMessagesTest() throws InterruptedException {
        testLogicForTestWithByteman(10, CONTAINER1, CONTAINER1_IP, BYTEMAN_CONTAINER1_PORT, new ByteMessageBuilder(10 * 1024 * 1024));
    }

    /**
     * Kills target server - large messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @BMRules(
            {
                    @BMRule(name = "Initialization of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            action = "createCounter(\"counter\")"),
                    @BMRule(name = "Incrementation of the counter rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            action = "incrementCounter(\"counter\"); " +
                                    "System.out.println(\"Current counter - \" + readCounter(\"counter\"));"),
                    @BMRule(name = "Killing server rule",
                            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                            targetMethod = "commit",
                            condition = "readCounter(\"counter\")>5",
                            action = "System.out.println(\"!!! Killing server!!!\"); " +
                                    "killJVM();")
            })
    @RunAsClient
    @RestoreConfigAfterTest
    public void killTargetServerWithLargeMessagesTest() throws InterruptedException {
        testLogicForTestWithByteman(10, CONTAINER2, CONTAINER2_IP, BYTEMAN_CONTAINER2_PORT, new ByteMessageBuilder(10 * 1024 * 1024));
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
        final String TEST_QUEUE_IN = "dummyQueueIn";
        final String TEST_QUEUE_IN_JNDI = "/queue/dummyQueueIn";
        final String TEST_QUEUE_OUT = "dummyQueueOut";
        final String TEST_QUEUE_OUT_JNDI = "/queue/dummyQueueOut";

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP, 9999);

        // Create queue
        jmsAdminContainer1.cleanupQueue(TEST_QUEUE_IN);
        jmsAdminContainer1.createQueue(TEST_QUEUE_IN, TEST_QUEUE_IN_JNDI);
        jmsAdminContainer2.cleanupQueue(TEST_QUEUE_OUT);
        jmsAdminContainer2.createQueue(TEST_QUEUE_OUT, TEST_QUEUE_OUT_JNDI);

        jmsAdminContainer1.removeRemoteConnector("bridge-connector");
        jmsAdminContainer1.removeBridge("myBridge");
        jmsAdminContainer1.removeRemoteSocketBinding("messaging-bridge");

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE_IN, "jms.queue." + TEST_QUEUE_OUT, -1, "bridge-connector");

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        if (messageBuilder != null) {
            client1.setMessageBuilder(messageBuilder);
        }
        client1.sendMessages(TEST_QUEUE_IN_JNDI);

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
        assertEquals(messages, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));
        client2.receiveMessages(TEST_QUEUE_OUT_JNDI);
        assertEquals(messages, client2.getReceivedMessages());
        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));
        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }

    /**
     * Implementation of the basic test scenario with Byteman and restarting of the container
     *
     * @param messages           number of messages used for the test
     * @param restartedContainer name of the container which will be restarted
     * @param bytemanTargetHost  ip address with the container where will be Byteman rules installed
     * @param bytemanPort        target port
     * @param messageBuilder     instance of the message builder
     */
    private void testLogicForTestWithByteman(int messages, String restartedContainer,
                                             String bytemanTargetHost, int bytemanPort,
                                             MessageBuilder messageBuilder) {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
        final String TEST_QUEUE_OUT = "dummyQueueOut";
        final String TEST_QUEUE_OUT_JNDI = "/queue/dummyQueueOut";

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP);

        // Create queue
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.createQueue(TEST_QUEUE_OUT, TEST_QUEUE_OUT_JNDI);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        assertEquals(0, jmsAdminContainer1.getCountOfMessagesOnQueue(TEST_QUEUE));
        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false, messageBuilder);
        client1.sendMessages(TEST_QUEUE_JNDI);

        assertEquals(messages, jmsAdminContainer1.getCountOfMessagesOnQueue(TEST_QUEUE));
        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));

        // install rule to first server
        RuleInstaller.installRule(this.getClass(), bytemanTargetHost, bytemanPort);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, "jms.queue." + TEST_QUEUE_OUT, -1, "bridge-connector");

        // Server will be killed by Byteman and restarted
        controller.kill(restartedContainer);
        controller.start(restartedContainer);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }

        // Receive messages from the output node
        SimpleJMSClient client2 = new SimpleJMSClient(CONTAINER2_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false);
        assertEquals(messages, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));
        client2.receiveMessages(TEST_QUEUE_OUT_JNDI);
        assertEquals(messages, client2.getReceivedMessages());

        /**
         * TODO this method behaves very ugly, it returns -9
         */
//        assertEquals(0, jmsAdminContainer1.getCountOfMessagesOnQueue(TEST_QUEUE));
        assertEquals(0, jmsAdminContainer2.getCountOfMessagesOnQueue(TEST_QUEUE_OUT));
        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }

    /**
     * Implementation of the basic test scenario where target server is started later
     *
     * @param messageBuilder instance of the message messageBuilder
     */
    private void testLogicForSourceServerLaterStart(MessageBuilder messageBuilder) {
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
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false, messageBuilder);
        client1.sendMessages(TEST_QUEUE_JNDI);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");
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
     * Implementation of the basic test scenario where target server is started later
     *
     * @param messageBuilder instance of the message messageBuilder
     */
    private void testLogicForTargetServerLaterStart(MessageBuilder messageBuilder) {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
        final int messages = 50;

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations(CONTAINER2_IP, 9999);

        // Create queue
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer1.addRemoteSocketBinding("messaging-bridge", CONTAINER2_IP, 5445);
        jmsAdminContainer1.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);

        controller.start(CONTAINER1);

        // Send messages into input node
        SimpleJMSClient client1 = new SimpleJMSClient(CONTAINER1_IP, 4447, messages, Session.AUTO_ACKNOWLEDGE, false, messageBuilder);
        client1.sendMessages(TEST_QUEUE_JNDI);

        jmsAdminContainer1.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignore it
        }
        controller.start(CONTAINER2);
        try {
            Thread.sleep(1 * 60 * 1000);
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


}
