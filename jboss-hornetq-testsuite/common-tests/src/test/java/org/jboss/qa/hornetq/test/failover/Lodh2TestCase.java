package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * <p/>
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh2TestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5000;

    public static final String MDB_ON_QUEUE_1 = "mdb1";
    public static final String MDB_ON_QUEUE_2 = "mdb2";
    public static final String MDB_ON_QUEUE_WITH_FILTER_1 = "mdb1WithFilter";
    public static final String MDB_ON_QUEUE_WITH_FILTER_2 = "mdb2WithFilter";
    public static final String MDB_ON_NON_DURABLE_TOPIC = "nonDurableMdbOnTopic";
    public static final String MDB_WITH_PROPERTIES_MAPPED_NAME = "mdbWithPropertiesMappedName";
    public static final String MDB_WITH_PROPERTIES_NAME = "mdbWithPropertiesName";
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // inTopic
    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    String queueNamePrefix = "testQueue";

    String queueJndiNamePrefix = "jms/queue/testQueue";

    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_1)
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() throws Exception {
        File propertyFile = new File(getJbossHome(CONTAINER2) + File.separator + "mdb1.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + getHostname(CONTAINER1));
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_WITH_PROPERTIES_MAPPED_NAME)
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentMdbWithProperties() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB_WITH_PROPERTIES_MAPPED_NAME + ".jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdbWithPropertyReplacements.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_WITH_PROPERTIES_NAME)
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentMdbWithPropertiesName() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB_WITH_PROPERTIES_NAME + ".jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/" + MDB_WITH_PROPERTIES_NAME + ".jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_2)
    @TargetsContainer(CONTAINER4)
    public static Archive getDeployment2() throws Exception {

        File propertyFile = new File(getJbossHome(CONTAINER4) + File.separator + "mdb2.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + getHostname(CONTAINER3));
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_WITH_FILTER_1)
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentWithFilter1() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_WITH_FILTER_2)
    @TargetsContainer(CONTAINER4)
    public static Archive getDeploymentWithFilter2() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = MDB_ON_NON_DURABLE_TOPIC)
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentNonDurableMdbOnTopic() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /////////////////////////////// START - Local -> Remote
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killJmsLocalRemote() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false, false, CONTAINER2, CONTAINER1);
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbLocalRemote() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false, false, CONTAINER2, CONTAINER1);
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbLocalRemote() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, true, false, CONTAINER2, CONTAINER1);
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsLocalRemote() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, true, false, CONTAINER2, CONTAINER1);
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killLocalRemote() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false, false, CONTAINER2, CONTAINER1);
    }
    /////////////////////////////// END - Local -> Remote

    /////////////////////////////// START - Remote -> Local
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killJmsRemoteLocal() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false, false, CONTAINER1, CONTAINER2);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbRemoteLocal() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false, false, CONTAINER1, CONTAINER2);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsRemoteLocal() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, true, false, CONTAINER1, CONTAINER2);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbRemoteLocal() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, true, false, CONTAINER1, CONTAINER2);
    }
    /////////////////////////////// START - Remote -> Local


    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killWithFilters() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Shutdown jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3Shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2killWithTempTopic() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaWithTopic(failureSequence, false, false);

    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownOfJmsServers() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    public void testRemoteJcaWithTopic(List<String> failureSequence, boolean isShutdown, boolean isDurable) throws Exception {
        testRemoteJcaWithTopic(failureSequence, isShutdown, isDurable, CONTAINER1, CONTAINER3);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<String> failureSequence, boolean isShutdown, boolean isDurable, String inServer, String outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        // jms server
        controller.start(CONTAINER1);

        // mdb server
        controller.start(CONTAINER2);

        if (!isDurable) {
            deployer.deploy(MDB_ON_NON_DURABLE_TOPIC);
            Thread.sleep(5000);
        }

        PublisherTransAck producer1 = new PublisherTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1), getJNDIPort(CONTAINER1),
                inTopicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "clientId-myPublisher");
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.start();

        // deploy mdbs
        if (isDurable) {
            throw new UnsupportedOperationException("This was not yet implemented. Use Mdb on durable topic to do so.");
        }

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/10, 120000, CONTAINER1);

        executeFailureSequence(failureSequence, 3000, isShutdown);

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, CONTAINER1);

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1), getJNDIPort(CONTAINER1), outQueueJndiName, 3000, 10, 10);
        receiver1.setCommitAfter(100);
        receiver1.start();

        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getMessages()
                + ", Producer to jms1 server sent: " + producer1.getMessages() + " messages"));

        logger.info("Number of received messages: " + (receiver1.getCount()
                + ", Consumer from jms1 server received: " + receiver1.getCount() + " messages"));

        if (isDurable) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getMessages(), receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);

        } else {

            Assert.assertTrue("There SHOULD be different number of sent and received messages.",
                    producer1.getMessages() > receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);
            deployer.undeploy(MDB_ON_NON_DURABLE_TOPIC);
        }


        stopServer(CONTAINER2);
        stopServer(CONTAINER1);

    }

    public void testRemoteJcaInCluster(List<String> failureSequence, boolean isShutdown) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, false);
    }

    /**
     * For remote inQueue and remote OutQueue
     *
     * @param failureSequence
     * @param isShutdown
     * @param isFiltered
     */
    public void testRemoteJcaInCluster(List<String> failureSequence, boolean isShutdown, boolean isFiltered) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, isFiltered, CONTAINER1, CONTAINER3);
    }


    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(List<String> failureSequence, boolean isShutdown, boolean isFiltered, String inServer, String outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(inServer), getJNDIPort(inServer), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        // deploy mdbs
        if (isFiltered) {
            deployer.deploy(MDB_ON_QUEUE_WITH_FILTER_1);
            deployer.deploy(MDB_ON_QUEUE_WITH_FILTER_2);
        } else {
            deployer.deploy(MDB_ON_QUEUE_1);
            deployer.deploy(MDB_ON_QUEUE_2);
        }

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/100, 120000, CONTAINER1, CONTAINER2, CONTAINER3, CONTAINER4);

        executeFailureSequence(failureSequence, 5000, isShutdown);

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, CONTAINER1, CONTAINER2, CONTAINER3, CONTAINER4);

        waitUntilThereAreNoPreparedHornetQTransactions(300000, CONTAINER1);

        waitUntilThereAreNoPreparedHornetQTransactions(300000, CONTAINER3);

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(outServer), getJNDIPort(outServer), outQueueJndiName, 3000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        if (isFiltered) {
            deployer.undeploy(MDB_ON_QUEUE_WITH_FILTER_1);
            deployer.undeploy(MDB_ON_QUEUE_WITH_FILTER_2);
        } else {
            deployer.undeploy(MDB_ON_QUEUE_1);
            deployer.undeploy(MDB_ON_QUEUE_2);
        }

        stopServer(CONTAINER2);
        stopServer(CONTAINER4);
        stopServer(CONTAINER1);
        stopServer(CONTAINER3);
    }

    /**
     * @throws Exception
     */
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        String inServer = CONTAINER1;
        String outServer = CONTAINER1;

        int numberOfMessages = NUMBER_OF_MESSAGES_PER_PRODUCER;

        prepareRemoteJcaTopology(inServer, outServer);

        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(inServer), getJNDIPort(inServer), inQueueJndiName, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        deployer.deploy(MDB_ON_QUEUE_1);
        deployer.deploy(MDB_ON_QUEUE_2);

        waitForMessages(outQueueName, numberOfMessages / 100, 120000, CONTAINER1, CONTAINER3);

        stopServer(CONTAINER2);
        stopServer(CONTAINER4);

        // check there are still some messages in InQueue
        Assert.assertTrue("MDBs read all messages from InQueue before shutdown. Increase number of messages shutdown happens" +
                        " when MDB is processing messages", waitForMessages(inQueueName, 1, 10000, CONTAINER1, CONTAINER3));

        String journalFile1 = CONTAINER1 + "journal_content_after_shutdown.txt";
        String journalFile3 = CONTAINER3 + "journal_content_after_shutdown.txt";

        PrintJournal.printJournal(CONTAINER1, journalFile1);
        PrintJournal.printJournal(CONTAINER3, journalFile3);

        // check that there are failed transactions
        String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";
        String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");

        Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile1), stringToFind));
        Assert.assertFalse("There are unfinished HornetQ transactions in node-3. Failing the test.", checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile3), stringToFind));

        stopServer(CONTAINER1);
        stopServer(CONTAINER3);

        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        Assert.assertFalse("There are unfinished Arjuna transactions in node-2. Failing the test.", checkUnfinishedArjunaTransactions(CONTAINER2));
        Assert.assertFalse("There are unfinished Arjuna transactions in node-4. Failing the test.", checkUnfinishedArjunaTransactions(CONTAINER4));

        stopServer(CONTAINER2);
        stopServer(CONTAINER4);
    }


    /**
     * @throws Exception
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testPropertyReplacementWithName() throws Exception {
        testPropertyBasedMdb(MDB_WITH_PROPERTIES_NAME);
    }

    /**
     * @throws Exception
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testPropertyReplacementWithMappedName() throws Exception {
        testPropertyBasedMdb(MDB_WITH_PROPERTIES_MAPPED_NAME);
    }

    public void testPropertyBasedMdb(String mdbDeployemnt) throws Exception {
        String inServer = CONTAINER1;
        String outServer = CONTAINER1;

        prepareRemoteJcaTopology(inServer,outServer);
        // cluster A

        controller.start(CONTAINER1);

        String s = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(CONTAINER2)) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        s = containerDef.getContainerProperties().get("javaVmArguments");
                        s = s.concat(" -Djms.queue.InQueue=" + inQueueJndiName);
                        s = s.concat(" -Dpooled.connection.factory.name=java:/JmsXA");
                        containerDef.getContainerProperties().put("javaVmArguments", s);
                    }
                }
            }
        }
        Map<String,String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        controller.start(CONTAINER2, properties);

        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(inServer), getJNDIPort(inServer), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER/10);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        deployer.deploy(mdbDeployemnt);

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(outServer), getJNDIPort(outServer), outQueueJndiName, 10000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        deployer.undeploy(mdbDeployemnt);

        stopServer(CONTAINER2);
        stopServer(CONTAINER1);
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  list of container names
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<String> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (String containerName : failureSequence) {
                Thread.sleep(timeBetweenKills);
                stopServer(containerName);
                Thread.sleep(3000);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        } else {
            for (String containerName : failureSequence) {
                Thread.sleep(timeBetweenKills);
                killServer(containerName);
                Thread.sleep(3000);
                controller.kill(containerName);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        }
    }


    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER2);
        stopServer(CONTAINER4);
        stopServer(CONTAINER1);
        stopServer(CONTAINER3);
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology(String inServer, String outServer) throws Exception {

        prepareJmsServer(CONTAINER1);
        prepareMdbServer(CONTAINER2, CONTAINER1, inServer, outServer);

        prepareJmsServer(CONTAINER3);
        prepareMdbServer(CONTAINER4, CONTAINER3, inServer, outServer);

        if (isEAP6()) {
            copyApplicationPropertiesFiles();
        }
    }

    private boolean isServerRemote(String containerName) {
        if (CONTAINER1.equals(containerName) || CONTAINER3.equals(containerName)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String containerName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        if (isEAP5()) {

            int port = 9876;
            int groupPort = 9876;
            long broadcastPeriod = 500;


            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, getHostname(containerName), port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, getHostname(containerName), groupAddress, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);

            jmsAdminOperations.close();

//            deployDestinations(containerName);

        } else {


            String messagingGroupSocketBindingName = "messaging-group";

            controller.start(containerName);

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);
            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
            jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
            jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
            jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
            jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

//            jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
//            jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

            jmsAdminOperations.close();


            controller.stop(containerName);

            controller.start(containerName);

            jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

            jmsAdminOperations.close();

            deployDestinations(containerName);

            controller.stop(containerName);
        }

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareMdbServer(String containerName, String jmsServerName, String inServer, String outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        if (isEAP5()) {

            int port = 9876;

            int groupPort = 9876;
            long broadcastPeriod = 500;

            String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
            Map<String, String> connectionParameters = new HashMap<String, String>();
            connectionParameters.put(getHostname(jmsServerName), String.valueOf(getHornetqPort(jmsServerName)));
            boolean ha = false;

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, getHostname(containerName), port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, getHostname(containerName), groupAddress, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        Map<String, String> params = new HashMap<String, String>();
//        params.put("host", jmsServerBindingAddress);
//        params.put("port", "5445");
//        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "", params);

            jmsAdminOperations.setRA(connectorClassName, connectionParameters, ha);
            jmsAdminOperations.close();

        } else {

            String inVmConnectorName = "in-vm";
            String remoteConnectorName = "netty-remote";
            String messagingGroupSocketBindingName = "messaging-group";
            String inVmHornetRaName = "local-hornetq-ra";

            controller.start(containerName);

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);

            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));


            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
            jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
            jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
            jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

            jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);

            // enable trace logging
//            jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
//            jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

            // both are remote
            if (isServerRemote(inServer) && isServerRemote(outServer)) {
                jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(jmsServerName), getHornetqPort(jmsServerName));
                jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
                jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
                jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            }
            // local InServer and remote OutServer
            if (!isServerRemote(inServer) && isServerRemote(outServer)) {
                jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(jmsServerName), getHornetqPort(jmsServerName));
                jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
                jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
                jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);

                // create new in-vm pooled connection factory and configure it as default for inbound communication
                jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
                jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
            }

            // remote InServer and local OutServer
            if (isServerRemote(inServer) && !isServerRemote(outServer)) {

                // now reconfigure hornetq-ra which is used for inbound to connect to remote server
                jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(jmsServerName), getHornetqPort(jmsServerName));
                jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
                jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
                jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
                jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");

                jmsAdminOperations.close();
                stopServer(containerName);
                controller.start(containerName);
                jmsAdminOperations = this.getJMSOperations(containerName);

                // create new in-vm pooled connection factory and configure it as default for inbound communication
                jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
                jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
            }

            jmsAdminOperations.close();
            stopServer(containerName);
        }
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            copyFile(applicationUsersModified, applicationUsersOriginal);
            copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName containerName
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName container name
     * @param serverName    server name of the hornetq server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.close();

    }


}