package org.jboss.qa.hornetq.test.failover;


import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalCopyMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.settings.JmsServerSettings;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;


/**
 * @author mnovak@redhat.com
 * @author msvehla@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class BytemanLodh1TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(BytemanLodh1TestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5; //10000;

    private static final int LARGE_MESSAGE_SIZE = 1048576;

    // queue to send messages in
    private static final String IN_QUEUE_NAME = "InQueue";

    private static final String IN_QUEUE = "jms/queue/" + IN_QUEUE_NAME;

    // queue for receive messages out
    private static final String OUT_QUEUE_NAME = "OutQueue";

    private static final String OUT_QUEUE = "jms/queue/" + OUT_QUEUE_NAME;


    @Deployment(managed = false, testable = false, name = "mdb1")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh1Deployment() {
        JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");

        String ejbXml = createEjbJarXml(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(ejbXml), "jboss-ejb3.xml");
        logger.info(ejbXml);

        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = "mdb2-copy")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createLodh1CopyDeployment() {
        JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1-copy");
        mdbJar.addClass(LocalCopyMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"),
                "MANIFEST.MF");

        String ejbXml = createEjbJarXml(LocalCopyMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(ejbXml), "jboss-ejb3.xml");
        logger.info(ejbXml);

        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb-copy.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction start",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "start",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionStart() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill after transaction start",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "start",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionStart() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction end",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "end",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionEnd() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill after transaction end",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "end",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionEnd() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionPrepare() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill after transaction prepare",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "prepare",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionPrepare() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill after transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.ra.HornetQRAXAResource",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillWithLargeMessagesOnTransactionCommit() throws Exception {
        this.generalLodh1Test();
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on large message create",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "createLargeMessage",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnCreatingLargeMessage() throws Exception {
        this.generalLodh1Test("mdb2-copy", new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on large message file send",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "sendLargeMessageFiles",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnSendingLargeMessage() throws Exception {
        this.generalLodh1Test("mdb2-copy", new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on large message file create",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "createFileForLargeMessage",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnCreatingLargeMessageFile() throws Exception {
        this.generalLodh1Test("mdb2-copy", new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on large message file delete",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "deleteLargeMessageFile",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnDeletingLargeMessageFilePassThrough() throws Exception {
        this.generalLodh1Test("mdb2-copy", new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on large message file delete",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "deleteLargeMessageFile",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    @Ignore
    public void testServerKillOnDeletingLargeMessageFile() throws Exception {
        this.generalLodh1Test("mdb1", new ByteMessageBuilder(LARGE_MESSAGE_SIZE));
    }


    private void generalLodh1Test() throws Exception {
        this.generalLodh1Test("mdb1", new ClientMixMessageBuilder(10, 150));
    }


    private void generalLodh1Test(final String deploymentName, final MessageBuilder msgBuilder) throws Exception {
        List<String> receivedMessages = new LinkedList<String>();

        this.controller.start(CONTAINER1);
        this.updateServerSettings();

        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        logger.info("!!!!! FIRST PASS !!!!!");
        logger.info("Sending messages to InQueue");
        this.sendMessages(msgBuilder);

        logger.info("Deploying MDB " + deploymentName);
        RuleInstaller.installRule(this.getClass(), getHostname(CONTAINER1), BYTEMAN_CONTAINER1_PORT);
        try {
            this.deployer.deploy(deploymentName);
        } catch (Exception e) {
            // byteman might kill the server before control returns back here from deploy method, which results
            // in arquillian exception; it's safe to ignore, everything is deployed and running correctly on the server
            logger.debug("Arquillian got an exception while deploying", e);
        }

        // try to stop server in case the kill didn't work out for any reason, otherwise the test fails
        // and breaks all following tests from the test suite
        this.stopAllServers();
        this.controller.kill(CONTAINER1);

        // try to read out any possible messages
        // depending on a specific point of server kill, there might be already some messages in OutQueue
        logger.info("Reading messages from OutQueue");
        List<String> beforeCrash = this.readMessages();
        receivedMessages.addAll(beforeCrash);
        logger.info("Consumed " + beforeCrash.size() + " messages in first pass");

        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        logger.info("!!!!! SECOND PASS !!!!!");
        logger.info("Reading messages from OutQueue");
        List<String> afterCrash = this.readMessages();
        receivedMessages.addAll(afterCrash);
        logger.info("Consumed " + afterCrash.size() + " messages in second pass");
        this.controller.stop(CONTAINER1);

        assertEquals("Incorrect number of received messages", 5, receivedMessages.size());
        assertTrue("Large messages directory should be empty", this.isLargeMessagesDirEmpty());
    }


    private List<String> sendMessages(final MessageBuilder builder) throws Exception {
        SoakProducerClientAck producer = new SoakProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), IN_QUEUE,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        builder.setAddDuplicatedHeader(false);
        producer.setMessageBuilder(builder);

        logger.info("Start producer.");
        producer.start();
        producer.join();

        return producer.getListOfSentMessages();
    }


    private List<String> readMessages() throws Exception {
        SoakReceiverClientAck receiver = null;
        logger.info("Start receiver.");

        try {
            receiver = new SoakReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), OUT_QUEUE, 300000, 10, 10);
            receiver.start();
            receiver.join();
            return receiver.getListOfReceivedMessages();
        } catch (Exception e) {
            logger.info("Caought exception in client, shutting it down", e);
            if (receiver != null) {
                return receiver.getListOfReceivedMessages();
            } else {
                return Collections.emptyList();
            }
        }
    }


    @Before
    @After
    @Override
    public void stopAllServers() {
        stopServer(CONTAINER1);
    }


    public void updateServerSettings() {
        JmsServerSettings
                .forContainer(JmsServerSettings.ContainerType.EAP6_WITH_HORNETQ, CONTAINER1,
                this.getArquillianDescriptor())
                .withoutClustering()
                .withPersistence()
                .withSharedStore()
                .withPaging(10 * 1024 * 1024, 50 * 1024, 0, 0)
                .withQueue(IN_QUEUE_NAME, true)
                .withQueue(OUT_QUEUE_NAME, true)
                .create();
    }


    private static String createEjbJarXml(final Class<?> mdbClass) {
        StringBuilder ejbXml = new StringBuilder();
        ejbXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        ejbXml.append("<jboss:ejb-jar xmlns:jboss=\"http://www.jboss.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns=\"http://java.sun.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        ejbXml.append("xmlns:c=\"urn:clustering:1.0\"\n");
        ejbXml.append("xsi:schemaLocation=\"http://www.jboss.com/xml/ns/javaee ")
                .append("http://www.jboss.org/j2ee/schema/jboss-ejb3-2_0.xsd http://java.sun.com/xml/ns/javaee ")
                .append("http://java.sun.com/xml/ns/javaee/ejb-jar_3_1.xsd\"\n");
        ejbXml.append("version=\"3.1\"\n");
        ejbXml.append("impl-version=\"2.0\">\n");
        ejbXml.append("<enterprise-beans>\n");
        ejbXml.append("<message-driven>\n");
        ejbXml.append("<ejb-name>mdb-lodh1</ejb-name>\n");
        ejbXml.append("<ejb-class>").append(mdbClass.getName()).append("</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>").append(IN_QUEUE)
                .append("</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>").append(OUT_QUEUE).append("</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        return ejbXml.toString();
    }


    private boolean isLargeMessagesDirEmpty() {
        String path = JBOSS_HOME_1 + File.separator
                + "standalone" + File.separator
                + "data" + File.separator
                + "messaginglargemessages";
        File largeMessagesDir = new File(path);

        if (!largeMessagesDir.isDirectory() || !largeMessagesDir.canRead()) {
            throw new IllegalStateException("Cannot access large messages directory " + path);
        }

        // Deleting the file is async... we keep looking for a period of the time until the file is really gone
        long timeout = System.currentTimeMillis() + 5000;
        while (timeout > System.currentTimeMillis() && largeMessagesDir.listFiles().length != 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (0 != largeMessagesDir.listFiles().length) {
            for (File file : largeMessagesDir.listFiles()) {
                System.out.println("File " + file + " still on ");
            }
        }

        return largeMessagesDir.listFiles().length == 0;
    }

}