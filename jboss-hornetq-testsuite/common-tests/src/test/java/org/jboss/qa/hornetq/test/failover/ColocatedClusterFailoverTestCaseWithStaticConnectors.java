package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 * Created by mstyk on 7/12/16.
 */
public class ColocatedClusterFailoverTestCaseWithStaticConnectors extends ColocatedClusterFailoverTestCase {

    private static final Logger logger = Logger.getLogger(ColocatedClusterFailoverTestCase.class);

    private int livePort = 5445;
    private int backupPort = 5446;

    private String connectorNameToLiveOnThisNode = "netty";
    private String connectorNameToBackupOnThisNode = "netty-backup";
    private String connectorNameToLiveOnSecondNode = "netty-second-live";
    private String connectorNameToBackupOnSecondNode = "netty-second-backup";

    private String socketBindingNameToLiveOnThisNode = "binding-live";
    private String socketBindingNameToBackupOnThisNode = "messaging-backup";
    private String socketBindingNameToLiveOnSecondNode = "binding-second-live";
    private String socketBindingNameToBackupOnSecondNode = "binding-second-backup";

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterEAP6() {
        String journalType = getJournalType();
        prepareLiveServerEAP6(container(1), container(1).getHostname(), container(2), JOURNAL_DIRECTORY_A, journalType);
        prepareColocatedBackupServerEAP6(container(1), "backup", container(2), JOURNAL_DIRECTORY_B, journalType);
        prepareLiveServerEAP6(container(2), container(2).getHostname(), container(1), JOURNAL_DIRECTORY_B, journalType);
        prepareColocatedBackupServerEAP6(container(2), "backup", container(1), JOURNAL_DIRECTORY_A, journalType);
    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterEAP7(Constants.CONNECTOR_TYPE connectorType) {
        String journalType = getJournalType();
        prepareLiveServerEAP7(container(1), container(2), JOURNAL_DIRECTORY_A, journalType, connectorType);
        prepareBackupServerEAP7(container(1), container(2), JOURNAL_DIRECTORY_B, journalType);
        prepareLiveServerEAP7(container(2), container(1), JOURNAL_DIRECTORY_B, journalType, connectorType);
        prepareBackupServerEAP7(container(2), container(1), JOURNAL_DIRECTORY_A, journalType);
    }

    /**
     * Prepare two servers in cluster (without backups).
     */
    public void prepareTwoNodeClusterTopology(Constants.CONNECTOR_TYPE connectorType) {
        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            String journalType = getJournalType();
            prepareLiveServerEAP7(container(1), container(2), JOURNAL_DIRECTORY_A, journalType, connectorType);
            prepareLiveServerEAP7(container(2), container(1), JOURNAL_DIRECTORY_B, journalType, connectorType);
        } else {
            prepareLiveServerEAP6(container(1), container(1).getHostname(), container(2), JOURNAL_DIRECTORY_A);
            prepareLiveServerEAP6(container(2), container(2).getHostname(), container(1), JOURNAL_DIRECTORY_B);
        }
    }


    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP6(Container container, String bindingAddress, Container secondContainer, String journalDirectory) {
        prepareLiveServerEAP6(container, bindingAddress, secondContainer, journalDirectory, "ASYNCIO");
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP6(Container container, String bindingAddress, Container secondContainer, String journalDirectory, String journalType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalFileSize(10 * 1024 * 1024);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

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

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

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
     * @param container        test container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP7(Container container, Container secondContainer, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

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
        } catch (Exception e){
            logger.info(e);
        }
        jmsAdminOperations.setStaticClusterConnections("default", clusterConnectionName, "jms", false, 1, 1000, true, "netty", connectorNameToBackupOnThisNode, connectorNameToLiveOnSecondNode, connectorNameToBackupOnSecondNode);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreMaster("default", 5000, true);

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

    public void prepareBackupServerEAP7(Container container, Container secondContainer, String journalDirectory, String journalType) {

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
        jmsAdminOperations.addHAPolicySharedStoreSlave(backupServerName, true, 5000, true, true, false, null, null, null, null);

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
    public void prepareColocatedBackupServerEAP6(Container container,
                                                 String backupServerName, Container secondContainer, String journalDirectoryPath) {
        prepareColocatedBackupServerEAP6(container, backupServerName, journalDirectoryPath, "ASYNCIO");
    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param container            The arquilian container.
     * @param backupServerName     Name of the new HornetQ backup server.
     * @param journalDirectoryPath Absolute or relative path to journal directory.
     */
    public void prepareColocatedBackupServerEAP6(Container container,
                                                 String backupServerName, Container secondContainer, String journalDirectoryPath, String journalType) {

        String clusterGroupName = "my-cluster";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.disableSecurity(backupServerName);
//        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setBackup(backupServerName, true);
        jmsAdminOperations.setSharedStore(backupServerName, true);
        jmsAdminOperations.setJournalFileSize(backupServerName, 10 * 1024 * 1024);
        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFailoverOnShutdown(true, backupServerName);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setAllowFailback(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);

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
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

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

}
