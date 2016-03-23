package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.util.Random;

/**
 * Base class for clusering tests.
 */
@RunWith(Arquillian.class)
public class ClusterTestBase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusterTestBase.class);

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    protected static String queueNamePrefix = "testQueue";
    protected static String topicNamePrefix = "testTopic";
    protected static String queueJndiNamePrefix = "jms/queue/testQueue";
    protected static String topicJndiNamePrefix = "jms/topic/testTopic";

    // InQueue and OutQueue for mdb
    protected static String inQueueNameForMdb = "InQueue";
    protected static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    protected static String outQueueNameForMdb = "OutQueue";
    protected static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;

    // InTopic and OutTopic for mdb
    protected static String inTopicNameForMdb = "InTopic";
    protected static String inTopicJndiNameForMdb = "jms/topic/" + inTopicNameForMdb;
    protected static String outTopicNameForMdb = "OutTopic";
    protected static String outTopicJndiNameForMdb = "jms/topic/" + outTopicNameForMdb;

    /**
     * Create org.jboss.qa.hornetq.apps.clients with the given acknowledge mode
     * on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE,
     *                        Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic           true for topic
     * @return org.jboss.qa.hornetq.apps.clients
     * @throws Exception
     */
    protected Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
        messageBuilder.setAddDuplicatedHeader(true);
        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(100);
        clients.setReceivedMessagesAckCommitAfter(100);

        return clients;
    }

    /**
     * Prepares several servers for topology. Destinations will be created.
     */
    public void prepareServers() {
        prepareServers(true);
    }

    /**
     * Prepares several servers for topology.
     *
     * @param createDestinations Create destination topics and queues and topics
     *                           if true, otherwise no.
     */
    public void prepareServers(boolean createDestinations) {
        prepareServer(container(1), createDestinations);
        prepareServer(container(2), createDestinations);
        prepareServer(container(3), createDestinations);
        prepareServer(container(4), createDestinations);
    }

    /**
     * Prepares server for topology. Destination queues and topics will be
     * created.
     *
     * @param container The container - defined in arquillian.xml
     */
    protected void prepareServer(Container container) {
        prepareServer(container, true);
    }

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics
     *                           if true, otherwise no.
     */
    protected void prepareServer(Container container, boolean createDestinations) {
        prepareServer(container, createDestinations, -1);
    }

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics
     * @param reconnectAttempts  number of reconnect attempts in cluster connection
     *                           if true, otherwise no.
     */
    protected void prepareServer(Container container, boolean createDestinations, int reconnectAttempts) {

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
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, reconnectAttempts);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();
            // jmsAdminOperations.setLoggingLevelForConsole("INFO");
            // jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
            if (createDestinations) {
                for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
                    jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
                }

                for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
                    jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
                }

                jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
                jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);
                jmsAdminOperations.createTopic(inTopicNameForMdb, inTopicJndiNameForMdb);
                jmsAdminOperations.createTopic(outTopicNameForMdb, outTopicJndiNameForMdb);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();

        }

    }

    @Before
    @After
    public void stopAllServers() {

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

}
