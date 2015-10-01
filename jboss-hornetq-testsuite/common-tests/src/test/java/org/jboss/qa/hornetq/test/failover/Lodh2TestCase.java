package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static org.jboss.qa.hornetq.constants.Constants.*;


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
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentMdbWithProperties() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithPropertiesMappedName.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContainerWithReplacementProperties.class);
        logger.info(mdbJar.toString(true));

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
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentWithFilter2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentNonDurableMdbOnTopic() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
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
    public void testSimpleLodh2kill() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    /**
     *
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodh2OOMOnMdbServer() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
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
    public void testLodh3shutdown() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, FAILURE_TYPE.KILL);
    }

    public void testRemoteJcaWithTopic(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isDurable) throws Exception {
        testRemoteJcaWithTopic(failureSequence, failureType, isDurable, container(1), container(3));
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isDurable, Container inServer, Container outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        // jms server
        container(1).start();

        // mdb server
        container(2).start();

        if (!isDurable) {
            container(2).deploy(mdbOnNonDurableTopic);
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

        executeFailureSequence(failureSequence, 3000, failureType);

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
    public void testRemoteJcaInCluster(List<Container> failureSequence, FAILURE_TYPE failureType, boolean isFiltered, Container inServer, Container outServer) throws Exception {

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
        producer1.setMessageVerifier(messageVerifier);
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

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, container(1), container(2),
                container(3), container(4));

        executeFailureSequence(failureSequence, 5000, failureType);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(1), container(2),
                container(3), container(4));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 3000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
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
    public void testAllTransactionsFinishedAfterCleanShutdown() throws Exception {

        Container inServer = container(1);
        Container outServer = container(1);

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

        container(2).deploy(getDeployment1());
        container(4).deploy(getDeployment2());

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 100, 120000, container(1), container(3));

        container(2).stop();
        container(4).stop();

        // check there are still some messages in InQueue
        Assert.assertTrue("MDBs read all messages from InQueue before shutdown. Increase number of messages shutdown happens" +
                " when MDB is processing messages", new JMSTools().waitForMessages(inQueueName, 1, 10000, container(1), container(3)));

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
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
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
    public void testPropertyReplacementWithMappedName() throws Exception {
        testPropertyBasedMdb(mdbWithPropertiesMappedName);
    }

    public void testPropertyBasedMdb(Archive mdbDeployemnt) throws Exception {
        Container inServer = container(1);
        Container outServer = container(1);

        prepareRemoteJcaTopology(inServer, outServer);
        // cluster A

        container(1).start();

        String s = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(container(2).getName())) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        s = containerDef.getContainerProperties().get("javaVmArguments");
                        s = s.concat(" -Djms.queue.InQueue=" + inQueueJndiName);
                        s = s.concat(" -Dpooled.connection.factory.name=java:/JmsXA");
                        s = s.concat(" -Dpooled.connection.factory.name.jms=jms/JmsXA");
                        containerDef.getContainerProperties().put("javaVmArguments", s);
                    }
                }
            }
        }
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        container(2).start(properties);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER / 10);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        container(2).deploy(mdbDeployemnt);

        // set longer timeouts so xarecovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 10000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
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
            }
        } else if (FAILURE_TYPE.KILL.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        } else if (FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.equals(failureType)) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.fail(failureType);
                Thread.sleep(60000);
                container.kill();
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

    public void prepareRemoteJcaTopology(Container inServer, Container outServer) throws Exception {
        if (inServer.getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopologyEAP6(inServer, outServer);
        } else {
            prepareRemoteJcaTopologyEAP7(inServer, outServer);
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP7(container(1));
        prepareMdbServerEAP7(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP7(container(3));
        prepareMdbServerEAP7(container(4), container(3), inServer, outServer);

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Container inServer, Container outServer) throws Exception {


        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), container(1), inServer, outServer);

        prepareJmsServerEAP6(container(3));
        prepareMdbServerEAP6(container(4), container(3), inServer, outServer);

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
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.3";

        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
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

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.3";
        String httpSocketBindingName = "http";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBindingName, null);
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

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "netty-remote";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
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
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container jmsServer, Container inServer, Container outServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.5";

        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "http-connector-to-jms-server";
        String httpSocketBinding = "http";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmHornetRaName = "local-activemq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBinding, null);
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
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
        }
        // local InServer and remote OutServer
        if (!isServerRemote(inServer.getName()) && isServerRemote(outServer.getName())) {
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA", inVmConnectorName);
            jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);
        }

        // remote InServer and local OutServer
        if (isServerRemote(inServer.getName()) && !isServerRemote(outServer.getName())) {

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHornetqPort());
            jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
            jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
            jmsAdminOperations.setJndiNameForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, "java:jboss/DefaultJMSConnectionFactory");

            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // create new in-vm pooled connection factory and configure it as default for inbound communication
            jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(inVmHornetRaName, -1);
        }

        jmsAdminOperations.close();
        container.stop();

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

}