package org.jboss.qa.hornetq.test.journal;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Producer;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.Receiver;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.ColocatedReplicatedHA;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.FileTools;
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
@Category(Functional.class)
public class CleanUpOldReplicasTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(CleanUpOldReplicasTestCase.class);

    private static Set<String> journalInterestDirs;

    @Before
    public void setup() {
        if (ContainerUtils.isEAP7(container(1))) {
            journalInterestDirs = new HashSet<String>(Arrays.asList("journal", "paging", "largemessages", "bindings"));
        } else {
            journalInterestDirs = new HashSet<String>(Arrays.asList("messagingjournal", "messagingpaging", "messaginglargemessages", "messagingbindings"));
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
    @Prepare(value = "ReplicatedHA", params = {
            @Param(name = PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, value = "0"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 512 * 1024)
    })
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
    @Prepare(value = "ReplicatedHA", params = {
            @Param(name = PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, value = "2"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 512 * 1024)
    })
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
    @Prepare(value = "ReplicatedHA", params = {
            @Param(name = PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, value = "2"),
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 512 * 1024)
    })
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
    @Prepare(value = "ReplicatedHA", params = {
            @Param(name = PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, value = "4"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 512 * 1024)
    })
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
        container(1).start();
        container(2).start();

        int journalToDelete = 0;

        if (maxReplicatedJournalSize > 0) {
            Random randomGenerator = new Random();
            journalToDelete = randomGenerator.nextInt(maxReplicatedJournalSize);
        }
        logger.info("JournalToDelete: " + journalToDelete);

        Thread.sleep(10000);

        TextMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 50000);
        producer.addMessageVerifier(messageVerifier);
        producer.setMessageBuilder(messageBuilder);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI, 60000, 10, 10);
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

            JournalPrint journalPrintBeforeStartNode1 = JournalPrint.getJournalPrint(getMessagingDataDirectory(container(1)), true);
            JournalPrint journalPrintBeforeStartNode2 = JournalPrint.getJournalPrint(getMessagingDataDirectory(container(2)), false);

            if (randomDeletion && numberOfFailovers == 5) {
                deleteOldJournal(getMessagingDataDirectory(container(2)), journalToDelete);
            }

            container(1).start();


            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            JournalPrint journalPrintAfterStartNode1 = JournalPrint.getJournalPrint(getMessagingDataDirectory(container(1)), false);

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

            JournalPrint journalPrintAfterStartNode2 = JournalPrint.getJournalPrint(getMessagingDataDirectory(container(2)), false);

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
    @Prepare(value = "ColocatedReplicatedHA", params = {
            @Param(name = PrepareParams.MAX_SAVED_REPLICATED_JOURNAL_SIZE, value = "2"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 512 * 1024)
    })
    public void testColocatedMaxReplicatedJournalSize() throws Exception {

        int maxReplicatedJournalSize = 2;
        MessageBuilder messageBuilder = new TextMessageBuilder(20 * 1024);

        File container1DataDirLive = new File(getDataDirectory(container(1)), ColocatedReplicatedHA.JOURNALS_DIRECTORY_LIVE);
        File container1DataDirBackup = new File(getDataDirectory(container(1)), ColocatedReplicatedHA.JOURNALS_DIRECTORY_BACKUP);
        File container2DataDirLive = new File(getDataDirectory(container(2)), ColocatedReplicatedHA.JOURNALS_DIRECTORY_LIVE);
        File container2DataDirBackup = new File(getDataDirectory(container(2)), ColocatedReplicatedHA.JOURNALS_DIRECTORY_BACKUP);

        container(1).start();
        container(2).start();

        Thread.sleep(10000);

        TextMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, 50000);
        producer.addMessageVerifier(messageVerifier);
        producer.setMessageBuilder(messageBuilder);
        addClient(producer);

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI, 60000, 10, 10);
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
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), PrepareConstants.BACKUP_SERVER_NAME, 300000);

            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            JournalPrint journalPrintBeforeStartNode1 = JournalPrint.getJournalPrint(container1DataDirLive, true);
            JournalPrint journalPrintBeforeStartNode2 = JournalPrint.getJournalPrint(container2DataDirBackup, false);

            container(1).start();


            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            JournalPrint journalPrintAfterStartNode1 = JournalPrint.getJournalPrint(container1DataDirLive, false);

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), PrepareConstants.SERVER_NAME, 600000);

            // check that backup is really down
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), PrepareConstants.BACKUP_SERVER_NAME, 60000);

            Assert.assertTrue(producer.isAlive());
            Assert.assertTrue("Receiver crashed so crashing the test - this happens when client detects duplicates " +
                    "- check logs for message id of duplicated message", receiver.isAlive());

            waitForProducerToFailover(producer, 60000);
            waitForReceiverToFailover(receiver, 60000);

            Thread.sleep(5000); // give it some time

            JournalPrint journalPrintAfterStartNode2 = JournalPrint.getJournalPrint(container2DataDirBackup, false);

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

        return new File(path.toString());
    }

    private File getMessagingDataDirectory(Container container) {
        File dataDirectory = getDataDirectory(container);

        if (ContainerUtils.isEAP7(container)) {
            File messagingDataDirectory = new File(dataDirectory, "activemq");
            return messagingDataDirectory;
        }

        return dataDirectory;
    }

    private File getPagingDir(Container container) {
        StringBuilder path = new StringBuilder(getMessagingDataDirectory(container).getPath());

        if (ContainerUtils.isEAP7(container)) {
            path.append(File.separator); path.append("paging");
        } else {
            path.append(File.separator); path.append("messagingpaging");
        }
        logger.info("PATH: " + path);
        return new File(path.toString());
    }

    private void deleteOldJournal(File messagingDataDir, int journalToDelete) throws Exception {

        for (String fileName : messagingDataDir.list()) {
            if (journalInterestDirs.contains(fileName)) {
                File journalDir = new File(messagingDataDir, fileName);
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

        public static JournalPrint getJournalPrint(File journalDirectory, boolean captureActiveJournal) throws Exception {
            logger.info("Getting fingerprint of directory: " + journalDirectory.getPath());

            JournalPrint result = new JournalPrint(journalInterestDirs);

            List<String> journalDirs = Arrays.asList(journalDirectory.list());
            Collections.sort(journalDirs);

            for (String journalDir : journalDirs) {
                if (journalInterestDirs.contains(journalDir)) {
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
