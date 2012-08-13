package org.jboss.qa.hornetq.test.compatibility;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * This test is unique test for testing journal data compatibility - it's using mixed profile mode.
 * <p/>
 * First phase:
 * Start reduced lodh2 with EAP 5 servers (1mdb, 2jms servers) and send mixed messages and shutdown them.
 * <p/>
 * Phase two:
 * Start reduced lodh2 with EAP 5 servers (1mdb, 2jms servers) and send mixed messages.
 * <p/>
 * - add arquillian-multiple-containers extension to the classpath
 * - add multiple container adapter implementations to the classpath
 * - define multiple containers and specify each container's adapter implementation
 * via "adapterImplClass" property in arquillian.xml:
 * <p/>
 * ...
 * <container qualifier="as6">
 * <configuration>
 * <property name="adapterImplClass">org.jboss.arquillian.container.jbossas.managed_6.JBossASLocalContainer</property>
 * </configuration>
 * </container>
 * ...
 * <p/>
 * Limitations:
 * - container adapter loading does not employ any classloading isolation,
 * so this would not work for embedded containers; it should however work for
 * managed/remote containers via servlet protocol (tested with managed as6 and as7)
 * <p/>
 * <p/>
 * [1] https://github.com/dpospisil/arquillian-multiple-containers
 *
 * @author mnovak@redhat.com
 */

@RunWith(Arquillian.class)
public class BackwardCompatibilityJournalDataTestCase extends HornetQTestCase {

    @ArquillianResource
    private Deployer deployer;
    private static final Logger logger = Logger.getLogger(BackwardCompatibilityJournalDataTestCase.class);
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
    static boolean topologyCreatedEAP5 = false;
    static boolean topologyCreatedEAP6 = false;

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
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpAfterTest
    public void testRemoteJca() throws Exception {

//        prepareRemoteJcaTopologyEap5();
//
//        controller.start(CONTAINER1);
//        controller.start(CONTAINER2);
//
//        deployer.deploy("mdb1");
//        Thread.sleep(5000);
//        deployer.undeploy("mdb1");
//
//        stopServer(CONTAINER2);
//        stopServer(CONTAINER1);

        prepareRemoteJcaTopologyEap6();

        controller.start(CONTAINER3);
        controller.start(CONTAINER4);

        deployer.deploy("mdb2");
        Thread.sleep(5000);
        deployer.undeploy("mdb2");

        stopServer(CONTAINER4);
        stopServer(CONTAINER3);



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

        stopServer(CONTAINER2);
        stopServer(CONTAINER1);
        stopServer(CONTAINER4);
        stopServer(CONTAINER3);
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEap6() throws Exception {

        if (!topologyCreatedEAP6) {
            System.setProperty("JMS_PROVIDER_CLASS", "org.jboss.qa.tools.HornetQAdminOperationsEAP6");
            prepareJmsServerEap6(CONTAINER3, CONTAINER3_IP);
            prepareMdbServerEap6(CONTAINER4, CONTAINER4_IP, CONTAINER3_IP);
            topologyCreatedEAP6 = true;
        }
    }

    /**
     * EAP 5
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEap5() throws Exception {

        if (!topologyCreatedEAP5) {

            System.setProperty("JMS_PROVIDER_CLASS", "org.jboss.qa.tools.HornetQAdminOperationsEAP5");
            prepareJmsServerEap5(CONTAINER1, CONTAINER1_IP);
            prepareMdbServerEap5(CONTAINER2, CONTAINER2_IP, CONTAINER1_IP);
            topologyCreatedEAP5 = true;
        }
    }

    /**
     *
     * EAP 5
     *
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServerEap5(String containerName, String bindingAddress) throws IOException {

        String broadCastGroupName = "bg-group1";
        String discoveryGroupName = "dg-group1";
        String clusterGroupName  = "my-cluster";
        int port = 9876;
        String groupAddress = "233.6.88.3";
        int groupPort = 9876;
        long broadcastPeriod = 500;
        String connectorName = "netty";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setJournalDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setBindingsDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setPagingDirectory(JOURNAL_DIRECTORY_A);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        jmsAdminOperations.close();

        stopServer(containerName);

    }

    /**
     *
     * EAP 5
     *
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     *
     */
    private void prepareMdbServerEap5(String containerName, String bindingAddress, String jmsServerBindingAddress) throws IOException {

        String broadCastGroupName = "bg-group1";
        String discoveryGroupName = "dg-group1";
        String clusterGroupName  = "my-cluster";
        int port = 9876;
        String groupAddress = "233.6.88.5";
        int groupPort = 9876;
        long broadcastPeriod = 500;
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";

        String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
        Map<String,String> connectionParameters = new HashMap<String, String>();
        connectionParameters.put(jmsServerBindingAddress, String.valueOf(5445));
        boolean ha = false;

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        Map<String, String> params = new HashMap<String, String>();
//        params.put("host", jmsServerBindingAddress);
//        params.put("port", "5445");
//        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "", params);

        jmsAdminOperations.setRA(connectorClassName, connectionParameters, ha);
        jmsAdminOperations.close();

        stopServer(containerName);

    }

    /**
     * EAP 6
     * Prepares jms server for remote jca topology.
     */
    private void prepareJmsServerEap6(String containerName, String bindingAddress) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setJournalDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setBindingsDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.setPagingDirectory(JOURNAL_DIRECTORY_A);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.close();
        controller.stop(containerName);

    }

    /**
     * EAP 6
     * Prepares mdb server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareMdbServerEap6(String containerName, String bindingAddress, String jmsServerBindingAddress) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

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

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
        jmsAdminOperations.close();
        controller.stop(containerName);
    }

}