package org.jboss.qa.hornetq.test.failover;


import org.jboss.qa.hornetq.Container;
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
import java.io.FileNotFoundException;
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

    public static final String MDB_1_WITH_FILTER = "mdb1WithFilter";
    public static final String MDB_2_WITH_FILTER = "mdb2WithFilter";
    public static final String NON_DURABLE_MDB_ON_TOPIC = "nonDurableMdbOnTopic";

    public final Archive MDB_ON_QUEUE_1 = getDeployment1();
    public final Archive MDB_ON_QUEUE_2 = getDeployment2();

    public Archive getDeployment1() {
        File propertyFile = new File(container(2).getServerHome() + File.separator + "mdb1.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(1).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeployment2() {
        File propertyFile = new File(container(4).getServerHome() + File.separator + "mdb2.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(3).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = MDB_1_WITH_FILTER)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeploymentWithFilter1() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_2_WITH_FILTER)
    @TargetsContainer(CONTAINER4_NAME)
    public static Archive getDeploymentWithFilter2() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = NON_DURABLE_MDB_ON_TOPIC)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeploymentNonDurableMdbOnTopic() throws Exception {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");
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
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "Kill in MDB server on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillWithFiltersOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testSimpleLodh2KillWithFiltersOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills jms servers.
     */
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills jms servers.
     */
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillWithTempTopicOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRule(name = "MDB server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    public void testLodh2KillWithTempTopicOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /**
     * Kills mdbs servers.
     */
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills mdbs servers.
     */
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
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(final List<Container> failureSequence, final int numberOfMessages,
                                       final boolean isDurable) throws Exception {

        prepareRemoteJcaTopology();

        // jms server
        container(1).start();
        // mdb server
        container(2).start();

        if (!isDurable) {
            deployer.deploy(NON_DURABLE_MDB_ON_TOPIC);
            Thread.sleep(5000);
        }

        SoakPublisherClientAck producer1 = new SoakPublisherClientAck(container(1), IN_TOPIC, numberOfMessages, "clientId-myPublisher");
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
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), OUT_QUEUE, 300000, 10, 10);

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
            deployer.undeploy(NON_DURABLE_MDB_ON_TOPIC);
        }


        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(final List<Container> failureSequence, final int numberOfMessages,
                                       final boolean waitForProducer) throws Exception {

        testRemoteJcaInCluster(failureSequence, numberOfMessages, waitForProducer, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(final List<Container> failureSequence, final int numberOfMessages,
                                       final boolean waitForProducer, final boolean isFiltered) throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), IN_QUEUE, numberOfMessages);

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
            deployer.deploy(MDB_1_WITH_FILTER);
            deployer.deploy(MDB_2_WITH_FILTER);
        } else {
            container(2).deploy(MDB_ON_QUEUE_1);
            container(4).deploy(MDB_ON_QUEUE_2);
        }

        waitForMessages(OUT_QUEUE_NAME, numberOfMessages / 20, 300000, container(1), container(3));

        if (waitForProducer) {
            executeFailureSequence(failureSequence, 15000);
        } else {
            executeFailureSequence(failureSequence, 30000);
        }

        waitForMessages(OUT_QUEUE_NAME, numberOfMessages, 300000, container(1), container(3));

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(3), OUT_QUEUE, 10000, 100, 10);
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
            deployer.undeploy(MDB_1_WITH_FILTER);
            deployer.undeploy(MDB_2_WITH_FILTER);
        } else {
            container(2).undeploy(MDB_ON_QUEUE_1.getName());
            container(4).undeploy(MDB_ON_QUEUE_2.getName());
        }

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  map Contanier -> ContainerIP
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills)
            throws Exception {

        for (Container container : failureSequence) {

            //String containerHostname = CONTAINER_BYTEMAN_MAP.get(containerName).containerHostname;
            //int bytemanPort = CONTAINER_BYTEMAN_MAP.get(containerName).bytemanPort;
            //HornetQCallsTracking.installTrackingRules(containerHostname, bytemanPort);
            RuleInstaller.installRule(this.getClass(), container);
            container.kill();
            logger.info("Starting server: " + container.getName());
            container.start();
            logger.info("Server " + container.getName() + " -- STARTED");
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

        prepareJmsServer(container(1));
        prepareMdbServer(container(2), container(1));

        prepareJmsServer(container(3));
        prepareMdbServer(container(4), container(3));

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {


        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

            /*JmsServerSettings
             .forContainer(ContainerType.EAP6_WITH_HORNETQ, containerName, this.getArquillianDescriptor())
             .withClustering(GROUP_ADDRESS)
             .withPersistence()
             .withSharedStore()
             .withPaging(1024 * 1024, 10 * 1024)
             .create();*/

        // .clusteredWith()

        JMSOperations jmsAdminOperations = container.getJmsOperations();

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

        container.restart();

        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55874);

        jmsAdminOperations.close();

        deployDestinations(container);
        container.stop();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareMdbServer(Container container, Container jmsServerContainer) {

        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

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

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerContainer.getHostname(),
                jmsServerContainer.getHornetqPort());
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        jmsAdminOperations.close();
        container.stop();

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
     * @param container container
     */
    private void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container  container
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(Container container, String serverName) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, QUEUE_NAME_PREFIX + queueNumber, QUEUE_JNDI_PREFIX + queueNumber,
                    true);
        }

        jmsAdminOperations.createQueue(serverName, IN_QUEUE_NAME, IN_QUEUE, true);
        jmsAdminOperations.createQueue(serverName, OUT_QUEUE_NAME, OUT_QUEUE, true);
        jmsAdminOperations.createTopic(IN_TOPIC_NAME, IN_TOPIC);


        jmsAdminOperations.close();
    }

}