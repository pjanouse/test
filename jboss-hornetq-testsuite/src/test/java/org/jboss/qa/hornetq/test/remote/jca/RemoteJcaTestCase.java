package org.jboss.qa.hornetq.test.remote.jca;

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
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.test.ContextProvider;
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
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class RemoteJcaTestCase extends HornetQTestCase {

    @ArquillianResource
    private Deployer deployer;
    private static final Logger logger = Logger.getLogger(RemoteJcaTestCase.class);
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
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     *
     * @throws Exception
     */
    @RunAsClient
    @Test
    public void testRemoteJcaInCluster() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        // cluster B
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);

        deployer.deploy("mdb1");
        deployer.deploy("mdb2");

        ProducerClientAck producer1 = new ProducerClientAck(CONTAINER1_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerClientAck producer2 = new ProducerClientAck(CONTAINER3_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueueJndiName, 10000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(CONTAINER3_IP, 4447, outQueueJndiName, 10000, 10, 10);

        receiver1.start();
        receiver2.start();

        // Wait to send and receive some messages
        Thread.sleep(12 * 60 * 60 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.", 
                producer1.getListOfSentMessages().size() + producer2.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());

        controller.stop(CONTAINER2);
        controller.stop(CONTAINER4);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER3);

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
    public void testRemoteJca() throws Exception {

        prepareRemoteJcaTopology();
        // cluster A
        controller.start(CONTAINER1);

        // cluster B
        controller.start(CONTAINER2);
        
        deployer.deploy("mdb1");
        
        ProducerClientAck producer1 = new ProducerClientAck(CONTAINER1_IP, 4447, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        producer1.start();
        
        ReceiverClientAck receiver1 = new ReceiverClientAck(CONTAINER1_IP, 4447, outQueueJndiName, 10000, 10, 10);
        
        receiver1.start();
        
        // Wait to send and receive some messages
        Thread.sleep(60000);

        producer1.stopSending();
        producer1.join();
        
        receiver1.join();
        
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());;

        controller.stop(CONTAINER2);
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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
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
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

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
}