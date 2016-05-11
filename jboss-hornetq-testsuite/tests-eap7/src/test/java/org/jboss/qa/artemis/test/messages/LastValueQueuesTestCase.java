package org.jboss.qa.artemis.test.messages;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.*;

/**
 * Test for last value queue.
 *
 * @author Martin Styk &lt;mstyk@redhat.com&gt;
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails Test for last value queues. Test that only last message with configured property _AMQ_LVQ_NAME stays in last value queue.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class LastValueQueuesTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(LastValueQueuesTestCase.class);
    private String QUEUE_NAME = "testQueue";
    private String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;

    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with last value property configured. Consumer receives only
     * the last message
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue </li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last message was received</li>
     * </ul>
     * @tpPassCrit Only last message is received
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTest() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), false, 1);
    }


    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with _AMQ_LVQ_NAME configured to value A, and 10 messages
     * with _AMQ_LVQ_NAME valueB. Consumer receives only the last messages, one for each _AMQ_LVQ_NAME property
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueA </li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueB </li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last messages were received</li>
     * </ul>
     * @tpPassCrit Only last message is received for each _AMQ_LVQ_NAME property on queue
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestTwoValues() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP", "MY_PROP2"), false, 2);
    }


    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with _AMQ_LVQ_NAME configured to value A, and 10 messages
     * without _AMQ_LVQ_NAME property. Consumer receives only the last message with AMQ_LVQ_NAME and also all messages
     * without _AMQ_LVQ_NAME property
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueA </li>
     * <li>Send 10 messages to queue configured as a last value queue without property _AMQ_LVQ_NAME</li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last messages were received</li>
     * </ul>
     * @tpPassCrit Only last message is received for _AMQ_LVQ_NAME property on queue and also all messages without
     * this property set.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestAlsoWithoutLastValueMessages() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), true, 11);
    }

    private void testOnOneNode(Container container, List<String> lvqPropNames, boolean sendNonLvq, int expectedReceiveSize) throws Exception {
        prepareServer(container);

        container.start();

        Context context = container.getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
        Connection connection = cf.createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) context.lookup(QUEUE_JNDI_NAME);

        MessageProducer producer = session.createProducer(destination);

        connection.start();

        for (String lvqProperty : lvqPropNames) {
            for (int i = 0; i < 10; i++) {
                TextMessage message = session.createTextMessage(i + lvqProperty);
                message.setStringProperty("_AMQ_LVQ_NAME", lvqProperty);
                producer.send(message);
            }
        }
        if (sendNonLvq) {
            for (int i = 0; i < 10; i++) {
                TextMessage message = session.createTextMessage(i + "nonLvq");
                producer.send(message);
            }
        }

        Assert.assertEquals("Only " + expectedReceiveSize + " messages should be in queue", expectedReceiveSize, new JMSTools().countMessages(QUEUE_NAME, container(1)));

        MessageConsumer consumer = session.createConsumer(destination);

        List<Message> receivedMessages = new ArrayList<Message>();
        Message message;
        do {
            message = consumer.receive(5000);
            log.info("Received " + message);
            if (message != null) {
                receivedMessages.add(message);
            }
        } while (message != null);

        connection.close();
        container.stop();

        Assert.assertEquals("Consumer should receive only " + expectedReceiveSize + " message", expectedReceiveSize, receivedMessages.size());

        for (int i = 0; i < lvqPropNames.size(); i++) {
            Assert.assertEquals("Last send message should be received", "9" + lvqPropNames.get(i), ((TextMessage) receivedMessages.get(i)).getText());
        }

    }


    /**
     * @tpTestDetails Two servers are started in cluster and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with property _AMQ_LVQ_NAME configured to node1. Consumer receives only the last message
     * with AMQ_LVQ_NAME from node2.
     * @tpProcedure <ul>
     * <li>Start 2 servers in cluster and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set. Send to node1 </li>
     * <li>Receive messages from last value queue on node2</li>
     * <li>Check that only last message was received</li>
     * </ul>
     * @tpPassCrit Only last message is received for _AMQ_LVQ_NAME property.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueClusterTest() throws Exception {
        testInCluster(container(1), container(2), Arrays.asList("MY_PROP"), 1);
    }

    /**
     * @tpTestDetails Two servers are started in cluster and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with property _AMQ_LVQ_NAME configured to valueA to node1. Consumer receives only the last message
     * with AMQ_LVQ_NAME from node2.
     * @tpProcedure <ul>
     * <li>Start 2 servers in cluster and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueA. Send to node1 </li>
     * <li>Send 10 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueB. Send to node1 </li>
     * <li>Receive messages from last value queue from node2</li>
     * <li>Check that only last messages for both values of _AMQ_LVQ_NAME are received</li>
     * </ul>
     * @tpPassCrit Only last message is received for each _AMQ_LVQ_NAME property.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueClusterTestTwoValues() throws Exception {
        testInCluster(container(1), container(2), Arrays.asList("MY_PROP", "SECOND_PROP"), 2);
    }

    private void testInCluster(Container container1, Container container2, List<String> lvqPropNames, int expectedReceiveSize) throws Exception {
        prepareServerCluster(container1);
        prepareServerCluster(container2);

        container1.start();
        container2.start();

        Context context = container1.getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
        Connection connection = cf.createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) context.lookup(QUEUE_JNDI_NAME);

        MessageProducer producer = session.createProducer(destination);

        connection.start();

        Map<String, String> lastMessageId = new HashMap<String, String>(lvqPropNames.size());

        for (String lvqProperty : lvqPropNames) {
            for (int i = 0; i < 10; i++) {
                TextMessage message = session.createTextMessage(i + lvqProperty);
                message.setStringProperty("_AMQ_LVQ_NAME", lvqProperty);
                producer.send(message);
                lastMessageId.put(lvqProperty, message.getJMSMessageID());
            }
        }

        Assert.assertEquals("Only " + expectedReceiveSize + " messages should be in queues in cluster", expectedReceiveSize, new JMSTools().countMessages(QUEUE_NAME, container1, container2));

        connection.close();

        ReceiverAutoAck receiverAutoAck = new ReceiverAutoAck(container2, QUEUE_JNDI_NAME);
        receiverAutoAck.start();
        receiverAutoAck.join();

        container1.stop();
        container2.stop();

        receiverAutoAck.getListOfReceivedMessages();

        Assert.assertEquals("Consumer should receive only " + expectedReceiveSize + " message", expectedReceiveSize, receiverAutoAck.getCount());

        for (int i = 0; i < lvqPropNames.size(); i++) {
            Assert.assertTrue("Last send message should be received", receiverAutoAck.getListOfReceivedMessages().get(i).containsValue(lastMessageId.get(lvqPropNames.get(i))));
        }
    }


    private void prepareServerCluster(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        try {

            if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
                jmsAdminOperations.setClustered(true);

            }
            jmsAdminOperations.setPersistenceEnabled(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                    connectorName);

            jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
            jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, 30);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
            jmsAdminOperations.addAddressSettings("default", "jms.queue.testQueue", "PAGE", 1024 * 1024, 0, 0, 512 * 1024, true);

        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();
        }
    }

    private void prepareServer(Container container) {
        container.start();
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        jmsOperations.addAddressSettings("default", "jms.queue.testQueue", "PAGE", 1024 * 1024, 0, 0, 512 * 1024, true);
        jmsOperations.close();
        container.stop();
    }
}