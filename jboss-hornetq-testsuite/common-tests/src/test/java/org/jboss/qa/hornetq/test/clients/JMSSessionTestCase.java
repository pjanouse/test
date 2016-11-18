package org.jboss.qa.hornetq.test.clients;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.xml.soap.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class JMSSessionTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JMSSessionTestCase.class);

    private static final String QUEUE_NAME_PREFIX = "testQueue";

    private static final String QUEUE_JNDI_PREFIX = "jms/queue/" + QUEUE_NAME_PREFIX;

    private static final int QUEUE_NUMBER = 10;

    /**
     * @tpTestDetails There is one server with 10 queues. Messages are uniformly spread to all queues and it is
     * verified that each message was delivered into the proper queue.
     * @tpProcedure <ul>
     * <li>Start one EAP server</li>
     * <li>Send 100 messages into the each queue</li>
     * <li>Receive all messages from all queues</li>
     * <li>Verify that all messages were delivered to the proper queue</li>
     * </ul>
     * @tpPassCrit All messages are delivered to the proper queues.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSendMessagesToMultipleQueues() throws Exception {
        final int MESSAGE_NUMBER = 100;
        List<Queue> queues = new ArrayList<Queue>();

        prepareServer("PAGE");
        container(1).start();

        Context context = getContext(container(1));
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            queues.add((Queue) context.lookup(QUEUE_JNDI_PREFIX + i));
        }

        Connection connection = cf.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(null);

        for (int i = 0; i < MESSAGE_NUMBER; i++) {
            for (Queue queue : queues) {
                producer.send(queue, session.createTextMessage(String.format("queue-%s-message-%d", queue.getQueueName(), i)));
            }
        }

        for (Queue queue : queues) {
            MessageConsumer consumer = session.createConsumer(queue);
            for (int i = 0; i < MESSAGE_NUMBER; i++) {
                TextMessage message = (TextMessage) consumer.receiveNoWait();
                Assert.assertEquals(String.format("queue-%s-message-%d", queue.getQueueName(), i), message.getText());
            }
            Assert.assertNull(consumer.receiveNoWait());
        }

        context.close();
        container(1).stop();
    }

    /**
     * @tpTestDetails There are two servers configured in the replicated HA topology. Producer sends several messages
     * into the queue-1. After that the server 1 is stopped and the same producer sends few more message into the queue-2
     * and then into the queue-1. All messages should be delivered into proper queues.
     * @tpProcedure <ul>
     * <li>Configure replicated HA topology</li>
     * <li>Start servers</li>
     * <li>Connect to server 1</li>
     * <li>Send several messages into the queue-1</li>
     * <li>Stop server 1</li>
     * <li>Send several messages into the queue-2</li>
     * <li>Send several messages into the queue-1</li>
     * <li>Verify that all messages were delivered to proper queues</li>
     * </ul>
     * @tpPassCrit All messages are delivered to the proper queues.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSendMessagesToMultipleQueuesAfterFailover() throws Exception {
        final int MESSAGES_NUMBER = 5;

        prepareHA();

        container(1).start();
        container(2).start();
        Thread.sleep(5000);

        Context context = getContext(container(1));
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Queue testQueue0 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 0);
        Queue testQueue1 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 1);

        Connection connection = cf.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(null);

        for (int i = 0; i < MESSAGES_NUMBER; i++) {
            producer.send(testQueue0, session.createTextMessage("testMessage"));
        }

        container(1).stop();

        for (int i = 0; i < MESSAGES_NUMBER;) {
            try {
                producer.send(testQueue1, session.createTextMessage("testMessage"));
                i++;
            } catch (JMSException ex) {
                logger.info(ex);
            }
        }

        for (int i = 0; i < MESSAGES_NUMBER;) {
            try {
                producer.send(testQueue0, session.createTextMessage("testMessage"));
                i++;
            } catch (JMSException ex) {
                logger.info(ex);
            }
        }

        connection.close();
        context.close();

        JMSOperations jmsOperations = container(2).getJmsOperations();
        Assert.assertEquals(2 * MESSAGES_NUMBER, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 0));
        Assert.assertEquals(MESSAGES_NUMBER, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 1));
        jmsOperations.close();

        container(2).stop();
    }

    /**
     * @tpTestDetails This test scenario tests whether the defaultAddress on JMS Session is properly negotiated
     * between server and client. If it is not, the message is delivered into the wrong queue. The issue was hit
     * by customer. @see https://bugzilla.redhat.com/show_bug.cgi?id=1344286
     * @tpProcedure <ul>
     * <li>Start EAP server with two queues and FAIL full policy.</li>
     * <li>Send messages into the queue-1 until exception is thrown (queue is full).</li>
     * <li>Close and recreate JMSContext.</li>
     * <li>Send one message into the queue-1 and expect exception from server.</li>
     * <li>Send several messages into the queue-2.</li>
     * <li>Create second JMSContext and receive all messages from queue-1</li>
     * <li>Send several messages into the queue-1.</li>
     * <li>Verify that all messages were delivered to proper queues.</li>
     * </ul>
     * @tpPassCrit All messages are delivered to proper queues.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSendAfterQueueIsFreeAgain() throws Exception {
        prepareServer("FAIL");
        container(1).start();

        Context context = getContext(container(1));
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Queue testQueue0 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 0);
        Queue testQueue1 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 1);

        // Full testQueue0
        Connection connection = cf.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(null);
        while (true) {
            try {
                producer.send(testQueue0, session.createTextMessage("Test message"));
            } catch (JMSException ex) {
                break;
            }
        }
        connection.close();

        connection = cf.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(null);

        try {
            producer.send(testQueue0, session.createTextMessage("Test message"));
            Assert.fail("Exception expected");
        } catch (JMSException ex) {

        }

        for (int i = 0; i < 5; i++) {
            producer.send(testQueue1, session.createTextMessage("Test message"));
        }

        Connection connection2 = cf.createConnection();
        connection2.start();
        Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(testQueue0);

        while (consumer.receiveNoWait() != null) {}

        connection2.close();

        for (int i = 0; i < 5; i++) {
            producer.send(testQueue0, session.createTextMessage("Test message"));
        }

        connection.close();
        context.close();

        JMSOperations jmsOperations = container(1).getJmsOperations();
        Assert.assertEquals(5, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 0));
        Assert.assertEquals(5, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 1));
        jmsOperations.close();

        container(1).stop();
    }

    /**
     * @tpTestDetails This test scenario tests whether the defaultAddress on JMS Session is properly negotiated
     * between server and client. If it is not, the message is delivered into the wrong queue. The issue was hit
     * with one-off patch. @see https://bugzilla.redhat.com/show_bug.cgi?id=1344286
     * @tpProcedure <ul>
     * <li>Start EAP server with two queues. Sending messages into the queue-1 is denied for user guest</li>
     * <li>Send one message into the queue-1 and expect exception.</li>
     * <li>Send two messages into the queue-2 and expect no exception.</li>
     * <li>Verify that all messages were delivered to proper queues.</li>
     * </ul>
     * @tpPassCrit No unexpected exception arises and all messages are delivered to proper queues.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSendFirstToForbiddenQueue() throws Exception {
        prepareServer("PAGE");

        UsersSettings.forDefaultEapServer()
                .withUser("guest", null, "guest")
                .create();

        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.addSecuritySetting("default", "jms.queue." + QUEUE_NAME_PREFIX + 1);
        jmsOperations.addRoleToSecuritySettings("jms.queue." + QUEUE_NAME_PREFIX + 1, "guest");
        jmsOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", false);
        jmsOperations.setPermissionToRoleToSecuritySettings("jms.queue." + QUEUE_NAME_PREFIX + 1, "guest", "send", true);
        jmsOperations.setSecurityEnabled(true);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("unauthenticatedIdentity","guest");
        jmsOperations.rewriteLoginModule("RealmDirect", opts);

        jmsOperations.reload();
        jmsOperations.close();

        Thread.sleep(5000);

        Context context = getContext(container(1));
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Queue testQueue0 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 0);
        Queue testQueue1 = (Queue) context.lookup(QUEUE_JNDI_PREFIX + 1);

        Connection connection = cf.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(null);

        try {
            producer.send(testQueue0, session.createTextMessage("testMessage"));
            Assert.fail("Exception expected");
        } catch (JMSException ex) {

        }

        try {
            producer.send(testQueue1, session.createTextMessage("testMessage"));
            producer.send(testQueue1, session.createTextMessage("testMessage"));
        } catch (JMSException ex) {
            Assert.fail("Unexpected exception " + ex);
        }

        connection.close();
        context.close();

        jmsOperations = container(1).getJmsOperations();
        Assert.assertEquals(0, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 0));
        Assert.assertEquals(2, jmsOperations.getCountOfMessagesOnQueue(QUEUE_NAME_PREFIX + 1));
        jmsOperations.close();

        container(1).stop();
    }

    protected void prepareServer(String fullPolicy) {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", fullPolicy, 50 * 1024, 0, 0, 40 * 1024);

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i, true);
        }

        jmsOperations.close();
        container(1).stop();
    }

    protected void prepareHA() {
        if (ContainerUtils.isEAP6(container(1))) {
            prepareHAEap6();
        } else {
            prepareHAEap7();
        }
    }

    protected void prepareHAEap6() {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group-0");
        jmsOperations.setCheckForLiveServer(true);

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i, true);
        }

        jmsOperations.close();

        container(1).stop();
        container(2).start();
        jmsOperations = container(2).getJmsOperations();
        jmsOperations.setBackup(true);
        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group-0");
        jmsOperations.setCheckForLiveServer(true);

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i, true);
        }

        jmsOperations.close();

        container(2).stop();
    }

    protected void prepareHAEap7() {
        container(1).start();

        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.addHAPolicyReplicationMaster(true, "my-cluster", "group-0");

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i, true);
        }

        jmsOperations.close();

        container(1).stop();
        container(2).start();
        jmsOperations = container(2).getJmsOperations();
        jmsOperations.addHAPolicyReplicationSlave(true, "my-cluster", 0, "group-0", 10, true, false, null, null, null, null);

        for (int i = 0; i < QUEUE_NUMBER; i++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + i, QUEUE_JNDI_PREFIX + i, true);
        }

        jmsOperations.close();

        container(2).stop();
    }

    protected Context getContext(Container container) throws NamingException {

        Context context;

        if (ContainerUtils.isEAP6(container)) {
            logger.info("Create EAP 6 InitialContext to hostname: " + container.getHostname() + " and port: " + container.getJNDIPort());
            context = JMSTools.getEAP6Context(container.getHostname(), container.getJNDIPort(), Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
        } else {
            logger.info("Create EAP 7 InitialContext to hostname: " + container.getHostname() + " and port: " + container.getJNDIPort());
            context = JMSTools.getEAP7Context(container.getHostname(), container.getJNDIPort(), Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
        }

        return context;
    }

}
