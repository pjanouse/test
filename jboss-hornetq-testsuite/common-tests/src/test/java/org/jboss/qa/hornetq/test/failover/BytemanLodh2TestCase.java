package org.jboss.qa.hornetq.test.failover;


import org.junit.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SoakPublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;


/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * <p/>
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 * @author msvehla@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class BytemanLodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(BytemanLodh2TestCase.class);

    private static final int NUMBER_OF_DESTINATIONS = 2;

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int SHORT_TEST_NUMBER_OF_MESSAGES = 2000;

    private static final int LODH2_NUMBER_OF_MESSAGES = 5000;

    // LODH3 waits for all messages to get generated before the failover test starts, so it requires more messages
    // to last through all 5 server kills in long test scenario
    private static final int LODH3_NUMBER_OF_MESSAGES = 20000;

    // queue to send messages in
    private static final String IN_QUEUE_NAME = "InQueue";

    private static final String IN_QUEUE = "jms/queue/" + IN_QUEUE_NAME;

    // inTopic
    private static final String IN_TOPIC_NAME = "InTopic";

    private static final String IN_TOPIC = "jms/topic/" + IN_TOPIC_NAME;

    // queue for receive messages out
    private static final String OUT_QUEUE_NAME = "OutQueue";

    private static final String OUT_QUEUE = "jms/queue/" + OUT_QUEUE_NAME;

    private static final String QUEUE_NAME_PREFIX = "testQueue";

    private static final String QUEUE_JNDI_PREFIX = "jms/queue/testQueue";

    private static final String DISCOVERY_GROUP_NAME = "dg-group1";

    private static final String BROADCAST_GROUP_NAME = "bg-group1";

    private static final String CLUSTER_GROUP_NAME = "my-cluster";

    private static final String CONNECTOR_NAME = "netty";

    private static final String GROUP_ADDRESS = "233.6.88.5";

