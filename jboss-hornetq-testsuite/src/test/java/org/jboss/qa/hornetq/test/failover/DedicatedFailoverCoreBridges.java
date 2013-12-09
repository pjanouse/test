package org.jboss.qa.hornetq.test.failover;
// TODO test re-deploy from backup -> live (failback)

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is modified failover with mdb test case which is testing remote jca.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class DedicatedFailoverCoreBridges extends FailoverBridgeTestBase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverCoreBridges.class);


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridgeWitStaticConnectors() throws Exception {

        deployBridge(CONTAINER3, false);

        testFailoverWithBridge(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverKillWithBridgeWithDiscovery() throws Exception {

        deployBridge(CONTAINER3, true);

        testFailoverWithBridge(false, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithStaticConnectors() throws Exception {

        deployBridge(CONTAINER3, false);

        testFailoverWithBridge(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithDiscovery() throws Exception {

        deployBridge(CONTAINER3, true);

        testFailoverWithBridge(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridgeWitStaticConnectors() throws Exception {

        deployBridge(CONTAINER3, false);

        testFailoverWithBridge(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridgeWithDiscovery() throws Exception {

        deployBridge(CONTAINER3, true);

        testFailoverWithBridge(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridgeWithStaticConnectors() throws Exception {

        deployBridge(CONTAINER3, false);

        testFailoverWithBridge(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridgeWithDiscovery() throws Exception {

        deployBridge(CONTAINER3, true);

        testFailoverWithBridge(true, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore
    public void testKillDeployBridgeLiveThenBackupWithStaticConnectors() throws Exception {

        deployBridge(CONTAINER1, false);

        deployBridge(CONTAINER2, false);

        testDeployBridgeLiveThenBackup(false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore
    public void testShutdownDeployBridgeLiveThenBackupWithStaticConnectors() throws Exception {

        deployBridge(CONTAINER1, false);

        deployBridge(CONTAINER2, false);

        testDeployBridgeLiveThenBackup(true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore
    public void testKillDeployBridgeLiveThenBackupWithDiscovery() throws Exception {
        deployBridge(CONTAINER1, true);

        deployBridge(CONTAINER2, true);

        testDeployBridgeLiveThenBackup(false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Ignore
    public void testShutdownDeployBridgeLiveThenBackupWithDiscovery() throws Exception {

        deployBridge(CONTAINER1, true);

        deployBridge(CONTAINER2, true);

        testDeployBridgeLiveThenBackup(true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailoverWithDiscovery() throws Exception {

        deployBridge(CONTAINER3, true);

        testInitialFailover();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailoverWithStaticConnectors() throws Exception {

        deployBridge(CONTAINER3, false);

        testInitialFailover();
    }


    protected void deployBridge(String containerName, boolean useDiscovery) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        if (useDiscovery) {
            if (CONTAINER3.equals(containerName)) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupName);
            } else {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupNameForBridges);
            }

        } else {
            if (CONTAINER1.equals(containerName) || CONTAINER2.equals(containerName)) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, "bridge-connector");
            } else if (CONTAINER3.equals(containerName)) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, "bridge-connector", "bridge-connector-backup");
            }
        }

        jmsAdminOperations.close();

        stopServer(containerName);
    }

}
