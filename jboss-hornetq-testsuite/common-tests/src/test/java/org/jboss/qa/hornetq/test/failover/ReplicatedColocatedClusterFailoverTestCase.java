package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;
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
     * @throws Exception
     * @tpTestDetails This test scenario tests whether the synchronization between Lives and Backups
     * will be successfully performed even the journal-min-files will be 100.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology with journal-min-files=100</li>
     * <li>start sending and receiving messages</li>
     * <li>shutdown node-1</li>
     * <li>start node-1</li>
     * <li>stop sending messages</li>
     * <li>wait until all messages are received</li>
     * </ul>
     * @tpPassCrit Replication is not stopped between Live and Backup (all checks whether brokers are active or inactive pass).
     * All sent messages are properly delivered. There are no losses or duplicates.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSynchronizationWithBigJournalMinFiles() throws Exception {
        prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE.NETTY_NIO);

        JMSOperations jmsOperations;

        container(1).start();
        jmsOperations = container(1).getJmsOperations();
        jmsOperations.setJournalMinFiles(100);
        jmsOperations.setJournalMinFiles("backup", 100);
        container(1).stop();

        container(2).start();
        jmsOperations = container(2).getJmsOperations();
        jmsOperations.setJournalMinFiles(100);
        jmsOperations.setJournalMinFiles("backup", 100);
        container(2).stop();

        container(2).start();

        container(1).start();

        // give some time for servers to find each other
        Thread.sleep(10000);

        messageBuilder.setAddDuplicatedHeader(true);

        clients = createClients(Session.SESSION_TRANSACTED, false, messageBuilder);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(10);

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 120, 300000);

        logger.info("########################################");
        logger.info("shutdown - first server");
        logger.info("########################################");
        container(1).stop();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 500, 300000);
        Assert.assertTrue("Backup on second server did not start - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(),
                container(2).getHornetqBackupPort(), 300000));

        logger.info("########################################");
        logger.info("failback - Start first server again ");
        logger.info("########################################");
        container(1).start();
        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 300000);
        Thread.sleep(20000); // give some time to org.jboss.qa.hornetq.apps.clients

        logger.info("########################################");
        logger.info("Stop org.jboss.qa.hornetq.apps.clients - this will stop producers");
        logger.info("########################################");
        clients.stopClients();

        logger.info("########################################");
        logger.info("Wait for end of all org.jboss.qa.hornetq.apps.clients.");
        logger.info("########################################");
        ClientUtils.waitForClientsToFinish(clients);
        logger.info("########################################");
        logger.info("All org.jboss.qa.hornetq.apps.clients ended/finished.");
        logger.info("########################################");


        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();
    }

    @BMRules({
            @BMRule(name = "Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown, Constants.CONNECTOR_TYPE connectorType, ConfigType configType) throws Exception {
        testFailInternal(acknowledge, failback, topic, shutdown, connectorType, configType);
    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    @Override
    public void prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE connectorType) {

        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            prepareLiveServerEAP7(container(1), "ASYNCIO", JOURNAL_DIRECTORY_A, "group1", connectorType);
            prepareBackupServerEAP7(container(1), "ASYNCIO", JOURNAL_DIRECTORY_B, "group2");
            prepareLiveServerEAP7(container(2), "ASYNCIO", JOURNAL_DIRECTORY_C, "group2", connectorType);
            prepareBackupServerEAP7(container(2), "ASYNCIO", JOURNAL_DIRECTORY_D, "group1");
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
     * @param groupName     name of group used for Live-Backup pair
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    public void prepareLiveServerEAP7(Container container, String journalType, String journalDirectory, String groupName, Constants.CONNECTOR_TYPE connectorType) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster("default", true, clusterConnectionName, groupName);

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
     * Prepares live server for dedicated topology.
     *
     * @param container   The container - defined in arquillian.xml
     * @param journalType ASYNCIO, NIO
     * @param groupName   name of group used for Live-Backup pair
     */
    public void prepareBackupServerEAP7(Container container, String journalType, String journalDirectory, String groupName) {

        final String backupServerName = "backup";

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7;
        String pooledConnectionFactoryName = "activemq-ra";
        String jgroupsChannel = "activemq-cluster";


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);

        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectory);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, null, jgroupsChannel, 1000, connectorName);
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, 1000, null, jgroupsChannel);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);


        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(backupServerName, true, clusterConnectionName, 1000, groupName, -1, true, false, null, null, null, null);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

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

        prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE.NETTY_NIO);

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
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.addMessageVerifier(messageVerifier);
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
        receiver1.addMessageVerifier(messageVerifier);
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

    //static connectors prepare


    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterStaticConnectorsEAP6() {
        String journalType = getJournalType();

        prepareLiveServerStaticConnectorsEAP6(container(1), container(2), "firstPair", "firstPairJournalLive");
        prepareColocatedBackupServerStaticConnectorsEAP6(container(1), "backup", "secondPair", "secondPairJournalBackup");

        prepareLiveServerStaticConnectorsEAP6(container(2), container(1), "secondPair", "secondPairJournalLive");
        prepareColocatedBackupServerStaticConnectorsEAP6(container(2), "backup", "firstPair", "firstPairJournalBackup");
    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterStaticConnectorsEAP7(Constants.CONNECTOR_TYPE connectorType) {
        String journalType = getJournalType();
        prepareLiveServerStaticConnectorsEAP7(container(1), container(2), JOURNAL_DIRECTORY_A, journalType, connectorType, "group1");
        prepareBackupServerStaticConnectorsEAP7(container(1), container(2), JOURNAL_DIRECTORY_B, journalType, "group2");
        prepareLiveServerStaticConnectorsEAP7(container(2), container(1), JOURNAL_DIRECTORY_B, journalType, connectorType, "group2");
        prepareBackupServerStaticConnectorsEAP7(container(2), container(1), JOURNAL_DIRECTORY_A, journalType, "group1");
    }


    public void prepareLiveServerStaticConnectorsEAP6(Container container, Container secondContainer, String backupGroupName, String journalDirectoryPath) {


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


        jmsAdminOperations.createSocketBinding(socketBindingNameToBackupOnThisNode, backupPort);
        jmsAdminOperations.addRemoteSocketBinding(socketBindingNameToLiveOnSecondNode, secondContainer.getHostname(), livePort + secondContainer.getPortOffset());
        jmsAdminOperations.addRemoteSocketBinding(socketBindingNameToBackupOnSecondNode, secondContainer.getHostname(), backupPort + secondContainer.getPortOffset());

        jmsAdminOperations.createRemoteConnector(connectorNameToBackupOnThisNode, socketBindingNameToBackupOnThisNode, null);
        jmsAdminOperations.createRemoteConnector(connectorNameToLiveOnSecondNode, socketBindingNameToLiveOnSecondNode, null);
        jmsAdminOperations.createRemoteConnector(connectorNameToBackupOnSecondNode, socketBindingNameToBackupOnSecondNode, null);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", false, 1, 1000, true, connectorName, connectorNameToBackupOnSecondNode, connectorNameToBackupOnThisNode, connectorNameToLiveOnSecondNode);


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
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerStaticConnectorsEAP7(Container container, Container secondContainer, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType, String groupName) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        setConnectorForClientEAP7(container, connectorType);

        //static connectors setup
        jmsAdminOperations.createSocketBinding(socketBindingNameToBackupOnThisNode, backupPort);
        jmsAdminOperations.addRemoteSocketBinding(socketBindingNameToLiveOnSecondNode, secondContainer.getHostname(), livePort + secondContainer.getPortOffset());
        jmsAdminOperations.addRemoteSocketBinding(socketBindingNameToBackupOnSecondNode, secondContainer.getHostname(), backupPort + secondContainer.getPortOffset());
        jmsAdminOperations.createSocketBinding(socketBindingNameToLiveOnThisNode, livePort);


        jmsAdminOperations.createRemoteConnector(connectorNameToBackupOnThisNode, socketBindingNameToBackupOnThisNode, null);
        jmsAdminOperations.createRemoteConnector(connectorNameToLiveOnSecondNode, socketBindingNameToLiveOnSecondNode, null);
        jmsAdminOperations.createRemoteConnector(connectorNameToBackupOnSecondNode, socketBindingNameToBackupOnSecondNode, null);

        try {
            jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
        } catch (Exception e) {
            logger.info(e);
        }
        jmsAdminOperations.setStaticClusterConnections("default", clusterConnectionName, "jms", false, 1, 1000, true, "netty", connectorNameToBackupOnThisNode, connectorNameToLiveOnSecondNode, connectorNameToBackupOnSecondNode);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster("default", true, clusterConnectionName, groupName);

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

    public void prepareBackupServerStaticConnectorsEAP7(Container container, Container secondContainer, String journalDirectory, String journalType, String groupName) {

        final String backupServerName = "backup";

        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String pooledConnectionFactoryName = "activemq-ra";


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectory);

        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToBackupOnThisNode, socketBindingNameToBackupOnThisNode, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingNameToBackupOnThisNode, null);

        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToLiveOnThisNode, socketBindingNameToLiveOnThisNode, null);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToLiveOnSecondNode, socketBindingNameToLiveOnSecondNode, null);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToBackupOnSecondNode, socketBindingNameToBackupOnSecondNode, null);

        jmsAdminOperations.setStaticClusterConnections(backupServerName, clusterConnectionName, "jms", false, 1, 1000, true, connectorNameToBackupOnThisNode, connectorNameToLiveOnThisNode, connectorNameToLiveOnSecondNode, connectorNameToBackupOnSecondNode);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);


        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(backupServerName, true, clusterConnectionName, 1000, groupName, -1, true, false, null, null, null, null);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        // set security persmissions for roles guest
        jmsAdminOperations.addSecuritySetting(backupServerName, "#");
        jmsAdminOperations.addRoleToSecuritySettings(backupServerName, "#", "guest");
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "send", true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param container            The arquilian container.
     * @param backupServerName     Name of the new HornetQ backup server.
     * @param journalDirectoryPath Absolute or relative path to journal directory.
     */
    public void prepareColocatedBackupServerStaticConnectorsEAP6(Container container, String backupServerName, String backupGroupName
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

        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToBackupOnThisNode, socketBindingNameToBackupOnThisNode, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingNameToBackupOnThisNode, null);

        jmsAdminOperations.createSocketBinding(socketBindingNameToLiveOnThisNode, livePort);

        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToLiveOnThisNode, socketBindingNameToLiveOnThisNode, null);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToLiveOnSecondNode, socketBindingNameToLiveOnSecondNode, null);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorNameToBackupOnSecondNode, socketBindingNameToBackupOnSecondNode, null);

        jmsAdminOperations.setStaticClusterConnections(backupServerName, clusterGroupName, "jms", false, 1, 1000, true, connectorNameToBackupOnThisNode, connectorNameToLiveOnThisNode, connectorNameToLiveOnSecondNode, connectorNameToBackupOnSecondNode);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);

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

    //tcp jgroups stack prepare

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    @Override
    public void prepareColocatedTopologyInClusterTcpStack(Constants.CONNECTOR_TYPE connectorType) {

        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            prepareLiveServerWithTcpStackEAP7(container(1), "ASYNCIO", JOURNAL_DIRECTORY_A, "group1", connectorType);
            prepareBackupServerWithTcpStackEAP7(container(1), "ASYNCIO", JOURNAL_DIRECTORY_B, "group2");
            prepareLiveServerWithTcpStackEAP7(container(2), "ASYNCIO", JOURNAL_DIRECTORY_C, "group2", connectorType);
            prepareBackupServerWithTcpStackEAP7(container(2), "ASYNCIO", JOURNAL_DIRECTORY_D, "group1");
        } else {
            prepareLiveServerWithTcpStackEAP6(container(1), "firstPair", "firstPairJournalLive");
            prepareColocatedBackupServerWithTcpStackEAP6(container(1), "backup", "secondPair", "secondPairJournalBackup");

            prepareLiveServerWithTcpStackEAP6(container(2), "secondPair", "secondPairJournalLive");
            prepareColocatedBackupServerWithTcpStackEAP6(container(2), "backup", "firstPair", "firstPairJournalBackup");
        }

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container     The container - defined in arquillian.xml
     * @param journalType   ASYNCIO, NIO
     * @param groupName     name of group used for Live-Backup pair
     * @param connectorType whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    public void prepareLiveServerWithTcpStackEAP7(Container container, String journalType, String journalDirectory, String groupName, Constants.CONNECTOR_TYPE connectorType) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "tcp", "tcp", 2000, "netty");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, "tcp", "tcp");

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, "netty");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster("default", true, clusterConnectionName, groupName);

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
     * Prepares live server for dedicated topology.
     *
     * @param container   The container - defined in arquillian.xml
     * @param journalType ASYNCIO, NIO
     * @param groupName   name of group used for Live-Backup pair
     */
    public void prepareBackupServerWithTcpStackEAP7(Container container, String journalType, String journalDirectory, String groupName) {

        final String backupServerName = "backup";

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7;
        String pooledConnectionFactoryName = "activemq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);

        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectory);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, "tcp", "tcp", 1000, connectorName);
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, 1000, "tcp", "tcp");
        jmsAdminOperations.setClusterConnections(backupServerName, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);


        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(backupServerName, true, clusterConnectionName, 1000, groupName, -1, true, false, null, null, null, null);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    public void prepareLiveServerWithTcpStackEAP6(Container container, String backupGroupName, String journalDirectoryPath) {

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
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "tcp", "tcp", 2000, connectorName);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, "tcp", "tcp");

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
    public void prepareColocatedBackupServerWithTcpStackEAP6(Container container, String backupServerName, String backupGroupName
            , String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6;

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

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, "tcp", "tcp", 2000, connectorName);
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, 10000, "tcp", "tcp");
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
