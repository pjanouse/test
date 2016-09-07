package org.jboss.qa.hornetq.test.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueWithOutQueueLookups;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.util.Map;

/**
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ RESOURCE ADAPTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class JcaTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JcaTestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    private final Archive mdbDeployment = createDeployment();
    private final Archive lodhLikemdb = getLodhLikeMdb();

    public JavaArchive createDeployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());

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
        ejbXml.append("<ejb-name>mdb-lodh1</ejb-name>\n");
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue</ejb-class>\n");
        ejbXml.append("<activation-config>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destination</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>").append(PrepareBase.IN_QUEUE_JNDI).append("</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("<activation-config-property>\n");
        ejbXml.append("<activation-config-property-name>destinationType</activation-config-property-name>\n");
        ejbXml.append("<activation-config-property-value>javax.jms.Queue</activation-config-property-value>\n");
        ejbXml.append("</activation-config-property>\n");
        ejbXml.append("</activation-config>\n");
        ejbXml.append("<resource-ref>\n");
        ejbXml.append("<res-ref-name>queue/OutQueue</res-ref-name>\n");
        ejbXml.append("<jndi-name>").append(PrepareBase.OUT_QUEUE_JNDI).append("</jndi-name>\n");
        ejbXml.append("<res-type>javax.jms.Queue</res-type>\n");
        ejbXml.append("<res-auth>Container</res-auth>\n");
        ejbXml.append("</resource-ref>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        mdbJar.addAsManifestResource(new StringAsset(ejbXml.toString()), "jboss-ejb3.xml");

        logger.info(ejbXml);
        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getLodhLikeMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "lodhLikemdb1.jar");
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClasses(MdbWithRemoteOutQueueWithOutQueueLookups.class, MessageUtils.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        if (container(2).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        } else {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis \n"), "MANIFEST.MF");
        }
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    /**
     * @tpTestDetails Start server. Deploy InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue. Read messages from OutQueue
     * @tpProcedure <ul>
     * <li>start server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB to server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.TRANSACTION_TIMEOUT, value = "60000")
    })
    public void testJcaSmallMessages() throws Exception {
        MessageBuilder messageBuilder = new TextMessageBuilder(10);
        testJca(messageBuilder);
    }


    /**
     * @throws Exception
     */
    public void testJca(MessageBuilder messageBuilder) throws Exception {

        // we use only the first server
        container(1).start();

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), PrepareBase.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();

        container(1).deploy(mdbDeployment);

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 6000, 10, 10);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver1.getCount());

