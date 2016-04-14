package org.jboss.qa.artemis.test.annotation;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated2;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTopicAnnotated;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
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
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 *
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ RESOURCE ADAPTER - TEST SCENARIOS
 * @tpTestCaseDetails Test case focuses on defining destinations and connection
 * factories using Java EE annotations "@JMSConnectionFactoryDefinition"
 * ,"@JMSDestinationDefinition".
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @author mstyk
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class AnnotationsTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(AnnotationsTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    // topic for receive messages out
    static String outTopicName = "OutTopic";
    static String outTopic = "jms/topic/" + outTopicName;

    /**
     * @tpTestDetails Start one server. Send messages to InQueue. Deploy MDB
     * which uses "@JMSDestinationDefinition" annotation to define OutQueue. MDB
     * sends messages from InQueue to OutQueue. Receive messages from OutQueue.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Send 1000 messages to InQueue.</li>
     * <li>Deploy MDB which sends messages from InQueue to OutQueue (OutQueue
     * defined using annotation in MDB).</li>
     * <li>Consumer reads messages from OutQueue.</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver read all messages from queue defined in MDB.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testBasicSendAndReceiveQueue() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb");
        basicSendAndReceiveQueue(mdbDeployment);
    }

    /**
     * @tpTestDetails Start one server. Send messages to InQueue. Deploy MDB
     * which uses "@JMSDestinationDefinition" annotation to define OutTopic. MDB
     * sends messages from InQueue to OutTopic. Receive messages from OutTopic.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Send 1000 messages to InQueue.</li>
     * <li>Deploy MDB which sends messages from InQueue to OutTopic (OutTopic
     * defined using annotation in MDB).</li>
     * <li>Consumer reads messages from OutTopic.</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver read all messages from topic defined in MDB.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testBasicSendAndReceiveTopic() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsTopic("mdb");
        basicSendAndReceiveTopic(mdbDeployment);
    }

    /**
     * @tpTestDetails Start one server. Send messages to InQueue. Deploy MDB
     * which uses "@JMSDestinationDefinition" annotation to define OutQueue and
     * deploy another mdb which uses OutQueue without its definition. MDBs sends
     * messages from InQueue to OutQueue. Receive messages from OutQueue.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Send 1000 messages to InQueue.</li>
     * <li>Deploy MDB which sends messages from InQueue to OutQueue (OutQueue
     * defined using annotation in MDB).</li>
     * <li>Deploy MDB which sends messages from InQueue to OutQueue (OutQueue
     * only looked up).</li>
     * <li>Consumer reads messages from OutTopic.</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver read all messages from queue defined in MDB.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testQueueAccessibilityFromAnotherMdb() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithTwoMdbs("mdb");
        basicSendAndReceiveQueue(mdbDeployment);
    }

    /**
     * @tpTestDetails Start one server. Deploy MDB which uses
     * "@JMSDestinationDefinition" annotation to define OutQueue. Check whether
     * the client is able to lookup for queue. Undeploy MDB and do lookup for
     * queue again.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Deploy MDB which defines OutQueue using annotation in MD).</li>
     * <li>Do lookup for OutQueue.</li>
     * <li>Undeploy MDB.</li>
     * <li>Do lookup for OutQueue.</li>
     * <li>Stop server</li>
     * </ul>
     * @tpPassCrit OutQueue defined in MDB is available only during deployment
     * of MDB.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testExistenceOfDestinations() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");
        JavaArchive mdbDeployment2 = createDeploymentWithDefinitionsTopic("mdb2");

        prepareServer(container(1));
        container(1).start();

        container(1).deploy(mdbDeployment);
        container(1).deploy(mdbDeployment2);

        Thread.sleep(10000);

        Context ctx = null;
        try {
            ctx = container(1).getContext();
            Queue myQueue = (Queue) ctx.lookup(outQueue);
            logger.info("queue exists, should have been created on undeployment of mdb defining it - ok");
        } catch (NamingException e) {
            Assert.fail("naming exception catch, queue doesnt exist when mdb defining it is deployed - thats not ok");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
        try {
            ctx = container(1).getContext();
            Topic myTopic = (Topic) ctx.lookup(outTopic);
            logger.info("topic exists, should have been created on undeployment of mdb defining it - ok");
        } catch (NamingException e) {
            Assert.fail("naming exception catch, topic doesnt exist when mdb defining it is deployed - thats not ok");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        logger.info("Undeploy mdbs.");
        container(1).undeploy(mdbDeployment2);
        container(1).undeploy(mdbDeployment);

        //lookup for queue defined in already undeployed mdb
        ctx = null;
        try {
            ctx = container(1).getContext();
            Queue myQueue = (Queue) ctx.lookup(outQueue);
            Assert.fail("queue should not exist, should have been deleted on undeployment of mdb defining it - thats not ok");
        } catch (NamingException e) {
            logger.info("naming exception catch, queue doesnt exist anymore - ok");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
        try {
            ctx = container(1).getContext();
            Topic myTopic = (Topic) ctx.lookup(outTopic);
            Assert.fail("Topic should not exist, should have been deleted on undeployment of mdb defining it - thats not ok");
        } catch (NamingException e) {
            logger.info("naming exception catch, topic doesnt exist anymore - ok");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        container(1).stop();
    }

    /**
     * @tpTestDetails Start one server. Deploy MDB which uses
     * "@JMSConnectionFactoryDefinition" annotation to define connection
     * factory. Check whether the client is able to lookup for connection
     * factory. Undeploy MDB and do lookup for connection factory again.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Deploy MDB which defines connection factory using annotation in
     * MDB.</li>
     * <li>Do lookup for connection factory defined in MDB.</li>
     * <li>Undeploy MDB.</li>
     * <li>Do lookup for connection factory.</li>
     * <li>Stop server</li>
     * </ul>
     * @tpPassCrit Connection factory defined in MDB is available only during
     * deployment of MDB.
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testExistenceOfConnectionFactory() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");

        prepareServer(container(1));
        container(1).start();

        container(1).deploy(mdbDeployment);

        Context ctx = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("MyConnectionFactory");
            logger.info("cf exists, should have been created on undeployment of mdb defining it - ok");
        } catch (org.jboss.naming.remote.protocol.NamingIOException e) {
            logger.info(e.toString());
            //ok not serializable
        } catch (NameNotFoundException e) {
            logger.info("connection factory should exist but wasnt found");
            Assert.fail("connection factory not found");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        logger.info("undeploy mdb");
        container(1).undeploy(mdbDeployment);
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("MyConnectionFactory");
        } catch (org.jboss.naming.remote.protocol.NamingIOException e) {
            logger.info(e.toString());
            Assert.fail("cf should not exist.");
            //ok not serializable
        } catch (NameNotFoundException e) {
            logger.info("connection factory should exist but wasnt found");
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }

        container(1).stop();
    }

    /**
     * @tpTestDetails Start one server with OutQueue deployed. Deploy MDB which
     * uses "@JMSDestinationDefinition" annotation to define OutQueue, and sends
     * messages from InQueue to OutQueue. Send 200 messages in InQeeue. Try to
     * receive them from OutQueue.
     * @tpProcedure <ul>
     * <li>Start server.</li>
     * <li>Send messages to InQueue.</li>
     * <li>Deploy MDB which defines OutQueue using annotation in MDB and sends
     * messages from InQueue to outQueue.</li>
     * <li>Receive messages from OutQueue.</li>
     * </ul>
     * @tpPassCrit No messages were received from OutQueue because MDB wasnt
     * deployed(duplicated resource).
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testDeployMdbDefiningAlreadyDefinedDestination() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");

        prepareServerWithOutQueue(container(1));
        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), inQueue, 200);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();
        logger.info("Producer finished. Deploy mdb.");

        container(1).deploy(mdbDeployment);

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver = new SoakReceiverClientAck(container(1), outQueue, 6000, 10, 10);
        receiver.start();

        receiver.join();
        logger.info("Receiver finished.");

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver.getCount());

        Assert.assertEquals("Mdb was deployed although it is defining already defined destination.", 0, receiver.getCount());

        container(1).undeploy(mdbDeployment);

        container(1).stop();

    }

    private void basicSendAndReceiveQueue(JavaArchive... mdbDeployment) throws InterruptedException {

        prepareServer(container(1));
        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();
        logger.info("Producer finished. Deploy mdb.");

        //deploy archives
        for (JavaArchive archive : mdbDeployment) {
            container(1).deploy(archive);
        }

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), outQueue, 6000, 10, 10);
        receiver1.start();

        receiver1.join();
        logger.info("Receiver finished.");

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver1.getCount());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
        Assert.assertTrue("No message was received.", receiver1.getCount() > 0);

        //undeploy archives
        for (JavaArchive archive : mdbDeployment) {
            container(1).undeploy(archive);
        }

        container(1).stop();
    }

    private void basicSendAndReceiveTopic(JavaArchive... mdbDeployment) throws Exception {

        prepareServer(container(1));
        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), inQueue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();
        logger.info("Producer finished. Deploy mdb.");

        //deploy archives
        for (JavaArchive archive : mdbDeployment) {
            container(1).deploy(archive);
        }

        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), outTopic, "subscriber1",
                "subscription1");

        logger.info("Start receiver.");
        subscriber.start();
        subscriber.join();
        logger.info("Receiver finished.");

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + subscriber.getCount());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                subscriber.getCount());
        Assert.assertTrue("No message was received.", subscriber.getCount() > 0);

        //undeploy archives
        for (JavaArchive archive : mdbDeployment) {
            container(1).undeploy(archive);
        }

        container(1).stop();
    }

    /**
     * Be sure that the server is stopped before and after the test. Delete also
     * the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
    }

    private void prepareServer(Container container) {

        String connectionFactoryName = Constants.RESOURCE_ADAPTER_NAME_EAP7;

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setMinPoolSizeOnPooledConnectionFactory(connectionFactoryName, 10);
        jmsAdminOperations.setMaxPoolSizeOnPooledConnectionFactory(connectionFactoryName, 20);
        jmsAdminOperations.setDefaultResourceAdapter("activemq-ra");

        try {
            jmsAdminOperations.removeQueue(inQueueName);

            jmsAdminOperations.removeQueue(outQueueName);
            jmsAdminOperations.removeTopic(outTopicName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);

        jmsAdminOperations.close();
        container.stop();
    }

    private void prepareServerWithOutQueue(Container container) {
        prepareServer(container);
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        container.start();

        try {
            jmsAdminOperations.removeQueue(outQueueName);
            jmsAdminOperations.removeTopic(outTopicName);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);
        jmsAdminOperations.close();
        container.stop();

    }

    public JavaArchive createDeploymentWithDefinitionsQueue(String resourceName) {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, resourceName);

        mdbJar.addClass(LocalMdbFromQueueAnnotated.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");

//        logger.info(ejbXml);
//        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public JavaArchive createDeploymentWithDefinitionsTopic(String resourceName) {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, resourceName);

        mdbJar.addClass(LocalMdbFromQueueToTopicAnnotated.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");

//        logger.info(ejbXml);
//        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public JavaArchive createDeploymentWithoutDefinitions(String resourceName) {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, resourceName);

        mdbJar.addClass(LocalMdbFromQueueAnnotated2.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");

//        logger.info(ejbXml);
//        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public JavaArchive createDeploymentWithTwoMdbs(String resourceName) {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, resourceName);
        Class[] mdbs = new Class[]{LocalMdbFromQueueAnnotated.class, LocalMdbFromQueueAnnotated2.class};
        mdbJar.addClasses(mdbs);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");

//        logger.info(ejbXml);
//        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }
}
