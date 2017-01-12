package org.jboss.qa.hornetq.test.failover;

import category.XaTransactions;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SoakPublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.MdbListenningOnNonDurableTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithFilter2;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca in cluster and have remote inqueue and outqueue.
 * <p/>
 * This test can work with EAP 5.
 *
 * @author mnovak@redhat.com
 * @author msvehla@redhat.com
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-xa-transactions/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails Test case simulates server crashes and capability to
 * recover with XA transaction. There are 4 servers. First 2 servers are in
 * (jms) cluster and queues/topics are deployed to them. Other 2 servers are
 * connected to first 2 servers through resource adapter. MDB deployed to other
 * 2 servers is resending messaging from one destination to another. During this
 * some of the servers are killed.
 */
@Category(XaTransactions.class)
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare("BytemanLodh2Prepare")
public class BytemanLodh2TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(BytemanLodh2TestCase.class);

    private static final int NUMBER_OF_DESTINATIONS = 2;

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int SHORT_TEST_NUMBER_OF_MESSAGES = 2000;

    private static final int LODH2_NUMBER_OF_MESSAGES = 5000;

    // LODH3 waits for all messages to get generated before the failover test starts, so it requires more messages
    // to last through all 5 server kills in long test scenario
    private static final int LODH3_NUMBER_OF_MESSAGES = 20000;

    public final Archive mdb1WithFilter = getDeploymentWithFilter1();
    public final Archive mdb2WithFilter = getDeploymentWithFilter2();
    public final Archive nonDurableMdbOnTopic = getDeploymentNonDurableMdbOnTopic();

    public final Archive mdbOnQueue1 = getDeployment1();
    public final Archive mdbOnQueue2 = getDeployment2();

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
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
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
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(3));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentWithFilter1() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getDeploymentWithFilter2() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(3));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2WithFilter.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithFilter2.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getDeploymentNonDurableMdbOnTopic() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(2));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "nonDurableMdbOnTopic.jar");
        mdbJar.addClasses(MdbListenningOnNonDurableTopic.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 with deployed MDB is
     * killed on transaction commit. Restart Node 2. Read messages from
     * OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction commit", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction commit", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testSimpleLodh2KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 with deployed MDB is
     * killed on transaction prepare. Restart Node 2. Read messages from
     * OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction prepare", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction prepare", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testSimpleLodh2KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 with deployed MDB is
     * killed on transaction commit. Restart Node 2. Read messages from
     * OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq Kill in MDB server on transaction commit", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis Kill in MDB server on transaction commit", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testSimpleLodh2KillWithFiltersOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false, true);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 with deployed MDB is
     * killed on transaction prepare. Restart Node 2. Read messages from
     * OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction prepare", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction prepare", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testSimpleLodh2KillWithFiltersOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false, true);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Once producer finishes, deploy MDBs (nodes 2, 4) which read
     * messages from InQueue and sends them to OutQueue (in XA transaction).
     * Node 1 is killed on transaction commit. Restart Node 1. Read messages
     * from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq JMS server kill on client transaction commit", targetClass = "org.hornetq.core.transaction.Transaction",
                targetMethod = "commit", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
        @BMRule(name = "Artemis JMS server kill on client transaction commit", targetClass = "org.apache.activemq.artemis.core.transaction.Transaction",
                targetMethod = "commit", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    })
    public void testSimpleLodh3KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 2000 messages (mix of small and large messages) to
     * InQueue. Once producer finishes, deploy MDBs (nodes 2, 4) which read
     * messages from InQueue and sends them to OutQueue (in XA transaction).
     * Node 1 is killed on transaction prepare. Restart Node 1. Read messages
     * from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq server kill on client transaction prepare", targetClass = "org.hornetq.core.transaction.Transaction",
                targetMethod = "prepare", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
        @BMRule(name = "Artemis server kill on client transaction prepare", targetClass = "org.apache.activemq.artemis.core.transaction.Transaction",
                targetMethod = "prepare", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    })
    public void testSimpleLodh3KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        testRemoteJcaInCluster(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 5000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 and Node 4 with
     * deployed MDBs are killed on transaction commit. Restart nodes and read
     * messages from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction commit", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction commit", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testLodh2KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 5000 messages (mix of small and large messages) to
     * InQueue. Deploy MDBs (nodes 2, 4) which read messages from InQueue and
     * sends them to OutQueue (in XA transaction). Node 2 and Node 4 with
     * deployed MDBs are killed on transaction prepare. Restart nodes and read
     * messages from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction prepare", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction prepare", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testLodh2KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        failureSequence.add(container(4));
        testRemoteJcaInCluster(failureSequence, LODH2_NUMBER_OF_MESSAGES, false);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started,
     * nondurable topic InTopic and queue OutQueue are deployed to both of them.
     * Start publisher which publishes 2000 messages (mix of small and large
     * messages) to InTopic. Deploy MDBs (nodes 2, 4) which read messages from
     * InTopic and sends them to OutQueue (in XA transaction). Node 2 with
     * deployed MDB is killed on transaction commit. Restart Node 2. Read
     * messages from OutQueue.
     *
     * @tpPassCrit Number of received messages is not same as number of send messages. Receiver doesnt receive all messages.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction commit", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction commit", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "commit", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testLodh2KillWithTempTopicOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started,
     * nondurable topic InTopic and queue OutQueue are deployed to both of them.
     * Start publisher which publishes 2000 messages (mix of small and large
     * messages) to InTopic. Deploy MDBs (nodes 2, 4) which read messages from
     * InTopic and sends them to OutQueue (in XA transaction). Node 2 with
     * deployed MDB is killed on transaction prepare. Restart Node 2. Read
     * messages from OutQueue.
     *
     * @tpPassCrit Number of received messages is not same as number of send messages. Receiver doesnt receive all messages.
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq MDB server kill on transaction prepare", targetClass = "org.hornetq.ra.HornetQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()"),
        @BMRule(name = "Artemis MDB server kill on transaction prepare", targetClass = "org.apache.activemq.artemis.ra.ActiveMQRAXAResource",
                targetMethod = "prepare", action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM()")
    })
    public void testLodh2KillWithTempTopicOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(2));
        testRemoteJcaWithTopic(failureSequence, SHORT_TEST_NUMBER_OF_MESSAGES, false);

    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 20000 messages (mix of small and large messages) to
     * InQueue. Once producer finishes, deploy MDBs (nodes 2, 4) which read
     * messages from InQueue and sends them to OutQueue (in XA transaction).
     * Node 1 and Node 3 are killed on transaction commit. Restart nodes and
     * read messages from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq server kill on client transaction commit", targetClass = "org.hornetq.core.transaction.Transaction",
                targetMethod = "commit", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
        @BMRule(name = "Artemis server kill on client transaction commit", targetClass = "org.apache.activemq.artemis.core.transaction.Transaction",
                targetMethod = "commit", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    })
    public void testLodh3KillOnTransactionCommit() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /**
     * Kills mdbs servers.
     *
     * @tpTestDetails There are 4 nodes. Cluster A with node 1 and 3 is started
     * and queues InQueue and OutQueue are deployed to both of them. Start
     * producer which sends 20000 messages (mix of small and large messages) to
     * InQueue. Once producer finishes, deploy MDBs (nodes 2, 4) which read
     * messages from InQueue and sends them to OutQueue (in XA transaction).
     * Node 1 and Node 3 are killed on transaction prepare. Restart nodes and
     * read messages from OutQueue.
     *
     * @tpPassCrit Number of send and received messages is the same
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    @BMRules({
        @BMRule(name = "Hornetq server kill on client transaction prepare", targetClass = "org.hornetq.core.transaction.Transaction",
                targetMethod = "prepare", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();"),
        @BMRule(name = "Artemis server kill on client transaction prepare", targetClass = "org.apache.activemq.artemis.core.transaction.Transaction",
                targetMethod = "prepare", isInterface = true, action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    })
    public void testLodh3KillOnTransactionPrepare() throws Exception {
        List<Container> failureSequence = new ArrayList<Container>();
        failureSequence.add(container(1));
        failureSequence.add(container(3));
        testRemoteJcaInCluster(failureSequence, LODH3_NUMBER_OF_MESSAGES, true);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaWithTopic(final List<Container> failureSequence, final int numberOfMessages,
            final boolean isDurable) throws Exception {

        // jms server
        container(1).start();
        // mdb server
        container(2).start();

        if (!isDurable) {
            container(2).deploy(nonDurableMdbOnTopic);
            Thread.sleep(5000);
        }

        SoakPublisherClientAck producer1 = new SoakPublisherClientAck(container(1), PrepareConstants.IN_TOPIC_JNDI, numberOfMessages,
                "clientId-myPublisher");
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.setTimeout(0);
        producer1.start();

        // deploy mdbs
        if (isDurable) {
            throw new UnsupportedOperationException("This was not yet implemented. Use Mdb on durable topic to do so.");
        }

        executeFailureSequence(failureSequence, 30000);

        // Wait to send and receive some messages
        Thread.sleep(60 * 1000);

        // set longer timeouts so xarecovery is done at least once
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 300000, 10, 10);

        receiver1.start();

        producer1.join();
        receiver1.join();

        logger.info("Number of sent messages: "
                + (producer1.getMessages() + ", Producer to jms1 server sent: " + producer1.getMessages() + " messages"));

        logger.info("Number of received messages: "
                + (receiver1.getCount() + ", Consumer from jms1 server received: " + receiver1.getCount() + " messages"));

        printThreadDumpsOfAllServers(true);

        if (isDurable) {
            Assert.assertEquals("There is different number of sent and received messages.", producer1.getMessages(),
                    receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.", receiver1.getCount() > 0);

        } else {

            Assert.assertTrue("There SHOULD be different number of sent and received messages.",
                    producer1.getMessages() > receiver1.getCount());
            Assert.assertTrue("Receivers did not get any messages.", receiver1.getCount() > 0);
            container(2).undeploy(nonDurableMdbOnTopic);
        }

        container(2).stop();
        container(1).stop();

    }

    public void testRemoteJcaInCluster(final List<Container> failureSequence, final int numberOfMessages,
            final boolean waitForProducer) throws Exception {

        testRemoteJcaInCluster(failureSequence, numberOfMessages, waitForProducer, false);
    }

    /**
     * @throws Exception
     */
    public void testRemoteJcaInCluster(final List<Container> failureSequence, final int numberOfMessages,
            final boolean waitForProducer, final boolean isFiltered) throws Exception {

        // cluster A
        container(1).start();
        container(3).start();
        // cluster B
        container(2).start();
        container(4).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);

        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(builder);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producer1.addMessageVerifier(messageVerifier);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.start();

        if (waitForProducer) {
            producer1.join();
        }

        // deploy mdbs
        if (isFiltered) {
            container(2).deploy(mdb1WithFilter);
            container(4).deploy(mdb2WithFilter);
        } else {
            container(2).deploy(mdbOnQueue1);
            container(4).deploy(mdbOnQueue2);
        }

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.OUT_QUEUE_NAME, numberOfMessages / 20, 300000, container(1), container(3)));

        if (waitForProducer) {
            executeFailureSequence(failureSequence, 15000);
        } else {
            executeFailureSequence(failureSequence, 30000);
        }

        producer1.join();
        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.OUT_QUEUE_NAME, numberOfMessages, 420000, container(1), container(3)));

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(3), PrepareConstants.OUT_QUEUE_JNDI, 30000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);

        receiver1.start();
        receiver1.join();

        logger.info("Number of messages in OutQueue on nodes:" + JMSTools.countMessages(PrepareConstants.OUT_QUEUE_NAME, container(1),container(3)));

        logger.info("Number of sent messages: "
                + (producer1.getListOfSentMessages().size() + ", Producer to jms1 server sent: "
                + producer1.getListOfSentMessages().size() + " messages"));

        logger.info("Number of received messages: "
                + (receiver1.getListOfReceivedMessages().size() + ", Consumer from jms1 server received: "
                + receiver1.getListOfReceivedMessages().size() + " messages"));

        printThreadDumpsOfAllServers(false);

        Assert.assertTrue("There are lost ", messageVerifier.verifyMessages());

        Assert.assertEquals("There is different number of sent and received messages.", producer1.getListOfSentMessages()
                .size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.", receiver1.getCount() > 0);

        if (isFiltered) {
            container(2).undeploy(mdb1WithFilter);
            container(4).undeploy(mdb2WithFilter);
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
     * Executes kill sequence.
     *
     * @param failureSequence map Contanier -> ContainerIP
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills) throws Exception {

        for (Container container : failureSequence) {

            // String containerHostname = CONTAINER_BYTEMAN_MAP.get(containerName).containerHostname;
            // int bytemanPort = CONTAINER_BYTEMAN_MAP.get(containerName).bytemanPort;
            // HornetQCallsTracking.installTrackingRules(containerHostname, bytemanPort);
            RuleInstaller.installRule(this.getClass(), container);
            container.waitForKill();
            logger.info("Starting server: " + container.getName());
            container.start();
            logger.info("Server " + container.getName() + " -- STARTED");
            //verify server is really running
            CheckServerAvailableUtils.waitHornetQToAlive(container, 10000);
            Thread.sleep(timeBetweenKills);
        }
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    @Override
    public void stopAllServers() {
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
    }

    private void printThreadDumpsOfAllServers(boolean isTestWithTopic) throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        if (!isTestWithTopic) {
            ContainerUtils.printThreadDump(container(3));
            ContainerUtils.printThreadDump(container(4));
        }
    }

}
