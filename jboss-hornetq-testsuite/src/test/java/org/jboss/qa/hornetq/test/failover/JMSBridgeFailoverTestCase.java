package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    /////////////////// Test Initial Failover /////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK, 5);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE, 5);

        testInitialFailover();
    }

    /////////////////// Test Failover /////////////////    ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(false, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(false, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, false, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(true, false, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(true, false, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, false, ONCE_AND_ONLY_ONCE);
    }

    ////////////////////////////////////// Test Failback ///////////////// ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(false, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(false, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, true, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(true, true, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(true, true, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, true, ONCE_AND_ONLY_ONCE);
    }

    //////////////////////////////// Test Redeploy live -> backup ////////////////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testKillDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER1, AT_MOST_ONCE);

        deployBridge(CONTAINER2, AT_MOST_ONCE);

        testDeployBridgeLiveThenBackup(false, AT_MOST_ONCE);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testKillDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER1, DUPLICATES_OK);

        deployBridge(CONTAINER2, DUPLICATES_OK);

        testDeployBridgeLiveThenBackup(false, DUPLICATES_OK);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testKillDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER1, ONCE_AND_ONLY_ONCE);

        deployBridge(CONTAINER2, ONCE_AND_ONLY_ONCE);

        testDeployBridgeLiveThenBackup(false);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testShutdownDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER1, AT_MOST_ONCE);

        deployBridge(CONTAINER2, AT_MOST_ONCE);

        testDeployBridgeLiveThenBackup(true, AT_MOST_ONCE);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testShutdownDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER1, DUPLICATES_OK);

        deployBridge(CONTAINER2, DUPLICATES_OK);

        testDeployBridgeLiveThenBackup(true, DUPLICATES_OK);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore // not valid test scenario
    public void testShutdownDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER1, ONCE_AND_ONLY_ONCE);

        deployBridge(CONTAINER2, ONCE_AND_ONLY_ONCE);

        testDeployBridgeLiveThenBackup(true);

    }

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
        if (CONTAINER1.equalsIgnoreCase(containerName)) { // if deployed to container 1 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER3_IP + ":4447");
        } else if (CONTAINER2.equalsIgnoreCase(containerName)) { // if deployed to container 2 then target is container 3
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER3_IP + ":4447");
        } else if (CONTAINER3.equalsIgnoreCase(containerName)) { // if deployed to container 3 then target is container 1 and 2
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_IP + ":4447,remote://" + CONTAINER2_IP + ":4447");
//            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_IP + ":4447");
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
