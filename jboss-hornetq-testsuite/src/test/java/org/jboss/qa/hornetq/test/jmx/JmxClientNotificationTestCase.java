package org.jboss.qa.hornetq.test.jmx;


import org.apache.log4j.Logger;
import org.hornetq.api.core.management.ObjectNameBuilder;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
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
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmxClientNotificationTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(JmxClientNotificationTestCase.class);

    @Before
    public void startServerBeforeTest() {
        controller.start(CONTAINER1);
    }

    @After
    public void stopServerAfterTest() {
        stopServer(CONTAINER1);
    }

    /**
     * Test that there are proper JMX notifications sent on consumer connect/disconnect.
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testConsumerConnectDisconnectNotifications() throws Exception {

        String queueName = "testQueue";
        String queueJndiName = "jms/queue/" + queueName;
        JMSOperations ops = getJMSOperations();
        ops.setJmxManagementEnabled(true);
        ops.createQueue(queueName+"0", queueJndiName+"0");
        ops.close();

        // we have to restart server for JMX to activate after config change
        stopServer(CONTAINER1);
        controller.start(CONTAINER1);

        // connect notification listeners to HornetQ MBean server
        JmxNotificationListener jmsListener = new JmxNotificationListener();
        JmxNotificationListener coreListener = new JmxNotificationListener();

        JMXConnector connector = null;
        try {
            connector = JmxUtils.getJmxConnectorForEap(CONTAINER1_INFO);
            MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getHornetQServerObjectName(), coreListener,
                    null, null);
            mbeanServer.addNotificationListener(ObjectNameBuilder.DEFAULT.getJMSServerObjectName(), jmsListener,
                    null, null);

            // send and receive single message
            Clients clients = new QueueClientsAutoAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiName, 1, 1, 1, 1);
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
        assertEquals("There should be notification for consumer creation", "CONSUMER_CREATED",
                coreNotifications.get(0).getType());
        assertEquals("There should be notification for consumer destruction", "CONSUMER_CLOSED",
                coreNotifications.get(1).getType());
        assertEquals("There should be no JMS notifications", 0, jmsNotifications.size());
    }

}
