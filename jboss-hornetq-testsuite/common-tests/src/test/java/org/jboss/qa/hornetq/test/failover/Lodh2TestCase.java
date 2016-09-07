package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.MdbListenningOnNonDurableTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbOnlyInboundWithWait;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContainerWithReplacementProperties;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter2;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.specific.Lodh2Prepare;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jboss.qa.hornetq.constants.Constants.FAILURE_TYPE;


/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * This test can work with EAP 5.
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails Test case simulates server crashes and capability to recover with XA transaction.
 * There are 4 servers. First 2 servers are in (jms) cluster and queues/topics are deployed to them. Other 2 servers are connected
 * to first 2 servers through resource adapter. MDB deployed to other 2 servers is resending messaging from one destination to another.
 * During this process one of the servers is killed or cleanly shutdowned.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare("Lodh2Prepare")
public class Lodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh2TestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    public final Archive mdbOnQueue1 = getDeployment1();
    public final Archive mdbOnQueue2 = getDeployment2();
    public final Archive mdbOnQueueWithFilter1 = getDeploymentWithFilter1();
    public final Archive mdbOnQueueWithFilter2 = getDeploymentWithFilter2();
    public final Archive mdbOnNonDurableTopic = getDeploymentNonDurableMdbOnTopic();
    public final Archive mdbWithPropertiesMappedName = getDeploymentMdbWithProperties();
    public final Archive mdbWithPropertiesName = getDeploymentMdbWithPropertiesName();
    public final Archive inboundMdb = getDeploymentMdbInbound();

    public Archive getDeployment1() {
        File propertyFile = new File(container(2).getServerHome() + File.separator + "mdb1.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(1).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentMdbInbound() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-inbound.jar");
        mdbJar.addClasses(MdbOnlyInboundWithWait.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeployment2() {
        File propertyFile = new File(container(4).getServerHome() + File.separator + "mdb2.properties");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(propertyFile);
        } catch (FileNotFoundException e) {
            logger.error("Problem during creating PrintWriter: ", e);
        }
        writer.println("remote-jms-server=" + container(3).getHostname());
        writer.close();
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentMdbWithProperties() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesMappedName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        logger.info(mdbJar.toString(true));
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());

        //          Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdbWithPropertyReplacements.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentMdbWithPropertiesName() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementPropertiesName.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));

        //          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/" + mdbWithPropertiesName + ".jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public Archive getDeploymentWithFilter1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentWithFilter2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentNonDurableMdbOnTopic() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /////////////////////////////// START - Local -> Remote

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue OutQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue InQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed outQueue</li>
     * <li>start cluster two containing node 2 and 4 with deployed inQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "2"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testSimpleLodh2killJmsLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL, false, container(2), container(1));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue OutQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue InQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed outQueue</li>
     * <li>start cluster two containing node 2 and 4 with deployed inQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "2"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testSimpleLodh2killMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL, false, container(2), container(1));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue OutQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue InQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed outQueue</li>
     * <li>start cluster two containing node 2 and 4 with deployed inQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>shutdown node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "2"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testSimpleLodh2ShutdownMdbLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN, false, container(2), container(1));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue OutQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue InQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed outQueue</li>
     * <li>start cluster two containing node 2 and 4 with deployed inQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>shutdown node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "2"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testSimpleLodh2ShutdownJmsLocalRemote() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN, false, container(2), container(1));
    }

    /////////////////////////////// END - Local -> Remote

    /////////////////////////////// START - Remote -> Local

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue InQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue OutQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue </li>
     * <li>start cluster two containing node 2 and 4 with deployed outQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "2")
    })
    public void testSimpleLodh2killJmsRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL, false, container(1), container(2));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue InQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue OutQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue </li>
     * <li>start cluster two containing node 2 and 4 with deployed outQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "2")
    })
    public void testSimpleLodh2killMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL, false, container(1), container(2));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue InQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue OutQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue </li>
     * <li>start cluster two containing node 2 and 4 with deployed outQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>shutdown node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "2")
    })
    public void testSimpleLodh2ShutdownJmsRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN, false, container(1), container(2));
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queue InQueue is deployed to both of them.
     * Cluster B with nodes 2 and 4 is started and queue OutQueue is deployed to both of them. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDBs to node-2 and node-4 which read messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue </li>
     * <li>start cluster two containing node 2 and 4 with deployed outQueue</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "2")
    })
    public void testSimpleLodh2ShutdownMdbRemoteLocal() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN, false, container(1), container(2));
    }
    /////////////////////////////// START - Remote -> Local


    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testSimpleLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cause OutOfMemoryError on node 2,
     * restart it and then repeat for node 4. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>cause OOM node-2 and restart it while MDB is processing messages</li>
     * <li>cause OOM node-4 and restart it while MDB is processing messages</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh2OOMOnMdbServer() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cause OutOfMemoryError on node 1,
     * restart it and then repeat for node 3. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>cause OOM node-1 and restart it while MDB is processing messages</li>
     * <li>cause OOM node-3 and restart it while MDB is processing messages</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh2OOMOnJmsServer() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Each message has set property color to RED or GREEN.
     * Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). MDB on node-2 reads only RED messages and on node-4 only GREEN messages.
     * When MDBs are processing messages, kill node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue, each message has set property color to RED or GREEN</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDB to node-2 which reads only RED messages and sends them to outQueue</li>
     * <li>deploy MDB to node-4 which reads only GREEN messages and sends them to outQueue</li>
     * <li>kill node-2 while MDB is processing messages</li>
     * <li>start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testSimpleLodh2killWithFilters() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL, true);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testSimpleLodh3kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 1 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>shutdown node-1 while MDB is processing messages</li>
     * <li>start node-1</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testSimpleLodh3Shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill and restart nodes in following sequence 2,2,4,2,4.
     * Wait until all messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to InQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>kill and start following nodes in this sequence: 2,2,4,2,4</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and topic InTopic and queue OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start publisher which sends 5000 messages
     * (mix of small and large messages) to InTopic. Once producer finishes, deploy MDB which creates non-durable subscription on InTopic and sends messages
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill node 2 and start again. Wait until all
     * messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inTopic and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inTopic</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which create non-durable subscription on inTopic and sends messages to
     * outQueue in XA transactions</li>
     * <li>kill and start node-2</li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh2killWithTempTopic() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, FAILURE_TYPE.KILL, false);

    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown and restart nodes in following sequence 2,2,4,2,4.
     * Wait until all messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to
     * outQueue in XA transactions</li>
     * <li>cleanly shutdown and start following nodes in this sequence: 2,2,4,2,4 </li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh2shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown and restart nodes in following sequence 1,2.
     * Wait until all messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to
     * outQueue in XA transactions</li>
     * <li>cleanly shutdown and start following nodes in this sequence: 1,2 </li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testShutdownOfJmsServers() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, kill and restart nodes in following sequence 1,3,1,3,1.
     * Wait until all messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to
     * outQueue in XA transactions</li>
     * <li>kill and start following nodes in this sequence: 1,3,1,3,1 </li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh3kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown and restart nodes in following sequence 1,3,1,3,1.
     * Wait until all messages are processed and consume messages from OutQueue.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which read messages from inQueue and sends them to
     * outQueue in XA transactions</li>
     * <li>cleanly shutdown and start following nodes in this sequence: 1,3,1,3,1 </li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "3")
    })
    public void testLodh3shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isDurable) throws Exception {

        // jms server
        container(1).start();

        // mdb server
        container(2).start();

        if (!isDurable) {
            container(2).deploy(mdbOnNonDurableTopic);
            Thread.sleep(5000);
        }

        PublisherTransAck producer1 = new PublisherTransAck(container(1),
                PrepareBase.IN_TOPIC_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER, "clientId-myPublisher");
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

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 10, 120000, container(1));

        executeFailureSequence(failureSequence, 3000, failureType);

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1));

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 3000, 10, 10);
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
            container(2).undeploy(mdbOnNonDurableTopic);
        }


        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(List<Container> failureSequence, FAILURE_TYPE failureType) throws Exception {
        testRemoteJcaInCluster(failureSequence, failureType, false);
    }

    /**
     * For remote inQueue and remote OutQueue
     *
     * @param failureSequence failure sequence
     * @param failureType     failure type
     * @param isFiltered      filtered
     */
    public void testRemoteJcaInCluster(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isFiltered) throws Exception {
        testRemoteJcaInCluster(failureSequence, failureType, isFiltered, container(1), container(3));
    }


    /**
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Artemis Log if LargeMessageControllerImpl.popPacket called - invoked",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    action = "System.out.println(\"org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl.poppacket - invoked\")"),
            @BMRule(name = "Artemis Log if LargeMessageControllerImpl.popPacket called - exit",
                    targetClass = "org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"org.apache.activemq.artemis.core.client.impl.LargeMessageControllerImpl.poppacket. - exit\")"),
            @BMRule(name = "Hornetq Log if LargeMessageControllerImpl.popPacket called - invoked",
                    targetClass = "org.hornetq.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    action = "System.out.println(\"org.hornetq.core.client.impl.LargeMessageControllerImpl.poppacket - invoked\")"),
            @BMRule(name = "Hornetq Log if LargeMessageControllerImpl.popPacket called - exit",
                    targetClass = "org.hornetq.core.client.impl.LargeMessageControllerImpl",
                    targetMethod = "popPacket",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"org.hornetq.core.client.impl.LargeMessageControllerImpl.poppacket() - exit\")"),
    })
    public void testRemoteJcaInCluster(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isFiltered, Container inServer, Container outServer) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        installBytemanRules(container(2));
        installBytemanRules(container(4));

        ProducerTransAck producer1 = new ProducerTransAck(inServer, PrepareBase.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
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
            container(2).deploy(mdbOnQueueWithFilter1);
            container(4).deploy(mdbOnQueueWithFilter2);
        } else {
            container(2).deploy(mdbOnQueue1);
            container(4).deploy(mdbOnQueue2);
        }

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, container(1), container(2),
                container(3), container(4));

        executeFailureSequence(failureSequence, 5000, failureType);

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 400000, container(1), container(2),
                container(3), container(4));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(2));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(4));

        // wait some time so recovered rollbacked TXs have some time to be processed
        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, NUMBER_OF_MESSAGES_PER_PRODUCER, 60000, container(1), container(2),
                container(3), container(4));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, PrepareBase.OUT_QUEUE_JNDI, 30000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        printThreadDumpsOfAllServers();

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        if (isFiltered) {
            container(2).undeploy(mdbOnQueueWithFilter1);
            container(4).undeploy(mdbOnQueueWithFilter2);
        } else {
            container(2).undeploy(mdbOnQueue1.getName());
            container(4).undeploy(mdbOnQueue2.getName());
        }

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "2")
    })
    public void testRemoteJcaInboundOnly() throws Exception {

        int numberOfMessages = 1;

        Container jmsServer = container(1);
        Container mdbServer = container(2);

        jmsServer.start();
        mdbServer.start();

        ProducerTransAck producer1 = new ProducerTransAck(jmsServer, PrepareBase.IN_QUEUE_JNDI, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        mdbServer.deploy(inboundMdb);
        Thread.sleep(10000);
        mdbServer.kill();
        mdbServer.start();

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(jmsServer, PrepareBase.IN_QUEUE_NAME, 0, TimeUnit.MINUTES.toMillis(5));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, jmsServer);

        logger.info("Number of sent messages: " + (producer1.getListOfSentMessages().size()
                + ", Producer to jms1 server sent: " + producer1.getListOfSentMessages().size() + " messages"));

        long countMessages = new JMSTools().countMessages(PrepareBase.IN_QUEUE_NAME, jmsServer);
        Assert.assertTrue("Number of messages in InQueue must be 0 but is: " + countMessages, countMessages == 0);

        mdbServer.undeploy(inboundMdb);
        mdbServer.stop();
        jmsServer.stop();
    }

    private void printThreadDumpsOfAllServers() throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));
    }

    private void installBytemanRules(Container container)  {
        RuleInstaller.installRule(this.getClass(), container.getHostname(), container.getBytemanPort());
    }


    /**
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started and queues InQueue and OutQueue are deployed to both of them.
     * Cluster B with nodes 2 and 4 is started. Start producer which sends 5000 messages
     * (mix of small and large messages) to InQueue. Once producer finishes, deploy MDB which reads messages from InQueue and sends
     * to OutQueue (in XA transaction) to cluster B (node 2,4). When MDBs are processing messages, cleanly shutdown node 2 and 4.
     * @tpProcedure <ul>
     * <li>start cluster one containing node 1 and 3 with deployed inQueue and outQueue</li>
     * <li>start cluster two containing node 2 and 4</li>
     * <li>producer sends 5000 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDBs to node-2 and node-4 which  read messages from inQueue and sends them to
     * outQueue in XA transactions</li>
     * <li>cleanly shutdown node 2 and 4/li>
     * <li>wait until all messages are processed</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit Verify there are no unfished XA transactions.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        Container inServer = container(1);
        Container outServer = container(1);

        int numberOfMessages = NUMBER_OF_MESSAGES_PER_PRODUCER;

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, PrepareBase.IN_QUEUE_JNDI, numberOfMessages);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        container(2).deploy(getDeployment1());
        container(4).deploy(getDeployment2());

        new JMSTools().waitForMessages(PrepareBase.OUT_QUEUE_NAME, numberOfMessages / 100, 120000, container(1), container(3));

        container(2).stop();
        container(4).stop();

        // check there are still some messages in InQueue
        Assert.assertTrue("MDBs read all messages from InQueue before shutdown. Increase number of messages shutdown happens" +
                " when MDB is processing messages", new JMSTools().waitForMessages(PrepareBase.IN_QUEUE_NAME, 1, 10000, container(1), container(3)));

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
     * @tpTestDetails There are 2 nodes. node 1 and 2 are started and queues InQueue and OutQueue are deployed to node 1.
     * Start producer which sends 500 messages (mix of small and large messages) to InQueue.
     * Once producer finishes, deploy MDB to node 2 which reads messages from InQueue and sends
     * to OutQueue (in XA transaction). MDB is using property replacement in @Resource(lookup=${property}) and activation config properties.
     * @tpProcedure <ul>
     * <li>start node 1 with deployed inQueue and outQueue</li>
     * <li>start node 2 without destinations</li>
     * <li>producer sends 500 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDB which is using property replacement in @Resource(name=${property}) and activation config properties
     * and which reads messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit number of sent and received messages is the same
     * IGNORED DUE TO: https://issues.jboss.org/browse/WFLY-4703
     */
    @Ignore
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testPropertyReplacementWithName() throws Exception {
        testPropertyBasedMdb(mdbWithPropertiesName);
    }

    /**
     * @tpTestDetails There are 2 nodes. node 1 and 2 are started and queues InQueue and OutQueue are deployed to node 1.
     * Start producer which sends 500 messages (mix of small and large messages) to InQueue.
     * Once producer finishes, deploy MDB to node 2 which reads messages from InQueue and sends
     * to OutQueue (in XA transaction). MDB is using property replacement in @Resource(mappedName=${property}) and activation config properties.
     * @tpProcedure <ul>
     * <li>start node 1 with deployed inQueue and outQueue</li>
     * <li>start node 2 without destinations</li>
     * <li>producer sends 500 small and large messages to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>deploy MDB which is using property replacement in @Resource(mappedName=${property}) and activation config properties
     * and which reads messages from inQueue and sends them to outQueue in XA transactions</li>
     * <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit number of sent and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @Prepare(params = {
            @Param(name = Lodh2Prepare.IN_SERVER, value = "1"),
            @Param(name = Lodh2Prepare.OUT_SERVER, value = "1")
    })
    public void testPropertyReplacementWithMappedName() throws Exception {
        testPropertyBasedMdb(mdbWithPropertiesMappedName);
    }

    public void testPropertyBasedMdb(Archive mdbDeployemnt) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        Container inServer = container(1);
        Container outServer = container(1);

        // cluster A

        container(1).start();

        String s = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(container(2).getName())) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        s = containerDef.getContainerProperties().get("javaVmArguments");
                        s = s.concat(" -Djms.queue.InQueue=" + PrepareBase.IN_QUEUE_JNDI);
                        s = s.concat(" -Dpooled.connection.factory.name=java:/JmsXA");
                        s = s.concat(" -Dpooled.connection.factory.name.jms=java:/JmsXA");
                        containerDef.getContainerProperties().put("javaVmArguments", s);
                    }
                }
            }
        }
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        container(2).start(properties, 180000);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareBase.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER / 10);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.addMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        container(2).deploy(mdbDeployemnt);

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, PrepareBase.OUT_QUEUE_JNDI, 10000, 10, 10);
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

        container(2).undeploy(mdbDeployemnt);

        container(2).stop();
        container(1).stop();
    }

    /**
     * Executes kill sequence.
     *
     * @param failureSequence  list of containers
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, FAILURE_TYPE failureType) throws InterruptedException {

        if (FAILURE_TYPE.SHUTDOWN.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        } else if (FAILURE_TYPE.KILL.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        } else if (FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.fail(failureType);
                Thread.sleep(300000);
                container.kill();
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
                installBytemanRules(container);
            }
        }
    }

}
