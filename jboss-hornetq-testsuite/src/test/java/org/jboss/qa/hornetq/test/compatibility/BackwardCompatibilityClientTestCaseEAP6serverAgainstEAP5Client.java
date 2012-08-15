package org.jboss.qa.hornetq.test.compatibility;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Test compatibility of EAP 5.1.2 clients against EAP 6 server.
 *
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 */
@RunWith(Arquillian.class)
public class BackwardCompatibilityClientTestCaseEAP6serverAgainstEAP5Client extends HornetQTestCase {
    private static final Logger log = Logger.getLogger(BackwardCompatibilityClientTestCaseEAP6serverAgainstEAP5Client.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;
    private static final int NUMBER_OF_DESTINATIONS = 1;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;

    private static final String JOURNAL_DIR = JOURNAL_DIRECTORY_A;

    private String queueNamePrefix = "testQueue";
    private String topicNamePrefix = "testTopic";
    private String queueJndiNamePrefix = "jms/queue/testQueue";
    private String topicJndiNamePrefix = "jms/topic/testTopic";

    public enum DestinationType {
        QUEUE, TOPIC
    }

    /**
     * Set all jms binding which will be needed for tests.
     */
    private void doPreparingOfServer() {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        if (isEAP6())   {
            controller.start(CONTAINER1);

            JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

            jmsAdminOperations.setInetAddress("public", CONTAINER1_IP);
            jmsAdminOperations.setInetAddress("unsecure", CONTAINER1_IP);
            jmsAdminOperations.setInetAddress("management", CONTAINER1_IP);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
            jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
            jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
            jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);

            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

            jmsAdminOperations.close();

        } else {

            JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
            jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
            jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
            jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

            jmsAdminOperations.close();

            controller.start(CONTAINER1);

        }
    }

    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations() {
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + destinationNumber, queueJndiNamePrefix + destinationNumber, true);
            jmsAdminOperations.createTopic(topicNamePrefix + destinationNumber, topicJndiNamePrefix + destinationNumber);
        }
        jmsAdminOperations.close();
    }

    /**
     * Destroy all destinations - queues and topics.
     */
    private void destroyDestinations() {
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            jmsAdminOperations.removeQueue(queueNamePrefix + destinationNumber);
            jmsAdminOperations.removeTopic(topicNamePrefix + destinationNumber);
        }
        jmsAdminOperations.close();
    }

    /**
     * Creating client to be run against the server.
     */
    private Clients createClient(int acknowledgeMode, DestinationType dest) throws Exception {

        Clients clients = null;

        if (dest == DestinationType.TOPIC) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        return clients;
    }

    private void testClient(int acknowledgeMode, DestinationType destination) throws Exception {
        deployDestinations();

        try {
            Clients client = createClient(acknowledgeMode, destination);
            client.startClients();
            client.stopClients();

            while (!client.isFinished()) {
                log.info("Waiting for client " + client + " to finish.");
                Thread.sleep(1500);
            }

            Assert.assertTrue("There are failures detected by clients. More information in log.", client.evaluateResults());
        } finally {
            destroyDestinations();
        }
    }

    @Test
    @InSequence(1)
    public void prepareServer() {
        doPreparingOfServer();
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testAutoAckQueue() throws Exception {
        testClient(Session.AUTO_ACKNOWLEDGE, DestinationType.QUEUE);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testAckQueue() throws Exception {
        testClient(Session.CLIENT_ACKNOWLEDGE, DestinationType.QUEUE);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testTransAckQueue() throws Exception {
        testClient(Session.SESSION_TRANSACTED, DestinationType.QUEUE);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testAutoAckTopic() throws Exception {
        testClient(Session.AUTO_ACKNOWLEDGE, DestinationType.TOPIC);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testClientAckTopic() throws Exception {
        testClient(Session.CLIENT_ACKNOWLEDGE, DestinationType.TOPIC);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testTransAckTopic() throws Exception {
        testClient(Session.SESSION_TRANSACTED, DestinationType.TOPIC);
    }

    @Test
    @RunAsClient
    @InSequence(5)
    public void testClientQueueOnMessageTypes() throws Exception {

        deployDestinations();

        try {
            Map<Integer, SoakProducerClientAck> producers = new HashMap<Integer, SoakProducerClientAck>();
            // Put messages to queues
            for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
                SoakProducerClientAck producer = new SoakProducerClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + destinationNumber, NUMBER_OF_MESSAGES_PER_PRODUCER);
                producer.setMessageBuilder(new ClientMixMessageBuilder());
                producer.start();
                producers.put(destinationNumber, producer);
                log.info("Producer " + producer + " started");
            }
            for (Thread thread : producers.values()) {
                thread.join();
            }

            Map<Integer, SoakReceiverClientAck> receivers = new HashMap<Integer, SoakReceiverClientAck>();
            // Let's read the messages from queues
            for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
                SoakReceiverClientAck receiver = new SoakReceiverClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + destinationNumber, 100000, 10, 10);
                receiver.start();
                receivers.put(destinationNumber, receiver);
                log.info("Receiver " + receiver + " started");
            }
            for (Thread thread : receivers.values()) {
                thread.join();
            }

            // And let's check that we get all messages - yippee...
            for (Integer destinationNumber : producers.keySet()) {
                SoakProducerClientAck producer = producers.get(destinationNumber);
                SoakReceiverClientAck receiver = receivers.get(destinationNumber);

                log.info(String.format("Producer %s sent % messages on destination %s", producer, producer.getCounter(), destinationNumber));
                log.info(String.format("Receiver %s received %s messages on destination %s", receiver, receiver.getCount(), destinationNumber));

                Assert.assertNotSame("The producer had to sent at least some message. Otherwise there is an error somewhere.",
                        0, producer.getCounter());
                Assert.assertEquals("There is different number of sent and received messages.",
                        producer.getCounter(),
                        receiver.getCount());
            }
        } finally {
            destroyDestinations();
        }
    }

    @Test
    @RunAsClient
    @InSequence(5)
    public void testClientTopicOnMessageTypes() throws Exception {
        deployDestinations();

        try {

            Map<Integer, SubscriberClientAck> receivers = new HashMap<Integer, SubscriberClientAck>();
            // Let's read the messages from queues
            for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
                SubscriberClientAck receiver = new SubscriberClientAck(CONTAINER1_IP, 4447, topicJndiNamePrefix + destinationNumber, "" +
                        "topicClient-subscriber-" + destinationNumber, "subscriber" + destinationNumber);
                receiver.setMessageVerifier(new CounterVerifier());
                receiver.subscribe();
                receiver.start();
                receivers.put(destinationNumber, receiver);
                log.info("Receiver " + receiver + " started");
            }

            Map<Integer, PublisherClientAck> producers = new HashMap<Integer, PublisherClientAck>();
            // Put messages to queues
            for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
                PublisherClientAck producer = new PublisherClientAck(CONTAINER1_IP, 4447, topicJndiNamePrefix + destinationNumber,
                        NUMBER_OF_MESSAGES_PER_PRODUCER, "topicClient-publisher-" + destinationNumber);
                producer.setMessageBuilder(new ClientMixMessageBuilder());
                List<FinalTestMessageVerifier> verifiers = new ArrayList<FinalTestMessageVerifier>();
                verifiers.add(receivers.get(destinationNumber).getMessageVerifier());
                producer.setMessageVerifiers(verifiers);
                producer.start();
                producers.put(destinationNumber, producer);
                log.info("Producer " + producer + " started");
            }

            // waiting for producers to be stopped
            for (Thread thread : producers.values()) {
                thread.join();
            }
            Thread.sleep(3000);
            // waiting for receivers to be stopped
            for (Thread thread : receivers.values()) {
                thread.join();
            }

            // And let's check whether we get all messages
            for (PublisherClientAck p : producers.values()) {
                Assert.assertTrue("The number of sent and received messages has to be the same.",
                        p.getMessageVerifiers().get(0).verifyMessages());
            }
        } finally {
            destroyDestinations();
        }
    }

    @Test
    @InSequence(100)
    @CleanUpAfterTest
    @RestoreConfigAfterTest
    public void cleanServers() {
        // just waiting for clean up after tests
    }

    @Test
    @InSequence(101)
    public void stopServer() {
        stopServer(CONTAINER1);
    }
}