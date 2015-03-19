package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.MdbFromNonDurableTopicWithOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithConnectionParameters;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
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
import java.util.HashMap;
import java.util.Map;

/**
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class RemoteJcaTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    private static final String MDB1 = "mdb1";
    private static final String MDB1_NON_DURABLE = "mdb1-non-durable";
    private static final String MDB2 = "mdb2";
    private static final String MDB1_WITH_CONNECTOR_PARAMETERS = "mdbWithConnectionParameters";

    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    @Deployment(managed = false, testable = false, name = MDB1)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeployment1() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB1_NON_DURABLE)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeployment67() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB1_NON_DURABLE + ".jar");
        mdbJar.addClasses(MdbFromNonDurableTopicWithOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB2)
    @TargetsContainer(CONTAINER4_NAME)
    public static Archive getDeployment2() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = MDB1_WITH_CONNECTOR_PARAMETERS)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeploymentMdbWithConnectorParameters() throws Exception {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB1_WITH_CONNECTOR_PARAMETERS + ".jar");
        mdbJar.addClasses(MdbWithConnectionParameters.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        mdbJar.addAsManifestResource(new StringAsset(createHornetqJmsXml()), "hornetq-jms.xml");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public static String createHornetqJmsXml() {

        StringBuilder hornetqJmsXml = new StringBuilder();

        hornetqJmsXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        hornetqJmsXml.append("<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">\n");
        hornetqJmsXml.append("<hornetq-server>\n");
        hornetqJmsXml.append("<jms-destinations>\n");
        hornetqJmsXml.append("<jms-queue name=\"InQueue\">\n");
        hornetqJmsXml.append("<entry name=\"jms/queue/InQueue\" />\n");
        hornetqJmsXml.append("<entry name=\"java:jboss/exported/jms/queue/InQueue\" />\n");
        hornetqJmsXml.append("</jms-queue>\n");
        hornetqJmsXml.append("<jms-queue name=\"OutQueue\">\n");
        hornetqJmsXml.append("<entry name=\"jms/queue/OutQueue\" />\n");
        hornetqJmsXml.append("<entry name=\"java:jboss/exported/jms/queue/OutQueue\" />\n");
        hornetqJmsXml.append("</jms-queue>\n");
        hornetqJmsXml.append("</jms-destinations>\n");
        hornetqJmsXml.append("</hornetq-server>\n");
        hornetqJmsXml.append("</messaging-deployment>\n");
        hornetqJmsXml.append("\n");

        return hornetqJmsXml.toString();
    }


    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testRemoteJcaInCluster() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER3_NAME);
        // cluster B with mdbs
        controller.start(CONTAINER2_NAME);
        controller.start(CONTAINER4_NAME);

        deployer.deploy(MDB1);
        deployer.deploy(MDB2);

        ProducerTransAck producer1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerTransAck producer2 = new ProducerTransAck(getHostname(CONTAINER3_NAME), getJNDIPort(CONTAINER3_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiName, 3000, 10, 10);
        ReceiverTransAck receiver2 = new ReceiverTransAck(getHostname(CONTAINER3_NAME), getJNDIPort(CONTAINER3_NAME), outQueueJndiName, 3000, 10, 10);

        receiver1.start();
        receiver2.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size() + producer2.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());

        deployer.undeploy(MDB1);
        deployer.undeploy(MDB2);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER4_NAME);
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER3_NAME);

    }

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testRemoteJca() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1_NAME);

        // cluster B
        controller.start(CONTAINER2_NAME);

        deployer.deploy(MDB1);

        ProducerTransAck producer1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiName, 3000, 10, 10);

        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer1.join();

        receiver1.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        deployer.undeploy(MDB1);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);

    }

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testRemoteJcaWithNonDurableMdbs() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1_NAME);

        // cluster B
        controller.start(CONTAINER2_NAME);

        deployer.deploy(MDB1_NON_DURABLE);

        stopServer(CONTAINER1_NAME);

        controller.start(CONTAINER1_NAME);

        while (!CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME))) {
            Thread.sleep(3000);
        }

        Thread.sleep(10000);

        // parse server.log with mdb for "HornetQException[errorType=QUEUE_EXISTS message=HQ119019: Queue already exists"
        StringBuilder pathToServerLogFile = new StringBuilder(getJbossHome(CONTAINER1_NAME));

        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        logger.info("Check server.log: " + pathToServerLogFile);

        File serverLog = new File(pathToServerLogFile.toString());

        String stringToFind = "errorType=QUEUE_EXISTS message=HQ119019: Queue already exists";

        Assert.assertFalse("Server log cannot contain string: " + stringToFind + ". This is fail - see https://bugzilla.redhat.com/show_bug.cgi?id=1167193.",
                checkThatFileContainsGivenString(serverLog, stringToFind));

        deployer.undeploy(MDB1_NON_DURABLE);

        stopServer(CONTAINER2_NAME);

        stopServer(CONTAINER1_NAME);

    }



    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testUndeployStopStartDeployMdb() throws Exception {

        int numberOfMessages = 500;

        prepareRemoteJcaTopology();

        controller.start(CONTAINER1_NAME);//jms server
        controller.start(CONTAINER2_NAME);// mdb server

        deployer.undeploy(MDB1);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerId(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(50, 300));
        producerToInQueue1.start();
        producerToInQueue1.join();
        deployer.deploy(MDB1);

        waitForNumberOfMessagesInQueue(container(1), inQueueName, numberOfMessages/10, 120000);

        deployer.undeploy(MDB1);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);

        // Start newer version of EAP and client with older version of EAP
        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        deployer.deploy(MDB1);
        ProducerTransAck producerToInQueue2 = new ProducerTransAck(getCurrentContainerId(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, numberOfMessages);
        producerToInQueue2.setMessageBuilder(new ClientMixMessageBuilder(50, 300));
        producerToInQueue2.start();
        producerToInQueue2.join();

        ReceiverTransAck receiverClientAck = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiName, 3000, 10, 5);
        receiverClientAck.start();
        receiverClientAck.join();
        logger.info("Receiver got: " + receiverClientAck.getCount() + " messages from queue: " + receiverClientAck.getQueueNameJndi());
        Assert.assertEquals("Number of sent and received messages should be equal.", 2 * numberOfMessages, receiverClientAck.getCount());

        deployer.undeploy(MDB1);

        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);

    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testRAConfiguredByMdbInRemoteJcaTopology() throws Exception {

        prepareJmsServer(container(1)); // jms server
        prepareMdbServer(container(2), CONTAINER1_NAME); // mdb server
        prepareJmsServer(container(3)); // jms server with mdb with cluster with container 1 and 2

        // cluster A
        controller.start(CONTAINER1_NAME);
        deployDestinations(container(1));
        controller.start(CONTAINER3_NAME);
        deployDestinations(container(3));
        // cluster B with mdbs
        String s = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(CONTAINER2_NAME)) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        s = containerDef.getContainerProperties().get("javaVmArguments");
                        s = s.concat(" -Dconnection.parameters=port=" + getHornetqPort(CONTAINER1_NAME) + ";host=" + getHostname(CONTAINER1_NAME));
                        containerDef.getContainerProperties().put("javaVmArguments", s);
                    }
                }
            }
        }
        Map<String,String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        controller.start(CONTAINER2_NAME, properties);


        deployer.deploy(MDB1_WITH_CONNECTOR_PARAMETERS);

        ProducerTransAck producer1 = new ProducerTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerTransAck producer2 = new ProducerTransAck(getHostname(CONTAINER3_NAME), getJNDIPort(CONTAINER3_NAME), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();

        //        Thread.sleep(10 * 60 * 1000); // min

        ReceiverTransAck receiver1 = new ReceiverTransAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueueJndiName, 10000, 10, 10);
        ReceiverTransAck receiver2 = new ReceiverTransAck(getHostname(CONTAINER3_NAME), getJNDIPort(CONTAINER3_NAME), outQueueJndiName, 10000, 10, 10);

        receiver1.start();
        receiver2.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size() + producer2.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());

        deployer.undeploy(MDB1);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER3_NAME);


    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER4_NAME);
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER3_NAME);

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {

            prepareJmsServer(container(1));
            prepareMdbServer(container(2), CONTAINER1_NAME);

            prepareJmsServer(container(3));
            prepareMdbServer(container(4), CONTAINER3_NAME);

            controller.start(CONTAINER1_NAME);
            deployDestinations(container(1));
            stopServer(CONTAINER1_NAME);

            controller.start(CONTAINER3_NAME);
            deployDestinations(container(3));
            stopServer(CONTAINER3_NAME);

            copyApplicationPropertiesFiles();

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue");
        Map<String,String> map = new HashMap<String,String>();
        map.put("use-nio","true");
        jmsAdminOperations.createRemoteAcceptor("netty", "messaging", map);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServer(Container container, String remoteSeverName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String remoteConnectorName = "netty-remote";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
//        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
//        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
//        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(remoteSeverName),
                getHornetqPort(remoteSeverName));
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
        jmsAdminOperations.close();
        container.stop();
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
     * @param container Test container
     */
    private void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(Container container, String serverName) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(serverName, outQueueName, outQueueJndiName, true);

        jmsAdminOperations.createTopic(serverName, inTopicName, inTopicJndiName);


        jmsAdminOperations.close();
    }

}