//        List<String> lostMessages = checkLostMessages(producer1.getListOfSentMessages(), receiver1.getListOfReceivedMessages());
//        Assert.assertEquals("There are lost messages. Check logs for details.", 0, lostMessages.size());
//        for (String dupId : lostMessages) {
//            logger.info("Lost message - _HQ_DUPL_ID=" + dupId);
//        }

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);


        container(1).undeploy(mdbDeployment);
        container(1).stop();

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.TRANSACTION_TIMEOUT, value = "60000")
    })
    public void testListPreparedTransactionsNPE() throws Exception {

        // we use only the first server
        container(1).start();

        // send messages to queue
        int numberOfMessages = 10000;
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareBase.IN_QUEUE_JNDI, numberOfMessages);
        producer1.setMessageBuilder(new TextMessageBuilder(1));
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        logger.info("Start producer.");
        producer1.start();
        addClient(producer1);

        container(1).deploy(mdbDeployment);

        JMSOperations jmsOperations = container(1).getJmsOperations();

        long timeout = System.currentTimeMillis() + (60 * 60 * 1000); //max one hour wait

        while (jmsOperations.getCountOfMessagesOnQueue(PrepareBase.OUT_QUEUE_NAME) < numberOfMessages) {
            JMXConnector connector = null;
            try {
                connector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
                MBeanServerConnection mbeanServer = connector.getMBeanServerConnection();
                ObjectName objectName = getObjectName();

                mbeanServer.invoke(objectName, "listPreparedTransactionDetailsAsJson", new Object[]{},
                        new String[]{});
            } finally {
                if (connector != null) {
                    connector.close();
                }
                if (System.currentTimeMillis() > timeout){
                    Assert.fail("Queue contains only " + jmsOperations.getCountOfMessagesOnQueue(PrepareBase.OUT_QUEUE_NAME) + " messages. Expected number is " + numberOfMessages );
                }
            }
            Thread.sleep(500);
        }

        logger.info("Start receiver.");
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 10000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.start();
        addClient(receiver1);
        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("No message was received.", receiver1.getListOfReceivedMessages().size() > 0);


        container(1).undeploy(mdbDeployment);
        container(1).stop();

    }

    private ObjectName getObjectName() throws Exception {
        ObjectName objectName = null;
        if (ContainerUtils.isEAP6(container(1))) {
            objectName = new ObjectName("jboss.as:subsystem=messaging,hornetq-server=default");
        } else {
            objectName = new ObjectName("jboss.as:subsystem=messaging-activemq,server=default");
        }
        return objectName;
    }

    /**
     * @tpTestDetails Start 2 servers in cluster. Deploy InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue. During processing of messages cause 100% cpu load on 1st server.
     * Read messages from OutQueue
     * @tpProcedure <ul>
     * <li>start 2 servers in cluster with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB to server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "TwoNodes", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.TRANSACTION_TIMEOUT, value = "60000")
    })
    public void testJcaInClusterWithLoad() throws Exception {

        logger.info("os.name=" + System.getProperty("os.name"));
        logger.info("os.version=" + System.getProperty("os.version"));
        Assume.assumeTrue((System.getProperty("os.name").contains("Linux")
                && (System.getProperty("os.version").contains("el7") || System.getProperty("os.version").contains("fc2"))));
        int numberOfMesasges = 10000;

        container(1).start();
        container(2).start();

        // send messages to InQueue
        FinalTestMessageVerifier mdbMessageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareBase.IN_QUEUE_JNDI, numberOfMesasges);
        TextMessageBuilder messageBuilder = new TextMessageBuilder();
        messageBuilder.setAddDuplicatedHeader(false);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(2));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(messageBuilder);
        producer1.addMessageVerifier(mdbMessageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        logger.info("Start producer.");
        producer1.start();
        producer1.join();

        // deploy MDBs
        container(1).deploy(lodhLikemdb);
        container(2).deploy(lodhLikemdb);

        // wait to have some messages in OutQueue
        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, numberOfMesasges / 10, 600000, container(1), container(2));

        // start load on 1st node
        Container containerUnderLoad = container(1);
        Process highCpuLoader1 = null;
        try {
            // bind EAP server to cpu core
            String cpuToBind = "0";
            highCpuLoader1 = HighCPUUtils.causeMaximumCPULoadOnContainer(containerUnderLoad, cpuToBind);
            logger.info("High Cpu loader was bound to cpu: " + cpuToBind);

            // Wait until some messages are consumed from InQueue
            Thread.sleep(300000);
            logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");
        } finally {
            if (highCpuLoader1 != null) {
                highCpuLoader1.destroy();
                try {
                    ProcessIdUtils.killProcess(ProcessIdUtils.getProcessId(highCpuLoader1));
                } catch (Exception ex) {
                    // we just ignore it as it's not fatal not to kill it
                    logger.warn("Process high cpu loader could not be killed, we're ignoring it it's not fatal usually.", ex);
                }
            }
        }
        new JMSTools().waitUntilMessagesAreStillConsumed(PrepareBase.IN_QUEUE_NAME, 300000, container(1), container(2));
        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(2), 0, false);

        logger.info("Start receiver.");
        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 20000, 100, 10);
        receiver1.addMessageVerifier(mdbMessageVerifier);
        receiver1.setTimeout(0);
        receiver1.setAckAfter(100);
        receiver1.start();
        receiver1.join();

        new ContainerUtils().printThreadDump(container(1));
        new ContainerUtils().printThreadDump(container(2));

        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        mdbMessageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);
        Assert.assertTrue("There should be no prepared transactions in HornetQ/Artemis but there are!!!", noPreparedTransactions);


        container(1).undeploy(lodhLikemdb);
        container(2).undeploy(lodhLikemdb);
        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails Start server. Deploy InQueue and OutQueue. Send messages to InQueue. Deploy MDB which reads
     * messages from InQueue and sends them to OutQueue. Call twice "start-delivery" on MDB.
     * Read messages from OutQueue
     * @tpInfo https://bugzilla.redhat.com/show_bug.cgi?id=1159572
     * @tpProcedure <ul>
     * <li>start server with deployed InQueue and OutQueue</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB to server which reads messages from InQueue and sends to OutQueue</li>
     * <li>wait for producer to send few messages</li>
     * <li>call operation "start-delivery" on MDB via model-node twice<li/>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.TRANSACTION_TIMEOUT, value = "60000")
    })
    public void testJcaWithDoubleStartOfDelivery() throws Exception {

        int numberOfMessages = 100;
        // we use only the first server
        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareBase.IN_QUEUE_JNDI, numberOfMessages);
        producer1.setCommitAfter(100);
        producer1.setMessageBuilder(new TextMessageBuilder(10));
        producer1.setTimeout(0);
        producer1.join();
        logger.info("Start producer.");
        producer1.start();

        container(1).deploy(mdbDeployment);

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, numberOfMessages / 2, 60000, container(1));

        // call stop delivery
        JMSOperations jmsOperations = container(1).getJmsOperations();

        jmsOperations.startDeliveryToMdb("mdb-lodh1");
        jmsOperations.startDeliveryToMdb("mdb-lodh1");

        jmsOperations.close();

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, numberOfMessages, 300000, container(1));

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 6000, 10, 10);
        receiver1.start();
        receiver1.join();


        logger.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getCount());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);


        container(1).undeploy(mdbDeployment);
        container(1).stop();

    }
}
