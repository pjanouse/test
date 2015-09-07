package org.jboss.qa.hornetq.test.failover;
// TODO test re-deploy from backup -> live (failback)

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is modified failover with mdb test case which is testing remote jca.
 * Tests JMS bridge failover deploy/un-deploy
 * @tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF CORE BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-60-hornetq-ha-failover-bridges/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class DedicatedFailoverCoreBridges extends FailoverBridgeTestBase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverCoreBridges.class);

    @After
    @Before
    public void stopAllServers() {
        container(3).stop();
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses pre configured (static) connector and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to use static connector
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
    public void testFailoverKillWithBridgeWitStaticConnectors() throws Exception {

        deployBridge(container(3), false);

        testFailoverWithBridge(false, false);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which UDP discovery and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to UDP discovery
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
    public void testFailoverKillWithBridgeWithDiscovery() throws Exception {

        deployBridge(container(3), true);

        testFailoverWithBridge(false, false);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses pre configured (static) connector and resends messages from its InQueue to live’s OutQueue. Shut
     * down live server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to use static connector
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Cleanly shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithStaticConnectors() throws Exception {

        deployBridge(container(3), false);

        testFailoverWithBridge(true, false);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses UDP discovery connector and resends messages from its InQueue to live’s OutQueue. Shut
     * down live server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to use UDP discovery
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Cleanly shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithDiscovery() throws Exception {

        deployBridge(container(3), true);

        testFailoverWithBridge(true, false);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) connector which resends messages from its InQueue to live’s OutQueue.
     * Kill live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed core bridge  configured to use pre-defined (static) connector
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
    public void testFailbackKillWithBridgeWitStaticConnectors() throws Exception {

        deployBridge(container(3), false);

        testFailoverWithBridge(false, true);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use UDP discovery which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed core bridge  configured to use UDP discovery
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Shutdown live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridgeWithDiscovery() throws Exception {

        deployBridge(container(3), true);

        testFailoverWithBridge(false, true);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) connector which resends messages from its InQueue to live’s OutQueue.
     * Shut down live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed core bridge  configured to use pre-defined (static) connector
     *     which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     *     <li>Start producer which sends messages to InQueue</li>
     *     <li>Start consumer which reads messages from OutQueue</li>
     *     <li>Cleanly shut down live server</li>
     *     <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridgeWithStaticConnectors() throws Exception {

        deployBridge(container(3), false);

        testFailoverWithBridge(true, true);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) connector which resends messages from its InQueue to live’s OutQueue.
     * Kill live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Start 3rd server with deployed core bridge  configured to use pre-defined (static) connector
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
    public void testFailbackShutdownWithBridgeWithDiscovery() throws Exception {

        deployBridge(container(3), true);

        testFailoverWithBridge(true, true);
    }

//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore
//    public void testKillDeployBridgeLiveThenBackupWithStaticConnectors() throws Exception {
//
//        deployBridge(CONTAINER1_NAME, false);
//
//        deployBridge(CONTAINER2_NAME, false);
//
//        testDeployBridgeLiveThenBackup(false);
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore
//    public void testShutdownDeployBridgeLiveThenBackupWithStaticConnectors() throws Exception {
//
//        deployBridge(CONTAINER1_NAME, false);
//
//        deployBridge(CONTAINER2_NAME, false);
//
//        testDeployBridgeLiveThenBackup(true);
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore
//    public void testKillDeployBridgeLiveThenBackupWithDiscovery() throws Exception {
//        deployBridge(CONTAINER1_NAME, true);
//
//        deployBridge(CONTAINER2_NAME, true);
//
//        testDeployBridgeLiveThenBackup(false);
//    }
//
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    @Ignore
//    public void testShutdownDeployBridgeLiveThenBackupWithDiscovery() throws Exception {
//
//        deployBridge(CONTAINER1_NAME, true);
//
//        deployBridge(CONTAINER2_NAME, true);
//
//        testDeployBridgeLiveThenBackup(true);
//    }

    /**
     * @tpTestDetails This scenario tests failover of Core bridge from live to backup before any client connects
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Stop live server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to use UDP discovery which sends messages from 3rd server’s
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
    public void testInitialFailoverWithDiscovery() throws Exception {

        deployBridge(container(3), true);

        testInitialFailover();
    }

    /**
     * @tpTestDetails This scenario tests failover of Core bridge from live to backup before any client connects
     * @tpProcedure <ul>
     *     <li>Start live and its backup server</li>
     *     <li>Stop live server</li>
     *     <li>Start 3rd server with deployed Core bridge configured to use pre-defined connector which sends messages from 3rd server’s
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
    public void testInitialFailoverWithStaticConnectors() throws Exception {

        deployBridge(container(3), false);

        testInitialFailover();
    }


    protected void deployBridge(Container container, boolean useDiscovery) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (useDiscovery) {
            if (CONTAINER3_NAME.equals(container.getName())) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupName);
            } else {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, true, discoveryGroupNameForBridges);
            }

        } else {
            if (CONTAINER1_NAME.equals(container.getName()) || CONTAINER2_NAME.equals(container.getName())) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, "bridge-connector");
            } else if (CONTAINER3_NAME.equals(container.getName())) {
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, "bridge-connector", "bridge-connector-backup");
            }
        }

        jmsAdminOperations.close();
        container.stop();
    }

}
