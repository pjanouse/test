package org.jboss.qa.hornetq.test.jmx;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.naming.Context;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails One server with deployed queue is started. Some messages
 * are send to queue and correct number of delivering/scheduled messages is
 * checked.
 *
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class RuntimeQueueOperationsTestCase extends HornetQTestCase {

    private static final String QUEUE_NAME = "testQueue";
    private static final String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;

    @After
    public void stopServerAfterTest() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 100
     * messages to queue. Once producer finishes, create transacted session
     * which uses local transactions, create receiver and start receiving
     * messages. Commit transaction after 50 received messages and check number
     * of delivering messages
     *
     * @tpProcedure <ul>
     * <li>Start one server with deployed queue</li>
     * <li>Create producer and send messages to queue</li>
     * <li>Wait for producer finish</li>
     * <li>Create transacted session (local transactions)</li>
     * <li>Create receiver and start receiving messages</li>
     * <li>After some messages received, commit transaction</li>
     * <li>Check number of delivering messages</li>
     * </ul>
     *
     * @tpPassCrit Correct number of delivering messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listDeliveringMessagesTestCase() throws Exception {
        Context context = null;
        Connection conn = null;
        Session session = null;

        int numberOfMessages = 100;
        int commitAfter = 50;

        container(1).start();

        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.close();

        container(1).restart();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_NAME, numberOfMessages);
        producer.start();
        producer.join();

        try {
            context = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            conn = cf.createConnection();
            conn.start();

            Queue queue = (Queue) context.lookup(QUEUE_JNDI_NAME);
            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiverTransAck = session.createConsumer(queue);

            int counter = 0;
            int listDeliveringMessagesSize = -1;
            while (receiverTransAck.receive(500) != null) {
                counter++;
                if (counter % commitAfter == 0) {
                    session.commit();
                }
                if (counter == commitAfter) {
                    listDeliveringMessagesSize = getListDeliveringMessagesSize(QUEUE_NAME);
                }
            }
            receiverTransAck.close();
            session.commit();
            Assert.assertEquals("Number of delivering messages does not match", commitAfter, listDeliveringMessagesSize);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);
        } finally {
            JMSTools.cleanupResources(context, conn, session);
        }

        container(1).stop();
    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 10
     * messages to queue. Once producer finishes, check number of scheduled
     * messages.
     *
     * @tpProcedure
     * <ul>
     * <li>start one server with deployed queue</li>
     * <li>create producer and send messages to queue</li>
     * <li>Wait for producer finish</li>
     * <li>Check number of scheduled messages</li>
     * </ul>
     *
     * @tpPassCrit Correct number of scheduled messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listScheduledMessagesTestCase() throws Exception {
        int numberOfMessages = 10;

        container(1).start();

        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.close();

        container(1).restart();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_NAME, numberOfMessages);
        DelayedTextMessageBuilder delayedTextMessageBuilder = new DelayedTextMessageBuilder(512, 100000);
        producer.setMessageBuilder(delayedTextMessageBuilder);
        producer.start();
        producer.join();

        try {
            Assert.assertEquals("Number of delivering messages does not match", numberOfMessages,
                    getListScheduledMessagesSize(QUEUE_NAME));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);
        }
        container(1).stop();
    }

    public int getListScheduledMessagesSize(String queueName) throws Exception {
        JMXConnector connector = null;
        CompositeData[] resultMap = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = getObjectName(queueName);

            resultMap = (CompositeData[]) mbeanServer.invoke(objectName, "listScheduledMessages", new Object[]{},
                    new String[]{});
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        return resultMap.length;
    }

    private ObjectName getObjectName(String queueName) throws Exception {
        ObjectName objectName = null;
        if (ContainerUtils.isEAP6(container(1))) {
            objectName = new ObjectName("jboss.as:subsystem=messaging,hornetq-server=default,runtime-queue=jms.queue." + queueName);
        } else {
            objectName = new ObjectName("jboss.as:subsystem=messaging-activemq,server=default,runtime-queue=jms.queue." + queueName);
        }
        return objectName;
    }

    public int getListDeliveringMessagesSize(String queueName) throws Exception {
        JMXConnector connector = null;
        CompositeData[] elements = null;
        try {

            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = getObjectName(queueName);
            CompositeData[] resultMap = (CompositeData[]) mbeanServer.invoke(objectName, "listDeliveringMessages",
                    new Object[]{}, new String[]{});
            elements = (CompositeData[]) resultMap[0].get("elements");
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        return elements.length;
    }

}
