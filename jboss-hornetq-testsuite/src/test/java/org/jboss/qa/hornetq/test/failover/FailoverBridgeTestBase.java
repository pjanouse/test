package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class FailoverBridgeTestBase extends HornetQTestCase {


    private static final Logger logger = Logger.getLogger(FailoverBridgeTestBase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1500;

    // quality services
    public final static String AT_MOST_ONCE = "AT_MOST_ONCE";
    public final static String DUPLICATES_OK = "DUPLICATES_OK";
    public final static String ONCE_AND_ONLY_ONCE = "ONCE_AND_ONLY_ONCE";

    // Queue to send messages in
    String inQueueName = "InQueue";
    String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    String outQueueName = "OutQueue";
    String outQueueJndiName = "jms/queue/" + outQueueName;
    //    MessageBuilder messageBuilder = new TextMessageBuilder(10);
    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 1000);
    String discoveryGroupName = "dg-group1";
    String discoveryGroupNameForBridges = "dg-group2";

    String messagingGroupSocketBindingNameForBridges = "messaging-group-bridges";
    String messagingGroupMulticastAddressForBridgeDiscovery = "234.46.21.68";

    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();


    public void testDeployBridgeLiveThenBackup(boolean shutdown) throws Exception {
        testDeployBridgeLiveThenBackup(shutdown, ONCE_AND_ONLY_ONCE);
    }

    public void testDeployBridgeLiveThenBackup(boolean shutdown, String qualityOfService) throws Exception {

        // start live-backup servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        controller.start(CONTAINER3);

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(CONTAINER1_IP, getJNDIPort(), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        // verify that some messages got to outqueue on container3
        JMSOperations jmsOperations = getJMSOperations(CONTAINER3);
        long startTime = System.currentTimeMillis();
        while (jmsOperations.getCountOfMessagesOnQueue(outQueueName) < NUMBER_OF_MESSAGES_PER_PRODUCER / 10) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > 120000) {
                Assert.fail("Target queue for the bridge does not receive any messages. Failing the test");
            }
        }
        jmsOperations.close();

        logger.warn("###################################");
        if (shutdown) {
            stopServer(CONTAINER1);
            logger.warn("Server shutdowned");
        } else {
            killServer(CONTAINER1);
            controller.kill(CONTAINER1); // check whether server was really killed
            logger.warn("Server killed");
        }
        logger.warn("###################################");
        waitHornetQToAlive(CONTAINER2_IP, 5445, 120000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER3_IP, 4447, outQueueJndiName, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        if (ONCE_AND_ONLY_ONCE.equals(qualityOfService)) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        } else if (AT_MOST_ONCE.equals(qualityOfService)) {
            Assert.assertTrue("There should be more send than received messages. Sent: " + producerToInQueue1.getListOfSentMessages().size()
                    + " , received: " + receiver1.getListOfReceivedMessages().size(),
                    producerToInQueue1.getListOfSentMessages().size() >= receiver1.getListOfReceivedMessages().size());
        } else if (DUPLICATES_OK.equals(qualityOfService)) {
            Assert.assertTrue("There should be more received than send messages. Sent: " + producerToInQueue1.getListOfSentMessages().size()
                    + " , received: " + receiver1.getListOfReceivedMessages().size(),
                    producerToInQueue1.getListOfSentMessages().size() <= receiver1.getListOfReceivedMessages().size());
        }

        stopServer(CONTAINER3);
        stopServer(CONTAINER2);
        stopServer(CONTAINER1);


    }

    /**
     * @throws Exception
     */
    public void testInitialFailover() throws Exception {

        // start live-backup servers
        // Without starting live first the backup server will not start
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        waitHornetQToAlive(CONTAINER1_IP, 5445, 60000);
        Thread.sleep(10000);
        controller.stop(CONTAINER1);

        controller.start(CONTAINER3);

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");
        logger.info("#############################");
        logger.info("Stopping container 1");
        logger.info("#############################");
        stopServer(CONTAINER1);
        logger.info("#############################");
        logger.info("Container 1 stopped");
        logger.info("#############################");


        ProducerClientAck producerToInQueue1 = new ProducerClientAck(CONTAINER3_IP, getJNDIPort(), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        // give it some time for backup to alive
        waitHornetQToAlive(CONTAINER2_IP, 5445, 120000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER2_IP, 4447, outQueueJndiName, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(CONTAINER3);
        stopServer(CONTAINER2);
    }

    /**
     * @param failback whether to do failback
     * @param shutdown shutdown server
     * @throws Exception
     */
    public void testFailoverWithBridge(boolean shutdown, boolean failback) throws Exception {
        testFailoverWithBridge(shutdown, failback, ONCE_AND_ONLY_ONCE);
    }

    /**
     * @param failback         whether to do failback
     * @param shutdown         shutdown server
     * @param qualityOfService quality of service
     * @throws Exception
     */
    public void testFailoverWithBridge(boolean shutdown, boolean failback, String qualityOfService) throws Exception {

        // start live-backup servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        controller.start(CONTAINER3);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(CONTAINER3_IP, getJNDIPort(), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        messageBuilder.setAddDuplicatedHeader(false);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        // verify that some messages got to outqueue on container1
        JMSOperations jmsOperations = getJMSOperations(CONTAINER1);
        long startTime = System.currentTimeMillis();
        while (jmsOperations.getCountOfMessagesOnQueue(outQueueName) < NUMBER_OF_MESSAGES_PER_PRODUCER / 20) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > 600000) {
                Assert.fail("Target queue for the bridge does not receive any messages. Failing the test");
            }
        }
        jmsOperations.close();

        logger.warn("###################################");
        if (shutdown) {
            stopServer(CONTAINER1);
            logger.warn("Server shutdowned");
        } else {
            killServer(CONTAINER1);
            controller.kill(CONTAINER1);
            logger.warn("Server killed");
        }
        logger.warn("###################################");
        waitHornetQToAlive(CONTAINER2_IP, 5445, 120000);

        // if failback then start container1 again
        // wait for container1 to start
        // start consumer on container1
        // else start receiver on container2
        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            controller.start(CONTAINER1);
            junit.framework.Assert.assertTrue("Live did not start again - failback failed.", waitHornetQToAlive(CONTAINER1_IP, 5445, 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            Thread.sleep(5000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            stopServer(CONTAINER2);
            logger.warn("########################################");
            logger.warn("failback - Backup server stopped");
            logger.warn("########################################");
        }

        ReceiverClientAck receiver1;
        if (failback) {
            receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueueJndiName, 30000, 100, 10);
        } else {
            receiver1 = new ReceiverClientAck(CONTAINER2_IP, 4447, outQueueJndiName, 30000, 100, 10);
        }

        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        if (ONCE_AND_ONLY_ONCE.equals(qualityOfService)) {
            Assert.assertEquals("There is different number of sent and received. Sent messages: " + producerToInQueue1.getListOfSentMessages().size()
                    + " received messages: " + receiver1.getListOfReceivedMessages().size(),
                    producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        } else if (AT_MOST_ONCE.equals(qualityOfService)) {
            Assert.assertTrue("There is more received messages then sent. That's bad for AT_MOST_ONCE. Sent messages: " + producerToInQueue1.getListOfSentMessages().size()
                    + " received messages: " + receiver1.getListOfReceivedMessages().size(),
                    producerToInQueue1.getListOfSentMessages().size() >= receiver1.getListOfReceivedMessages().size());
        } else if (DUPLICATES_OK.equals(qualityOfService)) {
            Assert.assertTrue("There is more send messages than received. That's bad for DUPLICATES_OK. Sent messages: " + producerToInQueue1.getListOfSentMessages().size()
                    + " received messages: " + receiver1.getListOfReceivedMessages().size(),
                    producerToInQueue1.getListOfSentMessages().size() <= receiver1.getListOfReceivedMessages().size());
        }

        stopServer(CONTAINER3);
        stopServer(CONTAINER2);
        stopServer(CONTAINER1);

    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareServerWithBridge(String containerName, String jmsLiveServerBindingAddress, String jmsBackupServerBindingAddress) {

        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String remoteConnectorNameBackup = "netty-remote-backup";
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";
        String connectionFactoryName = "RemoteConnectionFactory";
        String connectionFactoryJndiName = "java:/jms/" + connectionFactoryName;
        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        // add broadcast group to 3rd server
        jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingNameForBridges, messagingGroupMulticastAddressForBridgeDiscovery, 9876);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingNameForBridges, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
//        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsLiveServerBindingAddress, 5445);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", jmsBackupServerBindingAddress, 5445);
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.createRemoteConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(remoteConnectorName);
        connectorList.add(remoteConnectorNameBackup);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorList);
        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.addJndiBindingForConnectionFactory(connectionFactoryName, connectionFactoryJndiName);

        jmsAdminOperations.createPooledConnectionFactory("ra-connection-factory", "java:/jmsXALocal", "netty");
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("ra-connection-factory", connectorName);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory("ra-connection-factory", true);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", CONTAINER1_IP, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge-backup", CONTAINER2_IP, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector-backup", "messaging-bridge-backup", null);
        jmsAdminOperations.close();

        stopServer(containerName);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(String containerName, String journalDirectory) {

        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String connectionFactoryJndiName = "java:/jms/" + connectionFactoryName;
        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);
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
        jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingNameForBridges, messagingGroupMulticastAddressForBridgeDiscovery, 9876);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupNameForBridges, messagingGroupSocketBindingNameForBridges, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.addJndiBindingForConnectionFactory(connectionFactoryName, connectionFactoryJndiName);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", CONTAINER3_IP, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);

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
    protected void prepareBackupServer(String containerName, String journalDirectory) {

        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String connectionFactoryJndiName = "java:/jms/" + connectionFactoryName;
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        // this is necessary for failover tests with discovery
        // add discovery group to live/backup pair
        jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingNameForBridges, messagingGroupMulticastAddressForBridgeDiscovery, 9876);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupNameForBridges, messagingGroupSocketBindingNameForBridges, 10000);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.addJndiBindingForConnectionFactory(connectionFactoryName, connectionFactoryJndiName);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", CONTAINER3_IP, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);

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


    @Before
    public void prepareServers() throws Exception {
        prepareTopology();
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER3);
        stopServer(CONTAINER4);
        stopServer(CONTAINER2);
        stopServer(CONTAINER1);
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareTopology() throws Exception {

        prepareLiveServer(CONTAINER1, JOURNAL_DIRECTORY_A);

        prepareBackupServer(CONTAINER2, JOURNAL_DIRECTORY_A);

        prepareServerWithBridge(CONTAINER3, CONTAINER1_IP, CONTAINER2_IP);

        copyApplicationPropertiesFiles();

    }
}
