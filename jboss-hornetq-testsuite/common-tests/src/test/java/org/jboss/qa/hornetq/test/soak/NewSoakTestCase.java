package org.jboss.qa.hornetq.test.soak;


import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.soak.clients.DurableSubscriptionClient;
import org.jboss.qa.hornetq.test.soak.clients.FilterSoakClient;
import org.jboss.qa.hornetq.test.soak.clients.TemporaryQueuesSoakClient;
import org.jboss.qa.hornetq.test.soak.modules.*;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.MemoryMeasuring;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
public class NewSoakTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(NewSoakTestCase.class);

    private static final String CONTAINER1_NAME_DEPLOYMENT = "container1-deployment";

    private static final String CONTAINER2_DEPLOYMENT = "container2-deployment";

    private static final long DEFAULT_DURATION = TimeUnit.DAYS.toMillis(3);

    private static final int NUMBER_OF_MESSAGES = 10000000;

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


    @Before
    public void startUpServers() {

        this.controller.start(CONTAINER1_NAME, setMemoryForContainer(CONTAINER1_NAME, 4000));

        this.controller.start(CONTAINER2_NAME, setMemoryForContainer(CONTAINER2_NAME, 4000));

    }

    private Map<String,String> setMemoryForContainer(String containerName, int heapSizeInMB)    {
        Map<String,String> containerProperties = new HashMap<String,String>();

        ArquillianDescriptor descriptor = getArquillianDescriptor();
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {

                if (containerDef.getContainerName().equals(containerName)) {
                    containerProperties = containerDef.getContainerProperties();
                    String vmArguments = containerProperties.get("javaVmArguments");

                    if (vmArguments.contains("-Xmx"))    { //just change value
                        vmArguments = vmArguments.replaceAll("-Xmx.* ", "-Xmx" + heapSizeInMB + "m ");
                    } else { // add it
                        vmArguments = vmArguments.concat(" -Xmx" + heapSizeInMB + "m ");
                    }
                    LOG.info("vmargument are: " + vmArguments);
                    containerProperties.put("javaVmArguments", vmArguments);
                }
            }}
        return containerProperties;
    }


    @After
    public void stopServers() {
        this.controller.stop(CONTAINER2_NAME);
        this.controller.stop(CONTAINER1_NAME);
    }


    @Deployment(managed = false, testable = false, name = CONTAINER1_NAME_DEPLOYMENT)
    @TargetsContainer(CONTAINER1_NAME)
    public static Archive getDeploymentForContainer1() {
        return createArchiveForContainer(CONTAINER1_NAME);
    }


    @Deployment(managed = false, testable = false, name = CONTAINER2_DEPLOYMENT)
    @TargetsContainer(CONTAINER2_NAME)
    public static Archive getDeploymentForContainer2() {
        return createArchiveForContainer(CONTAINER2_NAME);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void soakTest() throws Exception {
        this.prepareServers();
        this.setupJmsServer(container(1));
        this.setupMdbServer(container(2));

        this.restartAllServers();

//        // start memory measuring of servers
        File jmsServerCsv = new File("jms-server-memory.csv");
        MemoryMeasuring jmsServerMeasurement = new MemoryMeasuring(getHostname(CONTAINER1_NAME), String.valueOf(getPort(CONTAINER1_NAME)), jmsServerCsv);
        jmsServerMeasurement.start();

        File mdbServerCsv = new File("mdb-server-memory.csv");
        MemoryMeasuring mdbServerMeasurement = new MemoryMeasuring(getHostname(CONTAINER2_NAME), String.valueOf(getPort(
                CONTAINER2_NAME)), mdbServerCsv);
        mdbServerMeasurement.start();

        this.deployer.deploy(CONTAINER1_NAME_DEPLOYMENT);
        this.deployer.deploy(CONTAINER2_DEPLOYMENT);

        String durationString = System.getProperty("soak.duration", String.valueOf(DEFAULT_DURATION));
        long testDuration = 0;
        try {
            testDuration = Long.parseLong(durationString);
        } catch (NumberFormatException e) {
            LOG.error(String.format("Cannot set test duration to '%s'", durationString));
            throw e;
        }
        LOG.info(String.format("Setting soak test duration to %dms", testDuration));

        // create in/out org.jboss.qa.hornetq.apps.clients
        SoakProducerClientAck producer = new SoakProducerClientAck(getHostname(CONTAINER1_NAME), this.getJNDIPort(CONTAINER1_NAME),
                RemoteJcaSoakModule.JCA_IN_QUEUE_JNDI, NUMBER_OF_MESSAGES);
        producer.setMessageBuilder(new TextMessageBuilder(104));

        SoakReceiverClientAck[] consumers = new SoakReceiverClientAck[NUMBER_OF_CLIENTS];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new SoakReceiverClientAck(getHostname(CONTAINER1_NAME), this.getJNDIPort(CONTAINER1_NAME),
                    DurableSubscriptionsSoakModule.DURABLE_MESSAGES_QUEUE_JNDI);
        }
        DurableSubscriptionClient durableTopicClient = new DurableSubscriptionClient(CONTAINER1_INFO);

        TemporaryQueuesSoakClient tempQueuesClients = new TemporaryQueuesSoakClient(CONTAINER1_INFO,
                NUMBER_OF_MESSAGES);
        FilterSoakClient filterClients = new FilterSoakClient(CONTAINER1_INFO, NUMBER_OF_MESSAGES);

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

//        // stop measuring
        jmsServerMeasurement.stopMeasuring();
        mdbServerMeasurement.stopMeasuring();
        jmsServerMeasurement.join();
        mdbServerMeasurement.join();
        jmsServerMeasurement.generatePng(jmsServerCsv);
        mdbServerMeasurement.generatePng(mdbServerCsv);

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

    }


    /**
     * Collect required classes (MDBs and EJBs) to deploy in container X and create an archive with them.
     *
     * @param containerName
     *
     * @return
     */
    private static Archive createArchiveForContainer(final String containerName) {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, containerName.toLowerCase() + ".jar")
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq\n"),
                "MANIFEST.MF");

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

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb-for-soak.jar");
        if (target.exists()) {
            target.delete();
        }
        archive.as(ZipExporter.class).exportTo(target, true);
        return archive;
    }


    /**
     * Let all modules prepare whatever they need on all servers.
     */
    private void prepareServers() {
        for (SoakTestModule module : MODULES) {
            module.setUpServers(this.controller);
        }
    }


    private void restartAllServers() {
        this.stopServers();
        this.startUpServers();
    }


    private void setupJmsServer(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.setClustered(true);
        ops.setJournalType("NIO");
        ops.setPersistenceEnabled(true);
        ops.setSharedStore(true);
        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, "netty", "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, "netty");

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 512 * 1204);

        ops.close();
    }


    private void setupMdbServer(final Container container) {
        JMSOperations ops = container.getJmsOperations();
        ops.setClustered(true);
        ops.setJournalType("NIO");
        ops.setPersistenceEnabled(true);
        ops.setSharedStore(true);
        ops.disableSecurity();
        ops.addLoggerCategory("com.arjuna", "ERROR");

        ops.removeBroadcastGroup("bg-group1");
        ops.setBroadCastGroup("bg-group1", "messaging-group", 2000, "netty", "");

        ops.removeDiscoveryGroup("dg-group1");
        ops.setDiscoveryGroup("dg-group1", "messaging-group", 10000);

        ops.removeClusteringGroup("my-cluster");
        ops.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, "netty");

        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1204);

        ops.close();
    }

}
