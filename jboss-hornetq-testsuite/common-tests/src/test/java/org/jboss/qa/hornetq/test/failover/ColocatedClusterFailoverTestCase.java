package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.*;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH SHARED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails HornetQ journal is located on GFS2 on SAN where journal type ASYNCIO must be used.
 * Or on NSFv4 where journal type is ASYNCIO or NIO.
 */
@RunWith(Arquillian.class)
public class ColocatedClusterFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ColocatedClusterFailoverTestCase.class);
    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;
    protected static final int BYTEMAN_PORT = 9091;
    protected static final String CLUSTER_PASSWORD = "CHANGE_ME!!!";

    private final Archive mdb1 = createLodh1Deployment();
    private final Archive mdb2 = createLodh1Deployment2();

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";

    private String journalType = "ASYNCIO";

    static String inQueueName = "InQueue";
    static String inQueue = "jms/queue/" + inQueueName;
    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueue = "jms/queue/" + outQueueName;

    String clusterConnectionName = "my-cluster";

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(40, 200);
    //    MessageBuilder messageBuilder = new TextMessageBuilder(1024);
    Clients clients = null;


    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test fail back
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback) throws Exception {
        testFailover(acknowledge, failback, false);
    }

    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, false);
    }

    public void testFailoverWithShutDown(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, true);
    }

    @Before
    @After
    public void makeSureAllClientsAreDead() throws InterruptedException {
        journalType = "ASYNCIO";
        if (clients != null) {
            clients.stopClients();
            ClientUtils.waitForClientsToFinish(clients, 600000);
        }
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Kill server when a number of messages were received HQ",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Kill server when a number of messages were received AMQ",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        testFail(acknowledge, failback, topic, shutdown, Constants.CONNECTOR_TYPE.NETTY_NIO);
    }

    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareColocatedTopologyInCluster(connectorType);

        container(2).start();

        container(1).start();

        // give some time for servers to find each other
        Thread.sleep(10000);

        messageBuilder.setAddDuplicatedHeader(true);

        clients = createClients(acknowledge, topic, messageBuilder);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(100);

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 120, 300000);

        logger.info("########################################");
        logger.info("kill - first server");
        logger.info("########################################");
        if (shutdown) {
            container(1).stop();
        } else {
            // install rule to first server
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());
            container(1).waitForKill();
        }

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 500, 300000);
        Assert.assertTrue("Backup on second server did not start - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(),
                container(2).getHornetqBackupPort(), 300000));

        if (failback) {
            logger.info("########################################");
            logger.info("failback - Start first server again ");
            logger.info("########################################");
            container(1).start();
            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 300000);
