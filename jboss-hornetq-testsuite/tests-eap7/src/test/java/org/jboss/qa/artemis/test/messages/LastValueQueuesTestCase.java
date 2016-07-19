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
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), false, 1, false);
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
        testOnOneNode(container(1), Arrays.asList("MY_PROP", "MY_PROP2"), false, 2, false);
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
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), true, 11, false);
    }


    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 large messages with last value property configured. Consumer receives only
     * the last message
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 large messages to queue configured as a last value queue </li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last  message was received</li>
     * </ul>
     * @tpPassCrit Only last message is received
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestLargeMessages() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), false, 1, true);
    }


    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 large messages with _AMQ_LVQ_NAME configured to value A, and 10 messages
     * with _AMQ_LVQ_NAME valueB. Consumer receives only the last messages, one for each _AMQ_LVQ_NAME property
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 large messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueA </li>
     * <li>Send 10 large messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueB </li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last messages were received</li>
     * </ul>
     * @tpPassCrit Only last message is received for each _AMQ_LVQ_NAME property on queue
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestTwoValuesLargeMessages() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP", "MY_PROP2"), false, 2, true);
    }


    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 large messages with _AMQ_LVQ_NAME configured to value A, and 10 messages
     * without _AMQ_LVQ_NAME property. Consumer receives only the last message with AMQ_LVQ_NAME and also all messages
     * without _AMQ_LVQ_NAME property
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 large messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to valueA </li>
     * <li>Send 10 large messages to queue configured as a last value queue without property _AMQ_LVQ_NAME</li>
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
    public void lastValueQueueTestAlsoWithoutLastValueMessagesLargeMessages() throws Exception {
        testOnOneNode(container(1), Arrays.asList("MY_PROP"), true, 11, true);
    }

    private void testOnOneNode(Container container, List<String> lvqPropNames, boolean sendNonLvq, int expectedReceiveSize, boolean isLargeMessage) throws Exception {
        String largeMessageSuffix = new String(new char[1024 * 120]);

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
                String text = isLargeMessage ? i + lvqProperty + largeMessageSuffix : i + lvqProperty;
                TextMessage message = session.createTextMessage(text);
                message.setStringProperty("_AMQ_LVQ_NAME", lvqProperty);
                producer.send(message);
            }
        }
        if (sendNonLvq) {
            for (int i = 0; i < 10; i++) {
                String text = isLargeMessage ? i + "nonLvq" + largeMessageSuffix : i + "nonLvq";
                TextMessage message = session.createTextMessage(text);
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

        JMSTools.cleanupResources(context, connection, session);

        Assert.assertEquals("Consumer should receive only " + expectedReceiveSize + " message", expectedReceiveSize, receivedMessages.size());

        for (int i = 0; i < lvqPropNames.size(); i++) {
            if (isLargeMessage) {
                Assert.assertEquals("Last send message should be received", "9" + lvqPropNames.get(i) + largeMessageSuffix, ((TextMessage) receivedMessages.get(i)).getText());
            } else {
                Assert.assertEquals("Last send message should be received", "9" + lvqPropNames.get(i), ((TextMessage) receivedMessages.get(i)).getText());
            }
        }

    }

    /**
     * Issue https://issues.jboss.org/browse/JBEAP-5196
     *
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue and thresholds are lowered to start paging soon. Producer sends 300 messages with _AMQ_LVQ_NAME
     * configured to different values (server is paging), and then it sends message with already configured _AMQ_LVQ_NAME.
     * Consumer receives only the last message for each _AMQ_LVQ_NAME.
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 300 messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to different values </li>
     * <li>Send 1 message to queue configured as a last value queue with already used property _AMQ_LVQ_NAME</li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last message for every _AMQ_LVQ_NAME was received</li>
     * </ul>
     * @tpPassCrit Only last message is received for each _AMQ_LVQ_NAME property.
     *
     * @ignore https://issues.jboss.org/browse/JBEAP-5196
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestWithPaging() throws Exception {
        internalLastValueQueueTestWithPaging(false);
    }

    /**
     * Issue https://issues.jboss.org/browse/JBEAP-5196
     *
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue and thresholds are lowered to start paging soon. Producer sends 300 large messages with _AMQ_LVQ_NAME
     * configured to different values (server is paging), and then it sends large message with already configured _AMQ_LVQ_NAME.
     * Consumer receives only the last message for each _AMQ_LVQ_NAME.
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 300 large messages to queue configured as a last value queue with property _AMQ_LVQ_NAME set to different values </li>
     * <li>Send 1 large message to queue configured as a last value queue with already used property _AMQ_LVQ_NAME</li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last message for every _AMQ_LVQ_NAME was received</li>
     * </ul>
     * @tpPassCrit Only last message is received for each _AMQ_LVQ_NAME property.
     *
     *
     * @ignore https://issues.jboss.org/browse/JBEAP-5196
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTestWithPagingLargeMessages() throws Exception {
        internalLastValueQueueTestWithPaging(true);
    }


    private void internalLastValueQueueTestWithPaging(boolean isLargeMessage) throws Exception {
        int numMessages = 300; //301 send, two with same AMQ_LVQ_NAME property
        int duplicatePropertyNumber = 42;
        String duplicatePropertyText = "Correct one !";
        String basicPropertyText = "MY_LVQ_PROP_";
        String largeMessageSuffix = new String(new char[150 * 1024]);

        prepareServer(container(1), true);

        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
        Connection connection = cf.createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) context.lookup(QUEUE_JNDI_NAME);

        MessageProducer producer = session.createProducer(destination);
        connection.start();

        for (int i = 0; i < numMessages; i++) {
            String currentProperty = basicPropertyText + i;
            String currentText = isLargeMessage ? currentProperty + largeMessageSuffix : currentProperty;
            TextMessage message = session.createTextMessage(currentText);
            message.setStringProperty("_AMQ_LVQ_NAME", currentProperty);
            producer.send(message);
        }

        //send message with "duplicatePropertyNumber" second time
        String currentProperty = basicPropertyText + duplicatePropertyNumber;
        String currentText = isLargeMessage ? duplicatePropertyText + largeMessageSuffix : duplicatePropertyText;
        TextMessage sendMessage = session.createTextMessage(currentText);
        sendMessage.setStringProperty("_AMQ_LVQ_NAME", currentProperty);
        producer.send(sendMessage);
        producer.close();

        //message with LVQ_PROP_42 is send twice, so 300 messages should be in InQueue (301 messages were send)
        //Assert.assertEquals("Only " + numMessages + " messages should be in queue", numMessages, new JMSTools().countMessages(QUEUE_NAME, container(1)));
        log.info("Only " + numMessages + " messages should be in queue. Actual value + " + new JMSTools().countMessages(QUEUE_NAME, container(1)));

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

        JMSTools.cleanupResources(context, connection, session);
        container(1).stop();

        Assert.assertEquals("Consumer should receive only " + numMessages + " message", numMessages, receivedMessages.size());

        for (int i = 0; i < numMessages; i++) {
            if (i == duplicatePropertyNumber) {
                String expected = isLargeMessage ? duplicatePropertyText + largeMessageSuffix : duplicatePropertyText;
                Assert.assertEquals("Last message should be received", expected, ((TextMessage) receivedMessages.get(i)).getText());
            } else {
                String expected = isLargeMessage ? basicPropertyText + i + largeMessageSuffix : basicPropertyText + i;
                Assert.assertEquals("Message should be received with correct text", expected, ((TextMessage) receivedMessages.get(i)).getText());
            }

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

        JMSTools.cleanupResources(context, connection, session);

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
        prepareServer(container, false);
    }

    private void prepareServer(Container container, boolean paging) {
        container.start();
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        if (paging) {
            jmsOperations.addAddressSettings("default", "jms.queue.testQueue", "PAGE", 10 * 1024, 0, 0, 2 * 1024, true);
        } else {
            jmsOperations.addAddressSettings("default", "jms.queue.testQueue", "PAGE", 5 * 1024 * 1024, 0, 0, 1024 * 1024, true);
        }
        jmsOperations.close();
        container.stop();
    }
}