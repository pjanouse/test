package org.jboss.qa.hornetq.test.failover;
// TODO prepare this for all types of quality service and also for failback, large small messages
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
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

    // quality services
    final static String AT_MOST_ONCE = "AT_MOST_ONCE";
    final static String DUPLICATES_OK = "DUPLICATES_OK";
    final static String ONCE_AND_ONLY_ONCE = "ONCE_AND_ONLY_ONCE";


    /////////////////// Test Initial Failover /////////////////
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailover_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testInitialFailover();
    }

    /////////////////// Test Failover /////////////////    ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, false);
    }

    ////////////////////////////////////// Test Failback ///////////////// ///////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER3, AT_MOST_ONCE);

        testFailoverWithBridge(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER3, DUPLICATES_OK);

        testFailoverWithBridge(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridge_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER3, ONCE_AND_ONLY_ONCE);

        testFailoverWithBridge(true, true);
    }

    //////////////////////////////// Test Redeploy live -> backup ////////////////////////////

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER1, AT_MOST_ONCE);

        deployBridge(CONTAINER2, AT_MOST_ONCE);

        testDeployBridgeLiveThenBackup(false);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER1, DUPLICATES_OK);

        deployBridge(CONTAINER2, DUPLICATES_OK);

        testDeployBridgeLiveThenBackup(false);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER1, ONCE_AND_ONLY_ONCE);

        deployBridge(CONTAINER2, ONCE_AND_ONLY_ONCE);

        testDeployBridgeLiveThenBackup(false);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownDeployBridgeLiveThenBackup_AT_MOST_ONCE() throws Exception {

        deployBridge(CONTAINER1, AT_MOST_ONCE);

        deployBridge(CONTAINER2, AT_MOST_ONCE);

        testDeployBridgeLiveThenBackup(true);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownDeployBridgeLiveThenBackup_DUPLICATES_OK() throws Exception {

        deployBridge(CONTAINER1, DUPLICATES_OK);

        deployBridge(CONTAINER2, DUPLICATES_OK);

        testDeployBridgeLiveThenBackup(true);

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownDeployBridgeLiveThenBackup_ONCE_AND_ONLY_ONCE() throws Exception {

        deployBridge(CONTAINER1, ONCE_AND_ONLY_ONCE);

        deployBridge(CONTAINER2, ONCE_AND_ONLY_ONCE);

        testDeployBridgeLiveThenBackup(true);

    }

    //////////////////////////////////////////////////////////////////////////////////


    protected void deployBridge(String containerName, String qualityOfService) {

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
//            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_IP + ":4447,remote://" + CONTAINER2_IP + ":4447");
            targetContext.put("java.naming.provider.url", "remote://" + CONTAINER1_IP + ":4447");
        }

        if (qualityOfService == null || "".equals(qualityOfService))
        {
            qualityOfService = "ONCE_AND_ONLY_ONCE";
        }

        long failureRetryInterval = 1000;
        int maxRetries = -1;
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
