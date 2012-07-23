package org.jboss.qa.hornetq.test.failover;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.JMSProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before; 
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class Lodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh2TestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    static String inQueueFullJndiName = "java:/" + inQueueJndiName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;
    static String outQueueFullJndiName = "java:/" + outQueueJndiName;
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
     * Kills mdbs servers.
     */
    @Test
    @RunAsClient
    public void testLodh2() throws Exception {
        List<String> killSequence = new ArrayList<String>();
        killSequence.add(CONTAINER2);
        killSequence.add(CONTAINER4);
        killSequence.add(CONTAINER4);
        killSequence.add(CONTAINER4);
        killSequence.add(CONTAINER4);
        testRemoteJcaInCluster(killSequence);
    }
    
    /**
     *
     * @throws Exception
     */
//    @CleanUpAfterTest
    public void testRemoteJcaInCluster(List<String> killSequence) throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);
        
        deployer.deploy("mdb1");
        deployer.deploy("mdb2");

        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        SoakProducerClientAck producer2 = new SoakProducerClientAck(CONTAINER3_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        producer1.setMessageBuilder(new MixMessageBuilder(1024 * 200));
        producer2.setMessageBuilder(new MixMessageBuilder(1024 * 200));
        
        producer1.start();
        producer2.start();

        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER1_IP, 4447, outQueueJndiName, 10000, 10, 10);
        SoakReceiverClientAck receiver2 = new SoakReceiverClientAck(CONTAINER3_IP, 4447, outQueueJndiName, 10000, 10, 10);

        receiver1.start();
        receiver2.start();
        
        executeKillSequence(killSequence, 20000);
        
        // Wait to send and receive some messages
        Thread.sleep(60 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();
        
        logger.info("Number of sent messages: " + (producer1.getCounter() + producer2.getCounter()) 
                + ", Producer to jms1 server sent: " + producer1.getCounter() + " messages, " 
                + ", Producer to jms2 server sent: " + producer2.getCounter() + " messages.");
        
        logger.info("Number of received messages: " + (receiver1.getCount() + receiver2.getCount()) 
                + ", Consumer from jms1 server received: " + receiver1.getCount() + " messages, " 
                + ", Consumer from jms2 server received: " + receiver2.getCount() + " messages.");
        
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter() + producer2.getCounter(),
                receiver1.getCount() + receiver2.getCount());

        deployer.undeploy("mdb1");
        deployer.undeploy("mdb2");
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER4);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER3);

    }
    
    /**
     * Executes kill sequence.
     * 
     * @param killSequence map Contanier -> ContainerIP
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeKillSequence(List<String> killSequence, long timeBetweenKills) throws InterruptedException {
        
        for (String containerName : killSequence) {
            Thread.sleep(timeBetweenKills);
            killServer(containerName);
            Thread.sleep(3000);
            controller.kill(containerName);
            logger.info("Start server: " + containerName);
            controller.start(containerName);
            logger.info("Server: " + containerName + " -- STARTED");
        }
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

        controller.stop(CONTAINER2);
        controller.stop(CONTAINER4);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER3);

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {

        if (!topologyCreated) {
            prepareJmsServer(CONTAINER1, CONTAINER1_IP);
            prepareMdbServer(CONTAINER2, CONTAINER2_IP, CONTAINER1_IP);

            prepareJmsServer(CONTAINER3, CONTAINER3_IP);
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
    private void prepareJmsServer(String containerName, String bindingAddress) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";
        String multicastAddress = "236.12.73.26";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsAdminOperations.close();
        
        controller.stop(containerName);
        controller.start(containerName);
        jmsAdminOperations = JMSProvider.getInstance(containerName);
        
        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", multicastAddress, 55874);
        deployDestinations(containerName);
        
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

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 5000, 1024 * 1024);

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
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
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

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
}