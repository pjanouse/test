package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
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
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
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
import java.util.HashMap;
import java.util.List;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000;

    private final Archive mdb1Archive = createLodh1Deployment();

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

    public static JavaArchive createLodh1Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml("mdb-lodh1")), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public static JavaArchive createLodhDeploymentForLimitedPoolSize(int id) {

        String deploymentName = "mdb-lodh-" + id;

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, deploymentName);

        mdbJar.addClass(LocalMdbFromQueueWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml(deploymentName)), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy 21 MDBs which
     *                reads messages from InQueue and send them to OutQueue in XA transaction. MDB use limited
     *                connection pool and use different users to create managed connections.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start first server with deployed InQueue and OutQueue</li>
     *              <li>start producer which sends messages to InQueue</li>
     *              <li>deploy 21 MDBs which reads messages from InQueue and sends to OutQueue (connection pool
     *                  limited to 10, and uses different user for creating connection)</li>
     *              <li>receive messages from OutQueue</li>
     *              </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLimitedPoolSize() throws Exception {

        prepareServer(container(1), true);

        container(1).start();

        logger.info("Deploy MDBs.");
        for (int j = 1; j < 22; j++) {
            container(1).deploy(createLodhDeploymentForLimitedPoolSize(j));
        }

        logger.info("Start producer.");

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        TextMessageBuilder builder = new TextMessageBuilder(1);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();

        logger.info("Start receiver.");
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueue, 20000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertEquals("There is different number of sent and received messages.", producer1.getListOfSentMessages()
                .size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        logger.info("Undeploy MDBs.");
        for (int j = 1; j < 22; j++) {
            container(1).undeploy(createLodhDeploymentForLimitedPoolSize(j));
        }
        container(1).stop();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy single MDB
     *                which reads messages from InQueue and sends them to OutQueue in XA transaction. Kill
     *                the server when MDB is processing messages and restart it. Read messages from OutQueue.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start first server with deployed InQueue and OutQueue</li>
     *              <li>start producer which sends messages to InQueue</li>
     *              <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *              <li>during processing messages kill the server</li>
     *              <li>restart the server</li>
     *              <li>receive messages from OutQueue</li>
     *              </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKill() throws Exception {
        testLodh(false);
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy single MDB
     *                which reads messages from InQueue and sends them to OutQueue in XA transaction. Shutdown
     *                the server when MDB is processing messages and restart it. Read messages from OutQueue.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start first server with deployed InQueue and OutQueue</li>
     *              <li>start producer which sends messages to InQueue</li>
     *              <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *              <li>during processing messages shutdown the server</li>
     *              <li>restart the server</li>
     *              <li>receive messages from OutQueue</li>
     *              </ul>
     * @tpPassCrit receiver consumes all messages
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
        prepareServer(container(1));

        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(1).deploy(mdb1Archive);

        List<Container> killSequence = new ArrayList<Container>();
        for (int i = 0; i < 2; i++) { // for (int i = 0; i < 5; i++) {
            killSequence.add(container(1));
        }

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 300000, container(1));
        executeNodeFaillSequence(killSequence, 20000, shutdown);

        // wait for 80% of messages
        new JMSTools().waitForMessages(outQueueName, (NUMBER_OF_MESSAGES_PER_PRODUCER * 8) / 10, 500000, container(1));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        logger.info("Start receiver.");
        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueue, 5000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertEquals("There is different number of sent and received messages. Received: "
                + receiver1.getListOfReceivedMessages().size() + ", Sent: " + producerToInQueue1.getListOfSentMessages().size()
                + ".", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).undeploy(mdb1Archive);
        container(1).stop();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy single MDB
     *                which reads messages from InQueue and cleanly shut-down the server. Check there are
     *                no unfinished transactions.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start first server with deployed InQueue and OutQueue</li>
     *              <li>start producer which sends messages to InQueue</li>
     *              <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *              <li>cleanly shutdown server</li>
     *              <li>check there are no unfinished transactions</li>
     *              </ul>
     * @tpPassCrit there are no unfinished transactions
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        int numberOfMessages = 2000;

        prepareServer(container(1));
        prepareServer(container(2));

        // cluster A
        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        container(1).deploy(mdb1Archive);

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 120000, container(1));
        container(1).stop();

        String journalFile1 = container(1).getName() + "-journal_content_after_shutdown.txt";

        // this create file in $WORKSPACE or working direcotry - depends whether it's defined
        PrintJournal printJournal = container(1).getPrintJournal();
        printJournal.printJournal(journalFile1);

        // check that there are failed transactions
        String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";

        String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System
                .getenv("WORKSPACE");
        Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", new TransactionUtils()
                .checkThatFileContainsUnfinishedTransactionsString(new File(workingDirectory, journalFile1), stringToFind));

        // copy tx-objectStore to container 2 and check there are no unfinished arjuna transactions
        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator),
                new File(container(2).getServerHome(), "standalone" + File.separator + "data"));
