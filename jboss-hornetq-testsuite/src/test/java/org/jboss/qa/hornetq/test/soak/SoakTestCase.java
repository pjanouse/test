package org.jboss.qa.hornetq.test.soak;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.SoakMdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.SoakMdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.ConnectionFactory;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.concurrent.Semaphore;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.JMSProvider;

/**
 * Complex SOAK test for HornetQ
 * <p/>
 * Test contains:
 * - testing remote jca in cluster
 * - remote input queue and output queue
 * - core bridges
 * - MDBs
 * <p/>
 * Duration of the soak test can be overridden with system property <code>soak.duration</code>
 * <p/>
 * Jms servers - Container 1 and 3 Mdb servers - Container 2 and 4
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class SoakTestCase extends HornetQTestCase {

    // Logger
    protected static final Logger logger = Logger.getLogger(SoakTestCase.class);

    @ArquillianResource
    private Deployer deployer;

    private static final int NUMBER_OF_DESTINATIONS = 2;

    // this is just maximum limit for producer - producer is stopped once fail-over test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    private static final int NUMBER_OF_SUBSCRIBERS = 10;

    // Input queue
    private final static String IN_QUEUE_NAME = "InQueue";
    private final static String IN_QUEUE_JNDI_NAME = "jms/queue/" + IN_QUEUE_NAME;

    // Output queue - serves as source queue for bridge from jms -> mdb servers to bridgeOutTopic
    private final static String OUT_QUEUE_NAME = "OutQueue";
    private final static String OUT_QUEUE_JNDI_NAME = "jms/queue/" + OUT_QUEUE_NAME;

    private final static String BRIDGE_OUT_TOPIC = "BridgeOutTopic";
    private final static String BRIDGE_OUT_TOPIC_JNDI_NAME = "jms/topic/" + BRIDGE_OUT_TOPIC;

    public static final String SYSTEM_PROPERTY_TEST_DURATION = "soak.duration";

    private boolean topologyCreated = false;

    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    // TODO stupid temporary solution - whole logic of producers with gap will be moved into separate class
    private Semaphore[] semaphores;

    /**
     * Creates deployment for Container 3 - server with MDB
     *
     * @return archive for deployment
     * @throws Exception
     */
    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER2)
    public static Archive getDeployment1() throws Exception {
        File propertyFile = new File("mdb1.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + CONTAINER1_IP);
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(SoakMdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    /**
     * Creates deployment for Container 3 - server with MDB
     *
     * @return archive for deployment
     * @throws Exception
     */
    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER4)
    public static Archive getDeployment2() throws Exception {
        File propertyFile = new File("mdb2.properties");
        PrintWriter writer = new PrintWriter(propertyFile);
        writer.println("remote-jms-server=" + CONTAINER3_IP);
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(SoakMdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * SOAK test implementation
     *
     * @throws Exception if something goes wrong
     */
    @Test
    @RunAsClient
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
        HighLoadConsumerWithSemaphores[] consumers = startSubscribersWithGap(CONTAINER2_IP, 50, NUMBER_OF_SUBSCRIBERS, 10000);
        for (int i = 0; i < NUMBER_OF_SUBSCRIBERS; i++) {
            consumers[i].start();
        }

        SoakProducerClientAck producerToInQueue1 = new SoakProducerClientAck(CONTAINER1_IP, PORT_JNDI, IN_QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER);
        SoakProducerClientAck producerToInQueue2 = new SoakProducerClientAck(CONTAINER3_IP, PORT_JNDI, IN_QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageBuilder(1024 * 1024));
        producerToInQueue2.setMessageBuilder(new MixMessageBuilder(1024 * 1024));

        producerToInQueue1.start();
        producerToInQueue2.start();

        // Wait to send and receive some messages
        int testDuration = 72 * 60 * 60 * 1000;
//        int testDuration = 5 * 60 * 1000;
        String systemPropTestDuration = System.getProperty(SYSTEM_PROPERTY_TEST_DURATION);
        if (systemPropTestDuration != null && systemPropTestDuration.trim().length() > 0) {
            try {
                testDuration = Integer.parseInt(systemPropTestDuration);
            } catch (Exception e) {
                logger.error(String.format("Cannot set test duration to '%s'", systemPropTestDuration));
            }
        }
        logger.info(String.format("Setting test duration to '%s' ms", testDuration));
        Thread.sleep(testDuration);

        producerToInQueue1.stopSending();
        producerToInQueue2.stopSending();
        // release all locks
        for (Semaphore semaphore : this.semaphores) {
            semaphore.release();
        }
        producerToInQueue1.join();
        producerToInQueue2.join();

        logger.info(String.format("Producer for host '%s' and queue '%s' received %s messages",
                producerToInQueue1.getHostname(), producerToInQueue1.getQueueNameJndi(),
                producerToInQueue1.getCounter()));
        logger.info(String.format("Producer for host '%s' and queue '%s' received %s messages",
                producerToInQueue2.getHostname(), producerToInQueue2.getQueueNameJndi(),
                producerToInQueue2.getCounter()));

        for (HighLoadConsumerWithSemaphores consumer : consumers) {
            consumer.join();
        }

        int totalCountOfMessages = (producerToInQueue1.getCounter() + producerToInQueue2.getCounter());
        logger.info(String.format("Test sent %s messages (all producers)", totalCountOfMessages));

        for (HighLoadConsumerWithSemaphores consumer : consumers) {
            logger.info("Subscriber - " + consumer.getName() + " = " + consumer.getReceivedMessages() + " messages");
        }
        for (HighLoadConsumerWithSemaphores consumer : consumers) {
            if (consumer.getReceivedMessages() != totalCountOfMessages) {
                Assert.fail(String.format("Receiver %s did not received all messages", consumer.getName()));
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
     * Prepares two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {
        if (!topologyCreated) {
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1);
            controller.stop(CONTAINER1);

            controller.start(CONTAINER3);
            deployDestinations(CONTAINER3);
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
     * @param containerName  Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be bind
     */
    private void prepareJmsServer(String containerName, String bindingAddress, String targetServerForBridgeIP) throws IOException {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

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
        jmsAdminOperations.createBridge("myBridge", "jms.queue." + OUT_QUEUE_NAME, "jms.topic." + BRIDGE_OUT_TOPIC, -1, "bridge-connector");

        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
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

        jmsAdminOperations.createTopic(BRIDGE_OUT_TOPIC, BRIDGE_OUT_TOPIC_JNDI_NAME);

        jmsAdminOperations.close();
        controller.stop(containerName);

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
     * @param hostname ip address where to bind to management interface
     * @param port     port of management interface - it should be 9999
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname   ip address where to bind to management interface
     * @param port       port of management interface - it should be 9999
     * @param serverName server name of the HornetQ server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, IN_QUEUE_NAME, IN_QUEUE_JNDI_NAME, true);
        jmsAdminOperations.createQueue(serverName, OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME, true);


        jmsAdminOperations.close();
    }

    /**
     * Copies file from one place to another.
     *
     * @param sourceFile      source file
     * @param destinationFile destination file - file will be rewritten
     * @throws IOException
     */
    private void copyFile(File sourceFile, File destinationFile) throws IOException {
        if (!destinationFile.exists()) {
            if (!destinationFile.createNewFile()) {
                throw new IOException("Cannot create new destination file");
            }
        }
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destinationFile).getChannel();
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

    /**
     * Prepares and starts load subscribers with gap
     *
     * @param hostname            target host name
     * @param gapBetweenConsumers number of messages between consumers = gap
     * @param consumersCount      count of the consumers
     * @param receiveTimeout      timeout for receive
     * @return array of load consumers
     */
    private HighLoadConsumerWithSemaphores[] startSubscribersWithGap(String hostname, int gapBetweenConsumers,
                                                                     int consumersCount, int receiveTimeout) {

        HighLoadConsumerWithSemaphores[] consumers;
        semaphores = new Semaphore[consumersCount];
        consumers = new HighLoadConsumerWithSemaphores[consumersCount];

        // The first semaphore must be released so first subscriber can start read
        semaphores[0] = new Semaphore(0);
        semaphores[0].release();

        for (int i = 1; i < semaphores.length; i++) {
            semaphores[i] = new Semaphore(0);
        }

        Context context = null;
        try {
            context = getContext(hostname, PORT_JNDI);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI);
            Topic topic = (Topic) context.lookup(BRIDGE_OUT_TOPIC_JNDI_NAME);
            for (int i = 0; i < consumers.length; i++) {
                consumers[i] = new HighLoadConsumerWithSemaphores("consumer " + i + " on " + hostname, topic, cf,
                        semaphores[i], (i + 1 < semaphores.length) ? semaphores[i + 1] : null,
                        gapBetweenConsumers, receiveTimeout);
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

        return consumers;
    }
}
