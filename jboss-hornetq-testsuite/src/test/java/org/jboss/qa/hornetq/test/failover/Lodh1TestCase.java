package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000;

    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    public static String createEjbXml(String mdbName) {

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

    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER1)
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
    public void testLimitedPoolSize() throws Exception {
        prepareJmsServer(CONTAINER1);
        controller.start(CONTAINER1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setAuthenticationForNullUsers(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        stopServer(CONTAINER1);

        controller.start(CONTAINER1);
        String connectionFactoryName = "hornetq-ra";
        JMSOperations jmsOperations = getJMSOperations(CONTAINER1);
        jmsOperations.setMinPoolSizeOnPooledConnectionFactory(connectionFactoryName, 5);
        jmsOperations.setMaxPoolSizeOnPooledConnectionFactory(connectionFactoryName, 10);
        stopServer(CONTAINER1);
        controller.start(CONTAINER1);

        logger.info("Deploy MDBs.");
        for (int j = 0; j < 22; j++) {
            deployer.deploy("mdb" + j);
        }

        logger.info("Start producer.");

        ProducerTransAck producer1 = new ProducerTransAck(CONTAINER1_IP, 4447, inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        TextMessageBuilder builder = new TextMessageBuilder(1);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();

        logger.info("Start receiver.");
        ReceiverTransAck receiver1 = new ReceiverTransAck(CONTAINER1_IP, 4447, outQueue, 6000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());


        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        deployer.undeploy("mdb1");
        stopServer(CONTAINER1);

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

        // we use only the first server
        prepareServer();

        controller.start(CONTAINER1);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 150);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
//        producer1.setMessageBuilder(new TextMessageBuilder(10000));

        logger.info("Start producer.");
        producer1.start();
        producer1.join();

        deployer.deploy("mdb1");

        List<String> killSequence = new ArrayList<String>();

        for (int i = 0; i < 5; i++) { // for (int i = 0; i < 5; i++) {
            killSequence.add(CONTAINER1);
        }

        // executeNodeFaillSequence(killSequence, 60000, shutdown);
        executeNodeFaillSequence(killSequence, 60000, shutdown);

        logger.info("Start receiver.");
        // SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 600000, 10, 10);
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER1_IP, 4447, outQueue, 600000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver1.getCount());

        List<String> lostMessages = checkLostMessages(producer1.getListOfSentMessages(), receiver1.getListOfReceivedMessages());
        Assert.assertEquals("There are lost messages. Check logs for details.", 0, lostMessages.size());
        for (String dupId : lostMessages) {
            logger.info("Lost message - _HQ_DUPL_ID=" + dupId);
        }

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);


        deployer.undeploy("mdb1");
        stopServer(CONTAINER1);

    }

    private List<String> checkLostMessages(List<String> listOfSentMessages, List<String> listOfReceivedMessages) {

        //get lost messages
        List<String> listOfLostMessages = new ArrayList<String>();

        for (String duplicateId : listOfSentMessages) {
            if (!listOfReceivedMessages.contains(duplicateId)) {
                listOfLostMessages.add(duplicateId);
            }
        }
        return listOfLostMessages;
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

                killServer(containerName);
                Thread.sleep(3000);
                controller.kill(containerName);
                logger.info("Start server: " + containerName);
                controller.start(containerName);
                logger.info("Server: " + containerName + " -- STARTED");

                printQueuesCount();
            }
        }
    }

    private void printQueuesCount() {
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
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
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        deleteFolder(new File(JOURNAL_DIRECTORY_A));
    }

    /**
     * Prepare server in simple topology.
     *
     * @throws Exception
     */
    public void prepareServer() throws Exception {
        prepareJmsServer(CONTAINER1);
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String containerName) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");

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

        controller.stop(containerName);

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh2Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh2");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh3Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh3");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh4Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh4");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh6Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh6");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh7Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh7");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh8Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh8");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh9Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh9");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh10Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh10");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh12Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh12");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh5Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh5");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh13Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh13");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh14Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh14");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh15Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh15");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh16Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh16");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh17Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh17");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh18Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh18");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh19Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh19");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh20Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh20");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh21Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh21");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh0Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh0");

        mdbJar.addClass(LocalMdbFromQueue.class);

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
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh11Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh11");

        mdbJar.addClass(LocalMdbFromQueue.class);

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