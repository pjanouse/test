package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.Assert;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails This test case implements  all tests from ColocatedClusterFailoverTestCase class with only one
 * difference in these tests: testKillInClusterLargeMessages, testShutdownInClusterLargeMessages, testShutdownInClusterSmallMessages,
 * testKillInClusterSmallMessages. Difference is, that node-2 is not started after kill/shutdown.
 */
@RunWith(Arquillian.class)
public class ReplicatedColocatedClusterFailoverTestCase extends ColocatedClusterFailoverTestCase {


    private static final Logger logger = Logger.getLogger(ReplicatedColocatedClusterFailoverTestCase.class);

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInCluster() {

        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            prepareLiveServerEAP7(container(1), "ASYNCIO", Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
            prepareLiveServerEAP7(container(2), "ASYNCIO", Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
        } else {
            prepareLiveServerEAP6(container(1), "firstPair", "firstPairJournalLive");
            prepareColocatedBackupServerEAP6(container(1), "backup", "secondPair", "secondPairJournalBackup");

            prepareLiveServerEAP6(container(2), "secondPair", "secondPairJournalLive");
            prepareColocatedBackupServerEAP6(container(2), "backup", "firstPair", "firstPairJournalBackup");
        }

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container     The container - defined in arquillian.xml
     * @param journalType   ASYNCIO, NIO
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareLiveServerEAP7(Container container, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyCollocatedReplicated("default", 1000, -1, 1000, 1, true, null);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);


        jmsAdminOperations.close();
        container.stop();
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

        container(1).start();
        container(2).start();

        // give some time for servers to find each other
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
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

        if (shutdown) {
            container(2).stop();
        } else {
            container(2).kill();
        }

//        logger.info("########################################");
//        logger.info("Start again - second server");
//        logger.info("########################################");
//        controller.start(CONTAINER2_NAME);
//        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000);
//        logger.info("########################################");
//        logger.info("Second server started");
//        logger.info("########################################");

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), inQueue, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);

        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }


    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    public void prepareLiveServerEAP6(Container container, String backupGroupName, String journalDirectoryPath) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";
        String remoteConnectorName = "netty-remote2";
        String remoteConnectorNameBackup = "netty-remote-backup";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setBackupGroupName(backupGroupName);
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setJournalFileSize(10 * 1024 * 1024);
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
        jmsAdminOperations.setNodeIdentifier(container.getName().hashCode());

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
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param container        The arquilian container.
     * @param backupServerName Name of the new HornetQ backup server.
     */
    public void prepareColocatedBackupServerEAP6(Container container, String backupServerName, String backupGroupName
            , String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6;
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setBackupGroupName(backupGroupName, backupServerName);
        jmsAdminOperations.setCheckForLiveServer(true, backupServerName);
        jmsAdminOperations.setJournalFileSize(backupServerName, 10 * 1024 * 1024);
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
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        container.stop();
    }

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
    }


}
