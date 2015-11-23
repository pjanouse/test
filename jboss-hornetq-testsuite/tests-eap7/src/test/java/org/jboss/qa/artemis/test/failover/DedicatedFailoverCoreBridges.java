package org.jboss.qa.artemis.test.failover;
// TODO test re-deploy from backup -> live (failback)

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ArtemisJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

/**
 * This is modified failover with mdb test case which is testing remote jca.
 * Tests JMS bridge failover deploy/un-deploy
 *
 * @author mnovak@redhat.com
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF CORE BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-bridges/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 */
@RunWith(Arquillian.class)
public class DedicatedFailoverCoreBridges extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverCoreBridges.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1500;


    // Queue to send messages in
    private String inQueueName = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    private String outQueueName = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueueName;

    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ArtemisJMSImplementation.getInstance());

    //    MessageBuilder messageBuilder = new TextMessageBuilder(10);
    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
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

    /**
     * @throws Exception
     */
    public void testInitialFailback(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareServers(connectorType);

        // start live-backup servers
        container(1).start();
        container(2).start();
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        Thread.sleep(10000);

        // stop live so backup activates
        container(1).stop();
        // start server with bridge
        container(3).start();

        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(container(3), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        messageBuilder.setAddDuplicatedHeader(true);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        // give it some time for backup to alive
        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 60000, container(2));

        // start container 1 again
        container(1).start();

        CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), 60000);
        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 120000, container(1));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueueJndiName, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(3).stop();
        container(2).stop();
    }

    /**
     * @throws Exception
     */
    public void testFailoverWithBridge(Constants.CONNECTOR_TYPE connectorType, boolean failback, Constants.FAILURE_TYPE failureType) throws Exception {

        prepareServers(connectorType);

        // start live-backup servers
        container(1).start();
        container(2).start();
        container(3).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(3), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        messageBuilder.setAddDuplicatedHeader(true);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
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

        producerToInQueue1.stopSending();
        producerToInQueue1.join();

        ReceiverClientAck receiver1;
        if (failback) {
            receiver1 = new ReceiverClientAck(container(1), outQueueJndiName, 30000, 100, 10);
        } else {
            receiver1 = new ReceiverClientAck(container(2), outQueueJndiName, 30000, 100, 10);
        }
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();
        Assert.assertEquals("Number of sent and received messages is different.", producerToInQueue1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        container(3).stop();
        container(2).stop();
        container(1).stop();

    }

    @After
    @Before
    public void stopAllServers() {

        container(3).stop();
        container(2).stop();
        container(1).stop();

    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses pre configured (static) http connector and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use static connector
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
    public void testFailoverKillWithBridgeWitStaticConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.HTTP_CONNECTOR, false, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses pre configured (static) nio connector and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use static connector
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
    public void testFailoverKillWithBridgeWitStaticNIOConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_NIO, false, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which UDP netty discovery and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to UDP discovery
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
    public void testFailoverKillWithBridgeWithNettyDiscovery() throws Exception {


        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY, false, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which UDP netty discovery and resends messages from its InQueue to live’s OutQueue. Kill live
     * server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to UDP discovery
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
    public void testFailoverKillWithBridgeWithJGroupsDiscovery() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.JGROUPS_DISCOVERY, false, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses pre configured (static) http connector and resends messages from its InQueue to live’s OutQueue. Shut
     * down live server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use static connector
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Cleanly shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithStaticConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.HTTP_CONNECTOR, false, Constants.FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails test failover of core bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge which uses UDP netty discovery connector and resends messages from its InQueue to live’s OutQueue. Shut
     * down live server and check that all messages are in OutQueue on backup.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use UDP discovery
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Cleanly shut down live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailoverShutdownWithBridgeWithNettyDiscovery() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY, false, Constants.FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) http connector which resends messages from its InQueue to live’s OutQueue.
     * Kill live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed core bridge configured to use pre-defined (static) connector
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
    public void testFailbackKillWithBridgeWitStaticConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.HTTP_CONNECTOR, true, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) bio connector which resends messages from its InQueue to live’s OutQueue.
     * Kill live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed core bridge configured to use pre-defined (static) connector
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
    public void testFailbackKillWithBridgeWitStaticNIOConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_NIO, true, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use UDP netty discovery which resends messages from its InQueue to live’s OutQueue. Shut down live
     * server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed core bridge  configured to use UDP discovery
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Shutdown live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackKillWithBridgeWithNettyDiscovery() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY, true, Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) http connector which resends messages from its InQueue to live’s OutQueue.
     * Shut down live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed core bridge  configured to use pre-defined (static) connector
     * which sends messages from 3rd server’s InQueue to live’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * <li>Cleanly shut down live server</li>
     * <li>start live server again</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailbackShutdownWithBridgeWithStaticConnectors() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.HTTP_CONNECTOR, true, Constants.FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails Test failback of JMS bridge. Start live server and its backup. Start 3rd server with deployed core
     * bridge configured to use pre-defined (static) connector which resends messages from its InQueue to live’s OutQueue.
     * Kill live server, wait a while and start live again (failback)
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Start 3rd server with deployed core bridge  configured to use pre-defined (static) connector
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
    public void testFailbackShutdownWithBridgeWithDiscovery() throws Exception {
        testFailoverWithBridge(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY, true, Constants.FAILURE_TYPE.SHUTDOWN);
    }


    /**
     * @tpTestDetails This scenario tests failback of core bridge from backup to live. Core bridge connects to backup first.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Stop live server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use UDP netty discovery which sends messages from 3rd server’s
     * InQueue to backup’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start live server again so failback occurs</li>
     * <li>Start consumer which reads messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailbackWithNettyDiscovery() throws Exception {
        testInitialFailback(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY);
    }

    /**
     * @tpTestDetails This scenario tests failback of core bridge from backup to live. Core bridge connects to backup first.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Stop live server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use UDP jgroups discovery which sends messages from 3rd server’s
     * InQueue to backup’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start live server again so failback occurs</li>
     * <li>Start consumer which reads messages from OutQueue from live server</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailbackWithJgroupsDiscovery() throws Exception {
        testInitialFailback(Constants.CONNECTOR_TYPE.JGROUPS_DISCOVERY);
    }

    /**
     * @tpTestDetails This scenario tests failback of core bridge from backup to live. Core bridge connects to backup first.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Stop live server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use pre-defined http connector which sends messages from 3rd server’s
     * InQueue to backup’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start live server again so failback occurs</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailbackWithStaticHttpConnectors() throws Exception {
        testInitialFailback(Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
    }

    /**
     * @tpTestDetails This scenario tests failback of core bridge from backup to live. Core bridge connects to backup first.
     * @tpProcedure <ul>
     * <li>Start live and its backup server</li>
     * <li>Stop live server</li>
     * <li>Start 3rd server with deployed Core bridge configured to use pre-defined nio connector which sends messages from 3rd server’s
     * InQueue to backup’s OutQueue</li>
     * <li>Start producer which sends messages to InQueue</li>
     * <li>Start live server again so failback occurs</li>
     * <li>Start consumer which reads messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver will receive all messages which were sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testInitialFailbackWithStaticNIOConnectors() throws Exception {
        testInitialFailback(Constants.CONNECTOR_TYPE.NETTY_NIO);
    }


    protected void prepareServers(Constants.CONNECTOR_TYPE connectorType) throws Exception {
        prepareLiveServerEAP7(container(1), JOURNAL_DIRECTORY_A, Constants.JOURNAL_TYPE.ASYNCIO, connectorType);
        prepareBackupServerEAP7(container(2), JOURNAL_DIRECTORY_A, Constants.JOURNAL_TYPE.ASYNCIO, connectorType);
        prepareServerWithBridge(container(3), container(1), container(2), connectorType);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     * @param journalType      ASYNCIO, NIO
     * @param connectorType    whether to use NIO in connectors for CF or old blocking IO, or http connector
     */
    protected void prepareLiveServerEAP7(Container container, String journalDirectory, Constants.JOURNAL_TYPE journalType, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        setConnectorTypeForLiveBackupPair(container, connectorType);
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
    protected void prepareBackupServerEAP7(Container container, String journalDirectory, Constants.JOURNAL_TYPE journalType, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setJournalType(journalType.toString());
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setPersistenceEnabled(true);
        setConnectorTypeForLiveBackupPair(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreSlave(true, 5000, true, true, false, null, null, null, null);
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * @param container     container where to set it
     * @param connectorType type of the connector
     */
    protected void setConnectorTypeForLiveBackupPair(Container container, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        String messagingGroupSocketBindingForConnector = "messaging";
        String nettyConnectorName = "netty";
        String nettyAcceptorName = "netty";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        switch (connectorType) {
            case HTTP_CONNECTOR:
                break;
            case NETTY_NIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, null);
                // add acceptor wtih NIO
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

        jmsAdminOperations.close();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    protected void prepareServerWithBridge(Container container, Container jmsLiveServer, Container jmsBackupServer, Constants.CONNECTOR_TYPE connectorType) throws Exception {

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
                jmsAdminOperations.addRemoteSocketBinding(removeSocketBindingToLive, container(1).getHostname(), container(1).getHornetqPort());
                jmsAdminOperations.createHttpConnector(remoteConnectorNameToLive, removeSocketBindingToLive, null);
                jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingToBackup, container(2).getHostname(), container(2).getHornetqPort());
                jmsAdminOperations.createHttpConnector(remoteConnectorNameToBackup, remoteSocketBindingToBackup, null);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, remoteConnectorNameToLive, remoteConnectorNameToBackup);
                break;
            case NETTY_NIO:
                jmsAdminOperations.addRemoteSocketBinding(removeSocketBindingToLive, container(1).getHostname(), defaultPortForMessagingSocketBinding + container(1).getPortOffset());
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToLive, removeSocketBindingToLive, null);
                jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingToBackup, container(2).getHostname(), defaultPortForMessagingSocketBinding + container(2).getPortOffset());
                jmsAdminOperations.createRemoteConnector(remoteConnectorNameToBackup, remoteSocketBindingToBackup, null);
                jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1, remoteConnectorNameToLive);
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
        jmsAdminOperations.close();
        container.stop();
    }
}
