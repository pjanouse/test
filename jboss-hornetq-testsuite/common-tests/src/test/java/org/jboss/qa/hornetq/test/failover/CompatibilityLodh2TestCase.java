// TODO DELETE THIS CLASS
package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.HornetQTestCase;
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

import java.io.*;
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
public class CompatibilityLodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(CompatibilityLodh2TestCase.class);
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
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeployment1() throws Exception {
//        File propertyFile = new File(container(2).getServerHome() + File.separator + "mdb1.properties");
//        PrintWriter writer = new PrintWriter(propertyFile);
//        writer.println("remote-jms-server=" + container(1).getHostname());
//        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER4_NAME)
    public static Archive getDeployment2() throws Exception {

//        File propertyFile = new File(container(4).getServerHome() + File.separator + "mdb2.properties");
//        PrintWriter writer = new PrintWriter(propertyFile);
//        writer.println("remote-jms-server=" + container(3).getHostname());
//        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = "mdb1WithFilter")
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeploymentWithFilter1() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2WithFilter")
    @TargetsContainer(CONTAINER4_NAME)
    public static Archive getDeploymentWithFilter2() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = "nonDurableMdbOnTopic")
    @TargetsContainer(CONTAINER2_NAME)
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
    public void testMultiContainer() throws Exception {

        prepareEAP6toEAP5topology();

        container(1).start();
        container(3).start();


        container(1).kill();
        container(3).kill();


        logger.info("###############################################");
        logger.info("KILLED - mnovak");
        logger.info("###############################################");
        Thread.sleep(10000);
        logger.info("###############################################");
        logger.info("now start them again - mnovak");
        logger.info("###############################################");

        container(1).start();
        container(3).start();

        container(1).stop();
        container(3).stop();


    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(2));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<Container> failureSequence, boolean isShutdown, boolean isDurable) throws Exception {

        prepareRemoteJcaTopology();
        // jms server
        container(1).start();

        // mdb server
        container(2).start();

        if (!isDurable) {
            deployer.deploy("nonDurableMdbOnTopic");
            Thread.sleep(5000);
        }

        SoakPublisherClientAck producer1 = new SoakPublisherClientAck(getCurrentContainerForTest(), container(1).getHostname(), container(1).getJNDIPort(), inTopicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "clientId-myPublisher");
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
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(getCurrentContainerForTest(), container(1).getHostname(), container(1).getJNDIPort(), outQueueJndiName, 300000, 10, 10);

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


        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown, boolean isFiltered) throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerClientAck producer1 = new ProducerClientAck(getCurrentContainerForTest(), container(1).getHostname(), container(1).getJNDIPort(), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

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
        ReceiverClientAck receiver1 = new ReceiverClientAck(getCurrentContainerForTest(), container(3).getHostname(), getJNDIPort(


                CONTAINER3_NAME), outQueueJndiName, 300000, 10, 10);
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

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
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
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (Container container: failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container: failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
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
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {

        prepareJmsServer(container(1), container(1).getHostname());
        prepareMdbServer(container(2), container(2).getHostname(), container(1).getHostname());

        prepareJmsServer(container(3), container(3).getHostname());
        prepareMdbServer(container(4), container(4).getHostname(), container(3).getHostname());

        if (isEAP6()) {
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareEAP6toEAP5topology() throws Exception {

        prepareJmsServer(EAP5_CONTAINER, container(1), container(1).getHostname());
        prepareJmsServer(EAP5_CONTAINER, container(2), container(2).getHostname());

        prepareMdbServer(EAP6_CONTAINER, container(3), container(3).getHostname(), container(3).getHostname());
        prepareMdbServer(EAP6_CONTAINER, container(4), container(4).getHostname(), container(4).getHostname());

        if (isEAP6()) {
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container      The container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(Container container, String bindingAddress) {
        prepareJmsServer(EAP6_CONTAINER, container, bindingAddress);
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container      The container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(String containerVersion, Container container, String bindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        if (EAP5_CONTAINER.equals(containerVersion)) {

            int port = 9876;
            int groupPort = 9876;
            long broadcastPeriod = 500;

            JMSOperations jmsAdminOperations = container.getJmsOperations();

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

            deployDestinations(container);

        } else {


            String messagingGroupSocketBindingName = "messaging-group";

            container.start();

            JMSOperations jmsAdminOperations = container.getJmsOperations();

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

            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

            jmsAdminOperations.close();

            deployDestinations(container);
            container.stop();
        }

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareMdbServer(Container container, String bindingAddress, String jmsServerBindingAddress) {
        prepareMdbServer(EAP6_CONTAINER, container, bindingAddress, jmsServerBindingAddress);
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareMdbServer(String containerVersion, Container container, String bindingAddress, String jmsServerBindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        if (EAP5_CONTAINER.equals(containerVersion)) {

            int port = 9876;

            int groupPort = 9876;
            long broadcastPeriod = 500;

            String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
            Map<String, String> connectionParameters = new HashMap<String, String>();
            connectionParameters.put(container(1).getHostname(), String.valueOf(container(1).getHornetqPort()));
            boolean ha = false;

            JMSOperations jmsAdminOperations = container.getJmsOperations();

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

            container.start();

            JMSOperations jmsAdminOperations = container.getJmsOperations();

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

            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(container.getName()),
                    getHornetqPort(container.getName()));
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.close();
            container.stop();
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
     * @param container container
     */
    private void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container container
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(Container container, String serverName) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(serverName, outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);


        jmsAdminOperations.close();
    }


}