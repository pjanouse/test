package org.jboss.qa.hornetq.test.cluster;

import javax.jms.Session;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigAfterTest
public class ClusterTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusterTestCase.class);
    
    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;
    
    private static boolean topologyCreated = false;
    
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";
    
    @ArquillianResource
    ContainerController controller;

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
    public void clusterTest() throws Exception {

        prepareServers();
        
        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // install rule to first server
        RuleInstaller.installRule(this.getClass());

        Clients queueClients = createClients(Session.CLIENT_ACKNOWLEDGE, false);
        Clients topicClients = createClients(Session.SESSION_TRANSACTED, true);
        
        queueClients.startClients();
        topicClients.startClients();
        
        waitForClientsToFinish(queueClients);
        waitForClientsToFinish(topicClients);

        Assert.assertTrue("There are failures detected by clients. More information in log.", queueClients.evaluateResults());
        Assert.assertTrue("There are failures detected by clients. More information in log.", topicClients.evaluateResults());

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

    }

    /**
     * Wait for clients to finish.
     * 
     * @param clients
     * @throws InterruptedException 
     */
    private void waitForClientsToFinish(Clients clients) throws InterruptedException {

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

    }
    
    /**
     * Create clients with the given acknowledge mode on topic or queue.
     * 
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE, Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic true for topic
     * @return clients
     * @throws Exception 
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception  {
        
        Clients clients = null;
        
        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(CONTAINER1_IP, PORT_JNDI, topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(CONTAINER1_IP, PORT_JNDI, topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(CONTAINER1_IP, PORT_JNDI, topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        
        return clients;
    }

    @Before
    public void prepareServers() {
        if (!topologyCreated) {
            prepareServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
            prepareServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_B);

            // deploy destinations 
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1_IP, 9999);
            controller.stop(CONTAINER1);
            controller.start(CONTAINER2);
            deployDestinations(CONTAINER2_IP, 9999);
            controller.stop(CONTAINER2);
            topologyCreated = true;
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     */
    private void deployDestinations(String hostname, int port) {
        deployDestinations(hostname, port, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String hostname, int port, String serverName) {

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(hostname, port);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
//        jmsAdminOperations.setLoopBackAddressType("public", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("unsecure", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("management", bindingAddress);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        controller.stop(containerName);

    }
    
    

    @Before
    @After
    public void stopAllServers() {

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

    }
}