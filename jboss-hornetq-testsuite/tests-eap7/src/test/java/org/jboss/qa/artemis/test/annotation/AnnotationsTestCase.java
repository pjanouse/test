package org.jboss.qa.artemis.test.annotation;

import org.apache.activemq.artemis.utils.IPV6Util;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Receiver;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueAnnotated2;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTopicAnnotated;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerInjectedJMSContext;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.io.File;

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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.PREPARE_OUT_QUEUE, value = "false")
    })
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.PREPARE_OUT_TOPIC, value = "false")
    })
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.PREPARE_OUT_QUEUE, value = "false")
    })
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.PREPARE_OUT_QUEUE, value = "false"),
            @Param(name = PrepareParams.PREPARE_OUT_TOPIC, value = "false")

    })
    public void testExistenceOfDestinations() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");
        JavaArchive mdbDeployment2 = createDeploymentWithDefinitionsTopic("mdb2");

        container(1).start();

        container(1).deploy(mdbDeployment);
        container(1).deploy(mdbDeployment2);

        Thread.sleep(10000);

        Context ctx = null;
        try {
            ctx = container(1).getContext();
            Queue myQueue = (Queue) ctx.lookup(PrepareConstants.OUT_QUEUE_JNDI);
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
            Topic myTopic = (Topic) ctx.lookup(PrepareConstants.OUT_TOPIC_JNDI);
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
            Queue myQueue = (Queue) ctx.lookup(PrepareConstants.OUT_QUEUE_JNDI);
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
            Topic myTopic = (Topic) ctx.lookup(PrepareConstants.OUT_TOPIC_JNDI);
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20"),
            @Param(name = PrepareParams.PREPARE_OUT_QUEUE, value = "false")
    })
    public void testExistenceOfConnectionFactory() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");

        container(1).start();

        container(1).deploy(mdbDeployment);

        Assert.assertTrue(JMSTools.isRegisteredInJNDI(container(1), "MyConnectionFactory"));

        logger.info("undeploy mdb");
        container(1).undeploy(mdbDeployment);

        Assert.assertFalse(JMSTools.isRegisteredInJNDI(container(1), "MyConnectionFactory"));

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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MIN_POOL_SIZE, value = "10"),
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "20")
    })
    public void testDeployMdbDefiningAlreadyDefinedDestination() throws Exception {

        JavaArchive mdbDeployment = createDeploymentWithDefinitionsQueue("mdb1");

        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, 200);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);

        logger.info("Start producer.");
        producer1.start();
        producer1.join();
        logger.info("Producer finished. Deploy mdb.");

        container(1).deploy(mdbDeployment);

        logger.info("Start receiver.");
        SoakReceiverClientAck receiver = new SoakReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 6000, 10, 10);
        receiver.start();

        receiver.join();
        logger.info("Receiver finished.");

        logger.info("Number of sent messages: " + producer1.getCounter());
        logger.info("Number of received messages: " + receiver.getCount());

        Assert.assertEquals("Mdb was deployed although it is defining already defined destination.", 0, receiver.getCount());

        container(1).undeploy(mdbDeployment);

        container(1).stop();

    }

    /**
     * @tpTestDetails Test scenario when max-pool-size is lower than number of Java EE components which need it.
     * Resources should be released after each request and not to be blocked during the whole lifecycle of component.
     * @tpProcedure <ul>
     * <li>Prepare server with two queues and max-pool-size=1</li>
     * <li>Deploy application with one servlet which sends messages and one MDB which resend messages between queues.
     *     Both Java Beans inject JMSContext which is taken from shared pool.</li>
     * <li>Send 100 HTTP GET requests on servlet</li>
     * <li>Receive 100 messages from target queue.</li>
     * </ul>
     * @tpPassCrit All messages were received.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.POOLED_CONNECTION_FACTORY_MAX_POOL_SIZE, value = "1")
    })
    public void testInjectedJMSContextLowMaxPoolSize() throws Exception {
        container(1).start();

        container(1).deploy(createDeploymentWithInjectedJMSContext());

        CloseableHttpClient httpClient = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(String.format("http://%s:%d/app/producer", IPV6Util.encloseHost(container(1).getHostname()), container(1).getHttpPort()));

        for (int i = 0; i < 100; i++) {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            try {
                logger.info(response.getStatusLine());
                HttpEntity entity = response.getEntity();
                logger.info(IOUtils.toString(entity.getContent()));
                EntityUtils.consume(entity);
            } finally {
                response.close();
            }
        }

        Receiver receiver = new ReceiverAutoAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 2000, 1);
        receiver.start();
        receiver.join();

        container(1).stop();

        Assert.assertEquals(100, receiver.getCount());
    }

    private void basicSendAndReceiveQueue(JavaArchive... mdbDeployment) throws InterruptedException {

        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
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
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 6000, 10, 10);
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

        container(1).start();

        MessageBuilder messageBuilder = new TextMessageBuilder(10);

        SoakProducerClientAck producer1 = new SoakProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
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

        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), PrepareConstants.OUT_TOPIC_JNDI, "subscriber1",
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

    public Archive createDeploymentWithInjectedJMSContext() {
        WebArchive deployment = ShrinkWrap.create(WebArchive.class, "app.war");
        deployment.addClass(ServletProducerInjectedJMSContext.class);
        deployment.addClass(LocalMdbFromQueueToQueue.class);

        File beans = new File(this.getClass().getResource("/beans-discovery-all.xml").getPath());
        deployment.add(new FileAsset(beans), "/WEB-INF/beans.xml");

//        deployment.as(ZipExporter.class).exportTo(new File("/tmp/app.war"));

        return deployment;
    }
}
