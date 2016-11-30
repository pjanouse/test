package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueNoRebalancing;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * This is modified failover with mdb test case which is testing remote jca.
 *
 * @author mnovak@redhat.com
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF HORNETQ RESOURCE ADAPTER WITH SHARED STORE AND REPLICATED JOURNAL IN DEDICATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-mdb/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails This is modified failover with mdb test case which is
 * testing remote jca. There are two servers in dedicated HA topology and MDB
 * deployed on another server. Live server is shutdown/killed and correct
 * failover/failback is tested. Live and backup servers use shared stores.
 */
@RunWith(Arquillian.class)
@Prepare("RemoteJCASharedStore")
public class DedicatedFailoverTestCaseWithMdb extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCaseWithMdb.class);
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 2000;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
    FinalTestMessageVerifier messageVerifier = null;

    protected final Archive mdbWithRebalancing = getDeployment1();
    protected final Archive mdbWithNORebalancing = getDeployment2();


    public Archive getDeployment1() {

        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeployment2() {

        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueNoRebalancing.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb2.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Before
    public void before() {
        messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Rebalancing is enabled. Kill live server. Receive
     * messages from OutQueue from backup server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Kill live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillWithRebalancing() throws Exception {
        testFailoverWithRemoteJca(false, mdbWithRebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Kill live server. Receive
     * messages from OutQueue from backup server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Kill live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKill() throws Exception {
        testFailoverWithRemoteJca(false, mdbWithNORebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Re-balancing is enabled. Kill live server. Receive
     * messages from OutQueue from backup server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Kill live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillMdbWithRebalancing() throws Exception {
        testFailoverWithRemoteJca(false, mdbWithRebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Kill live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Kill live server</li>
     * <li>Start live server again</li>
     * <li>Receive messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillWithFailback() throws Exception {
        testFailbackWithRemoteJca(false, mdbWithNORebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Rebalancing is enabled. Kill live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Kill live server</li>
     * <li>Start live server again</li>
     * <li>Receive messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillWithFailbackWithRebalancing() throws Exception {
        testFailbackWithRemoteJca(false, mdbWithRebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Shutdown live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Shutdown live server</li>
     * <li>Start live server again</li>
     * <li>Receive messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownWithFailback() throws Exception {
        testFailbackWithRemoteJca(true, mdbWithNORebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Rebalancing is enabled. Shutdown live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Shutdown live server</li>
     * <li>Start live server again</li>
     * <li>Receive messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownWithFailbackWithRebalancing() throws Exception {
        testFailbackWithRemoteJca(true, mdbWithRebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Shutdown live server. Receive
     * messages from OutQueue from backup server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Shutdown live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdown() throws Exception {
        testFailoverWithRemoteJca(true, mdbWithNORebalancing);
    }

    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Rebalacing is enabled. Shutdown live server. Receive
     * messages from OutQueue from backup server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>Shutdown live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownWithRebalancing() throws Exception {
        testFailoverWithRemoteJca(true, mdbWithRebalancing);
    }

    /**
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailoverWithRemoteJca(boolean shutdown, Archive mdb) throws Exception {

        // start live-backup servers
        container(1).start();
        container(2).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(3).start();

        logger.info("Deploying MDB to mdb server.");
//        // start mdb server
        container(3).deploy(mdb);

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(container(1), PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            logger.info("Stopping container 1.");
            container(1).stop();
            logger.info("Container 1 stopped.");
        } else {
            logger.info("Killing container 1.");
            container(1).kill();
            logger.info("Container 1 killed.");
        }

        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 600000);
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(container(2), PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(360000, container(2));
        new JMSTools().waitForMessages(PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(2));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 3000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        container(3).undeploy(mdb);

        container(3).stop();
        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());


    }

    /**
     * @tpTestDetails There are 2 servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 1 (live). MDB
     * sends messages form InQueue to OutQueue. Rebalacing is disabled. Shutdown live server and start again. Receive
     * messages from OutQueue from live server.
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownFailbackWithMdbOnLive() throws Exception {
        testFailbackWithMdbOnLive(true, mdbWithNORebalancing);
    }

    /**
     * @tpTestDetails There are 2 servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 1 (live). MDB
     * sends messages form InQueue to OutQueue. Rebalacing is disabled. Kill live server and start again. Receive
     * messages from OutQueue from live server.
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillFailbackWithMdbOnLive() throws Exception {
        testFailbackWithMdbOnLive(false, mdbWithNORebalancing);
    }


    /**
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailbackWithMdbOnLive(boolean shutdown, Archive mdb) throws Exception {

        // start live-backup servers
        container(1).start();
        container(2).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();
        logger.info("Deploying MDB to mdb server.");

        // start mdb server
        container(1).deploy(mdb);

        Assert.assertTrue("MDB on container 1 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(container(1), PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            logger.info("Stopping container 1.");
            container(1).stop();
            logger.info("Container 1 stopped.");
        } else {
            logger.info("Killing container 1.");
            container(1).kill();
            logger.info("Container 1 killed.");
        }

        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 600000);

        // start node 1 again so failback occur
        container(1).start();

        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 600000); // wait for live to activate
        CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), 30000); // wait for backup to deactivate

        new JMSTools().waitForMessages(PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(360000, container(1));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 3000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        container(1).undeploy(mdb);

        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

    }


    /**
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailbackWithRemoteJca(boolean shutdown, Archive mdb) throws Exception {

        // start live-backup servers
        container(1).start();
        container(2).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();


        container(3).start();

        // start mdb server
        container(3).deploy(mdb);
        logger.info("MDB was deployed to mdb server - container 3");

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(container(1), PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            container(1).stop();
            logger.info("Container 1 shut downed.");
        } else {
            container(1).kill();
            logger.info("Container 1 killed.");
        }

        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 600000);
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(container(2), PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));
        Thread.sleep(10000);
        logger.info("Container 1 starting...");
        container(1).start();
        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 600000);
        logger.info("Container 1 started again");
        Thread.sleep(10000);
        logger.info("Container 2 stopping...");
        container(2).stop();
        logger.info("Container 2 stopped");

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, container(1));
        new JMSTools().waitForMessages(PrepareConstants.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 3000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();


        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();


        logger.info("Undeploy mdb from mdb server and stop servers 1 and 3.");
        container(3).undeploy(mdb);
        container(3).stop();
        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
    }

    /**
     * @tpTestDetails There are 2 servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue is
     * deployed on live and backup. Send large messages to InQueue to live server.
     * When all messages are sent, kill live server and start again. Receive
     * messages from InQueue from live server.
     * @tpProcedure <ul>
     * <li>Start live and its backup server with shared/replicated journal</li>
     * <li>Start producer which sends large messages to InQueue</li>
     * <li>Kill live server</li>
     * <li>Receive messages from OutQueue from backup</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(params = {
            @Param(name = "1." + PrepareParams.CLUSTER_CONNECTION_CALL_TIMEOUT, value = "60000"),
            @Param(name = "1." + PrepareParams.CLUSTER_CONNECTION_CHECK_PERIOD, value = "60000"),
            @Param(name = "1." + PrepareParams.CLUSTER_CONNECTION_TTL, value = "120000"),
            @Param(name = "2." + PrepareParams.CLUSTER_CONNECTION_CALL_TIMEOUT, value = "60000"),
            @Param(name = "2." + PrepareParams.CLUSTER_CONNECTION_CHECK_PERIOD, value = "60000"),
            @Param(name = "2." + PrepareParams.CLUSTER_CONNECTION_TTL, value = "120000")
    })
    public void testJustFailbackWithLargeMessages() throws Exception {

        int numberOfMessages = 500;

        // start live-backup servers
        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(1024 * 200));
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(2).start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, 30000, 100, 10);
        receiver1.setTimeout(100);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();

        int receivedCount = 0;
        while ((receivedCount = receiver1.getCount()) < 5)  {
            logger.info("Receiver received: " + receivedCount);
            Thread.sleep(500);
        }

        logger.info("Container 2 stopping...");
        container(2).stop();
        logger.info("Container 2 stopped");

        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
    }

    /**
     * Return true if numberOfMessages is in queue in the given timeout. Otherwise false.
     *
     * @param container        test container
     * @param queueName        queue name (not jndi name)
     * @param numberOfMessages number of messages
     * @param timeout          time out
     * @return returns true if numberOfMessages is in queue in the given timeout. Otherwise false.
     * @throws Exception
     */
    protected boolean waitForMessagesOnOneNode(Container container, String queueName, int numberOfMessages, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        JMSOperations jmsOperations = container.getJmsOperations();
        while (numberOfMessages > (jmsOperations.getCountOfMessagesOnQueue(queueName))) {
            if (System.currentTimeMillis() - startTime > timeout) {
                return false;
            }
            Thread.sleep(1000);
        }
        jmsOperations.close();

        return true;
    }

}
