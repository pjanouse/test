package org.jboss.qa.hornetq.test.journal;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Producer;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.Receiver;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.FileTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The test case tests whether old replicas are removed when their number exceeds max-saved-replicated-journal-size.
 * This functionality is not enabled by default. The EAP has to be start with the system property -Dhornetq.enforce.maxreplica=false.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class CleanUpOldReplicasTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(CleanUpOldReplicasTestCase.class);

    private static final String QUEUE_NAME = "testQueue";

    private static final String QUEUE_JNDI = "queue/testQueue";

    private static final String TOPIC_NAME = "testTopic";

    private static final String TOPIC_JNDI = "topic/testTopic";

    private static final String BACKUP_SERVER_NAME = "backup";

    private static final String LIVE_SERVER_NAME = "default";

    private static Set<String> journalInterestDirs;

    private static Set<String> journalInterestDirsBackup;

    @Before
    public void stopServers() {
        container(1).stop();
        container(2).stop();

        if (ContainerUtils.isEAP7(container(1))) {
            journalInterestDirs = new HashSet<String>(Arrays.asList("journal", "paging", "largemessages", "bindings"));
            journalInterestDirsBackup = new HashSet<String>(Arrays.asList("journal-backup", "paging-backup", "largemessages-backup", "bindings-backup"));
        } else {
            journalInterestDirs = new HashSet<String>(Arrays.asList("messagingjournal", "messagingpaging", "messaginglargemessages", "messagingbindings"));
            journalInterestDirsBackup = new HashSet<String>(Arrays.asList("messagingjournal-backup", "messagingpaging-backup", "messaginglargemessages-backup", "messagingbindings-backup"));
        }
    }

    /**
     * @tpTestDetails Test that no old replicas are stored.
     * @tpProcedure <ul>
     *     <li>Start live and backup in dedicated topology (max-replicated-journal-size=0)</li>
     *     <li>Start producer and receiver on queue</li>
     *     <li>10 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Stop producer and wait for receiver to finish</li>
     * </ul>
     * @tpPassCrit no lost/duplicated messages, there should be no moved journals (with digit suffix directory) in live and backup
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMaxReplicatedJournalSize0() throws Exception {
        // Uncomment when https://bugzilla.redhat.com/show_bug.cgi?id=1348588 and https://issues.jboss.org/browse/JBEAP-5086 will be fixed
//        testMaxReplicatedJournalSize(0, new MixMessageBuilder(120 * 1024), true);
        testMaxReplicatedJournalSize(0, new TextMessageBuilder(20 * 1024), true, false);
    }

    /**
     * Test that maximum two old replicas are stored. Replicas beyond the maximum must be removed.
     * @tpProcedure <ul>
     *     <li>Start live and backup in dedicated topology (max-replicated-journal-size=2)</li>
     *     <li>Start producer and slow receiver on queue - this will make large journal over time to replicate</li>
     *     <li>Producer will send all types of messages (normal and large) and will trigger paging</li>
     *     <li>10 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Stop producer and wait for receiver to finish (in this stage make receiver fast by removing pauses between receives)</li>
     * </ul>
     * @tpPassCrit no lost/duplicated messages, check that only oldest journals were deleted from live and backup.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMaxReplicatedJournalSize2() throws Exception {
        // Uncomment when https://bugzilla.redhat.com/show_bug.cgi?id=1348588 and https://issues.jboss.org/browse/JBEAP-5086 will be fixed
//        testMaxReplicatedJournalSize(2, new MixMessageBuilder(120 * 1024), true);
        testMaxReplicatedJournalSize(2, new TextMessageBuilder(20 * 1024), true, false);
    }

    /**
     * Test that old replicas are correctly removed even when no paging or large message directories exist.
     * @tpProcedure <ul>
     *     <li>Start live and backup in dedicated topology (max-replicated-journal-size=2)</li>
     *     <li>Start producer and receiver on queue</li>
     *     <li>Send only normal messages and disable paging</li>
     *     <li>10 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Stop producer and wait for receiver to finish</li>
     * </ul>
     * @tpPassCrit no lost/duplicated messages, check that only oldest journals were deleted in live and backup
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    // https://bugzilla.redhat.com/show_bug.cgi?id=1369322
    public void testNoPagingLargeMessagesDirectories() throws Exception {
        testMaxReplicatedJournalSize(2, new TextMessageBuilder(20 * 1024), false, false);
    }

    /**
     * Test that old replicas are correctly removed even when some old replica is removed manually.
     * @tpProcedure <ul>
     *     <li>Start live and backup in dedicated topology (max-replicated-journal-size=4)</li>
     *     <li>Start producer and receiver on queue</li>
     *     <li>5 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Remove random journal directory which is NOT currently in use</li>
     *     <li>5 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Stop producer and wait for receiver to finish</li>
     * </ul>
     * @tpPassCrit no lost/duplicated messages, check that oldest and “random” journal directories were deleted, no other errors occur
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRandomJournalDeletion() throws Exception {
        // Uncomment when https://bugzilla.redhat.com/show_bug.cgi?id=1348588 and https://issues.jboss.org/browse/JBEAP-5086 will be fixed
//        testMaxReplicatedJournalSize(2, new MixMessageBuilder(120 * 1024), true);
        testMaxReplicatedJournalSize(4, new TextMessageBuilder(20 * 1024), true, true);
    }

    @BMRules({
            @BMRule(name = "HornetQ: Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();"),
            @BMRule(name = "Artemis: Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();")
    })
    public void testMaxReplicatedJournalSize(int maxReplicatedJournalSize, MessageBuilder messageBuilder, boolean pagingEnabled, boolean randomDeletion) throws Exception {
        prepareServers(maxReplicatedJournalSize, pagingEnabled);
        container(1).start();
        container(2).start();

        Random randomGenerator = new Random();
        int journalToDelete = randomGenerator.nextInt(maxReplicatedJournalSize);
        logger.info("JournalToDelete: " + journalToDelete);

        Thread.sleep(10000);

        TextMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerTransAck producer = new ProducerTransAck(container(1), QUEUE_JNDI, 50000);
        producer.addMessageVerifier(messageVerifier);
        producer.setMessageBuilder(messageBuilder);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), QUEUE_JNDI, 60000, 10, 10);
        receiver.addMessageVerifier(messageVerifier);
        receiver.setTimeout(0);
        addClient(receiver);

        producer.start();
        receiver.start();

        ClientUtils.waitForReceiverUntil(receiver, 200, 60000);
        ClientUtils.waitForReceiverUntil(producer, 100, 60000);

        if (pagingEnabled) {
            receiver.setTimeout(500);
        }

        for (int numberOfFailovers = 0; numberOfFailovers < 10; numberOfFailovers++) {

            logger.warn("########################################");
            logger.warn("Running new cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            if (pagingEnabled) {
                waitForPaging(container(1));
            }

            if (numberOfFailovers % 2 == 0) {

                logger.warn("########################################");
                logger.warn("Kill live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minutes", waitUntilFileExists("target/synced", 120000));
                Thread.sleep(5000);
                container(1).kill();

            } else {

                logger.warn("########################################");
                logger.warn("Shutdown live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minutes", waitUntilFileExists("target/synced", 120000));
                Thread.sleep(5000);
                container(1).stop();
            }

            logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 300000);

            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            JournalPrint journalPrintBeforeStartNode1 = JournalPrint.getJournalPrint(getDataDirectory(container(1)), false, true);
            JournalPrint journalPrintBeforeStartNode2 = JournalPrint.getJournalPrint(getDataDirectory(container(2)), false, false);

            if (randomDeletion && numberOfFailovers == 5) {
                deleteOldJournal(container(2), journalToDelete);
            }

            container(1).start();


            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            JournalPrint journalPrintAfterStartNode1 = JournalPrint.getJournalPrint(getDataDirectory(container(1)), false, false);

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 600000);

            // check that backup is really down
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), 60000);

            Assert.assertTrue(producer.isAlive());
            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            JournalPrint journalPrintAfterStartNode2 = JournalPrint.getJournalPrint(getDataDirectory(container(2)), false, false);

            journalPrintBeforeStartNode1.moveFirstToLast();
            journalPrintBeforeStartNode1.removeFirstIfExceedMax(2);
            Assert.assertTrue(journalPrintBeforeStartNode1.getMaxNumberOfJournals() <= 2);

            Assert.assertTrue(journalPrintAfterStartNode1.getMaxNumberOfJournals() <= 2);

            if (randomDeletion && numberOfFailovers == 5) {
                journalPrintBeforeStartNode2.remove(journalToDelete);
            } else {
                journalPrintBeforeStartNode2.removeFirstIfExceedMax(maxReplicatedJournalSize - 1);
            }

            Assert.assertTrue(journalPrintBeforeStartNode2.getMaxNumberOfJournals() <= maxReplicatedJournalSize);

            journalPrintAfterStartNode2.removeLast();
            Assert.assertTrue(journalPrintAfterStartNode2.getMaxNumberOfJournals() <= maxReplicatedJournalSize);

            Assert.assertEquals(journalPrintBeforeStartNode1, journalPrintAfterStartNode1);
            Assert.assertEquals(journalPrintBeforeStartNode2, journalPrintAfterStartNode2);

            logger.warn("########################################");
            logger.warn("Ending cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

        }

        producer.stopSending();
        receiver.setTimeout(0);
        receiver.join(300000);

        Assert.assertFalse(producer.isAlive());
        Assert.assertFalse(receiver.isAlive());

        container(1).stop();
        container(2).stop();

        Assert.assertTrue("There are failures detected by MessageVerifier. More information in log.", messageVerifier.verifyMessages());
    }

    /**
     * Test that old replicas are correctly removed in colocated HA topology.
     * @tpProcedure <ul>
     *     <li>Start 2 EAP servers in colocated topology (max-replicated-journal-size=2)</li>
     *     <li>Start producer and receiver on queue</li>
     *     <li>Send all sizes and types of messages</li>
     *     <li>10 times failover/failback  - either using kill or clean shutdown</li>
     *     <li>Stop producer and wait for receiver to finish</li>
     * </ul>
     * @tpPassCrit no lost/duplicated messages, check that only oldest journals were deleted
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "HornetQ: Kill server after the backup is synced with live",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();"),
            @BMRule(name = "Artemis: Kill server after the backup is synced with live",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendCommitRecord",
                    condition = "!$0.isSynchronizing() && flag(\"synced\")",
                    action = "System.out.println(\"Byteman - Synchronization with backup is done.\");(new java.io.File(\"target/synced\")).createNewFile();")
    })
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    // https://bugzilla.redhat.com/show_bug.cgi?id=1369322
    public void testColocatedMaxReplicatedJournalSize() throws Exception {

        int maxReplicatedJournalSize = 2;
        MessageBuilder messageBuilder = new TextMessageBuilder(20 * 1024);

        prepareColocatedTopology(maxReplicatedJournalSize);
        container(1).start();
        container(2).start();

        Thread.sleep(10000);

        TextMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerTransAck producer = new ProducerTransAck(container(1), QUEUE_JNDI, 50000);
        producer.addMessageVerifier(messageVerifier);
        producer.setMessageBuilder(messageBuilder);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), QUEUE_JNDI, 60000, 10, 10);
        receiver.addMessageVerifier(messageVerifier);
        receiver.setTimeout(0);
        addClient(receiver);

        producer.start();
        receiver.start();

        ClientUtils.waitForReceiverUntil(receiver, 200, 60000);
        ClientUtils.waitForReceiverUntil(producer, 100, 60000);

        for (int numberOfFailovers = 0; numberOfFailovers < 10; numberOfFailovers++) {

            logger.warn("########################################");
            logger.warn("Running new cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            if (numberOfFailovers % 2 == 0) {

                logger.warn("########################################");
                logger.warn("Kill live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minutes", waitUntilFileExists("target/synced", 120000));
                Thread.sleep(5000);
                container(1).kill();

            } else {

                logger.warn("########################################");
                logger.warn("Shutdown live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                RuleInstaller.installRule(this.getClass(), container(1));
                Assert.assertTrue("Live was not synced with backup in 2 minutes", waitUntilFileExists("target/synced", 120000));
                Thread.sleep(5000);
                container(1).stop();
            }

            logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), BACKUP_SERVER_NAME, 300000);

            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            JournalPrint journalPrintBeforeStartNode1 = JournalPrint.getJournalPrint(getDataDirectory(container(1)), false, true);
            JournalPrint journalPrintBeforeStartNode2 = JournalPrint.getJournalPrint(getDataDirectory(container(2)), true, false);

            container(1).start();


            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            JournalPrint journalPrintAfterStartNode1 = JournalPrint.getJournalPrint(getDataDirectory(container(1)), false, false);

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), LIVE_SERVER_NAME, 600000);

            // check that backup is really down
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), BACKUP_SERVER_NAME, 60000);

            Assert.assertTrue(producer.isAlive());
            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            JournalPrint journalPrintAfterStartNode2 = JournalPrint.getJournalPrint(getDataDirectory(container(2)), true, false);

            journalPrintBeforeStartNode1.moveFirstToLast();
            journalPrintBeforeStartNode1.removeFirstIfExceedMax(2);
            Assert.assertTrue(journalPrintBeforeStartNode1.getMaxNumberOfJournals() <= 2);

            Assert.assertTrue(journalPrintAfterStartNode1.getMaxNumberOfJournals() <= 2);

            journalPrintBeforeStartNode2.removeFirstIfExceedMax(maxReplicatedJournalSize - 1);

            Assert.assertTrue(journalPrintBeforeStartNode2.getMaxNumberOfJournals() <= maxReplicatedJournalSize);

            journalPrintAfterStartNode2.removeLast();
            Assert.assertTrue(journalPrintAfterStartNode2.getMaxNumberOfJournals() <= maxReplicatedJournalSize);

            Assert.assertEquals(journalPrintBeforeStartNode1, journalPrintAfterStartNode1);
            Assert.assertEquals(journalPrintBeforeStartNode2, journalPrintAfterStartNode2);

            logger.warn("########################################");
            logger.warn("Ending cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

        }

        producer.stopSending();
        receiver.setTimeout(0);
        receiver.join(300000);

        Assert.assertFalse(producer.isAlive());
        Assert.assertFalse(receiver.isAlive());

        container(1).stop();
        container(2).stop();

        Assert.assertTrue("There are failures detected by MessageVerifier. More information in log.", messageVerifier.verifyMessages());
    }

    protected void prepareServers(int maxReplicatedJournalSize, boolean pagingEnabled) {
        if (ContainerUtils.isEAP7(container(1))) {
            prepareLiveEAP7(container(1), pagingEnabled);
            prepareBackupEAP7(container(2), maxReplicatedJournalSize, pagingEnabled);
        } else {
            prepareLiveEAP6(container(1), pagingEnabled);
            prepareBackupEAP6(container(2), maxReplicatedJournalSize, pagingEnabled);
        }
    }

    protected void prepareColocatedTopology(int maxReplicatedJournalSize) {
        if (ContainerUtils.isEAP7(container(1))) {
            prepareColocatedLiveEAP7(container(1), "group-0");
            prepareColocatedBackupEAP7(container(1), "group-1", maxReplicatedJournalSize);
            prepareColocatedLiveEAP7(container(2), "group-1");
            prepareColocatedBackupEAP7(container(2), "group-0", maxReplicatedJournalSize);
        } else {
            prepareColocatedLiveEAP6(container(1), "group-0");
            prepareColocatedBackupEAP6(container(1), "group-1", maxReplicatedJournalSize);
            prepareColocatedLiveEAP6(container(2), "group-1");
            prepareColocatedBackupEAP6(container(2), "group-0", maxReplicatedJournalSize);
        }
    }

    private void prepareLiveEAP6(Container container, boolean pagingEnabled) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        String fullPolicy = pagingEnabled ? "PAGE" : "BLOCK";
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", fullPolicy, 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group0");
        jmsOperations.setCheckForLiveServer(true);
        jmsOperations.setAllowFailback(true);
        jmsOperations.setFailoverOnShutdown(true);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        jmsOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsOperations.close();
        container.stop();
    }

    private void prepareBackupEAP6(Container container, int maxReplicatedJournalSize, boolean pagingEnabled) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        String fullPolicy = pagingEnabled ? "PAGE" : "BLOCK";
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", fullPolicy, 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName("group0");
        jmsOperations.setCheckForLiveServer(true);
        jmsOperations.setAllowFailback(true);
        jmsOperations.setFailoverOnShutdown(true);

        jmsOperations.setMaxSavedReplicatedJournals(maxReplicatedJournalSize);
        jmsOperations.setBackup(true);

        jmsOperations.close();
        container.stop();
    }

    private void prepareLiveEAP7(Container container, boolean pagingEnabled) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        String fullPolicy = pagingEnabled ? "PAGE" : "BLOCK";
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", fullPolicy, 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.addHAPolicyReplicationMaster(true, "my-cluster", "group0");
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        jmsOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);
        configureNetty(jmsOperations);

        jmsOperations.close();
        container.stop();
    }

    private void prepareBackupEAP7(Container container, int maxReplicatedJournalSize, boolean pagingEnabled) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        String fullPolicy = pagingEnabled ? "PAGE" : "BLOCK";
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", fullPolicy, 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.addHAPolicyReplicationSlave(true, "my-cluster", 0, "group0", maxReplicatedJournalSize, true, false, null, null, null, null);
        configureNetty(jmsOperations);

        jmsOperations.close();
        container.stop();
    }

    private void prepareColocatedLiveEAP6(Container container, String groupName) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.setSecurityEnabled(false);

        jmsOperations.setSharedStore(false);
        jmsOperations.setBackupGroupName(groupName);
        jmsOperations.setCheckForLiveServer(true);
        jmsOperations.setAllowFailback(true);
        jmsOperations.setFailoverOnShutdown(true);

        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        jmsOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);

        jmsOperations.close();
        container.stop();
    }

    private void prepareColocatedBackupEAP6(Container container, String groupName, int maxReplicatedJournalSize) {
        final String discoveryGroupName = "dg-group-backup";
        final String broadCastGroupName = "bg-group-backup";
        final String clusterGroupName = "my-cluster";
        final String connectorName = "netty-backup";
        final String acceptorName = "netty-backup";
        final String socketBindingName = "messaging-backup";
        final String messagingGroupSocketBindingName = "messaging-group";
        final int socketBindingPort = Constants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6;

        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addMessagingSubsystem(BACKUP_SERVER_NAME);

        jmsOperations.removeAddressSettings(BACKUP_SERVER_NAME, "#");
        jmsOperations.addAddressSettings(BACKUP_SERVER_NAME, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.setSecurityEnabled(BACKUP_SERVER_NAME, false);

        jmsOperations.setSharedStore(BACKUP_SERVER_NAME, false);
        jmsOperations.setBackupGroupName(groupName, BACKUP_SERVER_NAME);
        jmsOperations.setCheckForLiveServer(true, BACKUP_SERVER_NAME);
        jmsOperations.setAllowFailback(BACKUP_SERVER_NAME, true);
        jmsOperations.setFailoverOnShutdown(true, BACKUP_SERVER_NAME);

        jmsOperations.setMaxSavedReplicatedJournals(BACKUP_SERVER_NAME, maxReplicatedJournalSize);
        jmsOperations.setBackup(BACKUP_SERVER_NAME, true);

        jmsOperations.setClustered(BACKUP_SERVER_NAME, true);
        jmsOperations.setPersistenceEnabled(BACKUP_SERVER_NAME, true);

        jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, connectorName, socketBindingName, null);
        jmsOperations.createRemoteAcceptor(BACKUP_SERVER_NAME, acceptorName, socketBindingName, null);

        jmsOperations.setBroadCastGroup(BACKUP_SERVER_NAME, broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsOperations.setDiscoveryGroup(BACKUP_SERVER_NAME, discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsOperations.setClusterConnections(BACKUP_SERVER_NAME, clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsOperations.setJournalDirectoryPath(BACKUP_SERVER_NAME, "messagingjournal-backup");
        jmsOperations.setPagingDirectoryPath(BACKUP_SERVER_NAME, "messagingpaging-backup");
        jmsOperations.setLargeMessagesDirectoryPath(BACKUP_SERVER_NAME, "messaginglargemessages-backup");
        jmsOperations.setBindingsDirectoryPath(BACKUP_SERVER_NAME, "messagingbindings-backup");

        jmsOperations.close();
        container.stop();
    }

    private void prepareColocatedLiveEAP7(Container container, String groupName) {
        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setSecurityEnabled(false);

        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.addHAPolicyReplicationMaster(true, "my-cluster", groupName);
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI);
        jmsOperations.createTopic(TOPIC_NAME, TOPIC_JNDI);
        configureNetty(jmsOperations);

        jmsOperations.close();
        container.stop();
    }

    private void prepareColocatedBackupEAP7(Container container, String groupName, int maxReplicatedJournalSize) {

        final String discoveryGroupName = "dg-group-backup";
        final String broadCastGroupName = "bg-group-backup";
        final String connectorName = "netty-backup";
        final String acceptorName = "netty-backup";
        final String inVmConnectorName = "in-vm";
        final String socketBindingName = "messaging-backup";
        final int socketBindingPort = Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7;
        final String jgroupsChannel = "activemq-cluster";
        final String clusterConnectionName = "my-cluster";

        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addMessagingSubsystem(BACKUP_SERVER_NAME);

        jmsOperations.setSecurityEnabled(BACKUP_SERVER_NAME, false);

        jmsOperations.setPersistenceEnabled(BACKUP_SERVER_NAME, true);
        jmsOperations.setJournalDirectoryPath(BACKUP_SERVER_NAME, "activemq/journal-backup");
        jmsOperations.setPagingDirectoryPath(BACKUP_SERVER_NAME, "activemq/paging-backup");
        jmsOperations.setLargeMessagesDirectoryPath(BACKUP_SERVER_NAME, "activemq/largemessages-backup");
        jmsOperations.setBindingsDirectoryPath(BACKUP_SERVER_NAME, "activemq/bindings-backup");

        jmsOperations.addAddressSettings(BACKUP_SERVER_NAME, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, connectorName, socketBindingName, null);
        jmsOperations.createInVmConnector(BACKUP_SERVER_NAME, inVmConnectorName, 0, null);
        jmsOperations.createRemoteAcceptor(BACKUP_SERVER_NAME, acceptorName, socketBindingName, null);

        jmsOperations.setBroadCastGroup(BACKUP_SERVER_NAME, broadCastGroupName, null, jgroupsChannel, 1000, connectorName);
        jmsOperations.setDiscoveryGroup(BACKUP_SERVER_NAME, discoveryGroupName, 1000, null, jgroupsChannel);
        jmsOperations.setClusterConnections(BACKUP_SERVER_NAME, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsOperations.addHAPolicyReplicationSlave(BACKUP_SERVER_NAME, true, clusterConnectionName, 0, groupName, maxReplicatedJournalSize, true, false, null, null, null, null);

        jmsOperations.close();
        container.stop();
    }

    private void configureNetty(JMSOperations jmsOperations) {
        jmsOperations.addSocketBinding("netty", 5445);

        jmsOperations.reload();

        jmsOperations.createRemoteAcceptor("remote-acceptor", "netty", null);
        jmsOperations.createRemoteConnector("remote-connector", "netty", null);

        jmsOperations.removeClusteringGroup("my-cluster");
        jmsOperations.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, "remote-connector");

        jmsOperations.removeBroadcastGroup("bg-group1");
        jmsOperations.setBroadCastGroup("bg-group1", null, "activemq-cluster", 1000, "remote-connector");

        jmsOperations.setConnectorOnConnectionFactory("RemoteConnectionFactory", "remote-connector");
    }

    private boolean waitUntilFileExists(String path, long timeout) throws InterruptedException {
        File file = new File(path);
        long timeToWait = System.currentTimeMillis() + timeout;
        while (!file.exists() && System.currentTimeMillis() < timeToWait) {
            Thread.sleep(100);
        }
        if (file.exists()) {
            file.delete();
            return true;
        } else {
            return false;
        }
    }

    private void waitForProducerToFailover(Producer client, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int initialCount = client.getListOfSentMessages().size();
        while (client.isAlive() && client.getListOfSentMessages().size() <= initialCount) {
            if (System.currentTimeMillis() - startTime > timeout) {
                Assert.fail("Client - " + client.toString() + " did not failover/failback in: " + timeout + " ms");
            }
            Thread.sleep(1000);
        }
    }

    private void waitForReceiverToFailover(Receiver client, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int initialCount = client.getListOfReceivedMessages().size();
        while (client.isAlive() && client.getListOfReceivedMessages().size() <= initialCount) {
            if (System.currentTimeMillis() - startTime > timeout) {
                Assert.fail("Client - " + client.toString() + " did not failover/failback in: " + timeout + " ms");
            }
            Thread.sleep(1000);
        }
    }

    private void waitForPaging(Container container) throws InterruptedException {

        long timeout = 120000;
        long timeToWait = System.currentTimeMillis() + timeout;

        File pagingDir = getPagingDir(container);

        while (System.currentTimeMillis() < timeToWait) {
            if (pagingDir.exists()) {
                Iterator it = FileUtils.iterateFiles(pagingDir, new String[] {"page"}, true);

                if (it.hasNext()) {
                    return;
                }
            }
            Thread.sleep(1000);
        }
        Assert.fail("Paging did not start in 120 sec.");
    }

    private File getDataDirectory(Container container) {
        StringBuilder path = new StringBuilder(container.getServerHome());
        path.append(File.separator); path.append("standalone");
        path.append(File.separator); path.append("data");

        if (ContainerUtils.isEAP7(container)) {
            path.append(File.separator); path.append("activemq");
        }
        return new File(path.toString());
    }

    private File getPagingDir(Container container) {
        StringBuilder path = new StringBuilder(getDataDirectory(container).getPath());

        if (ContainerUtils.isEAP7(container)) {
            path.append(File.separator); path.append("paging");
        } else {
            path.append(File.separator); path.append("messagingpaging");
        }
        logger.info("PATH: " + path);
        return new File(path.toString());
    }

    private void deleteOldJournal(Container container, int journalToDelete) throws Exception {
        File dataDir = getDataDirectory(container);

        for (String fileName : dataDir.list()) {
            if (journalInterestDirs.contains(fileName)) {
                File journalDir = new File(dataDir, fileName);
                List<String> files = Arrays.asList(journalDir.list());
                List<String> oldReplicas = new ArrayList<String>();

                for (String file : files) {
                    if (file.startsWith("oldreplica.")) {
                        oldReplicas.add(file);
                    }
                }

                Collections.sort(oldReplicas, new ImprovedStringComparator());

                if (oldReplicas.size() > journalToDelete) {
                    String oldReplicaToDelete = oldReplicas.get(journalToDelete);
                    File dirToDelete = new File(journalDir, oldReplicaToDelete);
                    logger.info("Deleting directory: " + dirToDelete);
                    FileUtils.deleteDirectory(dirToDelete);
                }
            }
        }
    }

    private static class ImprovedStringComparator implements Comparator<String> {

        @Override
        public int compare(String s1, String s2) {
            String ms1 = removeNumeralSuffix(s1);
            String ms2 = removeNumeralSuffix(s2);

            if (ms1.equals(ms2)) {
                return extractInt(s1) - extractInt(s2);
            } else {
                return ms1.compareTo(ms2);
            }
        }

        int extractInt(String s) {
            String num = s.replaceAll("\\D", "");
            // return 0 if no digits found
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        }

        String removeNumeralSuffix(String s) {
            return s.replaceAll("\\d$", "");
        }
    }

    private static class JournalPrint {

        private Map<String, List<String>> data = new HashMap<String, List<String>>();

        public static JournalPrint getJournalPrint(File journalDirectory, boolean backup, boolean captureActiveJournal) throws Exception {
            logger.info("Getting fingerprint of directory: " + journalDirectory.getPath());

            Set<String> interestDirs;
            if (backup) {
                interestDirs = journalInterestDirsBackup;
            } else {
                interestDirs = journalInterestDirs;
            }

            JournalPrint result = new JournalPrint(interestDirs);

            List<String> journalDirs = Arrays.asList(journalDirectory.list());
            Collections.sort(journalDirs);

            for (String journalDir : journalDirs) {
                if (interestDirs.contains(journalDir)) {
                    File journalDirFile = new File(journalDirectory, journalDir);
                    if (captureActiveJournal) {
                        String fingerPrint = FileTools.getFingerPrint(journalDirFile, "oldreplica\\..+");
                        if (fingerPrint != null) {
                            result.getJournal(journalDir).add(fingerPrint);
                        }
                    }
                    List<String> oldReplicaDirs = Arrays.asList(journalDirFile.list());
                    Collections.sort(oldReplicaDirs, new ImprovedStringComparator());
                    for (String oldReplicaDir : oldReplicaDirs) {
                        if (oldReplicaDir.startsWith("oldreplica.")) {
                            String fingerPrint = FileTools.getFingerPrint(new File(journalDirFile, oldReplicaDir));
                            if (fingerPrint != null) {
                                result.getJournal(journalDir).add(fingerPrint);
                            }
                        }
                    }
                }
            }
            return result;
        }

        public void moveFirstToLast() {
            for (String journalName : data.keySet()) {
                List<String> journal = data.get(journalName);

                if (journal.size() > 0) {
                    String first = journal.remove(0);
                    journal.add(first);
                }
            }
        }

        public void removeFirstIfExceedMax(int maxJournalSize) {
            for (String journalName : data.keySet()) {
                List<String> journal = data.get(journalName);

                if (journal.size() > 0 && journal.size() > maxJournalSize) {
                    journal.remove(0);
                }
            }
        }

        public void removeLast() {
            for (String journalName : data.keySet()) {
                List<String> journal = data.get(journalName);

                if (journal.size() > 0) {
                    journal.remove(journal.size() - 1);
                }
            }
        }

        public void remove(int pos) {
            for (String journalName : data.keySet()) {
                List<String> journal = data.get(journalName);

                if (journal.size() > pos) {
                    journal.remove(pos);
                }
            }
        }

        public int getMaxNumberOfJournals() {
            int max = 0;

            for (String journalName : data.keySet()) {
                List<String> journal = data.get(journalName);

                max = Math.max(max, journal.size());
            }

            return max;
        }

        @Override
        public String toString() {
            return "JournalPrint{" +
                    "data=" + data +
                    '}';
        }

        @Override
        public boolean equals(Object o) {

            if (!(o instanceof JournalPrint)) {
                logger.error("o is not instanceof JournalPrint");
                return false;
            }

            JournalPrint that = (JournalPrint) o;

            for (String journalName : data.keySet()) {
                List<String> journal1 = this.getJournal(journalName);
                List<String> journal2 = that.getJournal(journalName);

                if (journal1.size() != journal2.size()) {
                    logger.error(String.format("Sizes of journals are different:\njournal1(%d): %s\njournal2(%d): %s", journal1.size(), journal1.toString(), journal2.size(), journal2.toString()));
                    return false;
                }

                for (int i = 0; i < journal1.size(); i++) {
                    if (!journal1.get(i).equals(journal2.get(i))) {
                        logger.error(String.format("Journals are different: %s != %s", journal1.toString(), journal2.toString()));
                    }
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return data != null ? data.hashCode() : 0;
        }

        private JournalPrint(Set<String> interestDirs) {
            for (String interestDir : interestDirs) {
                data.put(interestDir, new ArrayList<String>());
            }
        }

        private List<String> getJournal(String name) {
            return data.get(name);
        }

    }

}
