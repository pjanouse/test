package org.jboss.qa.hornetq.test.soak;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue. Also there is configured bridge from server
 * with jms to servers with mdbs with finalQueue.
 *
 * This test case should survive all kinds of failures by defautl (ha ha)
 *
 * Jms servers - Container 1 and 3 Mdb servers - Container 2 and 4
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class SoakTestCase extends HornetQTestCase {

    @ArquillianResource
    private Deployer deployer;
    private static final Logger logger = Logger.getLogger(SoakTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    private static final int NUMBER_OF_SUBSCRIBERS = 10;
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out, serves as source queue for bridge from jms -> mdb servers to bridgeOutQueue
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;
//    // queue for bridge from jms -> mdb servers - bridgeOutQueue
//    static String bridgeOutQueue = "BridgeOutQueue";
//    static String bridgeOutQueueJndiName ="jms/queue/" + bridgeOutQueue;
    // bridgeOutTopic fo output from mdbTopic on mdb servers
    static String bridgeOutTopic = "BridgeOutTopic";
    static String bridgeOutTopicJndiName = "jms/topic/" + bridgeOutTopic;
    static boolean topologyCreated = false;
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() throws Exception {

        File propertyFile = new File("mdb1.properties");
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

        File propertyFile = new File("mdb2.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + CONTAINER3_IP);
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param bridgeOutTopic whether to test with topics
     *
     * @throws Exception
     */
    @RunAsClient
    @Test
    public void soakTest() throws Exception {

        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        deployer.deploy("mdb1");
        deployer.deploy("mdb2");

        // start subscribers with gap
        HighLoadConsumerWithSemaphores[] consumers = startSubscribersWithGap(CONTAINER2_IP, 5000, NUMBER_OF_SUBSCRIBERS, 10000);
        for (int i = 0; i < NUMBER_OF_SUBSCRIBERS; i++) {
            consumers[i].start();
        }
        

        SoakProducerClientAck producerToInQueue1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        SoakProducerClientAck producerToInQueue2 = new SoakProducerClientAck(CONTAINER3_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageBuilder(1024 * 1024));
        producerToInQueue2.setMessageBuilder(new MixMessageBuilder(1024 * 1024));

        producerToInQueue1.start();
        producerToInQueue2.start();

        // Wait to send and receive some messages
        Thread.sleep(12 * 60 * 60 * 1000);

        producerToInQueue1.stopSending();
        producerToInQueue2.stopSending();
        producerToInQueue1.join();
        producerToInQueue2.join();
        
//        logger.info("Producer for host " + producerToInQueue1.getHostname() + " and queue " + producerToInQueue1.getQueueNameJndi()
//                + " got: " + producerToInQueue1.getListOfSentMessages().size() + " messages");
//        logger.info("Producer for host " + producerToInQueue2.getHostname() + " and queue " + producerToInQueue2.getQueueNameJndi()
//                + " got: " + producerToInQueue2.getListOfSentMessages().size() + " messages");
        
        for (HighLoadConsumerWithSemaphores consumer : consumers) {
            consumer.join();
            logger.info("Subscriber - " + consumer.getName() + " got: " + consumer.getReceivedMessages() + " messages");
        }

        for (int i = 0; i < consumers.length; i++) {
            if (consumers[i].getReceivedMessages() != (producerToInQueue1.getCounter()
                    + producerToInQueue2.getCounter())) {
                Assert.fail(String.format("Receiver #%s did not received defined count of messages", i));
            }
        }

        controller.stop(CONTAINER2);
        controller.stop(CONTAINER4);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER3);

    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     *
     * @throws Exception
     */
    @Before
    @After
    public void stopAllServers() throws Exception {

        prepareRemoteJcaTopology();
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER4);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER3);

    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {
        if (!topologyCreated) {
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1_IP, 9999);
            controller.stop(CONTAINER1);

            controller.start(CONTAINER3);
            deployDestinations(CONTAINER3_IP, 9999);
            controller.stop(CONTAINER3);

            prepareJmsServer(CONTAINER1, CONTAINER1_IP, CONTAINER2_IP);
            prepareMdbServer(CONTAINER2, CONTAINER2_IP, CONTAINER1_IP);

            prepareJmsServer(CONTAINER3, CONTAINER3_IP, CONTAINER4_IP);
            prepareMdbServer(CONTAINER4, CONTAINER4_IP, CONTAINER3_IP);

            copyApplicationPropertiesFiles();

            topologyCreated = true;
        }

    }
   

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareJmsServer(String containerName, String bindingAddress, String targetServerForBridgeIP) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setJournalType("NIO");
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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 500 * 1024 * 1024, 0, 0, 1024 * 1024);

        // deploy bridge from OutQueue(jms server) -> bridgeOutQueue(mdb server)
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", targetServerForBridgeIP, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.createBridge("myBridge", "jms.queue." + outQueueName, "jms.topic." + bridgeOutTopic, -1, "bridge-connector");

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     *
     */
    private void prepareMdbServer(String containerName, String bindingAddress, String jmsServerBindingAddress) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setJournalType("NIO");
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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 500 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);

        // deploy bridgeOutQueue
//        jmsAdminOperations.createQueue(bridgeOutQueue, bridgeOutQueueJndiName, true);
        // depoy bridgeOutTopic for gap subscribers
        jmsAdminOperations.createTopic(bridgeOutTopic, bridgeOutTopicJndiName);

//        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
//        jmsAdminOperations.close();
//        
//        controller.stop(containerName);
//        controller.start(containerName);
//        jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
////        jmsAdminOperations.reload();
//        
//        jmsAdminOperations.addSocketBinding(messagingGroupSocketBindingName, "234.67.54.34", 45699);

//        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, "234.5.6.7");

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     *
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal = null;
        File applicationRolesOriginal = null;
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
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     */
    private void deployDestinations(String hostname, int port) {
        deployDestinations(hostname, port, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String hostname, int port, String serverName) {

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(hostname, port);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(serverName, outQueueName, outQueueJndiName, true);


        jmsAdminOperations.close();
    }

    /**
     * Copies file from one place to another.
     *
     * @param sourceFile source file
     * @param destFile destination file - file will be rewritten
     * @throws IOException
     */
    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    private HighLoadConsumerWithSemaphores[] startSubscribersWithGap(String hostname, int gapBetweenConsumers, int consumersCount, int receiveTimeout) {

        HighLoadConsumerWithSemaphores[] consumers;
        Semaphore[] semaphores;
        semaphores = new Semaphore[consumersCount];
        consumers = new HighLoadConsumerWithSemaphores[consumersCount];
        // first semaphor must be released so first subscriber can start read
        semaphores[0] = new Semaphore(0);
        semaphores[0].release();
        
        for (int i = 1; i < semaphores.length; i++) {
            semaphores[i] = new Semaphore(0);
        }
        
        Context context = null;

        try {
            
            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + PORT_JNDI);
            context = new InitialContext(env);
            
            ConnectionFactory cf = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI);
            Topic topic = (Topic) context.lookup(bridgeOutTopicJndiName);
            // initialize first subscriber with my semaphore
            
            for (int i = 0; i < consumers.length; i++) {
                consumers[i] = new HighLoadConsumerWithSemaphores("consumer " + i + " on " + hostname, topic, cf, semaphores[i],
                        (i + 1 < semaphores.length) ? semaphores[i + 1] : null,
                        gapBetweenConsumers, receiveTimeout);
            }

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            ex.printStackTrace();
        }

        return consumers;
    }
}
