package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * <p/>
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh2TestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5000;
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // inTopic
    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    String queueNamePrefix = "testQueue";

    String queueJndiNamePrefix = "jms/queue/testQueue";

    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() throws Exception {
        File propertyFile = new File(getJbossHome(CONTAINER2) + File.separator + "mdb1.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + CONTAINER1_IP);
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeployment2() throws Exception {

        File propertyFile = new File(getJbossHome(CONTAINER4) + File.separator + "mdb2.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + CONTAINER3_IP);
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = "mdb1WithFilter")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentWithFilter1() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2WithFilter")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeploymentWithFilter2() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = "nonDurableMdbOnTopic")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentNonDurableMdbOnTopic() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killWithFilters() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Shutdown jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3Shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2killWithTempTopic() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaWithTopic(failureSequence, false, false);

    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownOfJmsServers() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3kill() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3shutdown() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<String> failureSequence, boolean isShutdown, boolean isDurable) throws Exception {

        prepareRemoteJcaTopology();
        // jms server
        controller.start(CONTAINER1);

        // mdb server
        controller.start(CONTAINER2);

        if (!isDurable) {
            deployer.deploy("nonDurableMdbOnTopic");
            Thread.sleep(5000);
        }

        SoakPublisherClientAck producer1 = new SoakPublisherClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), inTopicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "clientId-myPublisher");
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.start();

        // deploy mdbs
        if (isDurable) {
            throw new UnsupportedOperationException("This was not yet implemented. Use Mdb on durable topic to do so.");
        }

        executeFailureSequence(failureSequence, 30000, isShutdown);

        // Wait to send and receive some messages
        Thread.sleep(60 * 1000);

        // set longer timeouts so xarecovery is done at least once
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), outQueueJndiName, 300000, 10, 10);

        receiver1.start();

        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getMessages()
                + ", Producer to jms1 server sent: " + producer1.getMessages() + " messages"));

        logger.info("Number of received messages: " + (receiver1.getCount()
                + ", Consumer from jms1 server received: " + receiver1.getCount() + " messages"));

        if (isDurable) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getMessages(), receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);

        } else {

            Assert.assertTrue("There SHOULD be different number of sent and received messages.",
                    producer1.getMessages() > receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);
            deployer.undeploy("nonDurableMdbOnTopic");
        }


        stopServer(CONTAINER2);
        stopServer(CONTAINER1);

    }

    public void testRemoteJcaInCluster(List<String> failureSequence, boolean isShutdown) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(List<String> failureSequence, boolean isShutdown, boolean isFiltered) throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        ProducerClientAck producer1 = new ProducerClientAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.start();
        producer1.join();

        // deploy mdbs
        if (isFiltered) {
            deployer.deploy("mdb1WithFilter");
            deployer.deploy("mdb2WithFilter");
        } else {
            deployer.deploy("mdb1");
            deployer.deploy("mdb2");
        }

        executeFailureSequence(failureSequence, 30000, isShutdown);

        // Wait to send and receive some messages
        Thread.sleep(60 * 1000);

        // set longer timeouts so xarecovery is done at least once
        ReceiverClientAck receiver1 = new ReceiverClientAck(getCurrentContainerForTest(), CONTAINER3_IP, getJNDIPort(), outQueueJndiName, 300000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();



        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));

        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        if (isFiltered) {
            deployer.undeploy("mdb1WithFilter");
            deployer.undeploy("mdb2WithFilter");
        } else {
            deployer.undeploy("mdb1");
            deployer.undeploy("mdb2");
        }

        stopServer(CONTAINER2);
        stopServer(CONTAINER4);
        stopServer(CONTAINER1);
        stopServer(CONTAINER3);
    }

    private List<String> checkLostMessages(List<String> listOfSentMessages, List<String> listOfReceivedMessages) {
        // TODO optimize or use some libraries
        //get lost messages
        List<String> listOfLostMessages = new ArrayList<String>();


        boolean messageIdIsMissing = false;
        for (String sentMessageId : listOfSentMessages) {
            for (String receivedMessageId : listOfReceivedMessages) {
                if (sentMessageId.equalsIgnoreCase(receivedMessageId)) {
                    messageIdIsMissing = true;
                }
            }
            if (messageIdIsMissing) {
                listOfLostMessages.add(sentMessageId);
                messageIdIsMissing = false;
            }
        }
        return listOfLostMessages;
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  map Contanier -> ContainerIP
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<String> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (String containerName : failureSequence) {
                Thread.sleep(timeBetweenKills);
                stopServer(containerName);
                Thread.sleep(3000);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        } else {
            for (String containerName : failureSequence) {
                Thread.sleep(timeBetweenKills);
                killServer(containerName);
                Thread.sleep(3000);
                controller.kill(containerName);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");
            }
        }
    }


    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER2);
        stopServer(CONTAINER4);
        stopServer(CONTAINER1);
        stopServer(CONTAINER3);
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {

        prepareJmsServer(CONTAINER1, CONTAINER1_IP);
        prepareMdbServer(CONTAINER2, CONTAINER2_IP, CONTAINER1_IP);

        prepareJmsServer(CONTAINER3, CONTAINER3_IP);
        prepareMdbServer(CONTAINER4, CONTAINER4_IP, CONTAINER3_IP);

        if (isEAP6()) {
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName  Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(String containerName, String bindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        if (isEAP5()) {

            int port = 9876;
            int groupPort = 9876;
            long broadcastPeriod = 500;


            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);

            jmsAdminOperations.close();

            deployDestinations(containerName);

        } else {


            String messagingGroupSocketBindingName = "messaging-group";

            controller.start(containerName);

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);
            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
            jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
            jmsAdminOperations.close();

            controller.stop(containerName);

            controller.start(containerName);

            jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

            jmsAdminOperations.close();

            deployDestinations(containerName);

            controller.stop(containerName);
        }

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareMdbServer(String containerName, String bindingAddress, String jmsServerBindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        if (isEAP5()) {

            int port = 9876;

            int groupPort = 9876;
            long broadcastPeriod = 500;

            String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
            Map<String, String> connectionParameters = new HashMap<String, String>();
            connectionParameters.put(jmsServerBindingAddress, String.valueOf(5445));
            boolean ha = false;

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(false);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        Map<String, String> params = new HashMap<String, String>();
//        params.put("host", jmsServerBindingAddress);
//        params.put("port", "5445");
//        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "", params);

            jmsAdminOperations.setRA(connectorClassName, connectionParameters, ha);
            jmsAdminOperations.close();

        } else {


            String remoteConnectorName = "netty-remote";
            String messagingGroupSocketBindingName = "messaging-group";

            controller.start(containerName);

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(false);

            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 5000, 1024 * 1024);

            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.close();
            controller.stop(containerName);
        }
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

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

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName containerName
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName container name
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(serverName, outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);


        jmsAdminOperations.close();
    }


}