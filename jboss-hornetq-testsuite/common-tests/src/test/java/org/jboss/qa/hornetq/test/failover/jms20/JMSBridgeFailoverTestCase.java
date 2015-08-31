package org.jboss.qa.hornetq.test.failover.jms20;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients20.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests JMS bridge failover deploy/un-deploy
 * @tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF JMS BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-60-hornetq-ha-failover-bridges/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 */
@RunWith(Arquillian.class)
public class JMSBridgeFailoverTestCase extends FailoverBridgeTestBase {

    private static final Logger logger = Logger.getLogger(JMSBridgeFailoverTestCase.class);

    // test JMS bridge clean shutdown - no unfinished transactions.
    /**
     * @tpTestDetails This scenario tests server shutdown while messages are transferred. No unfinished transactions
     * should be left in journal after shutdown.
     * @tpProcedure <ul>
     *     <li>Start node-1 with deployed destinations and deployed JMS bridge between inQueue and outQueue on node-3</li>
     *     <li>send 20000 messages to inQueue</li>
     *     <li>when all messages are send in inQueue start node-3</li>
     *     <li>wait JMS bridge to connect and transfer 10% of messages</li>
     *     <li>execute 5x clean shutdown and start sequence of node-1</li>
     *     <li>after each shutdown check journal for unfinished HQ transactions</li>
     * </ul>
     * @tpPassCrit Verify there are no unfinished HQ transactions after any shutdown.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testUnfinishedTransactionsAfterShutdownOfServerWithDeployedJMSBridge() throws Exception {

        int numberOfMessages = 20000;

        deployBridge(container(1), ONCE_AND_ONLY_ONCE, -1);

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
            container(1).getPrintJournal().printJournal(JOURNAL_DIRECTORY_A + File.separator + "bindings",
                    JOURNAL_DIRECTORY_A + File.separator + "journal", outputJournalFile);
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

    // ///////////////// Test Initial Failover /////////////////
    /**
     * @tpTestDetails  This scenario tests failover of JMS bridge from live to backup before any client connects.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Stop live server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS  which connects to live outQueue</li>
     *     <li>shut down live</li>
     *     <li>JMS bridge makes failover to backup</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE, 5);

        testInitialFailover();
    }

    /**
     * @tpTestDetails This scenario tests failover of JMS bridge from live to backup before any client connects
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Stop live server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS  which connects to lives outQueue</li>
     *     <li>shut down live</li>
     *     <li>JMS bridge makes failover to backup</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK, 5);

        testInitialFailover();
    }
    /**
     * @tpTestDetails This scenario tests failover of JMS bridge from live to backup before any client connects
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Stop live server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS  which sends messages from 3rd server’s
     *      InQueue to backup’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE, 5);

        testInitialFailover();
    }

    // ///////////////// Test Failover ////////////////////////////////////
    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(false, false, AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(false, false, DUPLICATES_OK);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, false, ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(true, false, AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(true, false, DUPLICATES_OK);
    }

    /**
     * @tpTestDetails test failover of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, false, ONCE_AND_ONLY_ONCE);
    }

    // //////////////////////////////////// Test Failback ///////////////// ///////////////////

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(false, true, AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(false, true, DUPLICATES_OK);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Kill live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Kill live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, true, ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "AT_MOST_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "AT_MOST_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(true, true, AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "DUPLICATES_OK" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "DUPLICATES_OK" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(true, true, DUPLICATES_OK);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed JMS
     * bridge with "ONCE_AND_ONLY_ONCE" QoS which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed JMS bridge with "ONCE_AND_ONLY_ONCE" QoS
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shut down live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, true, ONCE_AND_ONLY_ONCE);
    }

    // ////////////////////////////// Test Redeploy live -> backup ////////////////////////////

    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testKillDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, AT_MOST_ONCE);
    //
    // deployBridge(CONTAINER2_NAME, AT_MOST_ONCE);
    //
    // testDeployBridgeLiveThenBackup(false, AT_MOST_ONCE);
    //
    // }
    //
    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testKillDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, DUPLICATES_OK);
    //
    // deployBridge(CONTAINER2_NAME, DUPLICATES_OK);
    //
    // testDeployBridgeLiveThenBackup(false, DUPLICATES_OK);
    //
    // }
    //
    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testKillDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, ONCE_AND_ONLY_ONCE);
    //
    // deployBridge(CONTAINER2_NAME, ONCE_AND_ONLY_ONCE);
    //
    // testDeployBridgeLiveThenBackup(false);
    //
    // }
    //
    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testShutdownDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, AT_MOST_ONCE);
    //
    // deployBridge(CONTAINER2_NAME, AT_MOST_ONCE);
    //
    // testDeployBridgeLiveThenBackup(true, AT_MOST_ONCE);
    //
    // }
    //
    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testShutdownDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, DUPLICATES_OK);
    //
    // deployBridge(CONTAINER2_NAME, DUPLICATES_OK);
    //
    // testDeployBridgeLiveThenBackup(true, DUPLICATES_OK);
    //
    // }
    //
    // @Test
    // @RunAsClient
    // @RestoreConfigBeforeTest
    // @CleanUpBeforeTest
    // @Ignore // not valid test scenario
    // public void testShutdownDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {
    //
    // deployBridge(CONTAINER1_NAME_NAME, ONCE_AND_ONLY_ONCE);
    //
    // deployBridge(CONTAINER2_NAME, ONCE_AND_ONLY_ONCE);
    //
    // testDeployBridgeLiveThenBackup(true);
    //
    // }

    // ////////////////////////////////////////////////////////////////////////////////

    protected void deployBridge(Container container, String qualityOfService) {
        deployBridge(container, qualityOfService, -1);
    }

    protected void deployBridge(Container container, String qualityOfService, int maxRetries) {
        if (container.getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER)) {
            deployBridgeEAP6(container, qualityOfService, maxRetries);
        } else {
            deployBridgeEAP7(container, qualityOfService, maxRetries);
        }
    }

    protected void deployBridgeEAP6(Container container, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;
        String targetConnectionFactory = Constants.CONNECTION_FACTORY_JNDI_EAP6;
        String targetDestination = outQueueJndiName;

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP6);

        if (CONTAINER1_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 1 then target is container 3

            targetContext
                    .put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 + container(3).getHostname() + ":" + container(3).getJNDIPort());

        } else if (CONTAINER2_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 2 then target is
                                                                            // container 3

            targetContext
                    .put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 + container(3).getHostname() + ":" + container(3).getJNDIPort());

        } else if (CONTAINER3_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 3 then target is
                                                                            // container 1 and 2

            targetContext.put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 + container(1).getHostname() + ":" + container(1).getJNDIPort()
                    + "," + Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 + container(2).getHostname() + ":" + container(2).getJNDIPort());
        }

        if (qualityOfService == null || "".equals(qualityOfService)) {
            qualityOfService = "ONCE_AND_ONLY_ONCE";
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void deployBridgeEAP7(Container container, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;

        String targetConnectionFactory = Constants.CONNECTION_FACTORY_JNDI_EAP7;
        String targetDestination = outQueueJndiName;

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP7);

        if (CONTAINER1_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 1 then target is container 3
            targetContext.put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + container(3).getHostname()
                    + ":" + container(3).getJNDIPort());

        } else if (CONTAINER2_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 2 then target is
            // container 3
            targetContext.put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + container(3).getHostname()
                    + ":" + container(3).getJNDIPort());

        } else if (CONTAINER3_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 3 then target is
            // container 1 and 2
            targetContext.put(Context.PROVIDER_URL, Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + container(1).getHostname()
                    + ":" + container(1).getJNDIPort() + "," + Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7
                    + container(2).getHostname() + ":" + container(2).getJNDIPort());
        }

        if (qualityOfService == null || "".equals(qualityOfService)) {
            qualityOfService = "ONCE_AND_ONLY_ONCE";
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        container.stop();
    }
}
