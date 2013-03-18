package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Failover tests just with replicated journal.
 */
@RunWith(Arquillian.class)
public class ReplicatedDedicatedFailoverTestCase extends DedicatedFailoverTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCase.class);

    /**
     * Start simple failover test with trans_ack on queues. Server is killed when message is sent to server but not stored to journal.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueMessageSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues. Server is killed when message is sent to server and stored to journal.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueMessageSentStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues. Server is killed when commit is sent to journal and NOT stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueCommitSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues. Server is killed when commit is sent to journal and stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueCommitSentAndStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues. Server is killed when commit is sent to journal and stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueCommitStoredInBackupNotStoredInLive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues. Server is killed whem message is received but not acked.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueMessageReceivedNotAcked() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill before commit is stored to journal - receive",
            targetClass = "org.hornetq.core.replication.ReplicatedJournal",
            targetMethod = "appendCommitRecord",
            action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    public void testFailoverTransAckQueueCommitNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill after commit is stored to journal - receive",
            targetClass = "org.hornetq.core.replication.ReplicatedJournal",
            targetMethod = "appendCommitRecord",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman will invoke kill\"); killJVM();")
    public void testFailoverTransAckQueueCommitStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckQueueCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    /**
     *
     *
     * when message is sent and not stored into journal (P1)
     when message is sent and stored into journal but response is not returned (P1)
     message is replicated to backup
     message is not replicated to backup
     when message is sent and response for commit not recieved (P1)
     commit is not written to journal
     commit is written to live but not to backup
     commit is written to live/backup
     when message is received but not acked/commited (P1)
     when message is received but response for ack not received (P1)
     ack/commit is not written to journal
     ack/commit is written to live but not to backup
     ack/commit is written to live/backup
     *
     */

    /////////////////////////////////////////// FAILOVER ON TOPIC ///////////////////////////////////////////////
    /**
     * Start simple failover test with trans_ack on queues. Server is killed when message is sent to server but not stored to journal.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiMessageSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed when message is sent to server and stored to journal.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "addToPage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiMessageSentStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed when commit is sent to journal and NOT stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiCommitSentNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed when commit is sent to journal and stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiCommitSentAndStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed when commit is sent to journal and stored.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiCommitStoredInBackupNotStoredInLive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, false);
    }

    /**
     * Start simple failover test with trans_ack on Topis. Server is killed whem message is received but not acked.
     * It's the same method for client_ack and trans session.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.replication.ReplicatedJournal",
                    targetMethod = "appendDeleteRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiMessageReceivedNotAcked() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill before commit is stored to journal - receive",
            targetClass = "org.hornetq.core.replication.ReplicatedJournal",
            targetMethod = "appendCommitRecord",
            action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    public void testFailoverTransAckTopiCommitNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill after commit is stored to journal - receive",
            targetClass = "org.hornetq.core.replication.ReplicatedJournal",
            targetMethod = "appendCommitRecord",
            targetLocation = "EXIT",
            action = "System.out.println(\"Byteman will invoke kill\"); killJVM();")
    public void testFailoverTransAckTopiCommitStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into backup's journal.  - send",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void testFailoverTransAckTopiCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }



    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {

        prepareLiveServer(CONTAINER1);
        prepareBackupServer(CONTAINER2);

    }

    /**
     * When message is sent and not stored into journal.
     */

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareLiveServer(String containerName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);

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
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.setClusterUserPassword("heslo");
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 10 * 1024 * 1024, 0, 0, 2 * 1024 * 1024);


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

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.close();

        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareBackupServer(String containerName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(false);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setPersistenceEnabled(true);
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
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.removeAddressSettings("#");

        jmsAdminOperations.addAddressSettings("#", "PAGE", 10 * 1024 * 1024, 0, 0, 2 * 1024 * 1024);

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
