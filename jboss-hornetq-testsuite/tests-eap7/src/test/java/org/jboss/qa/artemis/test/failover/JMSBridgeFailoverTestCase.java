package org.jboss.qa.artemis.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.jboss.qa.hornetq.constants.Constants.*;

/**
 * Tests JMS bridge failover deploy/un-deploy
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF JMS BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-bridges/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 */
@RunWith(Arquillian.class)
public class JMSBridgeFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JMSBridgeFailoverTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;
    // Queue to send messages in
    private String inQueueName = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    private String outQueueName = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueueName;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ArtemisJMSImplementation.getInstance());

    String discoveryGroupName = "dg-group1";
    String clusterConnectionName = "my-cluster";
    String messagingGroupSocketBindingForDiscovery = "messaging-group-bridges";
    String messagingGroupMulticastAddressForDiscovery = "234.46.21.68";
    int messagingGroupMulticastPortForDiscovery = 9876;
    int defaultPortForMessagingSocketBinding = 5445;
    String jgroupsChannel = "activemq-cluster";
    String jgroupsStack = "udp";
    String broadcastGroupName = "bg-group1";
    String httpConnectorName = "http-connector";

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
    }


    /**
     * @throws Exception
     */
    public void testFailoverWithBridge(CONNECTOR_TYPE connectorType, boolean failback,
                                       FAILURE_TYPE failureType, QUALITY_OF_SERVICE qualityOfService) throws Exception {

        Container outContainer = failback ? container(1) : container(2);

        prepareServers(connectorType, qualityOfService);

        // start live-backup servers
        container(1).start();
        container(2).start();
        container(3).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(3), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        messageBuilder.setAddDuplicatedHeader(true);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 60000, container(1));

        logger.warn("###################################");
        logger.warn("Server failure will be executed: " + failureType);
        container(1).fail(failureType);
        logger.warn("###################################");
        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 120000);

        // if failback then start container1 again
        // wait for container1 to start
        // start consumer on container1
        // else start receiver on container2
        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.",
                    CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            Thread.sleep(5000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            container(2).stop();
            logger.warn("########################################");
            logger.warn("failback - Backup server stopped");
            logger.warn("########################################");
        }

        Thread.sleep(30000);    //wait for more messages
        producerToInQueue1.stopSending();
        producerToInQueue1.join();

        new JMSTools().waitForMessages(outQueueName, producerToInQueue1.getCount(), 180000, outContainer);

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(180000, container(3), 1);
        ReceiverClientAck receiver1;
        if (failback) {
            new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(180000, outContainer, 1);
//            logger.warn("########################################");
//            logger.warn("WAITING 3 minutes");
//            logger.warn("########################################");
//            Thread.sleep(180000);
        } else {
            new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(180000, outContainer, 1);
        }
        receiver1 = new ReceiverClientAck(outContainer, outQueueJndiName, 130000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Messages in outQueue on server from which it is read: " + new JMSTools().countMessages(outQueueName, outContainer));
        logger.info("Messages in inQueue on bridge server : " + new JMSTools().countMessages(inQueueName, container(3)));
        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        if (QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE.equals(qualityOfService)) {
            messageVerifier.verifyMessages();
            Assert.assertEquals("Number of sent and received messages is different.", producerToInQueue1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size());
        } else if (QUALITY_OF_SERVICE.AT_MOST_ONCE.equals(qualityOfService)) {
            //in case of fail, print information about duplicate messages-, not only assert message
            if (producerToInQueue1.getListOfSentMessages().size() < receiver1.getListOfReceivedMessages().size()) {
                logger.info("Info about lost messages can be ignored, since AT_MOST_ONCE mode is in use. Look for DUPLICATE messages");
                messageVerifier.verifyMessages();
                Assert.assertTrue("Number of sent must be same or higher than number of received messages. Producer: "
                                + producerToInQueue1.getListOfSentMessages().size() + " Receiver: " + receiver1.getListOfReceivedMessages().size(),
                        producerToInQueue1.getListOfSentMessages().size() >= receiver1.getListOfReceivedMessages().size());
            }
        } else if (QUALITY_OF_SERVICE.DUPLICATES_OK.equals(qualityOfService)) {
            //in case of fail, print information about missing messages-, not only assert message
            if (producerToInQueue1.getListOfSentMessages().size() > receiver1.getListOfReceivedMessages().size()) {
                logger.info("Info about duplicates can be ignored, since DUPLICATES_OK mode is in use. Look for LOST messages");
                messageVerifier.verifyMessages();
                Assert.assertTrue("Number of sent must be same or less than number of received messages. Producer: "
                                + producerToInQueue1.getListOfSentMessages().size() + " Receiver: " + receiver1.getListOfReceivedMessages().size(),
                        producerToInQueue1.getListOfSentMessages().size() <= receiver1.getListOfReceivedMessages().size());
            }
        }
        container(3).stop();
        container(2).stop();
        container(1).stop();

    }

    /**
     * @tpTestDetails This scenario tests server shutdown while messages are transferred. No unfinished transactions
     * should be left in journal after shutdown.
     * @tpProcedure <ul>
     * <li>Start node-1 with deployed destinations and deployed JMS bridge between inQueue and outQueue on node-3</li>
     * <li>send 20000 messages to inQueue</li>
     * <li>when all messages are send in inQueue start node-3</li>
     * <li>wait JMS bridge to connect and transfer 10% of messages</li>
     * <li>execute 5x clean shutdown and start sequence of node-1</li>
     * <li>after each shutdown check journal for unfinished HQ transactions</li>
     * </ul>
     * @tpPassCrit Verify there are no unfinished HQ transactions after any shutdown.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testUnfinishedTransactionsAfterShutdownOfServerWithDeployedJMSBridge() throws Exception {

        prepareServers(CONNECTOR_TYPE.HTTP_CONNECTOR, QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);

        int numberOfMessages = 20000;

        container(1).start();

        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);

        // send some messages to InQueue to server 1
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(40));
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(100);
        producerToInQueue1.start();
        producerToInQueue1.join();

        // start target server for the bridge with queue OutQueue
        container(3).start();

        // check that some messages got to OutQueue on server 3 and shutdown server1
        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10, 120000, container(3));

        for (int i = 0; i < 5; i++) {

            // shutdown server 3
            container(1).stop();

            // check HornetQ journal that there are no unfinished transactions
            String outputJournalFile = CONTAINER1_NAME + "-hornetq_journal_content_after_shutdown_of_JMS_bridge.txt";
            container(1).getPrintJournal().printJournal(
                    JOURNAL_DIRECTORY_A + File.separator + "bindings",
                    JOURNAL_DIRECTORY_A + File.separator + "journal",
                    JOURNAL_DIRECTORY_A + File.separator + "paging",
                    outputJournalFile);
            // check that there are failed transactions
            String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";
            // String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() :
            // System.getenv("WORKSPACE");

            Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", new TransactionUtils()
                    .checkThatFileContainsUnfinishedTransactionsString(new File(outputJournalFile), stringToFind));

            container(1).start();
        }

        container(3).stop();
    }

    // ///////////////// Test Failover ////////////////////////////////////

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.DUPLICATES_OK);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.DUPLICATES_OK);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, false, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }

    // //////////////////////////////////// Test Failback ///////////////// ///////////////////

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.DUPLICATES_OK);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Kill live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.KILL, QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.DUPLICATES_OK);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shut down live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {
        testFailoverWithBridge(CONNECTOR_TYPE.HTTP_CONNECTOR, true, FAILURE_TYPE.SHUTDOWN, QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }


    protected void deployBridgeEAP7(JMSOperations jmsAdminOperations, QUALITY_OF_SERVICE qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;
        String targetConnectionFactory = "jms/bridgeCF";
        String targetDestination = outQueueJndiName;
        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;
        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY_EAP7);
        targetContext.put(Context.PROVIDER_URL, PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + container(1).getHostname()
                + ":" + container(1).getJNDIPort() + "," + PROVIDER_URL_PROTOCOL_PREFIX_EAP7
                + container(2).getHostname() + ":" + container(2).getJNDIPort());


        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);


    }

    protected void prepareServers(CONNECTOR_TYPE connectorType, QUALITY_OF_SERVICE qualityOfService) throws Exception {
        prepareLiveServerEAP7(container(1), JOURNAL_DIRECTORY_A, JOURNAL_TYPE.ASYNCIO, connectorType);
        prepareBackupServerEAP7(container(2), JOURNAL_DIRECTORY_A, JOURNAL_TYPE.ASYNCIO, connectorType);
        prepareServerWithBridge(container(3), connectorType, qualityOfService);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareLiveServerEAP7(Container container, String journalDirectory, JOURNAL_TYPE journalType, CONNECTOR_TYPE connectorType) throws Exception {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        setConnectorTypeForLiveBackupPair(container, connectorType);
        setConnectionFactoryForBridge(container);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType.toString());
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreMaster(5000, true);
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO, or HTTP connector
     */
    protected void prepareBackupServerEAP7(Container container, String journalDirectory, JOURNAL_TYPE journalType, CONNECTOR_TYPE connectorType) throws Exception {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setJournalType(journalType.toString());
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setPersistenceEnabled(true);
        setConnectorTypeForLiveBackupPair(container, connectorType);
        setConnectionFactoryForBridge(container);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreSlave(true, 5000, true, true, false, null, null, null, null);
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.close();
        container.stop();
    }


    protected void setConnectionFactoryForBridge(Container container) {
        String CF = "bridgeCF";
        String JNDI_CF = "java:jboss/exported/jms/" + CF;
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createConnectionFactory(CF, JNDI_CF, "http-connector");
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(CF, 0);
        jmsAdminOperations.close();


    }

    /**
     * @param container     container where to set it
     * @param connectorType type of the connector
     */
    protected void setConnectorTypeForLiveBackupPair(Container container, CONNECTOR_TYPE connectorType) throws Exception {

        String messagingGroupSocketBindingForConnector = "messaging";
        String nettyConnectorName = "netty";
        String nettyAcceptorName = "netty";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        switch (connectorType) {
            case HTTP_CONNECTOR:
                break;
            case NETTY_BIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, null);
                // add acceptor wtih BIO
                Map<String, String> acceptorParams = new HashMap<String, String>();
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, null);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);
                break;
            case NETTY_NIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, connectorParamsNIO);
                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, acceptorParamsNIO);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingForDiscovery, messagingGroupMulticastAddressForDiscovery,
                        messagingGroupMulticastPortForDiscovery);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingForDiscovery, 10000);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, messagingGroupSocketBindingForDiscovery, 1000, httpConnectorName, null);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, httpConnectorName);
                break;
            case JGROUPS_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, httpConnectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, httpConnectorName);
                break;
            default:
                throw new Exception("No configuration prepared for connector type: " + connectorType);
        }

        jmsAdminOperations.setFactoryType(CONNECTION_FACTORY_EAP7, "XA_GENERIC");

        jmsAdminOperations.close();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    protected void prepareServerWithBridge(Container container, CONNECTOR_TYPE connectorType, QUALITY_OF_SERVICE qualityOfService) throws Exception {

        String removeSocketBindingToLive = "messaging-bridge";
        String remoteSocketBindingToBackup = "messaging-bridge-backup";
        String remoteConnectorNameToLive = "bridge-connector";
        String remoteConnectorNameToBackup = "bridge-connector-backup";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        switch (connectorType) {
            case HTTP_CONNECTOR:
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.addRemoteSocketBinding(removeSocketBindingToLive, container(1).getHostname(), container(1).getHornetqPort());
                jmsAdminOperations.createHttpConnector(remoteConnectorNameToLive, removeSocketBindingToLive, null);
                jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingToBackup, container(2).getHostname(), container(2).getHornetqPort());
                jmsAdminOperations.createHttpConnector(remoteConnectorNameToBackup, remoteSocketBindingToBackup, null);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, remoteConnectorNameToLive, remoteConnectorNameToBackup);
                break;
            case NETTY_BIO:
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.addRemoteSocketBinding(removeSocketBindingToLive, container(1).getHostname(), defaultPortForMessagingSocketBinding);
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToLive, removeSocketBindingToLive, null);
                jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingToBackup, container(2).getHostname(), defaultPortForMessagingSocketBinding);
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToBackup, remoteSocketBindingToBackup, null);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, remoteConnectorNameToLive, remoteConnectorNameToBackup);
                break;
            case NETTY_NIO:
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.addRemoteSocketBinding(removeSocketBindingToLive, container(1).getHostname(), defaultPortForMessagingSocketBinding);
                // add connector with NIO
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToLive, removeSocketBindingToLive, connectorParamsNIO);
                Map<String, String> connectorParamsNIOToBackup = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToBackup, remoteSocketBindingToBackup, connectorParamsNIO);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, remoteConnectorNameToLive, remoteConnectorNameToBackup);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingForDiscovery, messagingGroupMulticastAddressForDiscovery,
                        messagingGroupMulticastPortForDiscovery);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingForDiscovery, 10000);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, messagingGroupSocketBindingForDiscovery, 1000, httpConnectorName, null);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, httpConnectorName);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupName);
                break;
            case JGROUPS_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, httpConnectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, httpConnectorName);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupName);
                break;
            default:
                throw new Exception("No configuration prepared for connector type: " + connectorType);
        }

        jmsAdminOperations.setFactoryType(IN_VM_CONNECTION_FACTORY_EAP7, "XA_GENERIC");
        deployBridgeEAP7(jmsAdminOperations, qualityOfService, -1);
        jmsAdminOperations.close();
        container.stop();
    }
}
