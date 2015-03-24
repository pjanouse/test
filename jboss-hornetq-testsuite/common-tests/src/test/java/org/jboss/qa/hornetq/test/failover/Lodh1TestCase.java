package org.jboss.qa.hornetq.test.failover;


import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.annotations.TestPlan;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueWithSecurity;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mnovak@redhat.com
 * @tpChapter 2.6 RECOVERY/FAILOVER TESTING
 * @tpSub XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@TestPlan
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000;

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
    @TargetsContainer(CONTAINER1_NAME)
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



    public JavaArchive createLodh1TestDeployment() {

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
        prepareJmsServerEAP6(container(1));
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();

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

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        copyFile(applicationUsersModified, applicationUsersOriginal);

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        copyFile(applicationRolesModified, applicationRolesOriginal);

//        stopServer(CONTAINER1_NAME_NAME);
//
//        controller.start(CONTAINER1_NAME_NAME);
        String connectionFactoryName = "hornetq-ra";
        jmsAdminOperations.setMinPoolSizeOnPooledConnectionFactory(connectionFactoryName, 5);
        jmsAdminOperations.setMaxPoolSizeOnPooledConnectionFactory(connectionFactoryName, 10);
        jmsAdminOperations.close();

        container(1).restart();

        logger.info("Deploy MDBs.");
        for (int j = 2; j < 22; j++) {
            deployer.deploy("mdb" + j);
        }

        logger.info("Start producer.");

        ProducerTransAck producer1 = new ProducerTransAck(container(1).getHostname(), container(1).getJNDIPort(), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        TextMessageBuilder builder = new TextMessageBuilder(1);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();

        logger.info("Start receiver.");
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1).getHostname(), container(1).getJNDIPort(), outQueue, 20000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());


        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        logger.info("Undeploy MDBs.");
        for (int j = 2; j < 22; j++) {
            deployer.undeploy("mdb" + j);
        }
        container(1).stop();
    }


    /**
     * @tpScenario Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads messages
     * from InQueue and sends them to OutQueue in XA transaction. Kill the server when MDB is processing messages and restart it. Read messages from OutQueue
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul><li>start first server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>during processing messages kill/shutdown the server</li>
     * <li>restart the server</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKill() throws Exception {
        testLodh(false);
    }

    /**
     * @tpScenario Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads messages
     * from InQueue and sends them to OutQueue in XA transaction. Shutdown the server when MDB is processing messages and restart it. Read messages from OutQueue
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul><li>start first server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>during processing messages kill/shutdown the server</li>
     * <li>restart the server</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @throws Exception
     */
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
        prepareJmsServerEAP6(container(1));
        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1).getContainerType().name(),
                container(1).getHostname(), container(1).getJNDIPort(), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        deployer.deploy(MDB_NAME);

        List<Container> killSequence = new ArrayList<Container>();
        for (int i = 0; i < 2; i++) { // for (int i = 0; i < 5; i++) {
            killSequence.add(container(1));
        }

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/100, 300000, container(1));
        executeNodeFaillSequence(killSequence, 20000, shutdown);

        // wait for 80% of messages
        waitForMessages(outQueueName, (NUMBER_OF_MESSAGES_PER_PRODUCER * 8)/10, 500000, container(1));

        waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));

        waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        logger.info("Start receiver.");
        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1).getHostname(), container(1).getJNDIPort(), outQueue, 5000, 100, 10);
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
        container(1).stop();
    }


    /**
     * @tpScenario Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and cleanly shut-down the server. Check there are no unfinished transactions.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>cleanly shutdown server</li>
     * <li>check there are no unfinished HQ or Arjuna transactions</li>
     * </ul>
     * @tpPassCrit no unfinished HQ or Arjuna transactionsCheck there are no unfinished transactions
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        int numberOfMessages = 2000;

        prepareJmsServerEAP6(container(1));
        prepareJmsServerEAP6(container(2));

        // cluster A
        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1).getContainerType().name(),
                container(1).getHostname(), container(1).getJNDIPort(), inQueue, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        deployer.deploy(MDB_NAME);

        waitForMessages(outQueueName, numberOfMessages / 10, 120000, container(1));
        container(1).stop();

        String journalFile1 = container(1).getName() + "-journal_content_after_shutdown.txt";

        // this create file in $WORKSPACE or working direcotry - depends whether it's defined
        PrintJournal.printJournal(container(1), journalFile1);
        // check that there are failed transactions
        String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";

        String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");
        Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory,journalFile1), stringToFind));

        // copy tx-objectStore to container 2 and check there are no unfinished arjuna transactions
        copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator + "tx-object-store"),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data" + File.separator + "tx-object-store"));
        copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingbindings"),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingbindings"));
        copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingjournal"),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingjournal"));
        copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messaginglargemessages"),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messaginglargemessages"));
        copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingpaging"),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data" + File.separator + "messagingpaging/"));

        container(2).start();
        Assert.assertFalse("There are unfinished Arjuna transactions in node-2. Failing the test.", checkUnfinishedArjunaTransactions(
                container(2)));
        Assert.assertTrue("There are no messages in InQueue. Send more messages so server is shutdowned when MDB is processing messages.",
                waitForMessages(inQueueName, 1, 5000, container(2)));
        container(2).stop();
    }

    /**
     * @tpScenario Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue in XA transaction. Read messages from OutQueue
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLodhWithoutKill() throws Exception {

        // we use only the first server
        prepareServer();
        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1).getContainerType().name(),
                container(1).getHostname(), container(1).getJNDIPort(), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        deployer.deploy(MDB_NAME);

        logger.info("Start receiver.");

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1).getHostname(), container(1).getJNDIPort(),
                outQueue, 300000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        deployer.undeploy(MDB_NAME);
        container(1).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence     map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFaillSequence(List<Container> failSequence, long timeBetweenFails, boolean shutdown)
            throws InterruptedException {

        if (shutdown) {
            for (Container container: failSequence) {
                Thread.sleep(timeBetweenFails);

                printQueuesCount();

                logger.info("Shutdown server: " + container.getName());

                container.stop();
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");

                printQueuesCount();
            }
        } else {
            for (Container container : failSequence) {
                Thread.sleep(timeBetweenFails);

                printQueuesCount();

                container.kill();
                logger.info("Server container1 killed!.");
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");

                printQueuesCount();
            }
        }
    }

    private void printQueuesCount() {
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
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
        container(1).stop();
        container(2).stop();
        deleteFolder(new File(JOURNAL_DIRECTORY_A));
    }

    /**
     * Prepare server in simple topology.
     *
     * @throws Exception
     */
    public void prepareServer() throws Exception {

        if (container(1).getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareJmsServerEAP6(container(1));
        } else if (container(1).getContainerType().equals(CONTAINER_TYPE.EAP7_CONTAINER))   {
            prepareJmsServerEAP7(container(1));
        }
    }

    private void prepareJmsServerEAP7(Container container) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
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

        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Container used in the test
     */
    private void prepareJmsServerEAP6(Container container) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
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

        container.stop();

    }

    @Deployment(managed = false, testable = false, name = "mdb2")
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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
    @TargetsContainer(CONTAINER1_NAME)
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