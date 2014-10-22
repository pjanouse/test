package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.Session;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Failover tests just with replicated journal.
 */
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
    public void replicatedTestFailoverTransAckQueueMessageSentNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueMessageSentStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitSentNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitSentAndStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitStoredInBackupNotStoredInLive() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueMessageReceivedNotAcked() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitStored() throws Exception {
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
    public void replicatedTestFailoverTransAckQueueCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiMessageSentNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiMessageSentStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitSentNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitSentAndStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitStoredInBackupNotStoredInLive() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiMessageReceivedNotAcked() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitNotStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitStored() throws Exception {
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
    public void replicatedTestFailoverTransAckTopiCommitStoredInBackupNotStoredInLiveReceive() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, true, true);
    }


    /**
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientsNotBlockedWhenBackupStoppedDuringSynchronization() throws Exception {

        int numberOfMessages = 1000;

        prepareSimpleDedicatedTopology();

        controller.start(CONTAINER1);

        // send lots of messages (GBs)
        logger.info("Start producer to send: " + numberOfMessages + " messages.");

        long producerStartTime = System.currentTimeMillis();

        ProducerTransAck prod1 = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", numberOfMessages);

        prod1.setMessageBuilder(new TextMessageBuilder(1024 * 1024)); // 1MB

        prod1.setTimeout(0);

        prod1.setCommitAfter(20);

        prod1.start();

        prod1.join();

        long producerFinishTime = System.currentTimeMillis() - producerStartTime;

        logger.info("Producer sent: " + numberOfMessages + " messages.");


        logger.info("Start producer and consumer.");
        // start one producer and consumer - client ack - those get blocked for 2 min. later when backup is stopped

        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", 50);

        MessageBuilder builder = new TextMessageBuilder(1024 * 1024);

        builder.setAddDuplicatedHeader(true);

        producer.setMessageBuilder(builder);

        producer.setTimeout(100);

        producer.start();

        ReceiverClientAck receiver = new ReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix + "0", 30000, 1, 100);

        receiver.setTimeout(100);

        receiver.start();

        // start backup
        logger.info("Start backup server.");

        controller.start(CONTAINER2);

        logger.info("Backup started - synchronization with live will started now.");

        // put here some safe time, replication cannot be finished - lets say is a safe value producerFinishTime/2
        Thread.sleep(producerFinishTime/2);

        // during synchronization live-> backup stop backup (it takes 2 min for live disconnect backup and clients continue to work)
        logger.info("Stop backup server - synchronization with live must be in progress now.");

        stopServer(CONTAINER2);

        logger.info("Backup server stopped");

        // now check whether producer and consumer sent/received some messages
        long timeout = 60000;
        // wait for 1 min for producers and consumers to receive more messages
        long startTime = System.currentTimeMillis();

        Thread.sleep(10000);

        int startValueProducer = producer.getListOfSentMessages().size();

        int startValueConsumer = receiver.getListOfReceivedMessages().size();

        logger.info("Check that clients did send or received messages in next: " + timeout);

        while (producer.getListOfSentMessages().size() <= startValueProducer && receiver.getListOfReceivedMessages().size() <= startValueConsumer) {

            if (System.currentTimeMillis() - startTime > timeout) {

                Assert.fail("Clients - producer and consumer did not sent/received new messages after backup was stopped for 60 s.");
            }

            try {

                Thread.sleep(1000);

            } catch (InterruptedException e) {
                // ignore
            }
        }

        logger.info("Client did send or received messages in timeout: " + timeout);

        // ok, stop clients.
        producer.stopSending();

        receiver.interrupt();

        producer.join();

        receiver.join();

        stopServer(CONTAINER1);
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
//        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

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

//        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

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

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.close();

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
