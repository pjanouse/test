package org.jboss.qa.artemis.test.messages;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/job/eap7-artemis-qe-internal-ts-huge-messages/
 * @tpTestCaseDetails Test basic send - receive with large sized messages
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class HugeMessageTestCase extends HornetQTestCase {
    private static final Logger log = Logger.getLogger(HugeMessageTestCase.class);
    private final String inQueue = "InQueue";
    private final String inQueueJndiName = "jms/queue/" + inQueue;
    private final int messageSize = 1073741824; //1GB

    /**
     * @tpTestDetails Server is started. Send one byte message with size of 1GB.
     * Receive this message.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Connect to the server with the producer and send 1GB test messages to the
     * queue.</li>
     * <li>Connect to the server with consumer and receive all messages from the queue</li>
     * <li>Check that message was send are received correctly</li>
     * </ul>
     * @tpPassCrit Large message is correctly send and received
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void test1GbMessage() throws Exception {
        Container container = container(1);
        prepareServer(container);
        container.start();

        FinalTestMessageVerifier verifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        MessageBuilder messageBuilder = new ByteMessageBuilder(messageSize);
        ProducerClientAck producer = new ProducerClientAck(container, inQueueJndiName, 1);
        producer.setMessageBuilder(messageBuilder);
        producer.addMessageVerifier(verifier);
        producer.start();
        log.info("Waiting for message send");
        producer.join();
        log.info("Producer finished");

        ReceiverClientAck consumer = new ReceiverClientAck(container, inQueueJndiName);
        consumer.start();
        consumer.addMessageVerifier(verifier);
        log.info("Waiting for message receive");
        consumer.join();
        log.info("Consumer finished");

        verifier.verifyMessages();

        Assert.assertEquals("Message should be sent", 1, producer.getCount());
        Assert.assertEquals("Message should be receiver", 1, consumer.getCount());
        container.stop();
    }

    /**
     * @tpTestDetails 2 servers are started in cluster. Send one byte message with size of 1GB to queue on node1.
     * Receive this message from queue on node2.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Connect to the node1 with the producer and send 1GB test messages to the
     * queue.</li>
     * <li>Connect to the node2 with consumer and receive all messages from the queue</li>
     * <li>Check that message was send are received correctly</li>
     * </ul>
     * @tpPassCrit Large message is correctly send and received
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void test1GbMessageCluster() throws Exception {

        int redistributionWaitTimeMinutes = 10;
        JMSTools jmsTools = new JMSTools();

        prepareServer(container(1), container(2));
        container(1).start();
        container(2).start();

        MessageBuilder messageBuilder = new ByteMessageBuilder(messageSize);
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiName, 1);
        producer.setMessageBuilder(messageBuilder);
        producer.start();
        log.info("Waiting for message send");
        producer.join();
        log.info("Producer finished");


        // Lets receive message manually - first just connect receiver on node2 and wait for redistribution, then start receive
        Context namingContext = JMSTools.getEAP7Context(container(2));
        String connectionFactoryString = System.getProperty("connection.factory", Constants.CONNECTION_FACTORY_JNDI_EAP7);
        log.info("Attempting to acquire connection factory \"" + connectionFactoryString + "\"");
        ConnectionFactory connectionFactory = (ConnectionFactory) namingContext.lookup(connectionFactoryString);
        log.info("Found connection factory \"" + connectionFactoryString + "\" in JNDI");
        Queue destination = (Queue) namingContext.lookup(inQueueJndiName);
        log.info("Found destination \"" + inQueueJndiName + "\" in JNDI");
        Connection conn = connectionFactory.createConnection();
        conn.start();
        Session session = conn.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
        MessageConsumer receiver = session.createConsumer(destination);

        log.info("Waiting for large message to redistribute on node 2 (max " + redistributionWaitTimeMinutes + " minutes)");
        jmsTools.waitForMessages(inQueue, 1, TimeUnit.MINUTES.toMillis(redistributionWaitTimeMinutes), container(2));

        log.info("Starting receive. Max 10 minutes timeout.");

        Message msg = receiver.receive(TimeUnit.MINUTES.toMillis(10));
        Assert.assertEquals("Message should be sent", 1, producer.getCount());
        Assert.assertNotNull("Message should be received", msg);
        Assert.assertEquals("No message should be in queue", 0, jmsTools.countMessages(inQueue, container(1), container(2)));

        container(1).stop();
        container(2).stop();
    }

    protected void prepareServer(Container... containers) {

        for (Container container : containers) {

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
                jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, 10);

                jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
                jmsAdminOperations.disableSecurity();

                jmsAdminOperations.removeAddressSettings("#");
                jmsAdminOperations.addAddressSettings("#", "PAGE", 20480, 100, 0, 1024);

                jmsAdminOperations.createQueue(inQueue, inQueueJndiName);

            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                jmsAdminOperations.close();
                container.stop();

            }
        }

    }

}