//            Thread.sleep(10000);
//            logger.info("########################################");
//            logger.info("failback - Stop second server to be sure that failback occurred");
//            logger.info("########################################");
//            stopServer(CONTAINER2_NAME);
        }
        Thread.sleep(20000); // give some time to org.jboss.qa.hornetq.apps.clients

        logger.info("########################################");
        logger.info("Stop org.jboss.qa.hornetq.apps.clients - this will stop producers");
        logger.info("########################################");
        clients.stopClients();

        logger.info("########################################");
        logger.info("Wait for end of all org.jboss.qa.hornetq.apps.clients.");
        logger.info("########################################");
        ClientUtils.waitForClientsToFinish(clients);
        logger.info("########################################");
        logger.info("All org.jboss.qa.hornetq.apps.clients ended/finished.");
        logger.info("########################################");


        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    /**
     * @tpTestDetails This scenario tests failover and failback of live server with deployed MDB after kill.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated topology with destinations</li>
     * <li>start producer and send messages to inQueue</li>
     * <li>deploy MDBs to both nodes</li>
     * <li>wait until 10% of messages are processed</li>
     * <li>kill node-2</li>
     * <li>wait until 50% of messages are processed</li>
     * <li>check if backup server on node-1 comes alive</li>
     * <li>start node-2 again</li>
     * <li>check failback of live server on node-2</li>
     * <li>when all messages are processed, consume all messages form outQueue on node-1</li>
     * </ul>
     * @tpPassCrit backup comes alive after node-2 is killed, live comes alive win node-2 is started, receiver
     * gets all messages which was sent
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackWithMdbsKill() throws Exception {

        testFailWithMdbs(false);
    }

    /**
     * @tpTestDetails This scenario tests failover and failback of live server with deployed MDB after clean shutdown.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated topology with destinations</li>
     * <li>start producer and send messages to inQueue</li>
     * <li>deploy MDBs to both nodes</li>
     * <li>wait until 10% of messages are processed</li>
     * <li>shut down node-2</li>
     * <li>wait until 50% of messages are processed</li>
     * <li>check if backup server on node-1 comes alive</li>
     * <li>start node-2 again</li>
     * <li>check failback of live server on node-2</li>
     * <li>when all messages are processed, consume all messages form outQueue on node-1</li>
     * </ul>
     * @tpPassCrit backup comes alive after node-2 is killed, live comes alive win node-2 is started, receiver
     * gets all messages which was sent
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackWithMdbsShutdown() throws Exception {

        testFailWithMdbs(true);
    }

    public void testFailWithMdbs(boolean shutdown) throws Exception {

        prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE.NETTY_NIO);

        container(2).start();

        container(1).start();

        // give some time for servers to find each other
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 2000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(2).deploy(mdb2);
        container(1).deploy(mdb1);

        // when 1/3 is processed then kill/shut down 2nd server
        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 300000, container(1), container(2));

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown) {
            container(2).stop();
        } else {
            container(2).kill();
        }

        // when 1/2 is processed then start 2nd server
        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 2, 120000, container(1));

        Assert.assertTrue("Backup on first server did not start - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(),
                Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7 + container(1).getPortOffset(), 300000));
        Thread.sleep(10000);

        logger.info("########################################");
        logger.info("Start again - second server");
        logger.info("########################################");
        container(2).start();
        logger.info("########################################");
        logger.info("Second server started");
        logger.info("########################################");

        logger.warn("Wait some time (5 min) to give chance live in container 2 to come alive after failback.");
        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 300000);
        logger.warn("Live in container is alive.");
        new JMSTools().waitForMessages(outQueueName, numberOfMessages, 120000, container(1), container(2));

        logger.info("Get information about transactions from HQ:");
        long timeout = 300000;
        long startTime = System.currentTimeMillis();
        int numberOfPreparedTransaction = 100;
        JMSOperations jmsOperations = container(1).getJmsOperations();
        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {
            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
            Thread.sleep(1000);
        }
        jmsOperations.close();

        logger.info("Get information about transactions from HQ:");
        numberOfPreparedTransaction = 100;
        jmsOperations = container(2).getJmsOperations();
        while (numberOfPreparedTransaction > 0 && System.currentTimeMillis() - startTime < timeout) {
            numberOfPreparedTransaction = jmsOperations.getNumberOfPreparedTransaction();
            Thread.sleep(1000);
        }
        jmsOperations.close();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueue, 5000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).undeploy(mdb1);
        container(2).undeploy(mdb2);

        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails This scenario tests "only-once delivery" queue pattern while messages are loadbalanced in cluster
     * and one of nodes in cluster is killed and then comes alive.
     * @tpProcedure <ul>
     * <li>start two nodes in cluster with destinations</li>
     * <li>start producer and send normal messages with _HQ_DUPL_ID to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>kill node-2 and start it again</li>
     * <li>Start consumer and consume messages from inQueue on node-1</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKillInClusterSmallMessages() throws Exception {
        testFailInCluster(false, new TextMessageBuilder(10));
    }

    /**
     * @tpTestDetails This scenario tests "only-once delivery" queue pattern while messages are loadbalanced in cluster
     * and one of nodes in cluster is shut down and then comes alive.
     * @tpProcedure <ul>
     * <li>start two nodes in cluster with destinations</li>
     * <li>start producer and send normal messages with _HQ_DUPL_ID to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>shut down node-2 and start it again</li>
     * <li>Start consumer and consume messages from inQueue on node-1</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testShutdownInClusterSmallMessages() throws Exception {
        testFailInCluster(true, new TextMessageBuilder(10));
    }

    /**
     * @tpTestDetails This scenario tests "only-once delivery" queue pattern while large messages are loadbalanced in cluster
     * and one of nodes in cluster is killed and then comes alive.
     * @tpProcedure <ul>
     * <li>start two nodes in cluster with destinations</li>
     * <li>start producer and send large messages with _HQ_DUPL_ID to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>kill node-2 and start it again</li>
     * <li>Start consumer and consume messages from inQueue on node-1</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testKillInClusterLargeMessages() throws Exception {
        testFailInCluster(false, new ClientMixMessageBuilder(120, 200));
    }

    /**
     * @tpTestDetails This scenario tests "only-once delivery" queue pattern while large messages are loadbalanced in cluster
     * and one of nodes in cluster is shut down and then comes alive.
     * @tpProcedure <ul>
     * <li>start two nodes in cluster with destinations</li>
     * <li>start producer and send large messages with _HQ_DUPL_ID to inQueue</li>
     * <li>wait for producer to finish</li>
     * <li>shut down node-2 and start it again</li>
     * <li>Start consumer and consume messages from inQueue on node-1</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testShutdownInClusterLargeMessages() throws Exception {
        testFailInCluster(true, new ClientMixMessageBuilder(120, 200));
    }

    public void testFailInCluster(boolean shutdown, MessageBuilder messageBuilder) throws Exception {

        prepareLiveServerEAP6(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A);
        prepareLiveServerEAP6(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_B);

        container(2).start();
        container(1).start();

        // give some time for servers to find each other
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueue, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        logger.info("########################################");
        logger.info("kill - second server");
        logger.info("########################################");

        if (shutdown) {
            container(2).stop();
        } else {
            container(2).kill();
        }

        logger.info("########################################");
        logger.info("Start again - second server");
        logger.info("########################################");
        container(2).start();
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000);
        logger.info("########################################");
        logger.info("Second server started");
        logger.info("########################################");

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), inQueue, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(100);
        printQueueStatus(container(1), inQueueName);
        printQueueStatus(container(2), inQueueName);

        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }


    /**
     * @tpTestDetails This scenario tests failover when live server with LOCAL message-grouping handler is killed during
     * load in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGroupingFailoverNodeOneDown() throws Exception {
        testGroupingFailover(container(1), false, true);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with LOCAL message-grouping handler is killed during
     * load in colocated cluster topology. Large messages are used.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGroupingFailoverNodeOneDownLM() throws Exception {
        testGroupingFailover(container(1), true, true);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with LOCAL message-grouping handler is shut down during
     * load in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving shut down node-1</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGroupingFailoverNodeOneDownSd() throws Exception {
        testGroupingFailover(container(1), false, false);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with LOCAL message-grouping handler is shut down during
     * load in colocated cluster topology. Large messages are used.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving shut down node-1</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGroupingFailoverNodeOneDownSdLM() throws Exception {
        testGroupingFailover(container(1), true, false);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with REMOTE message-grouping handler is killed during
     * load in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving kill node-2</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testGroupingFailoverNodeTwoDown() throws Exception {
        testGroupingFailover(container(2), false, true);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with REMOTE message-grouping handler is killed during
     * load in colocated cluster topology. Large messages are used.
     * is killed.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving kill node-2</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testGroupingFailoverNodeTwoDownLM() throws Exception {
        testGroupingFailover(container(2), true, true);
    }

    /**
     * @tpTestDetails This scenario tests failover when live server with REMOTE message-grouping handler is shut down during
     * load in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving shut down node-2</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testGroupingFailoverNodeTwoDownSd() throws Exception {
        testGroupingFailover(container(2), false, false);
    }


    /**
     * @tpTestDetails This scenario tests failover when live server with REMOTE message-grouping handler is shut down during
     * load in colocated cluster topology. Large messages are used.
     * is shut down.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>configure message grouping on nodes in ths way: Node-1:live-LOCAL, backup-REMOTE and
     * Node-2: live-REMOTE, backup-LOCAL</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-2</li>
     * <li>during sending and receiving shut down node-2</li>
     * <li>producer makes failover on backup and continues in sending messages</li>
     * <li>wait for producer and consumer to finish and verify sent and received messages</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, producer sends all messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testGroupingFailoverNodeTwoDownSdLM() throws Exception {
        testGroupingFailover(container(2), true, false);
    }


    public void testGroupingFailover(Container containerToKill, boolean largeMessages, boolean useKill) throws Exception {

        int number_of_messages = 200;

        prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE.NETTY_NIO);

        configureMessageGrouping();

        container(1).start();

        container(2).start();

        logger.info("@@@@@@@@@@@@@@@ SERVERS RUNNING @@@@@@@@@@@");

        GroupMessageVerifier messageVerifier = new GroupMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), inQueue, number_of_messages);

        if (largeMessages) {

            producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED", true));

        } else {

            producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));

        }

        producerRedG1.setMessageVerifier(messageVerifier);

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(2), inQueue, 20000, 10, 10);

        receiver1.setMessageVerifier(messageVerifier);
        Thread.sleep(15000);
        receiver1.start();
        logger.info("@@@@@@@@@@@@@@@ RECEIVERS RUNNING @@@@@@@@@@@");
        // try to add here some delay so HQ knows about this consumer
        Thread.sleep(5000);

        producerRedG1.start();
        logger.info("@@@@@@@@@@@@@@@ PRODUCERS RUNNING @@@@@@@@@@@");
        Thread.sleep(8000);

        logger.info("################ KILL #######################");
        if (useKill) {
            containerToKill.kill();
        } else {
            containerToKill.stop();
        }

        producerRedG1.join();

        receiver1.join();

        messageVerifier.verifyMessages();

        Assert.assertEquals("Number of sent messages does not match", number_of_messages, producerRedG1.getListOfSentMessages().size());

        Assert.assertEquals("Number of received messages does not match", producerRedG1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopAllServers();

    }

    private void configureMessageGrouping() {
        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            configureMessageGroupingEAP7();
        } else {
            configureMessageGroupingEAP6();
        }
    }

    private void configureMessageGroupingEAP6() {

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 50000;
        long groupTimeout = 500;
        long reaperPeriod = 750;
        String liveServerName = "default";
        String backupServerName = "backup";

        container(1).start();

        container(2).start();

        JMSOperations jmsAdminOperationsC1 = container(1).getJmsOperations();

        JMSOperations jmsAdminOperationsC2 = container(2).getJmsOperations();

        jmsAdminOperationsC1.addMessageGrouping(liveServerName, name, "LOCAL", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC1.addMessageGrouping(backupServerName, name, "REMOTE", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC2.addMessageGrouping(liveServerName, name, "REMOTE", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC2.addMessageGrouping(backupServerName, name, "LOCAL", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC1.close();

        jmsAdminOperationsC2.close();

        container(1).stop();

        container(2).stop();

    }

    private void configureMessageGroupingEAP7() {

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 50000;
        long groupTimeout = 500;
        long reaperPeriod = 750;
        String liveServerName = "default";
        String backupServerName = "backup";

        container(1).start();

        container(2).start();

        JMSOperations jmsAdminOperationsC1 = container(1).getJmsOperations();

        JMSOperations jmsAdminOperationsC2 = container(2).getJmsOperations();

        jmsAdminOperationsC1.addMessageGrouping(liveServerName, name, "LOCAL", address, timeout, groupTimeout, reaperPeriod);
        jmsAdminOperationsC1.addMessageGrouping(backupServerName, name, "REMOTE", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC2.addMessageGrouping(liveServerName, name, "REMOTE", address, timeout, groupTimeout, reaperPeriod);
        jmsAdminOperationsC2.addMessageGrouping(backupServerName, name, "LOCAL", address, timeout, groupTimeout, reaperPeriod);

        jmsAdminOperationsC1.close();

        jmsAdminOperationsC2.close();

        container(1).stop();

        container(2).stop();

    }


    public void printQueueStatus(Container container, String queueCoreName) {

        JMSOperations jmsOperations1 = container.getJmsOperations();

        long count = jmsOperations1.getCountOfMessagesOnQueue(queueCoreName);

        logger.info("########################################");
        logger.info("Status of queue - " + queueCoreName + " - on node " + container.getName() + " is: " + count);
        logger.info("########################################");

        jmsOperations1.close();

    }

    public JavaArchive createLodh1Deployment() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh1");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming\n"), "MANIFEST.MF");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    public JavaArchive createLodh1Deployment2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-lodh2");

        mdbJar.addClass(LocalMdbFromQueue.class);

        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming\n"), "MANIFEST.MF");

        logger.info(mdbJar.toString(true));
//          Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/mdb2.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    protected Clients createClients(int acknowledgeMode, boolean topic, MessageBuilder messageBuilder) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(10);
        clients.setReceivedMessagesAckCommitAfter(5);

        return clients;
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckQueue() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false);
//    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is shut down in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving shut down node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueueShutDown() throws Exception {

        testFailoverWithShutDown(Session.CLIENT_ACKNOWLEDGE, false, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology. NIO journal type is used.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology with NIO journal type</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueueNIO() throws Exception {
        setJournalType("NIO");
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueHttpConnectors() throws Exception {
        testFail(Session.SESSION_TRANSACTED, true, false, false, Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology. NIO journal type is used.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology with NIO journal type</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueNIO() throws Exception {
        setJournalType("NIO");
        testFailover(Session.SESSION_TRANSACTED, true);
    }


//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckTopic() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false, true);
//    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, none of them gets any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to topic (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, none of them gets any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true);
    }

//    /**
//     * Start simple failback test with auto acknowledge on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailbackAutoAckTopic() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, true, true);
//    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * Both clients are using CLIENT_ACKNOWLEDGE session. During this process node-1 is killed, after while node-1 is started again
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is shut down in colocated cluster topology
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving shut down node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopicShutdown() throws Exception {
        testFailoverWithShutDown(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>during sending and receiving kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producer sends 500 messages start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true);
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

    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInCluster(Constants.CONNECTOR_TYPE connectorType) {
        if (Constants.CONTAINER_TYPE.EAP7_CONTAINER.equals(container(1).getContainerType())) {
            prepareColocatedTopologyInClusterEAP7(connectorType);
        } else {
            prepareColocatedTopologyInClusterEAP6();
        }
    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterEAP6() {
        String journalType = getJournalType();
        prepareLiveServerEAP6(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A, journalType);
        prepareColocatedBackupServerEAP6(container(1), "backup", JOURNAL_DIRECTORY_B, journalType);
        prepareLiveServerEAP6(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_B, journalType);
        prepareColocatedBackupServerEAP6(container(2), "backup", JOURNAL_DIRECTORY_A, journalType);
    }

    /**
     * Prepare two servers in colocated topology in cluster.
     */
    public void prepareColocatedTopologyInClusterEAP7(Constants.CONNECTOR_TYPE connectorType) {
        String journalType = getJournalType();
        prepareLiveServerEAP7(container(1), JOURNAL_DIRECTORY_A, journalType, connectorType);
        prepareBackupServerEAP7(container(1), JOURNAL_DIRECTORY_B, journalType);
        prepareLiveServerEAP7(container(2), JOURNAL_DIRECTORY_B, journalType, connectorType);
        prepareBackupServerEAP7(container(2), JOURNAL_DIRECTORY_A, journalType);
    }


    private String getJournalType() {

        return journalType;
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP6(Container container, String bindingAddress, String journalDirectory) {
        prepareLiveServerEAP6(container, bindingAddress, journalDirectory, "ASYNCIO");
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP6(Container container, String bindingAddress, String journalDirectory, String journalType) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalFileSize(10 * 1024 * 1024);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        test container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    public void prepareLiveServerEAP7(Container container, String journalDirectory, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreMaster("default", 5000, true);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue("default", inQueueName, inQueue, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueue, true);

        jmsAdminOperations.close();

        container.stop();
    }

    public void prepareBackupServerEAP7(Container container, String journalDirectory, String journalType) {

        final String backupServerName = "backup";

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7;
        String pooledConnectionFactoryName = "activemq-ra";
        String jgroupsChannel = "activemq-cluster";
        String jgroupsStack = "udp";


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectory);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectory);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, jgroupsStack, jgroupsChannel, 1000, connectorName);
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);


        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);
        jmsAdminOperations.disableSecurity(backupServerName);
        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicySharedStoreSlave(backupServerName, true, 5000, true, true, false, null, null, null, null);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        // set security persmissions for roles guest
        jmsAdminOperations.addSecuritySetting(backupServerName, "#");
        jmsAdminOperations.addRoleToSecuritySettings(backupServerName, "#", "guest");
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "send", true);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void setConnectorForClientEAP7(Container container, Constants.CONNECTOR_TYPE connectorType) {
        setConnectorForClientEAP7(container, "default", connectorType);
    }

    protected void setConnectorForClientEAP7(Container container, String serverName, Constants.CONNECTOR_TYPE connectorType) {

        String messagingGroupSocketBindingForConnector = "messaging";
        String nettyConnectorName = "netty";
        String nettyAcceptorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        int defaultPortForMessagingSocketBinding = 5445;
        String discoveryGroupName = "dg-group1";
        String jgroupsChannel = "activemq-cluster";
        String jgroupsStack = "udp";
        String broadcastGroupName = "bg-group1";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        switch (connectorType) {
            case HTTP_CONNECTOR:
                break;
            case NETTY_BIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();

                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                jmsAdminOperations.removeRemoteConnector(serverName, nettyConnectorName);
                jmsAdminOperations.createRemoteConnector(serverName, nettyConnectorName, messagingGroupSocketBindingForConnector, null);
                // add acceptor wtih BIO
                Map<String, String> acceptorParams = new HashMap<String, String>();
                jmsAdminOperations.removeRemoteAcceptor(serverName, nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(serverName, nettyAcceptorName, messagingGroupSocketBindingForConnector, null);
                jmsAdminOperations.setConnectorOnConnectionFactory(serverName, connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(serverName, clusterConnectionName);
                jmsAdminOperations.setClusterConnections(serverName, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(serverName, broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(serverName, broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            case NETTY_NIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                jmsAdminOperations.removeRemoteConnector(serverName, nettyConnectorName);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(serverName, nettyConnectorName, messagingGroupSocketBindingForConnector, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsAdminOperations.removeRemoteAcceptor(serverName, nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(serverName, nettyAcceptorName, messagingGroupSocketBindingForConnector, acceptorParamsNIO);
                jmsAdminOperations.setConnectorOnConnectionFactory(serverName, connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(serverName, clusterConnectionName);
                jmsAdminOperations.setClusterConnections(serverName, clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(serverName, broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(serverName, broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            default:
                break;
        }
        jmsAdminOperations.setHaForConnectionFactory(serverName, connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(serverName, connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(serverName, connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(serverName, connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.removeDiscoveryGroup(serverName, discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(serverName, discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);

        jmsAdminOperations.close();
    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param container            The arquilian container.
     * @param backupServerName     Name of the new HornetQ backup server.
     * @param journalDirectoryPath Absolute or relative path to journal directory.
     */
    public void prepareColocatedBackupServerEAP6(Container container,
                                                 String backupServerName, String journalDirectoryPath) {
        prepareColocatedBackupServerEAP6(container, backupServerName, journalDirectoryPath, "ASYNCIO");
    }

    /**
     * Prepares colocated backup. It creates new configuration of backup server.
     *
     * @param container            The arquilian container.
     * @param backupServerName     Name of the new HornetQ backup server.
     * @param journalDirectoryPath Absolute or relative path to journal directory.
     */
    public void prepareColocatedBackupServerEAP6(Container container,
                                                 String backupServerName, String journalDirectoryPath, String journalType) {

        String discoveryGroupName = "dg-group-backup";
        String broadCastGroupName = "bg-group-backup";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty-backup";
        String acceptorName = "netty-backup";
        String inVmConnectorName = "in-vm";
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6;
        String messagingGroupSocketBindingName = "messaging-group";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addMessagingSubsystem(backupServerName);
        jmsAdminOperations.setClustered(backupServerName, true);
        jmsAdminOperations.setPersistenceEnabled(backupServerName, true);
        jmsAdminOperations.disableSecurity(backupServerName);
//        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.setBackup(backupServerName, true);
        jmsAdminOperations.setSharedStore(backupServerName, true);
        jmsAdminOperations.setJournalFileSize(backupServerName, 10 * 1024 * 1024);
        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setFailoverOnShutdown(true, backupServerName);
        jmsAdminOperations.setPagingDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setBindingsDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setJournalDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setLargeMessagesDirectory(backupServerName, journalDirectoryPath);
        jmsAdminOperations.setAllowFailback(backupServerName, true);
        jmsAdminOperations.setJournalType(backupServerName, journalType);

        jmsAdminOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsAdminOperations.createRemoteConnector(backupServerName, connectorName, socketBindingName, null);
        jmsAdminOperations.createInVmConnector(backupServerName, inVmConnectorName, 0, null);
        jmsAdminOperations.createRemoteAcceptor(backupServerName, acceptorName, socketBindingName, null);

        jmsAdminOperations.setBroadCastGroup(backupServerName, broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.setDiscoveryGroup(backupServerName, discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.setClusterConnections(backupServerName, clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.setClusterUserPassword(backupServerName, CLUSTER_PASSWORD);

        jmsAdminOperations.removeAddressSettings(backupServerName, "#");
        jmsAdminOperations.addAddressSettings(backupServerName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        // set ha also for hornetq-ra
        jmsAdminOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);

        // enable debugging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");

        // set security persmissions for roles guest
        jmsAdminOperations.addSecuritySetting(backupServerName, "#");
        jmsAdminOperations.addRoleToSecuritySettings(backupServerName, "#", "guest");
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings(backupServerName, "#", "guest", "send", true);

//        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
//            jmsAdminOperations.createQueue(backupServerName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
//        }
//
//        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
//            jmsAdminOperations.createTopic(backupServerName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
//        }
//      jmsAdminOperations.createQueue(backupServerName, inQueueName, inQueue, true);
//      jmsAdminOperations.createQueue(backupServerName, outQueueName, outQueue, true);

        jmsAdminOperations.close();
        container.stop();
    }

    private void setJournalType(String journalType) {
        this.journalType = journalType;
    }
}