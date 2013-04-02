package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Failover tests just with replicated journal.
 */
@RunWith(Arquillian.class)
public class ReplicatedColocatedClusterFailoverTestCase extends ColocatedClusterFailoverTestCase {


    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCase.class);

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

        prepareLiveServer(CONTAINER1, "firstPair", "firstPairJournalLive");
        prepareColocatedBackupServer(CONTAINER1, "backup", "secondPair", "secondPairJournalBackup");

        prepareLiveServer(CONTAINER2, "secondPair", "secondPairJournalLive");
        prepareColocatedBackupServer(CONTAINER2, "backup", "firstPair", "firstPairJournalBackup");

    }


    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     */
    protected void prepareLiveServer(String containerName, String backupGroupName, String journalDirectoryPath) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setBackupGroupName(backupGroupName);
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setJournalFileSize(10 * 1024 *1024);

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


        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.removeAddressSettings("#");

        setAddressSettings(jmsAdminOperations);

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
    protected void prepareColocatedBackupServer(String containerName, String backupServerName, String backupGroupName
                , String journalDirectoryPath) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = 5446;
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

        jmsAdminOperations.setSecurityEnabled(backupServerName, true);
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

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(backupServerName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(backupServerName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

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

    /**
     * Copies file from one place to another.
     *
     * @param sourceFile source file
     * @param destFile   destination file - file will be rewritten
     * @throws java.io.IOException
     */
    public void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}
