package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 * @tpChapter 2.6 RECOVERY/FAILOVER TESTING
 * @tpSub XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh2TestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5000;

    public final Archive mdbOnQueue1 = getDeployment1();
    public final Archive mdbOnQueue2 = getDeployment2();
    public final Archive mdbOnQueueWithFilter1 = getDeploymentWithFilter1();
    public final Archive mdbOnQueueWithFilter2 = getDeploymentWithFilter2();
    public final Archive mdbOnNonDurableTopic = getDeploymentNonDurableMdbOnTopic();
    public final Archive mdbWithPropertiesMappedName = getDeploymentMdbWithProperties();
    public final Archive mdbWithPropertiesName = getDeploymentMdbWithPropertiesName();
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // inTopic
    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    public Archive getDeployment1() {
        File propertyFile = new File(container(2).getServerHome() + File.separator + "mdb1.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(1).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeployment2() {
        File propertyFile = new File(container(4).getServerHome() + File.separator + "mdb2.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(3).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentMdbWithProperties() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesMappedName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdbWithPropertyReplacements.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentMdbWithPropertiesName() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, mdbWithPropertiesName + ".jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName.class);
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/" + mdbWithPropertiesName + ".jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentWithFilter1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentWithFilter2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentNonDurableMdbOnTopic() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, true, false, container(2), container(1));
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, true, false, container(2), container(1));
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false, false, container(1), container(2));
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(1), container(2));
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, true, false, container(1), container(2));
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, true, false, container(1), container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false);
    }

    public void testRemoteJcaWithTopic(List<Container> failureSequence, boolean isShutdown, boolean isDurable) throws Exception {
        testRemoteJcaWithTopic(failureSequence, isShutdown, isDurable, container(1), container(3));
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<Container> failureSequence, boolean isShutdown, boolean isDurable, Container inServer, Container outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        // jms server
        container(1).start();

        // mdb server
        container(2).start();

        if (!isDurable) {
            container(2).deploy(mdbOnNonDurableTopic);
            Thread.sleep(5000);
        }

        PublisherTransAck producer1 = new PublisherTransAck(container(1),
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

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10, 120000, container(1));

        executeFailureSequence(failureSequence, 3000, isShutdown);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 10);
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
            container(2).undeploy(mdbOnNonDurableTopic);
        }


        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, false);
    }

    /**
     * For remote inQueue and remote OutQueue
     *
     * @param failureSequence  failure sequence
     * @param isShutdown shutdown
     * @param isFiltered filtered
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown, boolean isFiltered) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, isFiltered, container(1), container(3));
    }


    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown, boolean isFiltered, Container inServer, Container outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
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
            container(2).deploy(mdbOnQueueWithFilter1);
            container(4).deploy(mdbOnQueueWithFilter2);
        } else {
            container(2).deploy(mdbOnQueue1);
            container(4).deploy(mdbOnQueue2);
        }

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, container(1), container(2),
                container(3), container(4));

        executeFailureSequence(failureSequence, 5000, isShutdown);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1), container(2),
                container(3), container(4));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 3000, 10, 10);
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
            container(2).undeploy(mdbOnQueueWithFilter1);
            container(4).undeploy(mdbOnQueueWithFilter2);
        } else {
            container(2).undeploy(mdbOnQueue1.getName());
            container(4).undeploy(mdbOnQueue2.getName());
        }

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
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

        Container inServer = container(1);
        Container outServer = container(1);

        int numberOfMessages = NUMBER_OF_MESSAGES_PER_PRODUCER;

        prepareRemoteJcaTopology(inServer, outServer);

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        container(2).deploy(getDeployment1());
        container(4).deploy(getDeployment2());

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 100, 120000, container(1), container(3));

        container(2).stop();
        container(4).stop();

        // check there are still some messages in InQueue
        Assert.assertTrue("MDBs read all messages from InQueue before shutdown. Increase number of messages shutdown happens" +
                " when MDB is processing messages", new JMSTools().waitForMessages(inQueueName, 1, 10000, container(1), container(3)));

        String journalFile1 = CONTAINER1_NAME + "journal_content_after_shutdown.txt";
        String journalFile3 = CONTAINER3_NAME + "journal_content_after_shutdown.txt";

        container(1).getPrintJournal().printJournal(journalFile1);
        container(3).getPrintJournal().printJournal(journalFile3);

        // check that there are failed transactions
        String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";
        String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");

        Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", new TransactionUtils().checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile1), stringToFind));
        Assert.assertFalse("There are unfinished HornetQ transactions in node-3. Failing the test.", new TransactionUtils().checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile3), stringToFind));

        container(1).stop();
        container(3).stop();

        container(2).start();
        container(4).start();

        Assert.assertFalse("There are unfinished Arjuna transactions in node-2. Failing the test.", checkUnfinishedArjunaTransactions(
                container(2)));
        Assert.assertFalse("There are unfinished Arjuna transactions in node-4. Failing the test.", checkUnfinishedArjunaTransactions(
                container(4)));

        container(2).stop();
        container(4).stop();
    }


    /**
     * @throws Exception
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testPropertyReplacementWithName() throws Exception {
        testPropertyBasedMdb(mdbWithPropertiesName);
    }

    /**
     * @throws Exception
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testPropertyReplacementWithMappedName() throws Exception {
        testPropertyBasedMdb(mdbWithPropertiesMappedName);
    }

    public void testPropertyBasedMdb(Archive mdbDeployemnt) throws Exception {
        Container inServer = container(1);
        Container outServer = container(1);

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A

        container(1).start();

        String s = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(container(2).getName())) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        s = containerDef.getContainerProperties().get("javaVmArguments");
                        s = s.concat(" -Djms.queue.InQueue=" + inQueueJndiName);
                        s = s.concat(" -Dpooled.connection.factory.name=java:/JmsXA");
                        containerDef.getContainerProperties().put("javaVmArguments", s);
                    }
                }
            }
        }
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        container(2).start(properties);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        container(2).deploy(mdbDeployemnt);

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 10000, 10, 10);
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

        container(2).undeploy(mdbDeployemnt);

        container(2).stop();
        container(1).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  list of containers
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
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
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    public void prepareRemoteJcaTopology(Container inServer, Container outServer) throws Exception {
        if (inServer.getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER))  {
            prepareRemoteJcaTopologyEAP6(inServer, outServer);
        } else {
            prepareRemoteJcaTopologyEAP7(inServer, outServer);
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP7(container(1));
        prepareMdbServerEAP7(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP7(container(3));
        prepareMdbServerEAP7(container(4), container(3), inServer, outServer);

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP6(container(3));
        prepareMdbServerEAP6(container(4), container(3), inServer, outServer);

        copyApplicationPropertiesFiles();

    }

    private boolean isServerRemote(String containerName) {
        if (CONTAINER1_NAME.equals(containerName) || CONTAINER3_NAME.equals(containerName)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
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

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.3";
        String httpSocketBindingName = "http";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBindingName, null);
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

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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
        if (isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "http-connector";
        String httpSocketBinding = "http-remote-connector";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-activemq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBinding, null);
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
        if (isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, "java:/remoteJmsXA");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

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

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

}