//    private static final Map<String, BytemanCoords> CONTAINER_BYTEMAN_MAP;
//
//    static {
//        CONTAINER_BYTEMAN_MAP = new HashMap<String, BytemanCoords>(4);
//        CONTAINER_BYTEMAN_MAP.put(CONTAINER1, new BytemanCoords(getHostname(CONTAINER1), BYTEMAN_CONTAINER1_PORT));
//        CONTAINER_BYTEMAN_MAP.put(CONTAINER2, new BytemanCoords(getHostname(CONTAINER2), BYTEMAN_CONTAINER2_PORT));
//        CONTAINER_BYTEMAN_MAP.put(CONTAINER3, new BytemanCoords(getHostname(CONTAINER3), BYTEMAN_CONTAINER3_PORT));
//        CONTAINER_BYTEMAN_MAP.put(CONTAINER4, new BytemanCoords(getHostname(CONTAINER4), BYTEMAN_CONTAINER4_PORT));
//    }

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() throws Exception {
        File propertyFile = new File(getJbossHome(CONTAINER2) + File.separator + "mdb1.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + getHostname(CONTAINER1));
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeployment2() throws Exception {
        File propertyFile = new File(getJbossHome(CONTAINER4) + File.separator + "mdb2.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + getHostname(CONTAINER3));
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = "mdb1WithFilter")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentWithFilter1() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb2WithFilter")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeploymentWithFilter2() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = "nonDurableMdbOnTopic")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeploymentNonDurableMdbOnTopic() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill in MDB server on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillWithFiltersOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillWithFiltersOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /** Kills jms servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "JMS server kill on client transaction commit",
            targetClass = "org.hornetq.core.transaction.Transaction",
            targetMethod = "commit",
            isInterface = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testSimpleLodh3KillOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /** Kills jms servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "server kill on client transaction prepare",
            targetClass = "org.hornetq.core.transaction.Transaction",
            targetMethod = "prepare",
            isInterface = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testSimpleLodh3KillOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        failureSequence.add(CONTAINER4);
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillWithTempTopicOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillWithTempTopicOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER2);
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "server kill on client transaction commit",
            targetClass = "org.hornetq.core.transaction.Transaction",
            targetMethod = "commit",
            isInterface = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testLodh3KillOnTransactionCommit() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /** Kills mdbs servers. */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "server kill on client transaction prepare",
            targetClass = "org.hornetq.core.transaction.Transaction",
            targetMethod = "prepare",
            isInterface = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testLodh3KillOnTransactionPrepare() throws Exception {
        List<String> failureSequence = new ArrayList<String>();
        failureSequence.add(CONTAINER1);
        failureSequence.add(CONTAINER3);
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /** @throws Exception  */
    public void testRemoteJcaWithTopic(final List<String> failureSequence, final int numberOfMessages,
            final boolean isDurable) throws Exception {

        prepareRemoteJcaTopology();

        // jms server
        controller.start(CONTAINER1);
        // mdb server
        controller.start(CONTAINER2);

        if (!isDurable) {
            deployer.deploy("nonDurableMdbOnTopic");
            Thread.sleep(5000);
        }

        SoakPublisherClientAck producer1 = new SoakPublisherClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1),
                getJNDIPort(CONTAINER1), IN_TOPIC, numberOfMessages, "clientId-myPublisher");
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.start();

        // deploy mdbs
        if (isDurable) {
            throw new UnsupportedOperationException("This was not yet implemented. Use Mdb on durable topic to do so.");
        }

        executeFailureSequence(failureSequence, 30000);

        // Wait to send and receive some messages
        Thread.sleep(60 * 1000);

        // set longer timeouts so xarecovery is done at least once
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(getCurrentContainerForTest(), getHostname(CONTAINER1),
                getJNDIPort(CONTAINER1), OUT_QUEUE, 300000, 10, 10);

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

    public void testRemoteJcaInCluster(final List<String> failureSequence, final int numberOfMessages,
            final boolean waitForProducer) throws Exception {

        testRemoteJcaInCluster(failureSequence, numberOfMessages, waitForProducer, false);
    }

    /** @throws Exception  */
    public void testRemoteJcaInCluster(final List<String> failureSequence, final int numberOfMessages,
            final boolean waitForProducer, final boolean isFiltered) throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1),
                getJNDIPort(CONTAINER1), IN_QUEUE, numberOfMessages);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();
        producer1.setMessageVerifier(messageVerifier);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.start();

        if (waitForProducer) {
            producer1.join();
        }

        // deploy mdbs
        if (isFiltered) {
            deployer.deploy("mdb1WithFilter");
            deployer.deploy("mdb2WithFilter");
        } else {
            deployer.deploy("mdb1");
            deployer.deploy("mdb2");
        }

        waitForMessages(OUT_QUEUE_NAME, numberOfMessages/20, 300000, CONTAINER1,CONTAINER3);

        if (waitForProducer) {
            executeFailureSequence(failureSequence, 15000);
        } else {
            executeFailureSequence(failureSequence, 30000);
        }

        waitForMessages(OUT_QUEUE_NAME, numberOfMessages, 300000, CONTAINER1,CONTAINER3);

        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER3),
                getJNDIPort(CONTAINER3), OUT_QUEUE, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);

        receiver1.start();
        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));

        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        Assert.assertTrue("There are lost ", messageVerifier.verifyMessages());

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
    private void executeFailureSequence(List<String> failureSequence, long timeBetweenKills)
            throws Exception {

        for (String containerName : failureSequence) {

            //String containerHostname = CONTAINER_BYTEMAN_MAP.get(containerName).containerHostname;
            //int bytemanPort = CONTAINER_BYTEMAN_MAP.get(containerName).bytemanPort;
            //HornetQCallsTracking.installTrackingRules(containerHostname, bytemanPort);
            RuleInstaller.installRule(this.getClass(), getHostname(containerName), getBytemanPort(containerName));
            this.controller.kill(containerName);
            logger.info("Starting server: " + containerName);
            this.controller.start(containerName);
            logger.info("Server " + containerName + " -- STARTED");
            Thread.sleep(timeBetweenKills);
        }
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    @Override
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

        prepareJmsServer(CONTAINER1);
        prepareMdbServer(CONTAINER2, CONTAINER1);

        prepareJmsServer(CONTAINER3);
        prepareMdbServer(CONTAINER4, CONTAINER3);

        if (isEAP6()) {
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName  Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String containerName) {

        if (isEAP5()) {

            int port = 9876;
            int groupPort = 9876;
            long broadcastPeriod = 500;


            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
            jmsAdminOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, getHostname(containerName), port, GROUP_ADDRESS, groupPort,
                    broadcastPeriod, CONNECTOR_NAME, null);

            jmsAdminOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
            jmsAdminOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, getHostname(containerName), GROUP_ADDRESS, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(CLUSTER_GROUP_NAME);
            jmsAdminOperations.setClusterConnections(CLUSTER_GROUP_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000,
                    true, CONNECTOR_NAME);

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);

            jmsAdminOperations.close();

            deployDestinations(containerName);

        } else {


            String messagingGroupSocketBindingName = "messaging-group";

            controller.start(containerName);

            /*JmsServerSettings
             .forContainer(ContainerType.EAP6_WITH_HORNETQ, containerName, this.getArquillianDescriptor())
             .withClustering(GROUP_ADDRESS)
             .withPersistence()
             .withSharedStore()
             .withPaging(1024 * 1024, 10 * 1024)
             .create();*/

            // .clusteredWith()

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setPersistenceEnabled(true);
            jmsAdminOperations.setSharedStore(true);
            jmsAdminOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
            jmsAdminOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000,
                    CONNECTOR_NAME, "");
            jmsAdminOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, GROUP_ADDRESS);
            jmsAdminOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(CLUSTER_GROUP_NAME);
            jmsAdminOperations.setClusterConnections(CLUSTER_GROUP_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000,
                    true, CONNECTOR_NAME);
            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
            jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
            jmsAdminOperations.close();

            controller.stop(containerName);

            controller.start(containerName);

            jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55874);

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
    private void prepareMdbServer(String containerName, String jmsServerContainerName) {
        if (isEAP5()) {

            int port = 9876;

            int groupPort = 9876;
            long broadcastPeriod = 500;

            String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
            Map<String, String> connectionParameters = new HashMap<String, String>();
            connectionParameters.put(getHostname(containerName), String.valueOf(getHornetqPort(containerName)));
            boolean ha = false;

            JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

            jmsAdminOperations.setClustered(false);

            jmsAdminOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
            jmsAdminOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, getHostname(containerName), port, GROUP_ADDRESS, groupPort,
                    broadcastPeriod, CONNECTOR_NAME, null);

            jmsAdminOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
            jmsAdminOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, getHostname(containerName), GROUP_ADDRESS, groupPort, 10000);

            jmsAdminOperations.removeClusteringGroup(CLUSTER_GROUP_NAME);
            jmsAdminOperations.setClusterConnections(CLUSTER_GROUP_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000,
                    true, CONNECTOR_NAME);

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

            jmsAdminOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
            jmsAdminOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000,
                    CONNECTOR_NAME, "");

            jmsAdminOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
            jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, GROUP_ADDRESS);
            jmsAdminOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, messagingGroupSocketBindingName, 10000);
            jmsAdminOperations.disableSecurity();
            jmsAdminOperations.removeClusteringGroup(CLUSTER_GROUP_NAME);
            jmsAdminOperations.setClusterConnections(CLUSTER_GROUP_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000,
                    true, CONNECTOR_NAME);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 5000, 1024 * 1024);

            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(jmsServerContainerName), getHornetqPort(jmsServerContainerName));
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

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone"
                    + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone"
                    + File.separator
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
     * @param serverName    server name of the hornetq server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, QUEUE_NAME_PREFIX + queueNumber, QUEUE_JNDI_PREFIX + queueNumber,
                    true);
        }

        jmsAdminOperations.createQueue(serverName, IN_QUEUE_NAME, IN_QUEUE, true);
        jmsAdminOperations.createQueue(serverName, OUT_QUEUE_NAME, OUT_QUEUE, true);
        jmsAdminOperations.createTopic(IN_TOPIC_NAME, IN_TOPIC);


        jmsAdminOperations.close();
    }




    private static final class BytemanCoords {

        public final String containerHostname;

        public final int bytemanPort;


        public BytemanCoords(final String containerHostname, final int bytemanPort) {
            this.containerHostname = containerHostname;
            this.bytemanPort = bytemanPort;
        }

    }

}