//        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator
//                + "tx-object-store"), new File(container(2).getServerHome(), "standalone" + File.separator + "data"
//                + File.separator + "tx-object-store"));
//        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator
//                + "messagingbindings"), new File(container(2).getServerHome(), "standalone" + File.separator + "data"
//                + File.separator + "messagingbindings"));
//        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator
//                + "messagingjournal"), new File(container(2).getServerHome(), "standalone" + File.separator + "data"
//                + File.separator + "messagingjournal"));
//        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator
//                + "messaginglargemessages"), new File(container(2).getServerHome(), "standalone" + File.separator + "data"
//                + File.separator + "messaginglargemessages"));
//        FileUtils.copyDirectory(new File(container(1).getServerHome(), "standalone" + File.separator + "data" + File.separator
//                + "messagingpaging"), new File(container(2).getServerHome(), "standalone" + File.separator + "data"
//                + File.separator + "messagingpaging/"));

        container(2).start();
        Assert.assertFalse("There are unfinished Arjuna transactions in node-2. Failing the test.",
                checkUnfinishedArjunaTransactions(container(2)));
        Assert.assertTrue(
                "There are no messages in InQueue. Send more messages so server is shutdowned when MDB is processing messages.",
                new JMSTools().waitForMessages(inQueueName, 1, 5000, container(2)));
        container(2).stop();
    }

    /**
     * @tpTestDetails Start server with deployed InQueue and OutQueue. Send messages to InQueue. Deploy single MDB
     *                which reads messages from InQueue and sends them to OutQueue in XA transaction. Read messages
     *                from OutQueue
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start first server with deployed InQueue and OutQueue</li>
     *              <li>start producer which sends messages to InQueue</li>
     *              <li>deploy MDB which reads messages from InQueue and sends to OutQueue</li>
     *              <li>receive messages from OutQueue</li>
     *              </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLodhWithoutKill() throws Exception {

        int numberOfMessages = 100;

        // we use only the first server
        prepareServer(container(1));
        container(1).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.setTimeout(0);
        logger.info("Start producer.");
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(1).deploy(mdb1Archive);

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));

        new JMSTools().waitForMessages(outQueueName, numberOfMessages, 300000, container(1));

        logger.info("Start receiver.");

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueue, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        container(1).undeploy(mdb1Archive);
        container(1).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFaillSequence(List<Container> failSequence, long timeBetweenFails, boolean shutdown)
            throws InterruptedException {

        if (shutdown) {
            for (Container container : failSequence) {
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
     * Be sure that both of the servers are stopped before and after the test. Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }

    /**
     * Prepare server in simple topology.
     *
     * @throws Exception
     */
    public void prepareServer(Container container) throws Exception {
        prepareServer(container, false);
    }

    public void prepareServer(Container container, boolean isWithSecurityAndLimitedPoolSize) throws Exception {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.setNodeIdentifier(1234567);  

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsAdminOperations.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations.rewriteLoginModule("RealmDirect", opts);

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

        if (isWithSecurityAndLimitedPoolSize) {

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

            File applicationUsersModified = new File(
                    "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
            File applicationUsersOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + "application-users.properties");
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);

            File applicationRolesModified = new File(
                    "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
            File applicationRolesOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + "application-roles.properties");
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);

            String connectionFactoryName = null;
            if (container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER)) {
                connectionFactoryName = Constants.RESOURCE_ADAPTER_NAME_EAP7;
            } else {
                connectionFactoryName = Constants.RESOURCE_ADAPTER_NAME_EAP6;
            }
            jmsAdminOperations.setMinPoolSizeOnPooledConnectionFactory(connectionFactoryName, 5);
            jmsAdminOperations.setMaxPoolSizeOnPooledConnectionFactory(connectionFactoryName, 10);

        }
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
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
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

}