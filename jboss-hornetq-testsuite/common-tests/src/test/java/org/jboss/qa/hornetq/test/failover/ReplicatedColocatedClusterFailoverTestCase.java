package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

/**
 * Failover tests just with replicated journal.
 */
@RunWith(Arquillian.class)
public class ReplicatedColocatedClusterFailoverTestCase extends ColocatedClusterFailoverTestCase {


    private static final Logger logger = Logger.getLogger(ReplicatedColocatedClusterFailoverTestCase.class);

//    /**
//     * Start simple failover test with client_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void testSimpleConfiguration() throws Exception {
//
//        prepareColocatedTopologyInCluster();
//    }

    /**
     * Prepare two servers in colocated topology in cluster.
     *
     */
    public void prepareColocatedTopologyInCluster() {

        prepareLiveServer(CONTAINER1_NAME, "firstPair", "firstPairJournalLive");
        prepareColocatedBackupServer(CONTAINER1_NAME, "backup", "secondPair", "secondPairJournalBackup");

        prepareLiveServer(CONTAINER2_NAME, "secondPair", "secondPairJournalLive");
        prepareColocatedBackupServer(CONTAINER2_NAME, "backup", "firstPair", "firstPairJournalBackup");

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testShutdownInClusterLargeMessages() throws Exception {
        testFailInCluster(true, new ClientMixMessageBuilder(10, 200));
    }

    /**
     * In case of replication we don't want to start second server again. We expect that colocated backup
     * will have all load-balanced messages.
     *
     * @param shutdown
     * @param messageBuilder
     * @throws Exception
     */
    public void testFailInCluster(boolean shutdown, MessageBuilder messageBuilder) throws Exception {

        prepareColocatedTopologyInCluster();

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        // give some time for servers to find each other
        waitHornetQToAlive(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), 60000);
        waitHornetQToAlive(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueue, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown)   {
            controller.stop(CONTAINER2_NAME);
        } else {
            killServer(CONTAINER2_NAME);
            controller.kill(CONTAINER2_NAME);
        }

//        logger.info("########################################");
//        logger.info("Start again - second server");
//        logger.info("########################################");
//        controller.start(CONTAINER2_NAME);
//        waitHornetQToAlive(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 300000);
//        logger.info("########################################");
//        logger.info("Second server started");
//        logger.info("########################################");

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueue, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);

        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);

    }


    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     */
    public void prepareLiveServer(String containerName, String backupGroupName, String journalDirectoryPath) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";
        String remoteConnectorName = "netty-remote2";
        String remoteConnectorNameBackup = "netty-remote-backup";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setBackupGroupName(backupGroupName);
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setJournalFileSize(10 * 1024 *1024);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

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
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory(pooledConnectionFactoryName, true);

        // pooled connection factory must support HA
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        jmsAdminOperations.setPagingDirectory(journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectoryPath);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.removeAddressSettings("#");

        setAddressSettings(jmsAdminOperations);

        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);
        jmsAdminOperations.setNodeIdentifier(containerName.hashCode());

//        if (CONTAINER1_NAME_NAME.equalsIgnoreCase(containerName)) {
//            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", CONTAINER1_NAME_IP, 5445);
//            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
//            jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", CONTAINER1_NAME_IP, 5446);
//            jmsAdminOperations.createRemoteConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);
//        } else if (CONTAINER2_NAME.equalsIgnoreCase(containerName)) {
//            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", CONTAINER2_IP, 5445);
//            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
//            jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", CONTAINER2_IP, 5446);
//            jmsAdminOperations.createRemoteConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);
//        }
//
//        List<String> connectorList = new ArrayList<String>();
//        connectorList.add(remoteConnectorName);
//        connectorList.add(remoteConnectorNameBackup);
//        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorList);
//
////        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorName);
//
//        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
//        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);
//        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, 1000L);
//        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, 1.0);
//        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(getJbossHome(containerName) + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(getJbossHome(containerName) + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param containerName        Name of the arquilian container.
     * @param backupServerName     Name of the new HornetQ backup server.
     */
    public void prepareColocatedBackupServer(String containerName, String backupServerName, String backupGroupName
                , String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = PORT_HORNETQ_BACKUP_DEFAULT;
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setBackupGroupName(backupGroupName, backupServerName);
        jmsAdminOperations.setCheckForLiveServer(true, backupServerName);
        jmsAdminOperations.setJournalFileSize(backupServerName, 10 * 1024 *1024);
        jmsAdminOperations.setFailoverOnShutdown(true, backupServerName);

        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);

        jmsAdminOperations.setSecurityEnabled(backupServerName, false);
        jmsAdminOperations.setClusterUserPassword(backupServerName, "heslo");
        jmsAdminOperations.setBackup(backupServerName, true);
        jmsAdminOperations.setSharedStore(backupServerName, false);

        jmsAdminOperations.setAllowFailback(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, "ASYNCIO");

        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectoryPath);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        setAddressSettings(backupServerName, jmsAdminOperations);

        jmsAdminOperations.addSecuritySetting(backupServerName, "#");
        jmsAdminOperations.addRoleToSecuritySettings(backupServerName, "#", "guest");
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "send", true);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

//        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
//            jmsAdminOperations.createQueue(backupServerName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
//        }
//
//        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
//            jmsAdminOperations.createTopic(backupServerName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
//        }
//        jmsAdminOperations.createQueue(backupServerName, inQueueName, inQueue, true);
//        jmsAdminOperations.createQueue(backupServerName, outQueueName, outQueue, true);

        jmsAdminOperations.close();

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(getJbossHome(containerName) + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(getJbossHome(containerName) + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        controller.stop(containerName);
    }

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
    }


}