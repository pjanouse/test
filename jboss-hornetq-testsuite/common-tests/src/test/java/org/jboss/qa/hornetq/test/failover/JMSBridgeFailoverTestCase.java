package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
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
//            String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");

            Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", new TransactionUtils().checkThatFileContainsUnfinishedTransactionsString(
                    new File(outputJournalFile), stringToFind));

            container(1).start();
        }

        container(3).stop();
    }

    /////////////////// Test Initial Failover /////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE, 5);

        testInitialFailover();
    }

    /////////////////// Test Failover ////////////////////////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(false, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(false, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, false, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(true, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(true, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, false, ONCE_AND_ONLY_ONCE);
    }

    ////////////////////////////////////// Test Failback ///////////////// ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(false, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(false, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, true, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(container(3), AT_MOST_ONCE);

        testFailoverWithBridge(true, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(container(3), DUPLICATES_OK);

        testFailoverWithBridge(true, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(container(3), ONCE_AND_ONLY_ONCE);

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

    protected void deployBridge(Container container, String qualityOfService) {
        deployBridge(container, qualityOfService, -1);
    }

    protected void deployBridge(Container container, String qualityOfService, int maxRetries) {

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
        if (CONTAINER1_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 1 then target is container 3

            targetContext.put("java.naming.provider.url", "remote://" + container(3).getHostname() + ":" + container(3).getJNDIPort());

        } else if (CONTAINER2_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 2 then target is container 3

            targetContext.put("java.naming.provider.url", "remote://" + container(3).getHostname() + ":" + container(3).getJNDIPort());

        } else if (CONTAINER3_NAME.equalsIgnoreCase(container.getName())) { // if deployed to container 3 then target is container 1 and 2

            targetContext.put("java.naming.provider.url", "remote://" + container(1).getHostname() + ":" + container(1).getJNDIPort() +
                    ",remote://" + container(2).getHostname() + ":" + container(2).getJNDIPort());
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
