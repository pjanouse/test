package org.jboss.qa.hornetq.test.jmx;

import javax.management.ObjectName;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.jms.management.JMSQueueControl;
import org.apache.activemq.artemis.api.jms.management.JMSServerControl;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.remote.JMXConnector;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test case for checking that JMX notifications are delivered correctly.
 * @tpChapter  Functional testing
 * @tpSubChapter JMX MANAGEMENT BEANS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-tests
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */

@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmxClientNotificationTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(JmxClientNotificationTestCase.class);

    @Before
    public void startServerBeforeTest() {
        container(1).start();
    }

    @After
    public void stopServerAfterTest() {
        container(1).stop();
    }

    /**
     *
     * @tpTestDetails Test that there are proper JMX notifications sent on consumer connect/disconnect.
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>deploy queue on server</li>
     *     <li>create and register JmxNotificationListener jmsListener </li>
     *     <li>create and register JmxNotificationListener coreListener </li>
     *     <li>start client and sed and receive message</li>
     *     <li>stop client</li>
     *     <li>check number of notifications on listeners</li>
     * </ul>
     * @tpPassCrit 0 notifications on jmsListener, 1 notification "CONSUMER_CREATED" and 1 notification "CONSUMER_CLOSED"
     * on coreListener
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testConsumerConnectDisconnectNotifications() throws Exception {
        String queueName = "testQueue";
        String queueJndiName = "jms/queue/" + queueName;
        JMSOperations ops = container(1).getJmsOperations();
        ops.setJmxManagementEnabled(true);
        ops.createQueue(queueName + "0", queueJndiName + "0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        container(1).restart();

        // connect notification listeners to HornetQ MBean server
        JmxNotificationListener jmsListener = container(1).createJmxNotificationListener();
        JmxNotificationListener coreListener = container(1).createJmxNotificationListener();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), coreListener, null,
                    null);
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getJMSServerObjectName(), jmsListener, null, null);

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(container(1), queueJndiName, 1, 1, 1, 1);
            clients.setMessages(1);
            clients.startClients();
            while (!clients.isFinished()) {
                Thread.sleep(1000);
            }
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        List<Notification> coreNotifications = coreListener.getCaughtNotifications();
        List<Notification> jmsNotifications = jmsListener.getCaughtNotifications();

        LOG.info("Number of received Core notifications: " + coreNotifications.size());
        for (Notification n : coreNotifications) {
            LOG.info("Core notification (type " + n.getType() + "): " + n.getMessage());
        }

        LOG.info("Number of received JMS notifications: " + jmsNotifications.size());
        for (Notification n : jmsNotifications) {
            LOG.info("JMS notification (type " + n.getType() + "): " + n.getMessage());
        }

        assertEquals("There should be Core notifications with client connections info", 2, coreNotifications.size());
        assertEquals("There should be notification for consumer creation", "CONSUMER_CREATED", coreNotifications.get(0)
                .getType());
        assertEquals("There should be notification for consumer destruction", "CONSUMER_CLOSED", coreNotifications.get(1)
                .getType());
        assertEquals("There should be no JMS notifications", 0, jmsNotifications.size());
    }

    /**
     *
     * @tpTestDetails Test that queue can not be destroyed if there is connected client.
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>deploy queue on server</li>
     *     <li>connect clients and start sending and receiving messages from deployed queue</li>
     *     <li>try to destroy the queue</li>
     * </ul>
     * @tpPassCrit queue destruction command should work and kick the clients out of the server.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDestroyQueueWithConnectedClients() throws Exception {

        String queueName = "testQueue";
        String queueJndiName = "jms/queue/" + queueName;
        JMSOperations ops = container(1).getJmsOperations();
        ops.setJmxManagementEnabled(true);
        ops.createQueue(queueName + "0", queueJndiName + "0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        container(1).restart();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName queueControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-queue=" + queueName + "0");

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(container(1), queueJndiName, 1, 1, 1, 1000);
            clients.setMessages(100000);
            clients.startClients();

            ClientUtils.waitForReceiversUntil(clients.getConsumers(), 20, 60000);

            try {
                LOG.info("Removing the test queue");
                mbeanServer.invoke(queueControllerName, "remove", null, null);
                // unlike HornetQ MBeans, calling remove should kick the clients our of the server
                // (HornetQ MBean returned exception when trying to destroy queue with connected clients)
            } catch (Exception ex) {
                // this is expected
            }

            // properly end the client threads
            clients.stopClients();
            while (!clients.isFinished()) {
                Thread.sleep(1000);
            }

            // test that the queue was really destroyed by the JMX operation
            ops = container(1).getJmsOperations();
            try {
                ops.getCountOfMessagesOnQueue(queueName + "0");
                Assert.fail("The queue was supposed to be destroyed by the JMX operation destroyQueue()");
            } catch (Exception ex) {
                // this is expected
                LOG.info("The queue was successfully destroyed.");
            }
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        container(1).stop();

    }

    /**
     *
     * @tpTestDetails Test that queue can be destroyed when no consumers are connected and after destruction queue cant be
     * accessed nor by client nor by jms
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>deploy queue on server</li>
     *     <li>destroy queue</li>
     *     <li>try to send messages to queue</li>
     *     <li>try to invoke get count of messages on queue</>
     * </ul>
     * @tpPassCrit sending messages should fail and invoking operations on queue too
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDestroyQueueWithoutConnectedClients() throws Exception {
        String queueName = "testQueue";
        String queueJndiName = "jms/queue/" + queueName;
        JMSOperations ops = container(1).getJmsOperations();
        ops.setJmxManagementEnabled(true);
        ops.createQueue(queueName + "0", queueJndiName + "0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        container(1).restart();

        JMXConnector connector = null;
        try {
            LOG.info("Connecting to JMX management");
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));

            LOG.info("Getting queue controller from JMX");
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName queueControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-queue=" + queueName + "0");

            LOG.info("Destroying queue " + queueName + "0 via JMX");
            // remove operation returns null
            mbeanServer.invoke(queueControllerName, "remove", null, null);
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        // test that the queue was really destroyed by the JMX operation
        ops = container(1).getJmsOperations();
        try {
            ops.getCountOfMessagesOnQueue(queueName + "0");
            Assert.fail("The queue was supposed to be destroyed by the JMX operation destroyQueue()");
        } catch (Exception ex) {
            // this is expected
            LOG.info("The queue was successfully destroyed.");
        }

        //Thread.sleep(600000);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), queueJndiName + "0", 30);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        Assert.assertTrue("Producer must get exception.", producerToInQueue1.getException() != null);

        // check that the queue was not automatically created by the broker when we connected producer
        JMSOperations jmsOperations = container(1).getJmsOperations();
        try {
            jmsOperations.getCountOfMessagesOnQueue(queueName + "0");
            Assert.fail("Operation count messages must fail because queue was destroyed.");
        } catch (Exception ex) {
            // this is expected
        }

        jmsOperations.close();
        container(1).stop();
    }

    /**
     *
     * @tpTestDetails Test that topic can not be destroyed if there is connected client.
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>deploy topic on server</li>
     *     <li>connect clients and start sending and receiving messages from deployed topic</li>
     *     <li>try to destroy the topic</li>
     * </ul>
     * @tpPassCrit topic destruction command should fail and throw exception
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDestroyTopicWithConnectedClients() throws Exception {
        String topicName = "testTopic";
        String topicJndiName = "jms/topic/" + topicName;
        JMSOperations ops = container(1).getJmsOperations();
        ops.setJmxManagementEnabled(true);
        ops.createTopic(topicName + "0", topicJndiName + "0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        container(1).restart();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName topicControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-topic=" + topicName + "0");

            // send and receive single message
            Clients clients = new TopicClientsAutoAck(container(1), topicJndiName, 1, 1, 1, 1);
            clients.setMessages(100000);
            clients.startClients();

            ClientUtils.waitForReceiversUntil(clients.getConsumers(), 20, 60000);

            try {
                LOG.info("Removing the test topic");
                mbeanServer.invoke(topicControllerName, "remove", null, null);
                // unlike HornetQ MBeans, calling remove should kick the clients our of the server
                // (HornetQ MBean returned exception when trying to destroy topic with connected clients)
            } catch (Exception ex) {
                // this is expected
            }

            // properly end the client threads
            clients.stopClients();
            while (!clients.isFinished()) {
                Thread.sleep(1000);
            }

            // test that the queue was really destroyed by the JMX operation
            ops = container(1).getJmsOperations();
            try {
                ops.removeTpicJNDIName(topicName, topicJndiName);
                Assert.fail("The topic was supposed to be destroyed by the JMX operation");
            } catch (Exception ex) {
                // this is expected
                LOG.info("The queue was successfully destroyed.");
            }
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        container(1).stop();
    }

    /**
     *
     * @tpTestDetails Test that topic can be destroyed when no consumers are connected and after destruction queue cant be
     * accessed nor by client nor by jms
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>deploy topic on server</li>
     *     <li>destroy topic</li>
     *     <li>try to send messages to topic</li>
     *     <li>try to invoke get count of messages on topic</>
     * </ul>
     * @tpPassCrit sending messages should fail and invoking operations on topic too
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDestroyTopicWithoutConnectedClients() throws Exception {
        String topicName = "testTopic";
        String topicJndiName = "jms/topic/" + topicName;
        JMSOperations ops = container(1).getJmsOperations();
        ops.setJmxManagementEnabled(true);
        ops.createTopic(topicName + "0", topicJndiName + "0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        container(1).restart();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName topicControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-topic=" + topicName + "0");

            mbeanServer.invoke(topicControllerName, "remove", null, null);
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        PublisherTransAck publisher = new PublisherTransAck(container(1).getHostname(), container(1).getJNDIPort(),
                topicJndiName + "0", 30, "publisher");
        publisher.setCommitAfter(10);
        publisher.setTimeout(0);
        publisher.start();
        publisher.join();

        Assert.assertTrue("Producer must get exception.", publisher.getException() != null);

        // check that queue was destroyed
        JMSOperations jmsOperations = container(1).getJmsOperations();
        try {
            jmsOperations.removeTpicJNDIName(topicName, topicJndiName);
            Assert.fail("Operation getJNDIEntriesForTopic must fail because topic was destroyed.");
        } catch (Exception ex) {
            // this is expected
        }

        jmsOperations.close();
        container(1).stop();
    }


    /**
     *
     * @tpTestDetails Test that we cant define same JNDI name for queue twice
     * @tpProcedure <ul>
     *     <li>start 1 server</li>
     *     <li>create new queue via jmx</li>
     *     <li>restart server</li>
     *     <li>try set new  JNDI name for queue twice (same for each attempt)</li>
     * </ul>
     * @tpPassCrit second attempt should fail
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Ignore
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDuplicateJNDIName() throws Exception {
//        String queueName = "myTestQueue";
//        String queueJndiName = "jms/queue/" + queueName;
//
//        JMSOperations ops = container(1).getJmsOperations();
//        ops.setJmxManagementEnabled(true);
//        ops.createQueue(queueJndiName, queueName, true);
//        ops.close();
//
//        // we have to restart server for JMX to activate after config change
//        container(1).restart();
//
//        JMXConnector connector = null;
//        try {
//            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
//            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
//
//            JMSQueueControl jmsQueueControl = (JMSQueueControl) container(1).getJmxUtils().getHornetQMBean(mbeanServer,
//                    ObjectNameBuilder.DEFAULT.getJMSQueueObjectName(queueJndiName), JMSQueueControl.class);
//            try {
//                jmsQueueControl.addJNDI("newName");
//                jmsQueueControl.addJNDI("newName");
//                Assert.fail("Creating already existing queue must throw exception.");
//            } catch (NamingException ex) {
//                // this is expected
//            } catch (Exception e) {
//                e.printStackTrace();
//                Assert.fail("Unexpected exception during test was thrown.");
//            }
//        } finally {
//            if (connector != null) {
//                connector.close();
//            }
//        }
//
//        container(1).stop();
    }

}
