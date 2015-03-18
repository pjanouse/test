package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.tools.JMSOperations;
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

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER3_NAME)
    public static Archive getDeployment1() throws Exception {

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

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testKill() throws Exception {
        testFailoverWithRemoteJca(false);
    }

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testKillWithFailback() throws Exception {
        testFailbackWithRemoteJca(false);
    }

    @RunAsClient
    @Test
    @RestoreConfigBeforeTest @CleanUpBeforeTest
    public void testShutdownWithFailback() throws Exception {
        testFailbackWithRemoteJca(true);
    }

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
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        controller.start(CONTAINER3_NAME);

        logger.info("Deploying MDB to mdb server.");
//        // start mdb server
        deployer.deploy("mdb1");

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(CONTAINER1_NAME, outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            logger.info("Stopping container 1.");
            stopServer(CONTAINER1_NAME);
            logger.info("Container 1 stopped.");
        } else {
            logger.info("Killing container 1.");
            killServer(CONTAINER1_NAME);
            logger.info("Container 1 killed.");
        }

        Assert.assertTrue("Backup server (container2) did not start after kill.", waitHornetQToAlive(getHostname(
                CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 600000));
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(CONTAINER2_NAME, outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));

        waitUntilThereAreNoPreparedHornetQTransactions(360000, CONTAINER2_NAME);
        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, CONTAINER2_NAME);

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER2_NAME), getJNDIPort(CONTAINER2_NAME), outQueueJndiName, 3000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        deployer.undeploy("mdb1");

        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);
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
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setCommitAfter(500);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.start();
        producerToInQueue1.join();


        controller.start(CONTAINER3_NAME);

        // start mdb server
        deployer.deploy("mdb1");
        logger.info("MDB was deployed to mdb server - container 3");

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                waitForMessagesOnOneNode(CONTAINER1_NAME, outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 20, 300000));

        if (shutdown) {
            stopServer(CONTAINER1_NAME);
            logger.info("Container 1 shut downed.");
        } else {
            killServer(CONTAINER1_NAME);
            controller.kill(CONTAINER1_NAME);
            logger.info("Container 1 killed.");
        }

        Assert.assertTrue("Backup server (container2) did not start after kill.", waitHornetQToAlive(getHostname(
                CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), 300000));
        Assert.assertTrue("MDB can't resend messages after kill of live server. Time outed for waiting to get messages in outQueue",
                waitForMessagesOnOneNode(CONTAINER2_NAME, outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 2, 600000));
        Thread.sleep(10000);
        logger.info("Container 1 starting...");
        controller.start(CONTAINER1_NAME);
        waitHornetQToAlive(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), 600000);
        logger.info("Container 1 started again");
        Thread.sleep(10000);
        logger.info("Container 2 stopping...");
        stopServer(CONTAINER2_NAME);
        logger.info("Container 2 stopped");

        waitUntilThereAreNoPreparedHornetQTransactions(300000, CONTAINER1_NAME);
        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, CONTAINER1_NAME);

        ReceiverClientAck receiver1 = new ReceiverClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiName, 3000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();


        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();


        logger.info("Undeploy mdb from mdb server and stop servers 1 and 3.");
        deployer.undeploy("mdb1");
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
    }

    /**
     * Return true if numberOfMessages is in queue in the given timeout. Otherwise false.
     *
     * @param containerName container name
     * @param queueName queue name (not jndi name)
     * @param numberOfMessages number of messages
     * @param timeout time out
     * @return returns true if numberOfMessages is in queue in the given timeout. Otherwise false.
     * @throws Exception
     */
    private boolean waitForMessagesOnOneNode(String containerName, String queueName, int numberOfMessages, long timeout) throws Exception{
        long startTime = System.currentTimeMillis();
        JMSOperations jmsOperations = getJMSOperations(containerName);
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
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {

            prepareLiveServer(CONTAINER1_NAME, getHostname(CONTAINER1_NAME), JOURNAL_DIRECTORY_A);

            prepareBackupServer(CONTAINER2_NAME, getHostname(CONTAINER2_NAME), JOURNAL_DIRECTORY_A);

            prepareMdbServer(CONTAINER3_NAME, CONTAINER1_NAME, CONTAINER2_NAME);

            copyApplicationPropertiesFiles();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareMdbServer(String containerName, String containerLive, String containerBackup) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String remoteConnectorNameBackup = "netty-remote-backup";
        String pooledConnectionFactoryName = "hornetq-ra";
        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

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

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(containerLive), getHornetqPort(containerLive));
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", getHostname(containerBackup), getHornetqPort(containerBackup));
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
        controller.stop(containerName);
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.kill(containerName);
        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
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

        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareBackupServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

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

        controller.stop(containerName);
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

            copyFile(applicationUsersModified, applicationUsersOriginal);
            copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }


}
