package org.jboss.qa.hornetq.test.domain;


import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.DomainHornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueWithSecurity;
import org.jboss.qa.hornetq.tools.DomainOperations;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class DomainLodh1TestCase extends DomainHornetQTestCase {

    private static final Logger logger = Logger.getLogger(DomainLodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 2000;

    private static final String MDB_NAME = "mdb1";

    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    public static String createEjbXml(String mdbName) {

        StringBuilder ejbXml = new StringBuilder();

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
        ejbXml.append("<ejb-name>").append(mdbName).append("</ejb-name>\n");
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>").append(inQueue).append("</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>").append(outQueue).append("</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        return ejbXml.toString();
    }

    @Deployment(managed = false, testable = false, name = MDB_NAME)
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh1Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh1")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKill() throws Exception {
        testLodh(false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testShutDown() throws Exception {
        testLodh(true);
    }

    /**
     * @throws Exception
     */
    public void testLodh(boolean shutdown) throws Exception {
        // no need to prepare domain, we're using "node-1" on "server-group-1"
        DomainOperations.forDefaultContainer().reloadDomain().close();

        // we use only the first server
        prepareJmsServer(container(1));
        logger.info("Configuration is done, starting the node again");

        controller.start(CONTAINER1_NAME);
        logger.info("Node is up, commencing the test");

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        logger.info("Sending is done");

        deployer.deploy(MDB_NAME);
        logger.info("The MDB is deployed");

        List<String> killSequence = new ArrayList<String>();

        for (int i = 0; i < 2; i++) { // for (int i = 0; i < 5; i++) {
            killSequence.add(CONTAINER1_NAME);
        }

        logger.info("Starting the kill sequence");
        executeNodeFaillSequence(killSequence, 20000, shutdown);

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        logger.info("Start receiver.");
        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueue, 1000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertEquals("There is different number of sent and received messages. Received: " + receiver1.getListOfReceivedMessages().size()
                + ", Sent: " + producerToInQueue1.getListOfSentMessages().size()  + ".", producerToInQueue1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        deployer.undeploy(MDB_NAME);
        stopServer(CONTAINER1_NAME);

    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLodhWithoutKill() throws Exception {
        // no need to prepare domain, we're using "node-1" on "server-group-1"
        DomainOperations.forDefaultContainer().reloadDomain().close();

        // we use only the first server
        prepareServer();

        controller.start(CONTAINER1_NAME);

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        deployer.deploy(MDB_NAME);

        logger.info("Start receiver.");

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), outQueue, 300000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        deployer.undeploy(MDB_NAME);
        stopServer(CONTAINER1_NAME);

    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence     map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFaillSequence(List<String> failSequence, long timeBetweenFails, boolean shutdown) throws InterruptedException {

        if (shutdown) {
            for (String containerName : failSequence) {
                Thread.sleep(timeBetweenFails);

                printQueuesCount();

                logger.info("Shutdown server: " + containerName);

                controller.stop(containerName);
                Thread.sleep(3000);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");

                printQueuesCount();
            }
        } else {
            for (String containerName : failSequence) {
                Thread.sleep(timeBetweenFails);

                printQueuesCount();

                logger.info("Killing server " + containerName);
                killServer(containerName);
                Thread.sleep(3000);
                //controller.kill(containerName);
                logger.info("Server container1 killed!.");
                logger.info("Start server: " + containerName);
                //controller.start(containerName);
                // controller.start cannot be used on individual domain nodes
                DomainOperations.forDefaultContainer().startNode("server-1").close();
                Thread.sleep(5000);
                logger.info("Server: " + containerName + " -- STARTED");

                printQueuesCount();
            }
        }
    }

    private void printQueuesCount() {
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.addAddressPrefix("host", "master");
        jmsAdminOperations.addAddressPrefix("server", "server-1");
        logger.info("=============Queues status====================");
        logger.info("Messages on [" + inQueueName + "]=" + jmsAdminOperations.getCountOfMessagesOnQueue(inQueueName));
        logger.info("Messages on [" + outQueueName + "]=" + jmsAdminOperations.getCountOfMessagesOnQueue(outQueueName));
        logger.info("==============================================");
        jmsAdminOperations.close();
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
//    @Before
//    @After
//    public void stopAllServers() {
//        stopServer(CONTAINER1_NAME_NAME);
//        stopServer(CONTAINER2_NAME);
//        deleteFolder(new File(JOURNAL_DIRECTORY_A));
//    }

    /**
     * Prepare server in simple topology.
     *
     * @throws Exception
     */
    public void prepareServer() throws Exception {
        prepareJmsServer(container(1));
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {

        //controller.start(containerName);

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addAddressPrefix("profile", "full-ha-1");

        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.setNodeIdentifier(1234567);

        try {
            jmsAdminOperations.removeQueue(inQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);

        try {
            jmsAdminOperations.removeQueue(outQueueName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);
        jmsAdminOperations.close();

        logger.info("Configuration is up and read, restarting the node");

        //controller.stop(containerName);
    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh2Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh2");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh2")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb3")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh3Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh3");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh3")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb4")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh4Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh4");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh4")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb6")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh6Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh6");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh6")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb7")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh7Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh7");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh7")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb8")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh8Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh8");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh8")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb9")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh9Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh9");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh9")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb10")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh10Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh10");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh10")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb12")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh12Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh12");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh12")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb5")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh5Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh5");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh5")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb13")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh13Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh13");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh13")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb14")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh14Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh14");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh14")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb15")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh15Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh15");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh15")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb16")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh16Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh16");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh16")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb17")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh17Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh17");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh17")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb18")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh18Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh18");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh18")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb19")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh19Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh19");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh19")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb20")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh20Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh20");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh20")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb21")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh21Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh21");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh21")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb0")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh0Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh0");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh0")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = "mdb11")
    @TargetsContainer(SERVER_GROUP1)
    public static JavaArchive createLodh11Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh11");

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");


        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh11")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }


}
