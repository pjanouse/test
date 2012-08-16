package org.jboss.qa.hornetq.test.compatibility;
// TODO ADD RIGHTS FOR TOPIC AND QUEUES

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;

/**
 *
 * Test compatibility of EAP 5.1.2 clients against EAP 6 server.
 *
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 */
@RunWith(Arquillian.class)
public class BackwardCompatibilityClientTestCase extends HornetQTestCase {
    private static final Logger log = Logger.getLogger(BackwardCompatibilityClientTestCase.class);

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
            deployDestinations();
            jmsAdminOperations.close();

        } else {

            String groupAddress = "233.4.66.88";
            int groupPort = 9876;
            JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

            deployDestinations();

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
            jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
            jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
            jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, CONTAINER1_IP, 5445, groupAddress, groupPort, 2000, connectorName, null);

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, CONTAINER1_IP, groupAddress, groupPort, 10000);

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
     * Creating client to be run against the server.
     */
    private Clients createClient(int acknowledgeMode, DestinationType dest) throws Exception {

        Clients clients;

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

            Clients client = createClient(acknowledgeMode, destination);
            client.startClients();

            while (!client.isFinished()) {
                log.info("Waiting for client " + client + " to finish.");
                Thread.sleep(1500);
            }

            Assert.assertTrue("There are failures detected by clients. More information in log.", client.evaluateResults());
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
    public void testClientAckTopic() throws     Exception {
        testClient(Session.CLIENT_ACKNOWLEDGE, DestinationType.TOPIC);
    }

    @Test
    @RunAsClient
    @InSequence(2)
    public void testTransAckTopic() throws Exception {
        testClient(Session.SESSION_TRANSACTED, DestinationType.TOPIC);
    }



    @Test
    @InSequence(100)
    @CleanUpAfterTest
    //@RestoreConfigAfterTest
    public void cleanServers() {
        // just waiting for clean up after tests
    }

    @Test
    @InSequence(101)
    public void stopServer() {
        stopServer(CONTAINER1);
    }
}