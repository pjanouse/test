package org.jboss.qa.hornetq.test.jmx;

import org.hornetq.api.core.management.ObjectNameBuilder;
import org.hornetq.api.jms.management.JMSQueueControl;
import org.hornetq.api.jms.management.JMSServerControl;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
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
import javax.naming.NamingException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test case for checking that JMX notifications are delivered correctly.
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmxClientNotificationTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(JmxClientNotificationTestCase.class);

    /**
     * Test that there are proper JMX notifications sent on consumer connect/disconnect.
     *
     * @throws Exception
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
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getHornetQServerObjectName(), coreListener, null,
                    null);
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getJMSServerObjectName(), jmsListener, null, null);

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
     * @throws Exception
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
            JMSServerControl jmsServerControl = container(1).getJmxUtils().getJmsServerMBean(mbeanServer, JMSServerControl.class);

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, 1, 1, 1, 1);
            clients.setMessages(100000);
            clients.startClients();

            ClientUtils.waitForReceiversUntil(clients.getConsumers(), 20, 60000);

            boolean result = false;

            try {
                result = jmsServerControl.destroyQueue(PrepareConstants.QUEUE_NAME_PREFIX + "0");
                Assert.fail("Calling destroyQueue must throw exception when jms org.jboss.qa.hornetq.apps.clients are connected.");
            } catch (Exception ex) {
                // this is expected
            }

            Assert.assertFalse("Calling destroy queue with connected org.jboss.qa.hornetq.apps.clients must fail.", result);
            clients.stopClients();

            while (!clients.isFinished()) {
                Thread.sleep(1000);
            }

            // check that new org.jboss.qa.hornetq.apps.clients can be connected
            // send and receive single message
            Clients clients2 = new QueueClientsAutoAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, 1, 1, 1, 1);
            clients2.setMessages(100000);
            clients2.startClients();

            ClientUtils.waitForReceiversUntil(clients2.getConsumers(), 20, 60000);

            clients2.stopClients();

            while (!clients2.isFinished()) {
                Thread.sleep(1000);
            }

            Assert.assertTrue(clients.evaluateResults());
            Assert.assertTrue(clients2.evaluateResults());
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        container(1).stop();

    }

    /**
     * @throws Exception
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
            connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            JMSServerControl jmsServerControl = container(1).getJmxUtils().getJmsServerMBean(mbeanServer, JMSServerControl.class);

            boolean result = jmsServerControl.destroyQueue(PrepareConstants.QUEUE_NAME);
            Assert.assertTrue("Calling destroy queue with connected org.jboss.qa.hornetq.apps.clients must pass.", result);
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 30);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        Assert.assertTrue("Producer must get exception.", producerToInQueue1.getException() != null);

        // check that queue was destroyed
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
     * @throws Exception
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
            JMSServerControl jmsServerControl = container(1).getJmxUtils().getJmsServerMBean(mbeanServer, JMSServerControl.class);

            // send and receive single message
            Clients clients = new TopicClientsAutoAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, 1, 1, 1, 1);
            clients.setMessages(100000);
            clients.startClients();

            ClientUtils.waitForReceiversUntil(clients.getConsumers(), 20, 60000);

            boolean result = false;
            try {
                result = jmsServerControl.destroyTopic(PrepareConstants.TOPIC_NAME_PREFIX + "0");
                Assert.fail("Calling destroyTopic must throw exception when jms org.jboss.qa.hornetq.apps.clients are connected.");
            } catch (Exception ex) {
                // this is expected
            }

            Assert.assertFalse("Calling destroy topic with connected org.jboss.qa.hornetq.apps.clients must fail.", result);

            clients.stopClients();
            while (!clients.isFinished()) {
                Thread.sleep(1000);
            }

            // check that new org.jboss.qa.hornetq.apps.clients can be connected
            // send and receive single message
            Clients clients2 = new TopicClientsAutoAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, 1, 1, 1, 1);
            clients2.setMessages(100000);
            clients2.startClients();

            ClientUtils.waitForReceiversUntil(clients2.getConsumers(), 20, 60000);

            clients2.stopClients();
            while (!clients2.isFinished()) {
                Thread.sleep(1000);
            }

            Assert.assertTrue(clients.evaluateResults());
            Assert.assertTrue(clients2.evaluateResults());
        } finally {
            if (connector != null) {
                connector.close();
            }
        }

        container(1).stop();
    }

    /**
     * @throws Exception
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
            JMSServerControl jmsServerControl = container(1).getJmxUtils().getJmsServerMBean(mbeanServer, JMSServerControl.class);

            boolean result = jmsServerControl.destroyTopic(PrepareConstants.TOPIC_NAME);
            Assert.assertTrue("Calling destroy topic with connected org.jboss.qa.hornetq.apps.clients must pass.", result);
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

        container(1).stop();

        Assert.assertTrue("Producer must get exception.", publisher.getException() != null);
    }

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

            JMSQueueControl jmsQueueControl = (JMSQueueControl) container(1).getJmxUtils().getHornetQMBean(mbeanServer,
                    ObjectNameBuilder.DEFAULT.getJMSQueueObjectName(PrepareConstants.QUEUE_NAME), JMSQueueControl.class);
            try {
                jmsQueueControl.addJNDI("newName");
                jmsQueueControl.addJNDI("newName");
                Assert.fail("Creating already existing queue must throw exception.");
            } catch (NamingException ex) {
                // this is expected
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
