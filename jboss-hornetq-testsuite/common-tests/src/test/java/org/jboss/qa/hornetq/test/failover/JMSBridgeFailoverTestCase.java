package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests JMS bridge
 * failover
 * deploy/un-deploy
 *
 */
@RunWith(Arquillian.class)
public class JMSBridgeFailoverTestCase extends FailoverBridgeTestBase {

    private static final Logger logger = Logger.getLogger(JMSBridgeFailoverTestCase.class);

    // test JMS bridge clean shutdown - no unfinished transactions
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testUnfinishedTransactionsAfterShutdownOfServerWithDeployedJMSBridge() throws Exception {

        int numberOfMessages = 20000;

        deployBridge(CONTAINER1_NAME, ONCE_AND_ONLY_ONCE, -1);

        controller.start(CONTAINER1_NAME);

        waitHornetQToAlive(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), 60000);

        // send some messages to InQueue to server 1
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(40));
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(100);
        producerToInQueue1.start();
        producerToInQueue1.join();

        // start target server for the bridge with queue OutQueue
        controller.start(CONTAINER3_NAME);

        // check that some messages got to OutQueue on server 3 and shutdown server1
        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10, 120000, CONTAINER3_NAME);

        for (int i = 0; i < 5; i++) {

            // shutdown server 3
            stopServer(CONTAINER1_NAME);

            // check HornetQ journal that there are no unfinished transactions
            String outputJournalFile = CONTAINER1_NAME + "-hornetq_journal_content_after_shutdown_of_JMS_bridge.txt";
            PrintJournal.printJournal(getJbossHome(CONTAINER1_NAME), JOURNAL_DIRECTORY_A + File.separator + "bindings",
                    JOURNAL_DIRECTORY_A + File.separator + "journal", outputJournalFile);
            // check that there are failed transactions
            String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";
//            String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");

            Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", checkThatFileContainsUnfinishedTransactionsString(
                    new File(outputJournalFile), stringToFind));

            controller.start(CONTAINER1_NAME);
        }

        stopServer(CONTAINER3_NAME);
    }

    /////////////////// Test Initial Failover /////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, AT_MOST_ONCE, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3_NAME, DUPLICATES_OK, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, ONCE_AND_ONLY_ONCE, 5);

        testInitialFailover();
    }

    /////////////////// Test Failover ////////////////////////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, AT_MOST_ONCE);

        testFailoverWithBridge(false, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3_NAME, DUPLICATES_OK);

        testFailoverWithBridge(false, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, false, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, AT_MOST_ONCE);

        testFailoverWithBridge(true, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3_NAME, DUPLICATES_OK);

        testFailoverWithBridge(true, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, false, ONCE_AND_ONLY_ONCE);
    }

    ////////////////////////////////////// Test Failback ///////////////// ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, AT_MOST_ONCE);

        testFailoverWithBridge(false, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3_NAME, DUPLICATES_OK);

        testFailoverWithBridge(false, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, true, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, AT_MOST_ONCE);

        testFailoverWithBridge(true, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3_NAME, DUPLICATES_OK);

        testFailoverWithBridge(true, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3_NAME, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, true, ONCE_AND_ONLY_ONCE);
    }

    //////////////////////////////// Test Redeploy live -> backup ////////////////////////////

//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testKillDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, AT_MOST_ONCE);
//
//        deployBridge(CONTAINER2_NAME, AT_MOST_ONCE);
//
//        testDeployBridgeLiveThenBackup(false, AT_MOST_ONCE);
//
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testKillDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, DUPLICATES_OK);
//
//        deployBridge(CONTAINER2_NAME, DUPLICATES_OK);
//
//        testDeployBridgeLiveThenBackup(false, DUPLICATES_OK);
//
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testKillDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, ONCE_AND_ONLY_ONCE);
//
//        deployBridge(CONTAINER2_NAME, ONCE_AND_ONLY_ONCE);
//
//        testDeployBridgeLiveThenBackup(false);
//
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testShutdownDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, AT_MOST_ONCE);
//
//        deployBridge(CONTAINER2_NAME, AT_MOST_ONCE);
//
//        testDeployBridgeLiveThenBackup(true, AT_MOST_ONCE);
//
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testShutdownDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, DUPLICATES_OK);
//
//        deployBridge(CONTAINER2_NAME, DUPLICATES_OK);
//
//        testDeployBridgeLiveThenBackup(true, DUPLICATES_OK);
//
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore // not valid test scenario
//    public void testShutdownDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {
//
//        deployBridge(CONTAINER1_NAME_NAME, ONCE_AND_ONLY_ONCE);
//
//        deployBridge(CONTAINER2_NAME, ONCE_AND_ONLY_ONCE);
//
//        testDeployBridgeLiveThenBackup(true);
//
//    }

    //////////////////////////////////////////////////////////////////////////////////

    protected void deployBridge(String containerName, String qualityOfService) {
        deployBridge(containerName, qualityOfService, -1);
    }

    protected void deployBridge(String containerName, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
//        String sourceConnectionFactory = "jms/RemoteConnectionFactory";
        String sourceDestination = inQueueJndiName;

//        Map<String,String> sourceContext = new HashMap<String, String>();
//        sourceContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
//        sourceContext.put("java.naming.provider.url", "remote://" + getHostname(containerName) + ":4447");

        String targetConnectionFactory = "jms/RemoteConnectionFactory";
        String targetDestination = outQueueJndiName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        if (CONTAINER1_NAME.equalsIgnoreCase(containerName)) { // if deployed to container 1 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + getHostname(CONTAINER3_NAME) + ":" + getJNDIPort(


                    CONTAINER3_NAME));
        } else if (CONTAINER2_NAME.equalsIgnoreCase(containerName)) { // if deployed to container 2 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + getHostname(CONTAINER3_NAME) + ":" + getJNDIPort(
                    CONTAINER3_NAME));
        } else if (CONTAINER3_NAME.equalsIgnoreCase(containerName)) { // if deployed to container 3 then target is container 1 and 2
            targetContext.put("java.naming.provider.url", "remote://" + getHostname(CONTAINER1_NAME) + ":" + getJNDIPort(CONTAINER1_NAME) +
                    ",remote://" + getHostname(CONTAINER2_NAME) + ":" + getJNDIPort(CONTAINER2_NAME));
//            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_NAME_IP + ":4447");
        }

        if (qualityOfService == null || "".equals(qualityOfService))
        {
            qualityOfService = "ONCE_AND_ONLY_ONCE";
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();

        stopServer(containerName);
    }

}
