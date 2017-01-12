package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.jboss.qa.hornetq.constants.Constants.RESOURCE_ADAPTER_NAME_EAP7;

/**
 * Created by okalman on 23.3.16.
 */
public class AppleFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(AppleFailoverTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    public final Archive mdbOnQueue1 = getDeployment1();
    public final Archive mdbOnQueue2 = getDeployment2();

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // inTopic
    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

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
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
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
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentMdbWithProperties() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesMappedName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        logger.info(mdbJar.toString(true));
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdbWithPropertyReplacements.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentMdbWithPropertiesName() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName.class);
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/" + mdbWithPropertiesName + ".jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentWithFilter1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentWithFilter2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentNonDurableMdbOnTopic() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testApplekill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, Constants.FAILURE_TYPE.KILL, false, container(1), container(3));
    }


    /**
     * @tpTestDetails This test scenario tests https://bugzilla.redhat.com/show_bug.cgi?id=1318928
     * (Client Hang due to Long Default Read Timeout in LargeMessageControllerImpl).
     * There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, BM rules to invoke timeout are triggered kill node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is almost the same number of sent and received messages (due to bm rules some are missing)
     */
    @BMRules({
            @BMRule(name = "Artemis Setup counter for LargeMessageControllerImpl",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Artemis Increment counter for every call",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "WRITE $sizeToAdd",
                    isAfter = false,
                    action = "System.out.println(\"incrementing counter\");incrementCounter(\"counter\");"),
            @BMRule(name = "Artemis Log if LargeMessageControllerImpl.popPacket called",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "WRITE $sizeToAdd",
                    isAfter = true,
                    binding = "instance = $this;myPackets:LinkedBlockingQueue = instance.largeMessageData;",
                    condition = "readCounter(\"counter\")<2",
                    action = "System.out.println(\"org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl.poppacket() - after write\");myPackets.clear()"),
            @BMRule(name = "HORNETQ Setup counter for LargeMessageControllerImpl",
                    targetClass = "org.hornetq.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "HORNETQ Increment counter for every call",
                    targetClass = "org.hornetq.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "WRITE $sizeToAdd",
                    isAfter = false,
                    action = "System.out.println(\"incrementing counter\");incrementCounter(\"counter\");"),
            @BMRule(name = "HORNETQ Log if LargeMessageControllerImpl.popPacket called",
                    targetClass = "org.hornetq.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "WRITE $sizeToAdd",
                    isAfter = true,
                    binding = "instance = $this;myPackets:LinkedBlockingQueue = instance.packets;",
                    condition = "readCounter(\"counter\")<2",
                    action = "System.out.println(\"org.hornetq.core.client.impl.LargeMessageControllerImpl.poppacket() - after write\");myPackets.clear()"),
    })

    public void testRemoteJcaInCluster(List<Container> failureSequence, Constants.FAILURE_TYPE failureType, boolean isFiltered, Container inServer, Container outServer) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        installBytemanRules(container(2));
        installBytemanRules(container(4));

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.addMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        // deploy mdbs

            container(2).deploy(mdbOnQueue1);
            container(4).deploy(mdbOnQueue2);


        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, container(1), container(2),
                container(3), container(4)));

        executeFailureSequence(failureSequence, 5000, failureType);

        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 400000, container(1), container(2),
                container(3), container(4)));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(2));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(4));

        // wait some time so recovered rollbacked TXs have some time to be processed
        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 60000, container(1), container(2),
                container(3), container(4)));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 30000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        printThreadDumpsOfAllServers();
        Assert.assertTrue("There is a lot of lost messages, less than 5 lost messages is expected, otherwise clients were considered as stucked",producer1.getListOfSentMessages().size()-receiver1.getListOfReceivedMessages().size()<5);
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);


            container(2).undeploy(mdbOnQueue1.getName());
            container(4).undeploy(mdbOnQueue2.getName());


        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    private void installBytemanRules(Container container)  {
        RuleInstaller.installRule(this.getClass(), container.getHostname(), container.getBytemanPort());
    }

    private void printThreadDumpsOfAllServers() throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));
    }


    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, Constants.FAILURE_TYPE failureType) throws InterruptedException {

        if (Constants.FAILURE_TYPE.SHUTDOWN.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        } else if (Constants.FAILURE_TYPE.KILL.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        } else if (Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.fail(failureType);
                Thread.sleep(300000);
                container.kill();
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        }
    }

    public void prepareRemoteJcaTopology(Container inServer, Container outServer) throws Exception {
        if (inServer.getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopologyEAP6(inServer, outServer);
        } else {
            prepareRemoteJcaTopologyEAP7(inServer, outServer);
        }
    }

    public void prepareRemoteJcaTopologyEAP7(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP7(container(1));
        prepareMdbServerEAP7(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP7(container(3));
        prepareMdbServerEAP7(container(4), container(3), inServer, outServer);

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP6(container(3));
        prepareMdbServerEAP6(container(4), container(3), inServer, outServer);

        copyApplicationPropertiesFiles();

    }

    private void prepareJmsServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

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
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.setIdCacheSize(2000);
        jmsAdminOperations.setConfirmationWindowsSizeOnClusterConnection(clusterGroupName, 10000);
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.3";
        String httpSocketBindingName = "http";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBindingName, null);
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
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

//        jmsAdminOperations.setIdCacheSize(20000);
//        jmsAdminOperations.setConfirmationWindowsSizeOnClusterConnection(clusterGroupName, 10000);

        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-hornetq-ra";

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

        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));


        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);

        // enable trace logging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // both are remote
        if (isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "http-connector-to-jms-server";
        String httpSocketBinding = "http";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-activemq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBinding, null);
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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
//            jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);

        // enable trace logging
        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // both are remote
        if (isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, "java:jboss/DefaultJMSConnectionFactory");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p>
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

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

    private boolean isServerRemote(String containerName) {
        if (CONTAINER1_NAME.equals(containerName) || CONTAINER3_NAME.equals(containerName)) {
            return true;
        } else {
            return false;
        }
    }


}
