package org.jboss.qa.hornetq.test.paging;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ColoredMessagesBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;

/**
 * Created by mnovak on 12/2/14.
 */
public class FilteringQueueTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FilteringQueueTestCase.class);


    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSimpleFilterClient() throws Exception {

        int numberOfMessages = 100;
        int counter = 0;

        prepareJmsServer(CONTAINER1);

        controller.start(CONTAINER1);

        ProducerTransAck producer1 = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueue, 100);
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
            context = getContext(CONTAINER1);
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI_EAP6);
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

        stopServer(CONTAINER1);

        Assert.assertEquals("There must be half of the send messages.", numberOfMessages / 2, counter);

    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSimpleFilterClientWith2Consumers() throws Exception {

        int numberOfMessages = 100;
        int counter = 0;
        int counter2 = 0;

        prepareJmsServer(CONTAINER1);

        controller.start(CONTAINER1);

        ProducerTransAck producer1 = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inQueue, 100);
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
            context = getContext(CONTAINER1);
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI_EAP6);
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

        stopServer(CONTAINER1);

        Assert.assertEquals("There must be half of the send messages.", numberOfMessages / 2, counter);

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String containerName) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 100 * 1024);
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

        controller.stop(containerName);

    }
}
