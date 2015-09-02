package org.jboss.qa.hornetq.test.paging;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.ColoredMessagesBuilder;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;

/**
 *
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Goal of this test case is testing of filtering messages which will consumer receive from queue.
 * @author mnovak@redhat.com
 */
public class FilteringQueueTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FilteringQueueTestCase.class);

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    @After
    public void stopAllServers() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 100
     * messages to queue. Messages contains string property. Once producer
     * finishes, create consumer with message selector to receive only messages
     * with specific string property. Start receiving messages. Check number of
     * received messages.
     *
     * @tpProcedure <ul>
     * <li>Start one server with deployed queue</li>
     * <li>Create producer and send messages to queue (messages have string
     * property set)</li>
     * <li>Wait for producer finish</li>
     * <li>Create consumer with message selector for message property and start
     * receiving messages</li>
     * <li>Check number of received messages</li>
     * </ul>
     *
     * @tpPassCrit Consumer received correct number of messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSimpleFilterClient() throws Exception {

        int numberOfMessages = 100;
        int counter = 0;

        prepareJmsServer(container(1));

        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueue, 100);
        MessageBuilder builder = new ColoredMessagesBuilder(30);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        Context context = null;
        Connection connection = null;
        Session session = null;

        try {
            context = container(1).getContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            connection = connectionFactory.createConnection();
            connection.start();
            Queue queue = (Queue) context.lookup(inQueue);
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(queue, "color = 'RED'");

            Message msg;

            while ((msg = consumer.receive(10000)) != null) {

                counter++;

                logger.warn("Receiver for queue: " + queue.getQueueName()
                        + ". Received message - count: "
                        + counter + ", message-counter: " + msg.getStringProperty("counter")
                        + ", messageId:" + msg.getJMSMessageID() + " - SEND COMMIT");

                session.commit();

            }

            session.commit();

            logger.error("Filter consumer ended - received NULL - number of received messages: " + counter);

        } finally {

            if (connection != null) {
                connection.close();
            }

            if (context != null) {
                context.close();
            }
        }

        container(1).stop();

        Assert.assertEquals("There must be half of the send messages.", numberOfMessages / 2, counter);

    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 100
     * messages to queue. Messages contains string property. Once producer
     * finishes, create two consumers with selectors to receive only messages
     * with specific string property. Start receiving messages. Check number of
     * received messages.
     *
     * @tpProcedure <ul>
     * <li>Start one server with deployed queue</li>
     * <li>Create producer and send messages to queue (messages have string
     * property set)</li>
     * <li>Wait for producer finish</li>
     * <li>Create consumers with message selectors for message property and start
     * receiving messages</li>
     * <li>Check number of received messages</li>
     * </ul>
     *
     * @tpPassCrit Consumer received correct number of messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSimpleFilterClientWith2Consumers() throws Exception {

        int numberOfMessages = 100;
        int counter = 0;
        int counter2 = 0;

        prepareJmsServer(container(1));

        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueue, 100);
        MessageBuilder builder = new ColoredMessagesBuilder(30);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        Context context = null;
        Connection connection = null;
        Session session;

        try {
            context = container(1).getContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            connection = connectionFactory.createConnection();
            connection.start();
            Queue queue = (Queue) context.lookup(inQueue);
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageConsumer consumer = session.createConsumer(queue, "color = 'RED'");
            MessageConsumer consumer2 = session.createConsumer(queue, "color = 'GREEN'");

            Message msg;
            Message msg2 = null;

            while ((msg = consumer.receive(10000)) != null | (msg2 = consumer2.receive(10000)) != null) {

                if (msg != null) {
                    counter++;
                    logger.warn("Receiver for queue: " + queue.getQueueName()
                            + ". Received message - count: "
                            + counter + ", message-counter: " + msg.getStringProperty("counter")
                            + ", messageId:" + msg.getJMSMessageID() + " - SEND COMMIT");
                }
                if (msg2 != null) {
                    counter2++;
                    logger.warn("Receiver2 for queue: " + queue.getQueueName()
                            + ". Received message - count: "
                            + counter + ", message-counter: " + msg.getStringProperty("counter")
                            + ", messageId:" + msg.getJMSMessageID() + " - SEND COMMIT");
                }

                session.commit();

                msg = null;
                msg2 = null;
            }

            session.commit();

            logger.error("Filter consumer ended - received NULL - number of received messages: " + counter);

        } finally {

            if (connection != null) {
                connection.close();
            }

            if (context != null) {
                context.close();
            }
        }

        container(1).stop();

        Assert.assertEquals("There must be half of the send messages.", numberOfMessages / 2, counter);

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setClustered(false);
            jmsAdminOperations.setSharedStore(true);
        }
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 10 * 1024 * 1024, 0, 0, 100 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.setNodeIdentifier(1234567);

        try {
            jmsAdminOperations.removeQueue(inQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);

        try {
            jmsAdminOperations.removeQueue(outQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);
        jmsAdminOperations.close();
        container.stop();
    }
}
