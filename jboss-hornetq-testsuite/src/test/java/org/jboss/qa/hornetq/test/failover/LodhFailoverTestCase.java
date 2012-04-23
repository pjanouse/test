package org.jboss.qa.hornetq.test.failover;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import javax.jms.Message;
import javax.jms.Session;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.mdb.LocalMdb;
import org.jboss.qa.hornetq.apps.mdb.Mdb;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ArchiveExportException;
import org.jboss.shrinkwrap.api.exporter.FileExistsException;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.formatter.Formatter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.util.loading.MyClassLoaderClassLoaderSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class LodhFailoverTestCase extends HornetQTestCase {

    @ArquillianResource
    private Deployer deployer;
    private static final Logger logger = Logger.getLogger(LodhFailoverTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 5;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    // multicast address for cluster A
    private String broadcastAddressA = "231.10.20.31";
    // multicast address for cluster A
    private String broadcastAddressB = "232.10.20.32";
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;
    static String inQueueFullJndiName = "java:/" + inQueue;
    
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;
    static String outQueueFullJndiName = "java:/" + outQueue;
    
    static boolean topologyCreated = false;
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() {

        return createDeployment();

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeployment2() {

        return createDeployment();
    }
    
    /**
     * This mdb reads messages from remote InQueue
     *
     * @return
     */
    private static JavaArchive createDeployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb.jar");
        mdbJar.addClass(Mdb.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    
    private static JavaArchive createLodh1Deployment(String mdbName, String fullJndiInQueueName, String fullJndiOutQueueName)   {
        
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, mdbName);
        
        mdbJar.addClass(LocalMdb.class);
        
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        
        StringBuffer ejbXml = new StringBuffer();
        
        ejbXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        ejbXml.append("<jboss:ejb-jar xmlns:jboss=\"http://www.jboss.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns=\"http://java.sun.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        ejbXml.append("xmlns:c=\"urn:clustering:1.0\"\n");
        ejbXml.append("xsi:schemaLocation=\"http://www.jboss.com/xml/ns/javaee http://www.jboss.org/j2ee/schema/jboss-ejb3-2_0.xsd http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/ejb-jar_3_1.xsd\"\n");
        ejbXml.append("version=\"3.1\"\n");
        ejbXml.append("impl-version=\"2.0\">\n");
        ejbXml.append("<enterprise-beans>\n");
        ejbXml.append("<message-driven>\n");
        ejbXml.append("<ejb-name>" + mdbName + "</ejb-name>\n");
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.LocalMdb</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>" + fullJndiInQueueName + "</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>" + fullJndiOutQueueName + "</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");
        
        mdbJar.addAsManifestResource(new StringAsset(ejbXml.toString()), "jboss-ejb3.xml");
//        try {
//            PrintWriter writer = new PrintWriter("target/test-classes/org/jboss/qa/hornetq/apps/mdb/jboss-ejb3.xml");
//            writer.println(ejbXml.toString());
//        } catch (FileNotFoundException ex) {
//            java.util.logging.Logger.getLogger(LodhFailoverTestCase.class.getName()).log(Level.SEVERE, null, ex);
//        }
        
//        mdbJar.addAsManifestResource(LocalMdb.class.getPackage(),"jboss-ejb3.xml", "jboss-ejb3.xml");
        logger.info(ejbXml);                                
        logger.info(mdbJar.toString(true));
        
        return mdbJar;
        
    }
    @Deployment(managed = false, testable = false, name = "lodh0_mdb")
    @TargetsContainer(CONTAINER1)
    public static Archive getDeploymentForLodh0() {
        
        Archive archive = createLodh1Deployment("lodh0_mdb0", inQueueFullJndiName+"0", outQueueFullJndiName+"0");
        
        File target = new File("/tmp/mdb0.jar");
        
        archive.as(ZipExporter.class).exportTo(target, true);
        
        return archive;
        
    }
//    @Deployment(managed = false, testable = false, name = "lodh1_mdb")
//    @TargetsContainer(CONTAINER1)
//    public static Archive getDeploymentForLodh1() {
//        return createLodh1Deployment("lodh1_mdb1", inQueueFullJndiName+"1", outQueueFullJndiName+"1");
//    }
//    @Deployment(managed = false, testable = false, name = "lodh2_mdb")
//    @TargetsContainer(CONTAINER1)
//    public static Archive getDeploymentForLodh2() {
//        return createLodh1Deployment("lodh1_mdb2", inQueueFullJndiName+"2", outQueueFullJndiName+"2");
//    }
//    @Deployment(managed = false, testable = false, name = "lodh3_mdb")
//    @TargetsContainer(CONTAINER1)
//    public static Archive getDeploymentForLodh3() {
//        return createLodh1Deployment("lodh1_mdb3", inQueueFullJndiName+"3", outQueueFullJndiName+"3");
//    }
//    @Deployment(managed = false, testable = false, name = "lodh4_mdb")
//    @TargetsContainer(CONTAINER1)
//    public static Archive getDeploymentForLodh4() {
//        return createLodh1Deployment("lodh1_mdb4", inQueueFullJndiName+"4", outQueueFullJndiName+"4");
//    }
    
    /**
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     *
     * @throws Exception
     */
    @RunAsClient
    @Test
    public void testFailover() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        deployer.deploy("mdb1");
        deployer.deploy("mdb2");

        ProducerClientAck producer1 = new ProducerClientAck(CONTAINER1_IP, 4447, inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerClientAck producer2 = new ProducerClientAck(CONTAINER3_IP, 4447, inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();
        
        // killer sequence
        
        producer1.join();
        producer2.join();

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 1000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 1000, 10, 10);

        receiver1.start();
        receiver2.start();
        
        receiver1.join();
        receiver2.join();

        Assert.assertEquals(2 * NUMBER_OF_MESSAGES_PER_PRODUCER, receiver1.getListOfReceivedMessages().size()
                + receiver2.getListOfReceivedMessages().size());

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

    }
    
    
    /**
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     *
     * @throws Exception
     */
    @RunAsClient
    @Test
    public void testKillWithMdbLodh1() throws Exception {
        // we use only the first server
        prepareRemoteJcaTopology();
        
        controller.start(CONTAINER1);
//        deployer.undeploy("lodh1_mdb");
        deployer.deploy("lodh0_mdb");
//        deployer.deploy("lodh1_mdb");
//        deployer.deploy("lodh2_mdb");
//        deployer.deploy("lodh3_mdb");
//        deployer.deploy("lodh4_mdb");
//        
        QueueClientsClientAck clients = new QueueClientsClientAck(CONTAINER1_IP, PORT_JNDI, inQueue, NUMBER_OF_DESTINATIONS, 1, 1, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        clients.setQueueJndiNamePrefixProducers(inQueue);
                
        clients.setQueueJndiNamePrefixConsumers(outQueue);
        
        clients.startClients();
        
        Thread.sleep(10000);
        
        clients.stopClients();
        
        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }
        
        Assert.assertTrue("There are problems detected by jms clients. See log for more details", 
                clients.evaluateResults());
        
        controller.stop(CONTAINER1);

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

        controller.stop(CONTAINER1);

        controller.stop(CONTAINER2);

        deleteFolder(new File(JOURNAL_DIRECTORY_A));

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
            
            System.setProperty("SERVER_WITH_OUTBOUND_QUEUE", CONTAINER1_IP);
            
            prepareJmsServer(CONTAINER3, CONTAINER3_IP);
            prepareMdbServer(CONTAINER4, CONTAINER4_IP, CONTAINER3_IP);

            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1_IP, 9999);
            controller.stop(CONTAINER1);

            controller.start(CONTAINER3);
            deployDestinations(CONTAINER3_IP, 9999);
            controller.stop(CONTAINER3);

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
        int udpGroupPort = 9875;

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, null, Integer.MIN_VALUE, broadcastAddressA, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, null, broadcastAddressA, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

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
        int udpGroupPort = 9875;

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, null, Integer.MIN_VALUE, broadcastAddressB, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, null, broadcastAddressB, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);

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
        
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, inQueueName + queueNumber, inQueue + queueNumber, true);
        }
        
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, outQueueName + queueNumber, outQueue + queueNumber, true);
        }
        
        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
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