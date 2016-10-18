package org.jboss.qa.artemis.test.clients.clients;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;

import javax.jms.*;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * These are tests covering the RFEs EAP6-331 and EAP6-332.
 * <p/>
 * Point of these RFEs is adding new management operations to allow
 * forced client disconnection either by destination address or
 * by user name.
 * 
 * Abstract class used by CloseConnectionsForAddresTestCase and CloseConnectionForUserTestcase
 */
public abstract class AbstractClientCloseTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(AbstractClientCloseTestCase.class);

    private static final String QUEUE_NAME = "TestQueue";

    private static final String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;

    private static final int NUMBER_OF_MESSAGES = 3000;

    private static final Map<String, String> USER_PASSWORD = new HashMap<String, String>(3);

    static {
        USER_PASSWORD.put("guest", "guest");
        USER_PASSWORD.put("user", "useruser");
        USER_PASSWORD.put("admin", "adminadmin");
    }

    public void clientForcedDisconnectTest(ClientCloser closer) throws Exception {
        clientForcedDisconnectTest(false, "guest", closer);
    }

    public void clientForcedDisconnectTest(boolean secured, String username, ClientCloser closer) throws Exception {
        prepareServer(secured);

        // add notification listener to server control mbean
        // we will later use it to check if there was proper disconnection notification on the JMX server
        JmxNotificationListener notificationListener = container(1).createJmxNotificationListener();
        JMXConnector jmxConnector = null;

        Context ctx = null;
        Connection jmsConnection = null;

        try {
            LOG.info("Connecting to JMX server");
            jmxConnector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
            MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();

            LOG.info("Attaching notification listener to JMX server");

            connection.addNotificationListener(ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(),
                    "default", true).getActiveMQServerObjectName(),
                    notificationListener, null, null);

            LOG.info("Setting up error listener for JMS org.jboss.qa.hornetq.apps.clients");
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            jmsConnection = cf.createConnection(username, USER_PASSWORD.get(username));

            final CountDownLatch connectionClosed = new CountDownLatch(1);
            jmsConnection.setExceptionListener(new ExceptionListener() {
                @Override
                public void onException(JMSException e) {
                    connectionClosed.countDown();
                }
            });
            jmsConnection.start();

            LOG.info("Starting producer and consumer org.jboss.qa.hornetq.apps.clients");
            TestClients clients = new TestClients();
            clients.sendAndReceiveMessages(jmsConnection, QUEUE_NAME);

            LOG.info("Giving org.jboss.qa.hornetq.apps.clients a little bit to process messages");
            Thread.sleep(20000);

            LOG.info("Force closing the org.jboss.qa.hornetq.apps.clients");
            RuleInstaller.installRule(this.getClass());

            boolean closeOperationSucceeded = closer.closeClients();
            clients.ensureClientThreadsClosed();

            // check the org.jboss.qa.hornetq.apps.clients got disconnected
            assertTrue("Clients should be properly disconnected", connectionClosed.await(500, TimeUnit.MILLISECONDS));
            assertTrue("Operation should return true on successful client close", closeOperationSucceeded);

            // wait a little bit to make sure JMX notifications get delivered
            notificationListener.waitForNotificationsCount(2, 60000);


            // check JMX got proper notifications about client disconnection
            // there should be 2: 1 for client connect and 1 (more interesting in this case) for client disconnect
            List<Notification> notifications = notificationListener.getCaughtNotifications();
            // there might be other notifications if the reconnect-attempts were set to -1 or positive number
            LOG.info("Notification count: "+ notifications.size());

            assertEquals("There should be notification for consumer creation", "CONSUMER_CREATED",
                    notifications.get(0).getType());
            assertEquals("There should be notification for consumer destruction", "CONSUMER_CLOSED",
                    notifications.get(1).getType());
            assertTrue("There should be at least 2 notifications", notifications.size() >= 2);

            container(1).stop();
        } finally {
            if (jmxConnector != null) {
                jmxConnector.close();
            }
            if (jmsConnection != null) {
                jmsConnection.stop();
                jmsConnection.close();
            }
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void prepareServer(boolean secured) throws Exception {
        if (ContainerUtils.isEAP6(container(1)))    {
            prepareServerEAP6(secured);
        } else {
            prepareServerEAP7(secured);
        }
    }

    private void prepareServerEAP6(boolean secured) throws Exception {
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        // enable JMX on hornetq for notifications (and potentially calling the management operation too)
        ops.setJmxManagementEnabled(true);

        // we have to restart server for JMX to activate after config change
        container(1).stop();
        container(1).start();

        // disable clustering
        ops.setClustered(false);
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 8192);
        ops.setReconnectAttemptsForConnectionFactory("RemoteConnectionFactory", 0);

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.close();

        if (secured) {
            createUsersAndRoles();
        }

        container(1).stop();
        container(1).start();
    }

    private void prepareServerEAP7(boolean secured) throws Exception {
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        // enable JMX on hornetq for notifications (and potentially calling the management operation too)
        ops.setJmxManagementEnabled(true);

        // we have to restart server for JMX to activate after config change
        container(1).stop();
        container(1).start();

        // disable clustering
        ops.removeClusteringGroup("my-cluster");
        ops.removeBroadcastGroup("bg-group1");
        ops.removeDiscoveryGroup("dg-group1");
        ops.setNodeIdentifier(987654);

        // lower the paging threshold to force server into paging mode
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 10 * 1024, 1000, 1000, 8192);
        ops.setReconnectAttemptsForConnectionFactory("RemoteConnectionFactory", 0);

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        ops.close();

        if (secured) {
            createUsersAndRoles();
        }

        container(1).stop();
        container(1).start();
    }

    private void createUsersAndRoles() throws Exception {

        UsersSettings.forDefaultEapServer()
                .withUser("guest", null, "guest")
                .withUser("user", "useruser", "users")
                .withUser("admin", "adminadmin", "admins")
                .create();
    }

    private static class TestClients {

        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        private Future<Void> producer;
        private Future<Void> consumer;

        public void sendAndReceiveMessages(Connection connection, String queueName)
                throws ExecutionException, InterruptedException {

            producer = executor.submit(new TestProducer(connection, queueName));
            consumer = executor.submit(new TestConsumer(connection, queueName));
        }

        public void ensureClientThreadsClosed() {
            try {
                producer.get();
            } catch (Exception e) {
                LOG.info("Exception on producer thread", e);
            }

            try {
                consumer.get();
            } catch (Exception e) {
                LOG.info("Exception on consumer thread", e);
            }
        }

    }


    private static class TestProducer implements Callable<Void> {

        private final MessageBuilder msgBuilder = new DelayedTextMessageBuilder(2 * 1024, 10000);

        private final Connection connection;

        private final String queueName;

        public TestProducer(Connection connection, String queueName) {
            this.connection = connection;
            this.queueName = queueName;
        }

        @Override
        public Void call() throws Exception {
            Session session = null;
            try {
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue q = session.createQueue(queueName);

                MessageProducer producer = session.createProducer(q);
                for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                    Message msg = msgBuilder.createMessage(new MessageCreator10(session), ArtemisJMSImplementation.getInstance());
                    producer.send(msg);
                    if (i % 10 == 0) {
                        LOG.info("Sent message with counter " + i);
                    }
                }

                return null;
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }
    }


    private static class TestConsumer implements Callable<Void> {

        private final Connection connection;

        private final String queueName;

        private int counter = 0;

        public TestConsumer(Connection connection, String queueName) {
            this.connection = connection;
            this.queueName = queueName;
        }


        @Override
        public Void call() throws Exception {
            Session session = null;
            try {
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue q = session.createQueue(queueName);
                connection.start();

                MessageConsumer consumer = session.createConsumer(q);
                try {
                    while (consumer.receive(30000) != null) {
                        if ((++counter) % 10 == 0) {
                            LOG.info("Read message with counter " + counter++);
                        }
                    }
                } catch (JMSException e) {
                    LOG.info("JMSException on server force disconnect caught", e);
                }

                return null;
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }
    }

}
