package org.jboss.qa.hornetq.test.failover;

import java.io.File;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigAfterTest
public class FailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FailoverTestCase.class);
    private static final int NUMBER_OF_QUEUES = 5;
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;
    private static final int NUMBER_OF_PRODUCERS_PER_QUEUE = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_QUEUE = 2;
    private static final String UDP_GROUP_ADDRESS = "232.8.7.1";
    
    @ArquillianResource
    private Deployer deployer;
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    /**
     * This test will start two servers in dedicated topology - no cluster Sent
     * some messages to first Receive messages from the second one
     */
    @Test
    @RunAsClient
//    @RestoreConfigAfterTest
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
        condition = "readCounter(\"counter\")>50",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void simpleFailoverTest() throws Exception {
//        prepareColocatedTopologyInCluster();
        prepareDedicatedTopologyInCluster();
//        prepareSimpleDedicatedTopology();

        controller.start(CONTAINER3);

        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        // install rule to first server
        RuleInstaller.installRule(this.getClass());

//        StartManyClientsClientAck clients = new StartManyClientsClientAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_QUEUES, NUMBER_OF_PRODUCERS_PER_QUEUE, NUMBER_OF_RECEIVERS_PER_QUEUE, NUMBER_OF_MESSAGES_PER_PRODUCER);
        QueueClientsTransAck clients = new QueueClientsTransAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_QUEUES, NUMBER_OF_PRODUCERS_PER_QUEUE, NUMBER_OF_RECEIVERS_PER_QUEUE, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        clients.startClients();

        controller.kill(CONTAINER1);

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

        Assert.assertTrue(clients.evaluateResults());

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

        controller.stop(CONTAINER3);
    }


    @After
    public void stopAllServers() {

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);
        
        controller.stop(CONTAINER3);
        
        deleteFolder(new File(JOURNAL_DIRECTORY_A));
        
        deleteFolder(new File(JOURNAL_DIRECTORY_B));

    }

    public void prepareSimpleDedicatedTopology() throws Exception {

        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_A);
        
        controller.start(CONTAINER1);
        deployDestinations(CONTAINER1_IP, 9999);
        controller.stop(CONTAINER1);

        controller.start(CONTAINER2);
        deployDestinations(CONTAINER2_IP, 9999);
        controller.stop(CONTAINER2);

    }

    public void prepareColocatedTopologyInCluster() {

        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        prepareColocatedBackupServer(CONTAINER1, CONTAINER1_IP, "backup", JOURNAL_DIRECTORY_B);

        prepareLiveServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_B);
        prepareColocatedBackupServer(CONTAINER2, CONTAINER2_IP, "backup", JOURNAL_DIRECTORY_A);

        // deploy destinations 
        controller.start(CONTAINER1);
        deployDestinations(CONTAINER1_IP, 9999);
        deployDestinations(CONTAINER1_IP, 9999, "backup");
        controller.stop(CONTAINER1);
        controller.start(CONTAINER2);
        deployDestinations(CONTAINER2_IP, 9999);
        deployDestinations(CONTAINER2_IP, 9999, "backup");
        controller.stop(CONTAINER2);
    }

    public void prepareColocatedBackupServer(String containerName, String ipAddress,
            String backupServerName, String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String inVmAcceptorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = 5446;
        String connectionFactoryName = "RemoteConnectionFactory";
        String udpGroupAddress = "231.8.8.8";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;

        controller.start(containerName);
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(ipAddress, 9999);

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.setBackup(backupServerName, true);
        jmsAdminOperations.setSharedStore(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, "NIO");
        jmsAdminOperations.setJournalFileSize(backupServerName, 10240);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setAllowFailback(backupServerName, true);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, ipAddress, broadcastBindingPort, udpGroupAddress, udpGroupPort, 2000, connectorName, "");
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, ipAddress, udpGroupAddress, udpGroupPort, 10000);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        controller.stop(containerName);

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
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;

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
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, UDP_GROUP_ADDRESS, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, UDP_GROUP_ADDRESS, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
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
        
        int udpGroupPort = 9875;

        int broadcastBindingPort = 56880;

        controller.start(containerName);
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setJmxDomainName("org.hornetq.backup");
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
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, UDP_GROUP_ADDRESS, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, UDP_GROUP_ADDRESS, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

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

        for (int queueNumber = 0; queueNumber < NUMBER_OF_QUEUES; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_QUEUES; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }
    }

    public void prepareDedicatedTopologyInCluster() {
        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        controller.start(CONTAINER1);
        deployDestinations(CONTAINER1_IP, 9999);
        controller.stop(CONTAINER1);


        prepareBackupServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_A);
        controller.start(CONTAINER2);
        deployDestinations(CONTAINER2_IP, 9999);
        controller.stop(CONTAINER2);


        prepareLiveServer(CONTAINER3, CONTAINER3_IP, JOURNAL_DIRECTORY_B);
        controller.start(CONTAINER3);
        deployDestinations(CONTAINER3_IP, 9999);
        controller.stop(CONTAINER3);

//        prepareBackupServer(CONTAINER4, CONTAINER4_IP, JOURNAL_DIRECTORY_B);
    }
}