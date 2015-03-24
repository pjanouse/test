package org.jboss.qa.hornetq.test.soak;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.SoakMdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.SoakMdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.ConnectionFactory;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.*;
import java.util.concurrent.Semaphore;

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


    private final Archive mdb1Archive = getDeployment1();
    private final Archive mdb2Archive = getDeployment2();
    private static final String MDB1 = "mdb1";
    private static final String MDB2 = "mdb2";

    /**
     * Creates deployment for Container 3 - server with MDB
     *
     * @return archive for deployment
     * @throws Exception
     */
    public Archive getDeployment1() {
        File propertyFile = new File(container(2).getServerHome() + File.separator + "mdb1.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Exception during creating PrintWriter for MDB1 deployement.", e);
        }
        writer.println("remote-jms-server=" + container(1).getHostname());
        writer.println("remote-jms-jndi-port=" + container(1).getJNDIPort());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB1);
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
    public Archive getDeployment2() {
        File propertyFile = new File(container(4).getServerHome() + File.separator + "mdb2.properties");
        PrintWriter writer;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Exception during creating PrintWriter for MDB2 deployement.", e);
        }
        writer.println("remote-jms-server=" + container(3).getHostname());
        writer.println("remote-jms-jndi-port=" + container(3).getJNDIPort());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB2);
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
        prepareRemoteJcaTopology();

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        container(2).deploy(mdb1Archive);
        container(4).deploy(mdb2Archive);

        // start subscribers with gap
        HighLoadConsumerWithSemaphores[] consumers = startSubscribersWithGap(container(2).getHostname(), 50, NUMBER_OF_SUBSCRIBERS, 10000);
        for (int i = 0; i < NUMBER_OF_SUBSCRIBERS; i++) {
            consumers[i].start();
        }

        SoakProducerClientAck producerToInQueue1 = new SoakProducerClientAck(container(1).getHostname(), container(1).getJNDIPort(), IN_QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER);
        SoakProducerClientAck producerToInQueue2 = new SoakProducerClientAck(container(3).getHostname(), getJNDIPort(
                CONTAINER3_NAME), IN_QUEUE_JNDI_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(104));
        producerToInQueue2.setMessageBuilder(new TextMessageBuilder(104));

        producerToInQueue1.start();
        producerToInQueue2.start();

        // Wait to send and receive some messages
        int testDuration = 14 * 60 * 60 * 1000;
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

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     *
     * @throws Exception
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
     * Prepares two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {
        if (!topologyCreated) {
            container(1).start();
            deployDestinations(container(1));
            container(1).stop();

            container(3).start();
            deployDestinations(container(3));
            container(3).stop();

            prepareJmsServer(container(1), container(1).getHostname(), CONTAINER2_NAME);
            prepareMdbServer(container(2), container(2).getHostname(), CONTAINER1_NAME);

            prepareJmsServer(container(3), container(3).getHostname(), CONTAINER4_NAME);
            prepareMdbServer(container(4), container(4).getHostname(), CONTAINER3_NAME);

            copyApplicationPropertiesFiles();

            topologyCreated = true;
        }

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container      test container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be bind
     */
    private void prepareJmsServer(Container container, String bindingAddress, String targetServerName) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", getHostname(targetServerName), getHornetqPort(targetServerName));
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.createCoreBridge("myBridge", "jms.queue." + OUT_QUEUE_NAME, "jms.topic." + BRIDGE_OUT_TOPIC, -1, "bridge-connector");

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServer(Container container, String bindingAddress, String jmsServerName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", getHostname(jmsServerName), getHornetqPort(jmsServerName));
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);

        jmsAdminOperations.createTopic(BRIDGE_OUT_TOPIC, BRIDGE_OUT_TOPIC_JNDI_NAME);

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
     * @param container container
     */
    private void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container  test container
     * @param serverName server name of the HornetQ server
     */
    private void deployDestinations(Container container, String serverName) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(serverName, IN_QUEUE_NAME, IN_QUEUE_JNDI_NAME, true);
        jmsAdminOperations.createQueue(serverName, OUT_QUEUE_NAME, OUT_QUEUE_JNDI_NAME, true);


        jmsAdminOperations.close();
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
            context = getContext(hostname, this.getJNDIPort());
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
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
