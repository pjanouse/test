package org.jboss.qa.hornetq.test.domain;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.MdbListenningOnNonDurableTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContainerWithReplacementProperties;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter2;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DomainOperations;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * <p/>
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class DomainLodh2TestCase extends DomainHornetQTestCase {

    private static final Logger logger = Logger.getLogger(DomainLodh2TestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 5000;

    public static final String MDB_ON_QUEUE_1 = "mdb1";
    public static final String MDB_ON_QUEUE_2 = "mdb2";
    public static final String MDB_ON_QUEUE_WITH_FILTER_1 = "mdb1WithFilter";
    public static final String MDB_ON_QUEUE_WITH_FILTER_2 = "mdb2WithFilter";
    public static final String MDB_ON_NON_DURABLE_TOPIC = "nonDurableMdbOnTopic";
    public static final String MDB_WITH_PROPERTIES_MAPPED_NAME = "mdbWithPropertiesMappedName";
    public static final String MDB_WITH_PROPERTIES_NAME = "mdbWithPropertiesName";
    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;
    // inTopic
    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_1)
    @TargetsContainer(SERVER_GROUP2)
    public Archive getDeployment1() throws Exception {
        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_WITH_PROPERTIES_MAPPED_NAME)
    @TargetsContainer(SERVER_GROUP2)
    public Archive getDeploymentMdbWithProperties() throws Exception {

        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB_WITH_PROPERTIES_MAPPED_NAME + ".jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdbWithPropertyReplacements.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_WITH_PROPERTIES_NAME)
    @TargetsContainer(SERVER_GROUP2)
    public Archive getDeploymentMdbWithPropertiesName() throws Exception {

        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, MDB_WITH_PROPERTIES_NAME + ".jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/" + MDB_WITH_PROPERTIES_NAME + ".jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_2)
    @TargetsContainer(SERVER_GROUP4)
    public Archive getDeployment2() throws Exception {

        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_WITH_FILTER_1)
    @TargetsContainer(SERVER_GROUP2)
    public Archive getDeploymentWithFilter1() throws Exception {
        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    @Deployment(managed = false, testable = false, name = MDB_ON_QUEUE_WITH_FILTER_2)
    @TargetsContainer(SERVER_GROUP4)
    public Archive getDeploymentWithFilter2() throws Exception {
        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    @Deployment(managed = false, testable = false, name = MDB_ON_NON_DURABLE_TOPIC)
    @TargetsContainer(SERVER_GROUP2)
    public Archive getDeploymentNonDurableMdbOnTopic() throws Exception {
        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /////////////////////////////// START - Local -> Remote
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killJmsLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, true, false, container(2), container(1));
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, true, false, container(2), container(1));
    }
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(2), container(1));
    }
    /////////////////////////////// END - Local -> Remote

    /////////////////////////////// START - Remote -> Local
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killJmsRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false, false, container(1), container(2));
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false, false, container(1), container(2));
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownJmsRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, true, false, container(1), container(2));
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2ShutdownMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, true, false, container(1), container(2));
    }
    /////////////////////////////// START - Remote -> Local


    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh2killWithFilters() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Shutdown jms servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testSimpleLodh3Shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2killWithTempTopic() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, false, false);

    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Shutdown mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownOfJmsServers() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, true);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false);
    }

    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh3shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, false);
    }

    public void testRemoteJcaWithTopic(List<Container> failureSequence, boolean isShutdown, boolean isDurable) throws Exception {
        testRemoteJcaWithTopic(failureSequence, isShutdown, isDurable, container(1), container(3));
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<Container> failureSequence, boolean isShutdown, boolean isDurable, Container inServer, Container outServer) throws Exception {

        DomainOperations.forDefaultContainer()
                .removeServer("server-1")
                .removeServer("server-3")
                .createServer("server-1", "server-group-1", container(1).getPortOffset())
                .createServer("server-3", "server-group-1", container(3).getPortOffset())
                .reloadDomain()
                .close();

        prepareRemoteJcaTopology(inServer, outServer);
        // jms server
        container(1).start();

        // mdb server
        container(2).start();

        if (!isDurable) {
            deployer.deploy(MDB_ON_NON_DURABLE_TOPIC);
            Thread.sleep(5000);
        }

        PublisherTransAck producer1 = new PublisherTransAck(container(1),
                inTopicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "clientId-myPublisher");
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.start();

        // deploy mdbs
        if (isDurable) {
            throw new UnsupportedOperationException("This was not yet implemented. Use Mdb on durable topic to do so.");
        }

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10, 120000, container(1));

        executeFailureSequence(failureSequence, 3000, isShutdown);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 10);
        receiver1.setCommitAfter(100);
        receiver1.start();

        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getMessages()
                + ", Producer to jms1 server sent: " + producer1.getMessages() + " messages"));

        logger.info("Number of received messages: " + (receiver1.getCount()
                + ", Consumer from jms1 server received: " + receiver1.getCount() + " messages"));

        if (isDurable) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getMessages(), receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);

        } else {

            Assert.assertTrue("There SHOULD be different number of sent and received messages.",
                    producer1.getMessages() > receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.",
                    receiver1.getCount() > 0);
            deployer.undeploy(MDB_ON_NON_DURABLE_TOPIC);
        }


        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, false);
    }

    /**
     * For remote inQueue and remote OutQueue
     *
     * @param failureSequence
     * @param isShutdown
     * @param isFiltered
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown, boolean isFiltered) throws Exception {
        testRemoteJcaInCluster(failureSequence, isShutdown, isFiltered, container(1), container(3));
    }


    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, boolean isShutdown, boolean isFiltered, Container inServer, Container outServer) throws Exception {

        DomainOperations.forDefaultContainer()
                .removeServer("server-1")
                .removeServer("server-3")
                .createServer("server-1", "server-group-1", container(1).getPortOffset())
                .createServer("server-3", "server-group-1", container(3).getPortOffset())
                .reloadDomain()
                .close();

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.addMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        // deploy mdbs
        if (isFiltered) {
            deployer.deploy(MDB_ON_QUEUE_WITH_FILTER_1);
            deployer.deploy(MDB_ON_QUEUE_WITH_FILTER_2);
        } else {
            deployer.deploy(MDB_ON_QUEUE_1);
            deployer.deploy(MDB_ON_QUEUE_2);
        }

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, container(1), container(2),
                container(3), container(4));

        executeFailureSequence(failureSequence, 5000, isShutdown);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1), container(2),
                container(3), container(4));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 3000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        if (isFiltered) {
            deployer.undeploy(MDB_ON_QUEUE_WITH_FILTER_1);
            deployer.undeploy(MDB_ON_QUEUE_WITH_FILTER_2);
        } else {
            deployer.undeploy(MDB_ON_QUEUE_1);
            deployer.undeploy(MDB_ON_QUEUE_2);
        }

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    /**
     * @throws Exception
     */
    /**
     * Kills mdbs servers.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        Container inServer = container(1);
        Container outServer = container(2);

        DomainOperations.forDefaultContainer()
                .removeServer("server-1")
                .removeServer("server-3")
                .createServer("server-1", "server-group-1", container(1).getPortOffset())
                .createServer("server-3", "server-group-1", container(3).getPortOffset())
                .reloadDomain()
                .close();

        int numberOfMessages = NUMBER_OF_MESSAGES_PER_PRODUCER;

        prepareRemoteJcaTopology(inServer, outServer);

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        deployer.deploy(MDB_ON_QUEUE_1);
        deployer.deploy(MDB_ON_QUEUE_2);

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 100, 120000, container(1), container(3));

        container(2).stop();
        container(4).stop();

        // check there are still some messages in InQueue
        Assert.assertTrue("MDBs read all messages from InQueue before shutdown. Increase number of messages shutdown happens" +
                        " when MDB is processing messages", new JMSTools().waitForMessages(inQueueName, 1, 10000, container(1),
                container(3)));

        String journalFile1 = CONTAINER1_NAME + "journal_content_after_shutdown.txt";
        String journalFile3 = CONTAINER3_NAME + "journal_content_after_shutdown.txt";

        container(1).getPrintJournal().printJournal(journalFile1);
        container(3).getPrintJournal().printJournal(journalFile3);

        // check that there are failed transactions
        String stringToFind = "Failed Transactions (Missing commit/prepare/rollback record)";
        String workingDirectory = System.getenv("WORKSPACE") == null ? new File(".").getAbsolutePath() : System.getenv("WORKSPACE");

        Assert.assertFalse("There are unfinished HornetQ transactions in node-1. Failing the test.", new TransactionUtils().checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile1), stringToFind));
        Assert.assertFalse("There are unfinished HornetQ transactions in node-3. Failing the test.", new TransactionUtils().checkThatFileContainsUnfinishedTransactionsString(
                new File(workingDirectory, journalFile3), stringToFind));

        container(1).stop();
        container(3).stop();

        container(2).start();
        container(4).start();

        Assert.assertFalse("There are unfinished Arjuna transactions in node-2. Failing the test.", checkUnfinishedArjunaTransactions(
                container(2)));
        Assert.assertFalse("There are unfinished Arjuna transactions in node-4. Failing the test.", checkUnfinishedArjunaTransactions(
                container(4)));

        container(2).stop();
        container(4).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  list of container names
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        }
    }


    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology(Container inServer, Container outServer) throws Exception {

//        prepareJmsServer(CONTAINER1_NAME_NAME);
//        prepareJmsServer(CONTAINER3_NAME);
        prepareJmsServer("full-ha-1"); // use common profile for both node-1 and node-3

        prepareMdbServer(container(2), container(1), inServer, outServer, "full-ha-2");
        prepareMdbServer(container(4), container(3), inServer, outServer, "full-ha-4");

        copyApplicationPropertiesFiles();
    }

    private boolean isServerRemote(String containerName) {
        if (CONTAINER1_NAME.equals(containerName) || CONTAINER3_NAME.equals(containerName)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param profileName Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String profileName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        String messagingGroupSocketBindingName = "messaging-group";

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.addAddressPrefix("profile", profileName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        //            jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        //            jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        jmsAdminOperations.close();


        jmsAdminOperations = container(1).getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();

        //deployDestinations(containerName);
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     * @param profileName Name of the server domain profile
     */
    private void prepareMdbServer(Container container, Container jmsServerName, Container inServer, Container outServer,
            String profileName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-hornetq-ra";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addAddressPrefix("profile", profileName);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));


        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        //            jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        //            jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);

        // enable trace logging
        //            jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        //            jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // both are remote
        if (isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerName.getHostname(), jmsServerName.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerName.getHostname(), jmsServerName.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerName.getHostname(), jmsServerName.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");

//            jmsAdminOperations.close();
//            stopServer(containerName);
//            controller.start(containerName);
//            jmsAdminOperations = this.getJMSOperations(containerName);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        //stopServer(containerName);
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container test container
     */
    private void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container     test container
     * @param serverName    server name of the hornetq server
     */
    private void deployDestinations(Container container, String serverName) {
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.close();
    }


}