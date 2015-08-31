package org.jboss.qa.hornetq.test.failover.jms20;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients20.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients20.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is modified failover with mdb test case which is testing remote jca.
 *
 * @tpChapter Recovery/Failover testing
 * @tpSubChapter FAILOVER OF HORNETQ RESOURCE ADAPTER WITH SHARED STORE AND REPLICATED JOURNAL IN DEDICATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-mdb/
 * @tpTcmsLink tbd
 * @tpTestCaseDetails This is modified failover with mdb test case which is
 * testing remote jca. There are two servers in dedicated HA topology and MDB
 * deployed on another server. Live server is shutdown/killed and correct
 * failover/failback is tested. Live and backup servers use shared stores.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class DedicatedFailoverTestCaseWithMdb extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverCoreBridges.class);
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 2000;

    // Queue to send messages in 
    String inQueueName = "InQueue";
    String inQueueJndiName = "jms/queue/" + inQueueName;

    // queue for receive messages out
    String outQueueName = "OutQueue";
    String outQueueJndiName = "jms/queue/" + outQueueName;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();

    private final Archive mdb1 = getDeployment1();


    public static Archive getDeployment1() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdb1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }
 
    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Kill live server. Receive
     * messages from OutQueue from backup server.
     *
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
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testKill() throws Exception {
        testFailoverWithRemoteJca(false);
    }
    
    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Kill live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     *
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
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testKillWithFailback() throws Exception {
        testFailbackWithRemoteJca(false);
    }
    
    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology. InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Shutdown live server. Wait for
     * backup server to come alive, then start live server again and stop backup.
     * Receive messages from OutQueue from live server.
     *
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
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testShutdownWithFailback() throws Exception {
        testFailbackWithRemoteJca(true);
    }
   
    /**
     * @tpTestDetails There are three servers. Live server (Node 1) and backup
     * server (Node 2) are in dedicated HA topology.InQueue and OutQueue are
     * deployed on live and backup. Send messages to InQueue on live server.
     * When all messages are sent, deploy message driven bean on Node 3. MDB
     * sends messages form InQueue to OutQueue. Shutdown live server. Receive
     * messages from OutQueue from backup server.
     *
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
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testShutdown() throws Exception {
        testFailoverWithRemoteJca(true);
    }

    /**
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailoverWithRemoteJca(boolean shutdown) throws Exception {

        prepareRemoteJcaTopology();
        // start live-backup servers
        container(1).start();
        container(2).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(3).start();

        logger.info("Deploying MDB to mdb server.");
//        // start mdb server
        container(3).deploy(mdb1);

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(container(1), outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            logger.info("Stopping container 1.");
            container(1).stop();
            logger.info("Container 1 stopped.");
        } else {
            logger.info("Killing container 1.");
            container(1).kill();
            logger.info("Container 1 killed.");
        }

        Assert.assertTrue("Backup server (container2) did not start after kill.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 600000));
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(container(2), outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(360000, container(2));
        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(2));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(2), outQueueJndiName, 3000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        container(3).undeploy(mdb1);

        container(3).stop();
        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());


    }

    /**
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailbackWithRemoteJca(boolean shutdown) throws Exception {

        prepareRemoteJcaTopology();
        // start live-backup servers
        container(1).start();
        container(2).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();


        container(3).start();

        // start mdb server
        container(3).deploy(mdb1);
        logger.info("MDB was deployed to mdb server - container 3");

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(container(1), outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            container(1).stop();
            logger.info("Container 1 shut downed.");
        } else {
            container(1).kill();
            logger.info("Container 1 killed.");
        }

        Assert.assertTrue("Backup server (container2) did not start after kill.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(container(2), outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));
        Thread.sleep(10000);
        logger.info("Container 1 starting...");
        container(1).start();
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
        logger.info("Container 1 started again");
        Thread.sleep(10000);
        logger.info("Container 2 stopping...");
        container(2).stop();
        logger.info("Container 2 stopped");

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueueJndiName, 3000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();


        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();


        logger.info("Undeploy mdb from mdb server and stop servers 1 and 3.");
        container(3).undeploy(mdb1);
        container(3).stop();
        container(2).stop();
        container(1).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
    }

    /**
     * Return true if numberOfMessages is in queue in the given timeout. Otherwise false.
     *
     * @param container test container
     * @param queueName queue name (not jndi name)
     * @param numberOfMessages number of messages
     * @param timeout time out
     * @return returns true if numberOfMessages is in queue in the given timeout. Otherwise false.
     * @throws Exception
     */
    private boolean waitForMessagesOnOneNode(Container container, String queueName, int numberOfMessages, long timeout) throws Exception{
        long startTime = System.currentTimeMillis();
        JMSOperations jmsOperations = container.getJmsOperations();
        while (numberOfMessages > (jmsOperations.getCountOfMessagesOnQueue(queueName)))   {
            if (System.currentTimeMillis() - startTime > timeout)  {
                return false;
            }
            Thread.sleep(1000);
        }
        jmsOperations.close();

        return true;
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     *
     */
    @Before
    @After
    public void stopAllServers() {
        container(3).stop();
        container(2).stop();
        container(1).stop();
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {
            prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A);
            prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A);
            prepareMdbServer(container(3), container(1), container(2));
            copyApplicationPropertiesFiles();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareMdbServer(Container container, Container containerLive, Container containerBackup) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String remoteConnectorNameBackup = "netty-remote-backup";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown("RemoteConnectionFactory", true);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
//        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
//        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
//        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", containerLive.getHostname(), containerLive.getHornetqPort());
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", containerBackup.getHostname(), containerBackup.getHornetqPort());
        jmsAdminOperations.createRemoteConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(remoteConnectorName);
        connectorList.add(remoteConnectorNameBackup);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorList);

        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);

        jmsAdminOperations.createPooledConnectionFactory("ra-connection-factory", "java:/jmsXALocal", "netty");
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("ra-connection-factory", connectorName);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory("ra-connection-factory", true);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setNodeIdentifier(123);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        Test container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(Container container, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.kill();
        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.setFailoverOnShutdown("RemoteConnectionFactory", true);
        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareBackupServer(Container container, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setSecurityEnabled(true);
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
     * TODO - change config by cli console
     */
    protected void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }


}
