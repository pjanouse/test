package org.jboss.qa.hornetq.test.soak;


import category.Soak;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.soak.clients.DurableSubscriptionClient;
import org.jboss.qa.hornetq.test.soak.clients.FilterSoakClient;
import org.jboss.qa.hornetq.test.soak.clients.TemporaryQueuesSoakClient;
import org.jboss.qa.hornetq.test.soak.modules.BridgeSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.DurableSubscriptionsSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.JcaBridgeModuleConnection;
import org.jboss.qa.hornetq.test.soak.modules.RemoteJcaSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.TemporaryQueueSoakModule;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @tpChapter PERFORMANCE TESTING
 * @tpSubChapter HORNETQ SOAK TEST
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-soak-test/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19046/activemq-artemis-performance#testcases
 */
@RunWith(Arquillian.class)
@Category(Soak.class)
public class NewSoakTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(NewSoakTestCase.class);

    private static final long DEFAULT_DURATION = TimeUnit.DAYS.toMillis(1);

    private static final int NUMBER_OF_MESSAGES = Integer.MAX_VALUE;

    private static final int NUMBER_OF_CLIENTS = 5;

    /**
     * Submodules active in the soak test.
     */
    private final static SoakTestModule[] MODULES = {
            new RemoteJcaSoakModule(),
            new BridgeSoakModule(),
            new JcaBridgeModuleConnection(), // connects previous 2 with mdb
            new DurableSubscriptionsSoakModule(), // connects to bridge module

            new TemporaryQueueSoakModule(),
            new EjbSoakModule()
    };

    @After
    @Before
    public void stopServers() {
        container(2).stop();
        container(1).stop();
    }

    public void startUpServers() {
        container(2).start();
        container(1).start();
    }

    /**
     * @tpTestDetails Two EAP 7 containers are started. Client sends messages to
     * one of them, where the messages go through various destinations and
     * components between servers. Client then reads the messages again from the
     * output destinations, see if all of the messages are available as
     * expected.
     * @tpProcedure <ul>
     * <li>Client sends messages to input queue. Messages then go through:</li>
     * <ul>
     * <li>one server to another through MDB reading and sending them from remote container through resource adapter</li>
     * <li>messages are forwarded from one server to another over JMS bridge and back over Core bridge</li>
     * <li>messages have JMSReplyTo defined with a temporary queue, that is filled with responses for the client</li>
     * <li>messages are read from the destination with stateless EJB and sent back to clients</li>
     * </ul>
     * <li>Client reads the messages after the pass through all the soak modules.</li>
     * </ul>
     * @tpPassCrit Receiver received all messages send by producer.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void soakTest() throws Exception {

        //make assumptions whether to run the test
        makeAssumption();

        //Prepare phase
        Archive container1Deployment = createArchiveForContainer(container(1));
        Archive container2Deployment = createArchiveForContainer(container(2));

        startUpServers();

        prepareServers();
        setupJmsServer(container(1));
        setupMdbServer(container(2));

        restartAllServers();

        container(1).deploy(container1Deployment);
        container(2).deploy(container2Deployment);
        
        // Start memory measuring of servers
        String protocol = ContainerUtils.isEAP6(container(1)) ? ResourceMonitor.Builder.JMX_URL_EAP6 : ResourceMonitor.Builder.JMX_URL_EAP7;
        ResourceMonitor jmsServerMeasurement = new ResourceMonitor.Builder()
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(protocol)
                .outFileNamingPattern("jms-server")
                .generateCharts()
                .build();
        jmsServerMeasurement.startMeasuring();

        ResourceMonitor mdbServerMeasurement = new ResourceMonitor.Builder()
                .host(container(2).getHostname())
                .port(container(2).getPort())
                .protocol(protocol)
                .outFileNamingPattern("mdb-server")
                .generateCharts()
                .build();
        mdbServerMeasurement.startMeasuring();

        final long testDuration = getTestDuration();

        // create in/out org.jboss.qa.hornetq.apps.clients
        SoakProducerClientAck producer = new SoakProducerClientAck(container(1),
                RemoteJcaSoakModule.JCA_IN_QUEUE_JNDI, NUMBER_OF_MESSAGES);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10,200));

        SoakReceiverClientAck[] consumers = new SoakReceiverClientAck[NUMBER_OF_CLIENTS];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new SoakReceiverClientAck(container(1), DurableSubscriptionsSoakModule.DURABLE_MESSAGES_QUEUE_JNDI, 60 * 1000, 100, 25);
        }
        DurableSubscriptionClient durableTopicClient = new DurableSubscriptionClient(container(1));

        TemporaryQueuesSoakClient tempQueuesClients = new TemporaryQueuesSoakClient(container(1),
                NUMBER_OF_MESSAGES);
        FilterSoakClient filterClients = new FilterSoakClient(container(1), NUMBER_OF_MESSAGES);

        // run
        producer.start();
        tempQueuesClients.start();
        filterClients.start();
        for (int i = 0; i < consumers.length; i++) {
            consumers[i].start();
        }
        durableTopicClient.start();

        Thread.sleep(testDuration);

        LOG.info("Time is up, stopping the test");
        producer.stopSending();
        tempQueuesClients.stopSending();
        filterClients.stopSending();

        producer.join();
        tempQueuesClients.join();
        filterClients.join();
        for (int i = 0; i < consumers.length; i++) {
            consumers[i].join();
        }
        durableTopicClient.join();

        // sum of messages received by all org.jboss.qa.hornetq.apps.clients
        int receivedMessagesCount = 0;
        for (int i = 0; i < consumers.length; i++) {
            receivedMessagesCount += consumers[i].getCount();
        }

        // stop measuring
        jmsServerMeasurement.stopMeasuring();
        mdbServerMeasurement.stopMeasuring();

        // evaluate
        LOG.info("Soak test results:");
        LOG.info("========================================");
        LOG.info("Messages sent:              " + producer.getCounter());
        LOG.info("Messages received:          " + receivedMessagesCount);
        LOG.info("Topic messages received     " + durableTopicClient.getCount());
        LOG.info("========================================");
        LOG.info("Temp queue module sent:     " + tempQueuesClients.getNumberOfSentMessages());
        LOG.info("Temp queue module received: " + tempQueuesClients.getNumberOfReceivedMessages());
        LOG.info("========================================");
        LOG.info("Filter queue module sent:   " + filterClients.getNumberOfSentMessages());
        LOG.info("Filter queue module received: " + filterClients.getNumberOfReceivedMessages());
        LOG.info("========================================");

        assertEquals("Number of sent and received messages should be same",
                producer.getCounter(), receivedMessagesCount);
        assertEquals("Number of sent and received messages from topic should be same",
                producer.getCounter(), durableTopicClient.getCount());
        assertEquals("Number of sent and received messages in temporary queues module should be same",
                tempQueuesClients.getNumberOfSentMessages(), tempQueuesClients.getNumberOfReceivedMessages());

        int filterModuleExpected = filterClients.getNumberOfSentMessages();
        assertEquals("Number of messages received in filtering module should be half of sent messages",
                filterModuleExpected, filterClients.getNumberOfReceivedMessages());

        // stop test
        container(1).undeploy(container1Deployment);
        container(2).undeploy(container2Deployment);
        container(1).stop();
        container(2).stop();

    }

    /**
     * Decide whether test should be skipped. This is because JdbcStoreSoakTestCase which can be run only against EAP7.
     * Tests extending NewSoakTestCase must be placed in common-tests module even when they are only for EAP7.
     *
     */
    protected void makeAssumption() {
    }


    /**
     * Collect required classes (MDBs and EJBs) to deploy in container X and create an archive with them.
     *
     * @param container
     * @return
     */
    private static Archive createArchiveForContainer(final Container container) {
        String containerName = container.getName();

        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, containerName.toLowerCase() + "-deployment.jar");

        // add all component classes to the archive
        for (SoakTestModule module : MODULES) {
            for (ClassDeploymentDefinition classDeployment : module.getRequiredClasses()) {
                if (containerName.equals(classDeployment.getContainerName())) {
                    archive.addClass(classDeployment.getClassToDeploy());
                }
            }
        }

        // add all file resources to the archive
        for (SoakTestModule module : MODULES) {
            for (FileDeploymentDefinition fileAsset : module.getRequiredAssets()) {
                if (containerName.equals(fileAsset.getContainerName())) {
                    archive.addAsResource(fileAsset.getContents(), fileAsset.getTargetName());
                }
            }
        }

        if (ContainerUtils.isEAP6(container)) {
            archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq\n"),
                    "MANIFEST.MF");
        } else {
            archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis\n"),
                    "MANIFEST.MF");
        }
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb-for-soak" + containerName + ".jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        archive.as(ZipExporter.class).exportTo(target, true);

        return archive;
    }


    /**
     * Let all modules prepare whatever they need on all servers.
     */
    private void prepareServers() {
        for (SoakTestModule module : MODULES) {
            module.setUpServers();
        }
    }


    private void restartAllServers() {
        this.stopServers();
        this.startUpServers();
    }


    protected void setupJmsServer(final Container container) throws Exception {
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";

        JMSOperations ops = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            ops.setClustered(true);
            ops.setSharedStore(true);
        }

        ops.setJournalType("NIO");
        ops.setPersistenceEnabled(true);
        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, connectorName, "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, connectorName);

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 512 * 1204);

        ops.close();
    }


    protected void setupMdbServer(final Container container) throws Exception {
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";

        JMSOperations ops = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            ops.setClustered(true);
            ops.setSharedStore(true);
        }

        ops.setJournalType("NIO");
        ops.setPersistenceEnabled(true);
        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, connectorName, "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, connectorName);

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1204);

        ops.close();
    }

    private Map<String, String> setMemoryForContainer(String containerName, int heapSizeInMB) {
        Map<String, String> containerProperties = new HashMap<String, String>();

        ArquillianDescriptor descriptor = getArquillianDescriptor();
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                if (containerDef.getContainerName().equals(containerName)) {
                    containerProperties = containerDef.getContainerProperties();
                    String vmArguments = containerProperties.get("javaVmArguments");

                    if (vmArguments.contains("-Xmx")) { //just change value
                        vmArguments = vmArguments.replaceAll("-Xmx.* ", "-Xmx" + heapSizeInMB + "m ");
                    } else { // add it
                        vmArguments = vmArguments.concat(" -Xmx" + heapSizeInMB + "m ");
                    }
                    LOG.info("vmargument are: " + vmArguments);
                    containerProperties.put("javaVmArguments", vmArguments);
                }
            }
        }
        return containerProperties;
    }


    private long getTestDuration() {
        Long testDuration = 0l;
        String durationString = System.getProperty("soak.duration", String.valueOf(DEFAULT_DURATION));
        try {
            testDuration = Long.parseLong(durationString);
        } catch (NumberFormatException e) {
            LOG.error(String.format("Cannot set test duration to '%s'", durationString));
            throw e;
        }
        LOG.info(String.format("Setting soak test duration to %dms", testDuration));
        return testDuration;
    }


}
