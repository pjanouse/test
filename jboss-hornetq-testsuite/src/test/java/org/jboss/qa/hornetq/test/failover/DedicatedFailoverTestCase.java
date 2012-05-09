package org.jboss.qa.hornetq.test.failover;

import java.io.File;
import javax.jms.Session;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
//@RestoreConfigAfterTest
public class DedicatedFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;
    private static final int BYTEMAN_PORT = 9091;
  
    static boolean topologyCreated = false;
    
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";
    
    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     * 
     * @param acknowledge acknowledge type
     * @param failback whether to test fail back
     * 
     * @throws Exception 
     */
    public void testFailover(int acknowledge, boolean failback) throws Exception {
        
        testFailover(acknowledge, failback, false);
        
    }
    
    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     * 
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     * 
     * @throws Exception 
     */
    @BMRules({
        @BMRule(name = "Setup counter for PostOfficeImpl",
        targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
        targetMethod = "processRoute",
        action = "createCounter(\"counter\")"),
        @BMRule(name = "Info messages and counter for PostOfficeImpl",
        targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
        targetMethod = "processRoute",
        action = "incrementCounter(\"counter\");"
        + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
        @BMRule(name = "Kill server when a number of messages were received",
        targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
        targetMethod = "processRoute",
        condition = "readCounter(\"counter\")>333",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {

        prepareSimpleDedicatedTopology();

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);
        
        Thread.sleep(10000); // give some time to clients to failover
        
        // install rule to first server
        RuleInstaller.installRule(this.getClass(), CONTAINER1_IP, BYTEMAN_PORT);

        Clients clients = createClients(acknowledge, topic);

        clients.startClients();
        
        controller.kill(CONTAINER1);
        
        logger.info("Wait some time to give chance backup to come alive and clients to failover");
        Thread.sleep(10000); // give some time to clients to failover

        if (failback)   {
            logger.info("########################################");
            logger.info("failback - Start live server again ");
            logger.info("########################################");
            controller.start(CONTAINER1);
            Thread.sleep(10000); // give it some time 
            logger.info("########################################");
            logger.info("failback - Stop backup server");
            logger.info("########################################");
            controller.stop(CONTAINER2);
        }
        
        Thread.sleep(5000);
        clients.stopClients();
        
        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }
        
        Assert.assertTrue("There are failures detected by clients. More information in log.", clients.evaluateResults());

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

    }
    
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
    
    /**
     * Start simple failover test with auto_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverAutoAckQueue() throws Exception {
        testFailover(Session.AUTO_ACKNOWLEDGE, false);
    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverClientAckQueue() throws Exception {
        
        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }
    
    /**
     * Start simple failover test with auto_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackAutoAckQueue() throws Exception {
        testFailover(Session.AUTO_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }
    
    /**
     * Start simple failover test with auto_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverAutoAckTopic() throws Exception {
        testFailover(Session.AUTO_ACKNOWLEDGE, false, true);
    }
    
    /**
     * Start simple failover test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }
    
    /**
     * Start simple failover test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailoverTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true);
    }
    
    /**
     * Start simple failback test with auto acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackAutoAckTopic() throws Exception {
        testFailover(Session.AUTO_ACKNOWLEDGE, true, true);
    }
    
    /**
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }
    
    /**
     * Start simple failback test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testFailbackTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true);
    }

    

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     * 
     * @throws Exception 
     */
    @Before @After
    public void stopAllServers() throws Exception {

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);
        
        deleteFolder(new File(System.getProperty("JBOSS_HOME_1") + File.separator 
                + "standalone" + File.separator + "data" + File.separator + JOURNAL_DIRECTORY_A));

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {

        if (!topologyCreated) {
            prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
            prepareBackupServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_A);

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
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        
        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
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
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        
        jmsAdminOperations.close();
        
        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     *
     */
    private void prepareBackupServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);
        
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setAllowFailback(true);

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
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        
        jmsAdminOperations.close();

        controller.stop(containerName);
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
        
        jmsAdminOperations.close();
    }
}