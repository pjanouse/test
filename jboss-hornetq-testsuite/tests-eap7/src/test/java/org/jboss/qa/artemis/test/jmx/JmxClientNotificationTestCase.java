package org.jboss.qa.artemis.test.jmx;

import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testConsumerConnectDisconnectNotifications() throws Exception {

        container(1).start();

        // connect notification listeners to HornetQ MBean server
        JmxNotificationListener jmsListener = container(1).createJmxNotificationListener();
        JmxNotificationListener coreListener = container(1).createJmxNotificationListener();

        JMXConnector connector = null;
        try {
            JmxUtils jmxUtils = container(1).getJmxUtils();
            ObjectNameBuilder objectNameBuilder = jmxUtils.getObjectNameBuilder(ObjectNameBuilder.class);
            connector = jmxUtils.getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(objectNameBuilder.getActiveMQServerObjectName(), coreListener, null,
                    null);
            mbeanServer.addNotificationListener(objectNameBuilder.getJMSServerObjectName(), jmsListener, null, null);

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, 1, 1, 1, 1);
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testDestroyQueueWithConnectedClients() throws Exception {

        container(1).start();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName queueControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-queue=" + PrepareConstants.QUEUE_NAME_PREFIX + "0");

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, 1, 1, 1, 1000);
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
            JMSOperations ops = container(1).getJmsOperations();
            try {
                ops.getCountOfMessagesOnQueue(PrepareConstants.QUEUE_NAME_PREFIX + "0");
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testDestroyQueueWithoutConnectedClients() throws Exception {

        container(1).start();

        JMXConnector connector = null;
        try {
            LOG.info("Connecting to JMX management");
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));

            LOG.info("Getting queue controller from JMX");
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName queueControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-queue=" + PrepareConstants.QUEUE_NAME);

            LOG.info("Destroying queue " + PrepareConstants.QUEUE_NAME + " via JMX");
            // remove operation returns null
            mbeanServer.invoke(queueControllerName, "remove", null, null);
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        // test that the queue was really destroyed by the JMX operation
        JMSOperations ops = container(1).getJmsOperations();
        try {
            ops.getCountOfMessagesOnQueue(PrepareConstants.QUEUE_NAME);
            Assert.fail("The queue was supposed to be destroyed by the JMX operation destroyQueue()");
        } catch (Exception ex) {
            // this is expected
            LOG.info("The queue was successfully destroyed.");
        }

        //Thread.sleep(600000);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 30);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        Assert.assertTrue("Producer must get exception.", producerToInQueue1.getException() != null);

        // check that the queue was not automatically created by the broker when we connected producer
        JMSOperations jmsOperations = container(1).getJmsOperations();
        try {
            jmsOperations.getCountOfMessagesOnQueue(PrepareConstants.QUEUE_NAME);
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testDestroyTopicWithConnectedClients() throws Exception {

        container(1).start();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName topicControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-topic=" + PrepareConstants.TOPIC_NAME_PREFIX + "0");

            // send and receive single message
            Clients clients = new TopicClientsAutoAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, 1, 1, 1, 1);
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
            JMSOperations ops = container(1).getJmsOperations();
            try {
                ops.removeTpicJNDIName(PrepareConstants.TOPIC_NAME_PREFIX + "0", PrepareConstants.TOPIC_JNDI_PREFIX + "0");
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testDestroyTopicWithoutConnectedClients() throws Exception {

        container(1).start();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName topicControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-topic=" + PrepareConstants.TOPIC_NAME);

            mbeanServer.invoke(topicControllerName, "remove", null, null);
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        PublisherTransAck publisher = new PublisherTransAck(container(1), PrepareConstants.TOPIC_JNDI, 30, "publisher");
        publisher.setCommitAfter(10);
        publisher.setTimeout(0);
        publisher.start();
        publisher.join();

        Assert.assertTrue("Producer must get exception.", publisher.getException() != null);

        // check that queue was destroyed
        JMSOperations jmsOperations = container(1).getJmsOperations();
        try {
            jmsOperations.removeTpicJNDIName(PrepareConstants.TOPIC_NAME, PrepareConstants.TOPIC_JNDI);
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
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JMX_MANAGEMENT_ENABLED, value = "true")
    })
    public void testDuplicateJNDIName() throws Exception {

        container(1).start();

        JMXConnector connector = null;
        try {
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            ObjectName queueControllerName = ObjectName.getInstance(
                    "jboss.as:subsystem=messaging-activemq,server=default,jms-queue=" + PrepareConstants.QUEUE_NAME);

            try {
                mbeanServer.invoke(queueControllerName, "add-jndi",
                        new Object[]{ "newJndiName" },
                        new String[]{String.class.getName()});

                // trying to bind the same name for the 2nd time should throw ReflectionException
                mbeanServer.invoke(queueControllerName, "add-jndi",
                        new Object[]{ "newJndiName" },
                        new String[]{String.class.getName()});

                Assert.fail("Creating already existing JNDI name must throw exception.");
            } catch (ReflectionException ex) {
                // this is expected
                LOG.info("The server thrown ReflectionException when trying to bind the same JNDI name "
                        + "for the 2nd time");
            } catch (Exception e) {
                e.printStackTrace();
                Assert.fail("Unexpected exception during test was thrown.");
            }
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        container(1).stop();
    }

}
