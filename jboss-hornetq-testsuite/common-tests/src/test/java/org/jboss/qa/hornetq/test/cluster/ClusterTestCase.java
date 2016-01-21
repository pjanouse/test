package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.impl.*;
import org.jboss.qa.hornetq.test.journalreplication.utils.JMSUtil;
import org.jboss.qa.hornetq.test.security.PermissionGroup;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerResp;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToQueueWithSelectorAndSecurity;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTempQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicToTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.security.AddressSecuritySettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.Context;
import java.io.File;
import java.util.*;
//TODO
//ClusterTestCase
//        - clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly
//        - clusterTestWithMdbOnTopicDeployAndUndeployTwoServers
//        - clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers
//        - clusterTestWithMdbWithSelectorAndSecurityTwoServers

/**
 * This test case can be run with IPv6 - just replace those environment
 * variables for ipv6 ones: export MYTESTIP_1=$MYTESTIPV6_1 export
 * MYTESTIP_2=$MYTESTIPV6_2 export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p>
 * This test also serves
 *
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ CLUSTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-ipv6-tests/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class ClusterTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusterTestCase.class);

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 3;

    private final JavaArchive MDB_ON_QUEUE1 = createDeploymentMdbOnQueue1();
    private final JavaArchive MDB_ON_QUEUE2 = createDeploymentMdbOnQueue2();
    private final JavaArchive MDB_ON_QUEUE3 = createDeploymentMdbOnQueue3();
    private final JavaArchive MDB_ON_QUEUE4 = createDeploymentMdbOnQueue4();
    private final JavaArchive MDB_ON_QUEUE1_SECURITY = createDeploymentMdbOnQueueWithSecurity();
    private final JavaArchive MDB_ON_QUEUE1_SECURITY2 = createDeploymentMdbOnQueueWithSecurity2();

    private final JavaArchive MDB_ON_TEMPQUEUE1 = createDeploymentMdbOnTempQueue1(container(1));
    private final JavaArchive MDB_ON_TEMPQUEUE2 = createDeploymentMdbOnTempQueue2(container(2));

    private final JavaArchive MDB_ON_TOPIC1 = createDeploymentMdbOnTopic1();
    private final JavaArchive MDB_ON_TOPIC2 = createDeploymentMdbOnTopic2();

    private final JavaArchive MDB_ON_TEMPTOPIC1 = createDeploymentMdbOnTempTopic1(container(1));
    private final JavaArchive MDB_ON_TEMPTOPIC2 = createDeploymentMdbOnTempTopic2(container(2));

    private final JavaArchive MDB_ON_QUEUE1_TEMP_QUEUE = createDeploymentMdbOnQueue1Temp();

    private final JavaArchive MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = createDeploymentMdbOnTopicWithSub1();

    static String queueNamePrefix = "testQueue";
    static String topicNamePrefix = "testTopic";
    static String queueJndiNamePrefix = "jms/queue/testQueue";
    static String topicJndiNamePrefix = "jms/topic/testTopic";

    // InQueue and OutQueue for mdb
    static String inQueueNameForMdb = "InQueue";
    static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    static String outQueueNameForMdb = "OutQueue";
    static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;

    // InTopic and OutTopic for mdb
    static String inTopicNameForMdb = "InTopic";
    static String inTopicJndiNameForMdb = "jms/topic/" + inTopicNameForMdb;
    static String outTopicNameForMdb = "OutTopic";
    static String outTopicJndiNameForMdb = "jms/topic/" + outTopicNameForMdb;

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue and
     * topic to each. Queue and topic are load-balanced. Start producer which
     * sends messages to queue on first server. Start publisher which sends
     * messages to topic on second server. Start consumer which reads messages
     * from queue on second server. Start subscriber, which reads messages from
     * topic on first server. Verify that all messages were received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with one queue and one topic
     * </li>
     * <li>producer starts to send messages to queue on node-1</li>
     * <li>publisher starts to send messages to topic on node-2</li>
     * <li>consumer reads messages from queue on node-2</li>
     * <li>subscriber reads messages from topic on node-1</li>
     * <li>start consumer on node-2 which reads messages from queue</li>
     * <li>wait for producer and receiver to finish and for consumer and
     * subscriber to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver and subscriber read all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTest() throws Exception {

        prepareServers();

        container(2).start();
        container(1).start();

        Client queueProducer = new ProducerTransAck(container(1), queueJndiNamePrefix + "0", NUMBER_OF_MESSAGES_PER_PRODUCER);
        Client topicProducer = new PublisherTransAck(container(2), topicJndiNamePrefix + "0", NUMBER_OF_MESSAGES_PER_PRODUCER, "producer");
        Client queueConsumer = new ReceiverTransAck(container(2), queueJndiNamePrefix + "0");
        SubscriberTransAck topicSubscriber = new SubscriberTransAck(container(1), topicJndiNamePrefix + "0", 60000, 100, 10, "subs", "name");
        topicSubscriber.subscribe();

        queueProducer.start();
        topicProducer.start();
        queueConsumer.start();
        topicSubscriber.start();
        queueProducer.join();
        topicProducer.join();
        queueConsumer.join();
        topicSubscriber.join();

        Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(), queueConsumer.getCount());
        Assert.assertEquals("Number of received messages form topic does not match: ", topicProducer.getCount(), topicSubscriber.getCount());

        container(1).stop();

        container(2).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithNetworkFailures() throws Exception {

        int numberOfMessages = 100000;
        prepareServers();
        setClusterNetworkTimeOuts(container(1), 2000, 2000, 4000);
        container(2).start();
        container(1).start();

        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        // A1 producer
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(messageBuilder);
        producer1.start();

        new JMSTools().waitForMessages(inQueueNameForMdb, 300, 60000, container(1), container(2));

        int pid = ProcessIdUtils.getProcessId(container(1));
        for (int i = 0; i < 10; i++) {
            ProcessIdUtils.suspendProcess(pid);
            Thread.sleep(5000);
            ProcessIdUtils.resumeProcess(pid);
            Thread.sleep(5000);
        }

        producer1.stopSending();
        producer1.join();

        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), inQueueJndiNameForMdb, 5000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        // Just prints lost or duplicated messages if there are any. This does not fail the test.
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();

        container(2).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithKills() throws Exception {

        int numberOfMessages = 100000;
        prepareServers();
        setClusterNetworkTimeOuts(container(1), 2000, 2000, 4000);
        container(2).start();
        container(1).start();

        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        // A1 producer
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(messageBuilder);
        producer1.start();

        new JMSTools().waitForMessages(inQueueNameForMdb, 300, 60000, container(1), container(2));

        for (int i = 0; i < 10; i++) {
            container(2).kill();
            Thread.sleep(5000);
            container(2).start();
            Thread.sleep(5000);
            container(1).kill();
            Thread.sleep(5000);
            container(1).start();
            Thread.sleep(5000);
        }

        producer1.stopSending();
        producer1.join();

        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), inQueueJndiNameForMdb, 20000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        // Just prints lost or duplicated messages if there are any. This does not fail the test.
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();

        container(2).stop();

    }

    private void setClusterNetworkTimeOuts(Container container, long callTimout, long checkPeriod, long ttl) {
        String clusterGroupName = "my-cluster";
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setJournalMinCompactFiles(0);
        jmsAdminOperations.setJournalMinFiles(100);
        jmsAdminOperations.setClusterConnectionCallTimeout(clusterGroupName, callTimout);
        jmsAdminOperations.setClusterConnectionCheckPeriod(clusterGroupName, checkPeriod);
        jmsAdminOperations.setClusterConnectionTTL(clusterGroupName, ttl);
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue and
     * each. Queue is load-balanced. Set reconnect attempts to -1 on cluster connection.
     * Kill server 2 and Start producer which sends messages to queue on first server. Start consumer which reads messages
     * from queue from 1st server. Verify that all messages were received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with one queue with reconnect attempts 1 in cluster connection
     * </li>
     * <li>kill 2nd server in cluster</li>
     * <li>producer starts to send messages to queue on node-1</li>
     * <li>consumer reads messages from queue on node-1</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestKillTargetServer1ReconnectAttemptNoRestart() throws Exception {
        clusterTestKillTargetServer1ReconnectAttempt(false);
    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue and
     * each. Queue is load-balanced. Set reconnect attempts to -1 on cluster connection.
     * Kill server 2 and Start producer which sends messages to queue on first server. Start consumer which reads messages
     * from queue from 1st server. Verify that all messages were received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with one queue with reconnect attempts 1 in cluster connection
     * </li>
     * <li>kill 2nd server in cluster</li>
     * <li>producer starts to send messages to queue on node-1</li>
     * <li>consumer reads messages from queue on node-1</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestKillTargetServer1ReconnectAttemptWithRestart() throws Exception {
        clusterTestKillTargetServer1ReconnectAttempt(true);
    }

    public void clusterTestKillTargetServer1ReconnectAttempt(boolean withRestart) throws Exception {

        int numberOfMessages = 1000;
        prepareServer(container(1), true, 1);
        prepareServer(container(2), true, 1);

        container(2).start();
        container(1).start();

        // give it some time to create cluster
        Thread.sleep(5000);

        // stop server 2
        container(2).kill();

        //send messages
        ProducerTransAck queueProducer = new ProducerTransAck(container(1), queueJndiNamePrefix + "0", numberOfMessages);
        queueProducer.setMessageBuilder(new TextMessageBuilder(1));
        queueProducer.setTimeout(0);
        queueProducer.setCommitAfter(10);
        queueProducer.start();
        queueProducer.join();

        // receive messages
        ReceiverTransAck queueConsumer1 = new ReceiverTransAck(container(1), queueJndiNamePrefix + "0");
        queueConsumer1.setTimeout(0);
        queueConsumer1.setReceiveTimeOut(10000);
        queueConsumer1.setCommitAfter(10);
        queueConsumer1.start();
        queueConsumer1.join();

        if (withRestart) {
            log.info("Restart 1st server and try to consume messages.");
            container(1).restart();
            // receive messages
            ReceiverTransAck queueConsumer2 = new ReceiverTransAck(container(1), queueJndiNamePrefix + "0");
            queueConsumer2.setTimeout(0);
            queueConsumer2.setReceiveTimeOut(10000);
            queueConsumer2.setCommitAfter(10);
            queueConsumer2.start();
            queueConsumer2.join();
            Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(),
                    queueConsumer1.getListOfReceivedMessages().size() + queueConsumer2.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(),
                    queueConsumer1.getListOfReceivedMessages().size());
        }

        container(1).stop();
        container(2).stop();

    }

    /**
     * This test will start two servers A and B in cluster. Start
     * producers/publishers connected to A with client/transaction acknowledge
     * on queue/topic. Start consumers/subscribers connected to B with
     * client/transaction acknowledge on queue/topic.
     *
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue to
     * each. Queue is load-balanced. Start producer which sends messages to
     * first server and kill second server during this sending. Start second
     * server again and read all messages from it. Verify that all messages were
     * received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with one queue</li>
     * <li>producer starts to send messages to queue to node-1</li>
     * <li>node-2 is killed and restarted during sending messages</li>
     * <li>start consumer on node-2 which reads messages from queue</li>
     * <li>servers are stopped</li>
     * </ul>
     * @tpPassCrit receiver/subscriber read all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithKillOfServerInCluster() throws Exception {

        prepareServers();

        container(1).start();
        container(2).start();

        JMSTools.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        JMSTools.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(128));
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        log.info("########################################");
        log.info("kill - second server");
        log.info("########################################");

        // if (shutdown) {
        // controller.stop(CONTAINER2_NAME);
        // } else {
        container(2).kill();
        // }

        log.info("########################################");
        log.info("Start again - second server");
        log.info("########################################");
        container(2).start();
        JMSTools.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000);
        log.info("########################################");
        log.info("Second server started");
        log.info("########################################");

        Thread.sleep(10000);

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 30000, 1000, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);
        // printQueueStatus(CONTAINER1_NAME_NAME, inQueueName);
        // printQueueStatus(CONTAINER2_NAME, inQueueName);

        receiver1.start();
        receiver1.join();

        log.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }

    // THIS WAS COMMENTED BECAUSE clusterTest() IS BASICALLY TESTING THE SAME SCENARIO
    // /**
    // * This test will start two servers A and B in cluster.
    // * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
    // * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitDuplicateId() throws Exception {
    //
    // prepareServersEAP6();
    //
    // controller.start(CONTAINER2_NAME);
    //
    // controller.start(CONTAINER1_NAME_NAME);
    //
    // ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME),
    // getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
    // MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
    // messageBuilder.setAddDuplicatedHeader(true);
    // producer1.setMessageBuilder(messageBuilder);
    // producer1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
    // producer1.start();
    // producer1.join();
    //
    // ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME_NAME),
    // getJNDIPort(CONTAINER1_NAME_NAME), inQueueJndiNameForMdb, 2000, 10, 10);
    // receiver1.setCommitAfter(NUMBER_OF_MESSAGES_PER_PRODUCER/5);
    // receiver1.start();
    // receiver1.join();
    //
    // stopServer(CONTAINER1_NAME_NAME);
    //
    // stopServer(CONTAINER2_NAME);
    //
    // }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue to
     * each. Queue is load-balanced. Start producer which sends messages to
     * first server and receiver which will rollback some messages back (does
     * not consume a single messages). Start consumer on second server and read
     * all messages. Verify that all messages were received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with one queue</li>
     * <li>producer sends messages to queue to node-1</li>
     * <li>wait for producer to finish</li>
     * <li>consume all messages from queue on node-1 and rollback session</li>
     * <li>consume messages from queue on node-2</li>
     * </ul>
     * @tpPassCrit receiver/subscriber read all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithoutDuplicateIdWithInterruption() throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        // send messages without dup id -> load-balance to node 2
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 100);
        builder.setAddDuplicatedHeader(false);
        producer1.setMessageBuilder(builder);
        producer1.start();
        producer1.join();

        // receive more then half message so some load-balanced messages gets back
        Context context = null;
        ConnectionFactory cf;
        Connection conn = null;
        Session session;
        Queue queue;

        try {

            context = container(1).getContext();

            cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(inQueueJndiNameForMdb);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiver = session.createConsumer(queue);

            int count = 0;
            while (count < NUMBER_OF_MESSAGES_PER_PRODUCER) {
                receiver.receive(10000);
                count++;
                log.info("Receiver got message: " + count);
            }
            session.rollback();

        } catch (JMSException ex) {
            log.error("Error occurred during receiving.", ex);
        } finally {
            if (conn != null) {
                conn.close();
            }
            if (context != null) {
                context.close();
            }

        }

        // receive some of them from first server and kill receiver -> only some of them gets back to
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), inQueueJndiNameForMdb, 10000, 10, 10);
        receiver2.start();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver2.getListOfReceivedMessages().size());
        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails MDBs are deployed on two servers in cluster. Producer
     * sends messages into input queue on node-1 and MDB repost them to output
     * queue. Receiver tries to read messages from output queue on node-2. were
     * received.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with deployed destinations</li>
     * <li>deploy MDBs to servers/li>
     * <li>create producer on node-1 and consumer on node-2</li>
     * <li>producer sends several hundred messages to input queue</li>
     * <li>MDB on node-1 repost them to output queue</li>
     * <li>receiver reads messages from output queue on node-2</li>
     * <li>servers are stopped</li>
     * </ul>
     * @tpPassCrit receiver read all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueue() throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_QUEUE1);

        container(2).deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: "
                        + producer.getListOfSentMessages().size() + "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size(), producer
                .getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(),
                receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                        + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(),
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        container(1).undeploy(MDB_ON_QUEUE1);

        container(2).undeploy(MDB_ON_QUEUE2);

        container(1).stop();

        container(2).stop();

    }

    /**
     * @tpTestDetails MDBs with queues are deployed on two servers in cluster.
     * Queues created with deploy have the same name on both servers and are
     * load balanced within cluster. Producer sends messages into input queue on
     * node-1 and MDB repost them to output queue. MDB on node-2 is undeployed
     * and deployed back. Receiver tries to read messages from output queue on
     * node-2.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with deployed destinations</li>
     * <li>deploy MDBs to servers/li>
     * <li>create producer on node-1 and consumer on node-2</li>
     * <li>producer sends several hundred messages to input queue</li>
     * <li>MDB on node-1 repost them to output queue</li>
     * <li>receiver reads messages from output queue on node-2</li>
     * <li>servers are stopped</li>
     * </ul>
     * @tpPassCrit receiver read all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueueWithPriorities() throws Exception {

        int numberOfMessages = 500;

        prepareServers();

        container(4).start();
        container(3).start();
        container(2).start();
        container(1).start();


        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder();
        messageBuilder.setIsAddPriorityToMessage(true);
        producer.setMessageBuilder(messageBuilder);
        log.info("Start producer.");
        producer.start();
        log.info("Producer finished.");
        producer.join();

        container(1).deploy(MDB_ON_QUEUE1);
        container(2).deploy(MDB_ON_QUEUE2);
        container(3).deploy(MDB_ON_QUEUE3);
        container(4).deploy(MDB_ON_QUEUE4);


        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start consumer.");
        receiver.start();


        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: "
                        + producer.getListOfSentMessages().size() + "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size(), producer
                .getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(),
                receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + numberOfMessages
                        + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(),
                numberOfMessages);

        container(1).undeploy(MDB_ON_QUEUE1);

        container(2).undeploy(MDB_ON_QUEUE2);

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
    }

    /**
     * @tpTestDetails MDBs with queues are deployed on two servers in cluster. Queues created with deploy have the
     * same name on both servers and are load balanced within cluster. Producer sends messages into input queue
     * on node-1 and MDB repost them to output queue. MDB on node-2 is undeployed and deployed back. Receiver tries to
     * read messages from output queue on node-2.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster (without destinations)</li>
     * <li>deploy MDBs to servers</li>
     * <li>create producer on node-1 and consumer on node-2</li>
     * <li>producer sends several hundred messages to input queue</li>
     * <li>MDB on node-1 repost them to output queue</li>
     * <li>MDB is removed from node-2</li>
     * <li>MDB is deployed back to node-2</li>
     * <li>receiver reads messages from output queue on node-2</li>
     * <li>servers are stopped</li>
     * </ul>
     * @tpPassCrit receiver read all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnQueueDeployAndUndeploy() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        removeInQueue(container(1), container(2), container(3), container(4));
        int numberOfMessages = 30;
        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_TEMPQUEUE1);

        container(2).deploy(MDB_ON_TEMPQUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        producer.start();
        producer.join();
        log.info("Removing MDB ond secnod server and adding it back ");
        container(2).undeploy(MDB_ON_TEMPQUEUE2);
        container(2).deploy(MDB_ON_TEMPQUEUE2);
        log.info("Trying to read from newly deployed OutQueue");

        receiver.start();
        receiver.join();

        Assert.assertEquals("Number of messages in OutQueue does not match", numberOfMessages, receiver
                .getListOfReceivedMessages().size());
        container(1).undeploy(MDB_ON_TEMPQUEUE1);
        container(2).undeploy(MDB_ON_TEMPQUEUE2);

        container(1).stop();

        container(2).stop();

    }

    /**
     * @tpTestDetails MDB with topic is deployed on server. Name of topic
     * distributed with MDB collides with topic name already existing on server.
     * @tpProcedure <ul>
     * <li>start one server with destinations</li>
     * <li>deploy MDB</li>
     * </ul>
     * @tpPassCrit deploy fails
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicDeployAleradyExistingTopic() throws Exception {
        log.info("PREPARING SERVERS");
        boolean passed = false;
        prepareServers(true);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        try {
            jmsAdminOperations.deploy(MDB_ON_TEMPTOPIC1);
            passed = true;

        } catch (Exception e) {
            // this is correct behavior
        } finally {
            jmsAdminOperations.close();
            // if deployement is deployed (test will fail) then try to undeploy
            if (!passed) {
                try {
                    container(1).undeploy(MDB_ON_TEMPTOPIC1);
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        Assert.assertFalse("Deployment of already existing topic didn't failed", passed);

        container(1).stop();
    }

    /**
     * @tpTestDetails One server without destinations is started. MDB with topic
     * is deployed on server. Subscriber creates durable subscription on this
     * topic. MDB is undeployed and deployed back.
     * @tpProcedure <ul>
     * <li>start one server without destinations</li>
     * <li>deploy MDB with topic</li>
     * <li>subscriber creates durable subscription on newly deployed topic</li>
     * <li>MDB with topic is undeployed</li>
     * <li>MDB with topic is deployed back</li>
     * <li>check if durable subscription exists</li>
     * </ul>
     * @tpPassCrit durable subscription is not deleted
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServers(false);
        container(1).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), inTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        subscriber.start();
        subscriber.join();
        container(1).undeploy(MDB_ON_TEMPTOPIC1);
        container(1).deploy(MDB_ON_TEMPTOPIC1);

        Assert.assertEquals("Number of subscriptions is not 0", 0,
                jmsAdminOperations.getNumberOfDurableSubscriptionsOnTopic("mySubscription"));
        container(1).undeploy(MDB_ON_TEMPTOPIC1);
        container(1).stop();
    }

    /**
     * @tpTestDetails two servers in cluster without destinations are started.
     * MDBs with same topics are deployed to each of them. Publisher starts
     * sending messages to topic on node-1. Subscriber creates durable
     * subscription on topic on node-2 and reads messages. Wait until all
     * messages are sent and read. MDB on node-2 is undeplyed and deployed back.
     * @tpProcedure <ul>
     * <li>start two servers in cluster without destinations</li>
     * <li>deploy MDBs with same topics to both servers</li>
     * <li>publisher starts sending messages to topic on node-1</li>
     * <li>subscriber creates durable subscription on topic on node-2</li>
     * <li>wait until all messages are sent and delivered to subscriber</li>
     * <li>MDB with topic is undeployed from node-2</li>
     * <li>MDB with topic is deployed back to node-2</li>
     * <li>check if durable subscription exists on node-2</li>
     * </ul>
     * @tpPassCrit durable subscription is not deleted
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(container(1), false);
        prepareServer(container(2), false);
        container(1).start();
        container(2).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), outTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();
        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails Two servers in cluster are started. First server is
     * started with deployed topic, second server without. MDBs with same topic
     * as on node-1 is deployed to node-2. Publisher starts sending messages to
     * topic on node-1. Subscriber creates durable subscription on topic on
     * node-2 and reads messages. Wait until all messages are sent and read. MDB
     * on node-2 is undeplyed and deployed back.
     * @tpProcedure <ul>
     * <li>start two servers in cluster, first with deployed topic and second
     * without</li>
     * <li>deploy MDBs with same topic as is on node-1 to node-2</li>
     * <li>publisher starts sending messages to topic on node-1</li>
     * <li>subscriber creates durable subscription on topic on node-2</li>
     * <li>wait until all messages are sent and delivered to subscriber</li>
     * <li>MDB with topic is undeployed from node-2</li>
     * <li>MDB with topic is deployed back to node-2</li>
     * <li>check if durable subscription exists on node-2</li>
     * </ul>
     * @tpPassCrit durable subscription is not deleted
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers() throws Exception {
        log.info("PREPARING SERVERS");
        prepareServer(container(1), true);
        prepareServer(container(2), false);
        container(1).start();
        container(2).start();
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), inTopicJndiNameForMdb, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), inTopicJndiNameForMdb, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();

        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopic("subscriber1"));
        container(1).stop();
        container(2).stop();
    }

    /**
     * @throws Exception
     * @tpTestDetails Two servers in cluster are started with configured
     * destinations in standalone-full-ha.xml. MDB is deployed on node-1. Client
     * creates temporary queue on node-1 and sends messages to input queue. MDB
     * reads messages sent by client and sends response to each of them into
     * temporary queue. During this communication is checked if temporary queue
     * is created on all nodes in cluster. It shouldn’t be.
     * TODO make this test working properly to avoid regression from
     * https://access.redhat.com/support/cases/#/case/01384076
     */
    @Ignore
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTopicLMNoPersistence() throws Exception {
        log.info("PREPARING SERVERS");
        int number = 10;
        prepareServers();
        container(2).start();
        container(1).start();
        JMSOperations jmsAdminOperationsContainer1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperationsContainer2 = container(2).getJmsOperations();
        jmsAdminOperationsContainer1.setPersistenceEnabled(true);
        jmsAdminOperationsContainer2.setPersistenceEnabled(true);
        jmsAdminOperationsContainer1.close();
        jmsAdminOperationsContainer2.close();
        container(2).stop();
        container(1).stop();
        container(2).start();
        container(1).start();

        log.info("SERVERS ARE READY");
        log.info("STARTING CLIENTS");
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), inQueueJndiNameForMdb);
        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(inQueueJndiNameForMdb);
        MessageProducer producer = session.createProducer(queue);
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < 1024 * 1024 * 3; i++) {
            sb.append('a');
        }
        log.info("SENDING MESSAGE");
        receiver.start();
        for (int i = 0; i < number; i++) {
            MapMessage message = session.createMapMessage();
            message.setString("str", sb.toString());
            producer.send(message);
            System.out.println(i + " message sent");
        }

        receiver.join();
        log.info("MESSAGE SENT");
        log.info("CLIENTS FINISHED");
        Assert.assertEquals("Receiver didn't get any messages", number, receiver.getCount());


    }


    /**
     * @tpTestDetails Two servers in cluster are started with configured destinations in standalone-full-ha.xml.
     * MDB is deployed on node-1. Client creates temporary queue on node-1 and sends messages to input queue. MDB reads
     * messages sent by client and sends response to each of them into temporary queue. During this communication is
     * checked if temporary queue is created on all nodes in cluster. It shouldn’t be.
     * >>>>>>> Stashed changes
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster</li>
     * <li>deploy MDB to node-1</li>
     * <li>create producer on node-1</li>
     * <li>producer sends several hundred messages to input queue</li>
     * <li>MDB on node-1 replies to them into temporary queue</li>
     * <li>existence of temporary queue on both nodes is checked</li>
     * <li>servers are stopped</li>
     * </ul>
     * @tpPassCrit only node-1 should have temporary queue
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTempQueueonOtherNodes() throws Exception {
        prepareServers(true);
        container(2).start();
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        JMSOperations jmsAdminOperationsContainer1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperationsContainer2 = container(2).getJmsOperations();
        responsiveProducer.start();
        // Wait fro creating connections and send few messages
        Thread.sleep(1000);
        cont1Count = jmsAdminOperationsContainer1.getNumberOfTempQueues();
        cont2Count = jmsAdminOperationsContainer2.getNumberOfTempQueues();
        responsiveProducer.join();
        Assert.assertEquals("Invalid number of temp queues on CONTAINER1_NAME", 1, cont1Count);
        Assert.assertEquals("Invalid number of temp queues on CONTAINER2_NAME", 0, cont2Count);

    }

    /**
     * @tpTestDetails One server is started. Client creates temporary queue and
     * sends several thousand messages (enough to trigger paging). Server is
     * forcibly killed and then started again. Paging file should be deleted.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>create temporary queue on server</li>
     * <li>create producer</li>
     * <li>send about ten thousand messages to temporary queue</li>
     * <li>check if paging file exists</li>
     * <li>kill server</li>
     * <li>start server</li>
     * <li>check if paging file exists</li>
     * </ul>
     * @tpPassCrit paging file should exists before restart, but should be gone
     * after it.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestPagingAfterFailOverTempQueue() throws Exception {
        prepareServers(true);
        container(1).start();
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
        TemporaryQueue tempQueue = session.createTemporaryQueue();
        MessageProducer producer = session.createProducer(tempQueue);
        TextMessage message = session.createTextMessage("message");
        for (int i = 0; i < 10000; i++) {
            producer.send(message);
        }

        Assert.assertEquals("No paging files was found", false, getAllPagingFiles(pagingPath).size() == 0);
        container(1).kill();
        container(1).start();
        // Thread.sleep(80000);
        for (File f : getAllPagingFiles(pagingPath)) {
            log.error("FILE: " + f.getName());
        }
        Assert.assertEquals("Too many paging files was found", 0, getAllPagingFiles(pagingPath).size());
        stopAllServers();

    }

    /**
     * @tpTestDetails One server is started. Non-durable queue is deployed.
     * Client sends to this queue several thousand messages (enough to trigger
     * paging). Server is forcibly killed and then started again. Paging file
     * should be deleted.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>create non-durable queue on server</li>
     * <li>create producer</li>
     * <li>send about ten thousand messages to temporary queue</li>
     * <li>check if paging file exists</li>
     * <li>kill server</li>
     * <li>start server</li>
     * <li>check if paging file exists</li>
     * </ul>
     * @tpPassCrit paging file should exists before restart, but should be gone
     * after it.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestPagingAfterFailOverNonDurableQueue() throws Exception {
        prepareServer(container(1), false);
        container(1).start();
        String pagingPath = null;
        int counter = 0;
        ArrayList<File> pagingFilesPath = new ArrayList<File>();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, false);
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = container(1).getContext();
        Queue inqueue = (Queue) context.lookup(inQueueJndiNameForMdb);
        ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);

        MessageProducer producer = session.createProducer(inqueue);
        TextMessage message = session.createTextMessage("message");
        for (int i = 0; i < 10000; i++) {
            producer.send(message);
        }

        Assert.assertEquals("No paging files was found", false, getAllPagingFiles(pagingPath).size() == 0);
        container(1).kill();
        container(1).start();
        Assert.assertEquals("Too many paging files was found", 0, getAllPagingFiles(pagingPath).size());
        stopAllServers();

    }

    /**
     * @tpTestDetails One server is started. Two with different connections are
     * connected to the server. First client uses first connection and creates
     * temporary queue and sends message in to it. Second client uses second
     * connection and tries to read these messages from this temporary queue.
     * Second client shouldn’t be able to read from temporary queue.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>create one client which creates temporary queue on server and sends
     * message in to it</li>
     * <li>create second client (on different connection) which tries to read
     * from this queue</li>
     * <li>stop server</li>
     * </ul>
     * @tpPassCrit second client is not able to read from temporary queue
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestReadMessageFromDifferentConnection() throws Exception {
        prepareServers(true);
        boolean failed = false;
        container(1).start();
        try {
            Context context = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            Connection connection1 = cf.createConnection();
            Connection connection2 = cf.createConnection();
            Session session1 = connection1.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            Session session2 = connection2.createSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            TemporaryQueue tempQueue = session1.createTemporaryQueue();
            MessageProducer producer = session1.createProducer(tempQueue);
            producer.send(session1.createTextMessage("message"));
            MessageConsumer consumer = session2.createConsumer(tempQueue);
            consumer.receive(100);
        } catch (JMSException e) {
            failed = true;
        } catch (Exception e) {
            throw e;
        } finally {
            stopAllServers();
        }
        Assert.assertEquals("Reading message didn't failed", true, failed);
    }

    /**
     * @tpTestDetails One server with deployed destinations is started. MDB is
     * deployed. Client creates temp queue for responses and sends message to
     * server. MDB reads this message and sends it back to temp queue with small
     * ttl. Client tries to receive message with already expired ttl.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>deploy MDB which replies with messages with low TTL</li>
     * <li>create one client which creates temporary queue on server and sends
     * message in to it</li>
     * <li>MDB take received massage and sends it to temp queue with low
     * ttl</li>
     * <li>try to receive response from temp queue</li>
     * <li>stop server</li>
     * </ul>
     * @tpPassCrit client doesn't receive any response from temporary queue
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTemQueueMessageExpiration() throws Exception {
        prepareServers(true);
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, 1, 300);
        responsiveProducer.start();
        responsiveProducer.join();

        Assert.assertEquals("Number of received messages don't match", 0, responsiveProducer.getRecievedCount());

        stopAllServers();

    }

    /**
     * @tpTestDetails One server with deployed destinations is started. MDB is
     * deployed. Client creates temp queue for responses and sends LARGE message
     * to server. MDB reads this message and sends it back to temp queue with
     * small ttl. Client tries to receive message with already expired ttl.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>deploy MDB which replies with messages with low TTL</li>
     * <li>create one client which creates temporary queue on server and sends
     * large message in to it</li>
     * <li>MDB take received massage and sends it to temp queue with low
     * ttl</li>
     * <li>try to receive response from temp queue</li>
     * <li>stop server</li>
     * </ul>
     * @tpPassCrit client doesn't receive any response from temporary queue
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestTemQueueMessageExpirationLM() throws Exception {
        prepareServers(true);
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), inQueueJndiNameForMdb, 1, 300);
        responsiveProducer.setMessageBuilder(new TextMessageBuilder(110 * 1024));
        responsiveProducer.start();
        responsiveProducer.join();

        Assert.assertEquals("Number of received messages don't match", 0, responsiveProducer.getRecievedCount());

        stopAllServers();

    }

    /**
     * @tpTestDetails One server without deployed destinations is starter. MDB
     * with destinations is deployed. MDB reads messages from inQueue and sends
     * them to outQueue. Producer sends messages to inQueue deployed with MDB.
     * The same MDB is deployed again. Receiver receives messages from outQueue
     * from queue
     * @tpProcedure <ul>
     * <li>start one server without destinations</li>
     * <li>deploy MDB with destinations</li>
     * <li>create producer which sends messages to inQueue</li>
     * <li>wait for producer to finish </li>
     * <li>deploy same MDB with destinations again</li>
     * <li>create consumer and read messages fom outQueue</li>
     * <li>stop server</li>
     * </ul>
     * @tpPassCrit Receiver get all messages which producer send. Re-deployment
     * didn't wiped queues.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbHqXmlOnQueueRedeploy() throws Exception {
        prepareServer(container(1), false);
        removeInQueue(container(1));

        container(1).start();
        container(1).deploy(MDB_ON_TEMPQUEUE1);
        ProducerClientAck producer = new ProducerClientAck(container(1), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(1), outQueueJndiNameForMdb, 10000, 10, 10);
        producer.start();
        producer.join();
        container(1).deploy(MDB_ON_TEMPQUEUE1);
        receiver.start();
        receiver.join();
        Assert.assertEquals("Number of messages does not match", NUMBER_OF_MESSAGES_PER_PRODUCER, receiver
                .getListOfReceivedMessages().size());
        container(1).stop();
    }

    /**
     * @tpTestDetails Two servers in cluster are started with this security
     * configuration: sending to InQueue is allowed for group "guest" and
     * reading from OutQueue is enabled for group "guest". Group named "user"
     * can read and write to both of these queues. MDB whith messageSelector
     * which filters messages containing red color flag and login to "user"
     * group is deployed to node-1. MDB reads messages from InQueue and sends
     * them to OutQueue. Three producers send 100 messages (as guests) in to
     * InQueue two of them send red messages but each of them sets different
     * JMSXGroupID. Two consumers read messages from OutQueue (as guests). Each
     * of them should read 100 messages with one JMSXGroupID. JMSXGroupID
     * shouldn't be mixed between consumers.
     * @tpProcedure <ul>
     * <li>Start two servers with destinations</li>
     * <li>Setup security and credentials on both of them</li>
     * <li>Deploy MDB on server one</li>
     * <li>Create create three producers and configure their message builders to
     * build messages with different JMSXGroupID and colors</li>
     * <li>Start producers to sending messages to both nodes</li>
     * <li>Create two consumers each of them will be connected on different node
     * in cluster</li>
     * <li>Start receiving messages</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Each receiver received 100 messages with one JMSXGroupID
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGrouping() throws Exception {
        prepareServer(container(1), true);
        prepareServer(container(2), true);
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

        // setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);
        jmsAdminOperations1.addLoggerCategory("org.jboss", "INFO");
        jmsAdminOperations1.addLoggerCategory("org.jboss.qa.hornetq.apps.mdb", "TRACE");
        jmsAdminOperations1.seRootLoggingLevel("TRACE");

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsAdminOperations1.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations1.rewriteLoginModule("RealmDirect", opts);
        jmsAdminOperations2.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations2.rewriteLoginModule("RealmDirect", opts);


        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        UsersSettings.forEapServer(container(1).getServerHome()).withUser("user", "user.1234", "user").create();
        UsersSettings.forEapServer(container(2).getServerHome()).withUser("user", "user.1234", "user").create();

        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        // restart servers to changes take effect
        container(1).stop();
        container(2).stop();

        container(1).start();
        container(2).start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), outQueueJndiNameForMdb, 10000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        // setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));
        ProducerClientAck producerRedG2 = new ProducerClientAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG2.setMessageBuilder(new GroupColoredMessageBuilder("g2", "RED"));
        ProducerClientAck producerBlueG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerBlueG1.setMessageBuilder(new GroupColoredMessageBuilder("g2", "BLUE"));

        container(1).deploy(MDB_ON_QUEUE1_SECURITY);

        receiver1.start();
        receiver2.start();

        producerBlueG1.start();
        Thread.sleep(2000);
        producerRedG1.start();
        producerRedG2.start();

        // producerBlueG1.join();
        producerRedG1.join();
        producerRedG2.join();
        receiver1.join();
        receiver2.join();

        jmsAdminOperations1 = container(1).getJmsOperations();
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue on server1: "
                + jmsAdminOperations1.getNumberOfConsumersOnQueue("InQueue"));
        jmsAdminOperations2 = container(2).getJmsOperations();
        log.info("Number of org.jboss.qa.hornetq.apps.clients on InQueue server2: "
                + jmsAdminOperations2.getNumberOfConsumersOnQueue("InQueue"));

        jmsAdminOperations1.close();
        jmsAdminOperations2.close();

        Assert.assertEquals("Number of received messages does not match on receiver1", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver1.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of received messages does not match on receiver2", NUMBER_OF_MESSAGES_PER_PRODUCER,
                receiver2.getListOfReceivedMessages().size());
        ArrayList<String> receiver1GroupiIDs = new ArrayList<String>();
        ArrayList<String> receiver2GroupiIDs = new ArrayList<String>();
        for (Map<String, String> m : receiver1.getListOfReceivedMessages()) {
            if (m.containsKey("JMSXGroupID") && !receiver1GroupiIDs.contains(m.get("JMSXGroupID"))) {
                receiver1GroupiIDs.add(m.get("JMSXGroupID"));
            }
        }
        for (Map<String, String> m : receiver2.getListOfReceivedMessages()) {
            if (m.containsKey("JMSXGroupID") && !receiver1GroupiIDs.contains(m.get("JMSXGroupID"))) {
                receiver2GroupiIDs.add(m.get("JMSXGroupID"));
            }
        }
        for (String gId : receiver1GroupiIDs) {
            if (receiver2GroupiIDs.contains(gId)) {
                Assert.assertEquals("GroupIDs was mixed", false, true);
            }

        }

        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails Two servers in cluster are started with different security
     * configuration. First server: group "user" can read nad write to any
     * queue, guests can write to InQueue and read from OutQueue. On second
     * server is not configured any "user group, rest of settings is same as
     * server 1. MDB with "user" group login is deployed on server one, reads
     * messages from InQueue and sends them to OutQueue. Producer produces
     * messages to InQueue on server one as "guest" and consumer tries to read
     * them from OutQueue on server two. Consumer should not received any
     * message because OutQueue on server two should be empty.
     * @tpProcedure <ul>
     * <li>Start two servers with destinations</li>
     * <li>Setup security and credentials on both of them</li>
     * <li>Deploy MDB on server one</li>
     * <li>Create create three producers and configure their message builders to
     * build messages with different JMSXGroupID and colors</li>
     * <li>Start producers to sending messages to both nodes</li>
     * <li>Create two consumers each of them will be connected on different node
     * in cluster</li>
     * <li>Start receiving messages</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Each receiver received 0 messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGroupingToFail() throws Exception {
        prepareServer(container(1), true);
        prepareServer(container(2), true);
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

        // setup security
        jmsAdminOperations1.setSecurityEnabled(true);
        jmsAdminOperations2.setSecurityEnabled(true);

        jmsAdminOperations1.setClusterUserPassword("password");
        jmsAdminOperations2.setClusterUserPassword("password");
        jmsAdminOperations1.addMessageGrouping("default", "LOCAL", "jms", 5000);
        jmsAdminOperations2.addMessageGrouping("default", "REMOTE", "jms", 5000);

        UsersSettings.forEapServer(container(1).getServerHome()).withUser("user", "user.1234", "user").create();

        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(1)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.SEND, "user").givePermissionToUsers(PermissionGroup.CONSUME, "guest")
                .create();

        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.InQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "user").givePermissionToUsers(PermissionGroup.SEND, "guest")
                .create();
        AddressSecuritySettings.forContainer(container(2)).forAddress("jms.queue.OutQueue")
                .givePermissionToUsers(PermissionGroup.CONSUME, "guest").create();

        // restart servers to changes take effect
        container(1).stop();
        container(1).kill();
        container(2).stop();
        container(2).kill();
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_SECURITY);

        // setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));

        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        producerRedG1.start();
        receiver2.start();

        producerRedG1.join();
        receiver2.join();

        Assert.assertEquals("Number of received messages does not match on receiver2", 0, receiver2.getListOfReceivedMessages()
                .size());
        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails Two servers in cluster with deployed destinations are
     * started. Two MDB with different subscriptionName and clientID are
     * deployed on each server where they create durable subscriptions on
     * inTopic and send received messages to outQueue. Producer sends messages
     * to inTopic. Consumer reads messages fom outQueue.
     * @tpProcedure <ul>
     * <li>Start two servers with destinations in cluster</li>
     * <li>deploy MDBs to servers which create durable subscription on inTopic -
     * with different clientId and subscriber name</li>
     * <li>create publisher on node-1 and receiver on node-2</li>
     * <li>Producer sends several hundred messages to inTopic</li>
     * <li>MDBs repost them to outQueue</li>
     * <li>receiver reads messages from output queue on node-2</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver gets 2x more messages than was send to inTopic
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithoutDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(false);
    }

    /**
     * @tpTestDetails Two servers in cluster with deployed destinations are
     * started. Two MDB with same subscriptionName and clientID are deployed on
     * each server where they create durable subscriptions on inTopic and send
     * received messages to outQueue. Producer sends messages to inTopic.
     * Consumer reads messages fom outQueue.
     * @tpProcedure <ul>
     * <li>Start two servers with destinations in cluster</li>
     * <li>deploy MDBs to servers which create durable subscription on inTopic -
     * with same clientId and subscriber name</li>
     * <li>create publisher on node-1 and receiver on node-2</li>
     * <li>Producer sends several hundred messages to inTopic</li>
     * <li>MDBs repost them to outQueue</li>
     * <li>receiver reads messages from output queue on node-2</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver gets as many messages as was send to inTopic
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithMdbOnTopicWithDifferentSubscription() throws Exception {
        clusterTestWithMdbOnTopic(true);
    }

    public void clusterTestWithMdbOnTopic(boolean mdbsWithDifferentSubscriptions) throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        if (mdbsWithDifferentSubscriptions) {

            container(1).deploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the
            // same
            container(2).deploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);
        } else {

            container(1).deploy(MDB_ON_TOPIC1);
            container(2).deploy(MDB_ON_TOPIC2);
        }

        // give it some time - mdbs to subscribe
        Thread.sleep(1000);

        // Send messages into input topic and read from out topic
        log.info("Start publisher and consumer.");
        PublisherClientAck publisher = new PublisherClientAck(container(1), inTopicJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 10000, 10, 10);

        publisher.start();
        receiver.start();

        publisher.join();
        receiver.join();

        if (mdbsWithDifferentSubscriptions) {
            Assert.assertEquals(
                    "Number of sent and received messages is different. There should be twice as many received messages"
                            + "than sent. Sent: " + publisher.getListOfSentMessages().size() + "Received: "
                            + receiver.getListOfReceivedMessages().size(), 2 * publisher.getListOfSentMessages().size(),
                    receiver.getListOfReceivedMessages().size());
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + 2
                    * NUMBER_OF_MESSAGES_PER_PRODUCER + " Received: " + receiver.getListOfReceivedMessages().size(), receiver
                    .getListOfReceivedMessages().size(), 2 * NUMBER_OF_MESSAGES_PER_PRODUCER);

        } else {
            Assert.assertEquals(
                    "Number of sent and received messages is not correct. There should be as many received messages as"
                            + " sent. Sent: " + publisher.getListOfSentMessages().size() + "Received: "
                            + receiver.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size(), receiver
                            .getListOfReceivedMessages().size());
            Assert.assertEquals("Receiver did not get expected number of messages. Expected: "
                    + NUMBER_OF_MESSAGES_PER_PRODUCER + " Received: " + receiver.getListOfReceivedMessages().size(), receiver
                    .getListOfReceivedMessages().size(), NUMBER_OF_MESSAGES_PER_PRODUCER);
        }

        Assert.assertFalse("Producer did not sent any messages. Sent: " + publisher.getListOfSentMessages().size(), publisher
                .getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(),
                receiver.getListOfReceivedMessages().size() == 0);

        if (mdbsWithDifferentSubscriptions) {

            container(1).deploy(MDB_ON_TOPIC1);
            // lets say I don't want to have two mdbs with just different subscription names in test suite, this will do the
            // same
            container(1).deploy(MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION);

        } else {

            container(1).deploy(MDB_ON_TOPIC1);
            container(2).deploy(MDB_ON_TOPIC2);
        }

        container(1).stop();

        container(2).stop();

    }

    // /**
    // * - Start 4 servers - local and remotes (1,2,3)
    // * - Send messages to remote 1 (message group ids 4 types)
    // * - Start consumer on remote 2
    // * - Crash remote 3
    // * - Send messages to remnote 1 (message group ids 4 types)
    // * - restart remote 3
    // * - chekc consumer whether it received all messages
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumer() throws Exception {
    // clusterWitMessageGroupingCrashServerWithNoConsumer(CONTAINER4_NAME, 20000);
    // }

    /**
     * @tpTestDetails Start 4 servers in cluster with message grouping. First
     * server is “local” and the others are “remote”. Producers send messages
     * with different group id to "REMOTE" node-2 and receivers receive them on
     * node-3. Kill node-4 ("REMOTE") and start it again after 20 seconds. Start
     * few more producers on node-2. Wait until producers and receivers finish.
     * Read messages from the servers a check whether receiver got all messages
     * with the same group id.
     * @tpProcedure <ul>
     * <li>Start four servers in cluster with message grouping. First is "LOCAL"
     * other are "REMOTE"</li>
     * <li>create group of 4 producers on node-2, each sends messages with
     * different groupID</li>
     * <li>create 4 consumers to consume send messages on node-3</li>
     * <li>crash server with REMOTE handler (node-4) and start it again after
     * 20s</li>
     * <li>create other group 4 producers on node-2, each sends messages with
     * different groupID (but same as first group)</li>
     * <li>wait for all producers and receivers to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receivers get all messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    // TODO see this
    // http://documentation-devel.engineering.redhat.com/site/documentation/en-US/JBoss_Enterprise_Application_Platform/6.4/html/Administration_and_Configuration_Guide/Clustered_Grouping.html
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void clusterTestWitMessageGroupingCrashRemoteWithNoConsumerLongRestart() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(container(4), 20000);
    }

    /**
     * @tpTestDetails Start 4 servers in cluster with message grouping. First
     * server is “local” and the others are “remote”. Producers send messages
     * with different group id to "REMOTE" node-2 and receivers receive them on
     * node-3. Kill node-1 ("LOCAL") and start it again after 20 seconds. Start
     * few more producers on node-2. Wait until producers and receivers finish.
     * Read messages from the servers a check whether receiver got all messages
     * with the same group id.
     * @tpProcedure <ul>
     * <li>Start four servers in cluster with message grouping. First is "LOCAL"
     * other are "REMOTE"</li>
     * <li>create group of 4 producers on node-2, each sends messages with
     * different groupID</li>
     * <li>create 4 consumers to consume send messages on node-3</li>
     * <li>crash server with LOCAL handler (node-1) and start it again after
     * 20s</li>
     * <li>create other group 4 producers on node-2, each sends messages with
     * different groupID (but same as first group)</li>
     * <li>wait for all producers and receivers to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receivers get all messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingCrashLocalWithNoConsumer() throws Exception {
        clusterWitMessageGroupingCrashServerWithNoConsumer(container(1), 20000);
    }

    public void clusterWitMessageGroupingCrashServerWithNoConsumer(Container serverToKill, long timeBetweenKillAndRestart)
            throws Exception {
        testMessageGrouping(serverToKill, timeBetweenKillAndRestart, container(3));
    }

    public void testMessageGrouping(Container serverToKill, long timeBetweenKillAndRestart, Container serverWithConsumer)
            throws Exception {

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = -1;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();

        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        groupMessageVerifiers.add(messageVerifier);

        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier(ContainerUtils.getJMSImplementation(serverWithConsumer));
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(serverWithConsumer, inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.setMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(3 * 1000);

        log.info("Kill server - " + serverToKill);
        // kill remote 3
        serverToKill.kill();

        Thread.sleep(3 * 1000);

        log.info("Killed server - " + serverToKill);

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }

        log.info("Wait for - " + timeBetweenKillAndRestart);
        Thread.sleep(timeBetweenKillAndRestart);
        log.info("Finished waiting for - " + timeBetweenKillAndRestart);

        // start killed server
        log.info("Start server - " + serverToKill);
        serverToKill.start();
        log.info("Started server - " + serverToKill);

        // wait timeout time to get messages redistributed to the other node
        Thread.sleep(2 * 1000);

        // stop producers
        for (ProducerTransAck p : producers) {
            p.stopSending();
            p.join();
            // we need to add messages manually!!!
            for (FinalTestMessageVerifier groupMessageVerifier : groupMessageVerifiers) {
                groupMessageVerifier.addSendMessages(p.getListOfSentMessages());
            }
        }
        // check consumers
        for (ReceiverTransAck r : receivers) {
            r.join();
            messageVerifier.addReceivedMessages(r.getListOfReceivedMessages());
        }

        // now message verifiers
        for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
            verifier.verifyMessages();
        }

        Assert.assertEquals("There is different number of sent and received messages: ", messageVerifier.getSentMessages()
                .size(), messageVerifier.getReceivedMessages().size());

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    /**
     * @tpTestDetails Start 4 servers in cluster with message grouping. First
     * server is “local” and the others are “remote”. One producer send 500
     * messages with different group-id for each to inQueue on node-1. Then for
     * each used group create own producer and connect him to one of four
     * servers (based on order in sequence), this producer starts messages with
     * its specific group-id. or each used group-id is also created one
     * consumer, connected to the same server as producer. During sending and
     * receiving messages is three times executed crash sequence of node-2.
     * @tpProcedure <ul>
     * <li>Start four servers in cluster with message grouping. First is "LOCAL"
     * other are "REMOTE"</li>
     * <li>create one producer which sends 500 messages to inQueue on
     * node-1,each message has different group-id</li>
     * <li>round-robin algorithm on LOCAL handler ensure, that every node in
     * cluster has assigned equal amount of groups</li>
     * <li>for each used group create own producer and consumer connected to
     * server and start sending/receiving messages</li>
     * <li>server election for clients is based on reverted round-robin
     * algorithm</li>
     * <li>crash sequence of node-2 is executed in 20s intervals </li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receivers get all messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMessageGroupingRestartOfRemote() throws Exception {

        Container serverToKill = container(2);
        long timeBetweenKillAndRestart = 20000;

        int numberOfMessages = 10000;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<String> groups = new ArrayList<String>();

        Context context = container(1).getContext();
        ConnectionFactory factory = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(inQueueJndiNameForMdb);
        MessageProducer producer = session.createProducer(queue);
        Message m = null;

        for (int i = 0; i < 500; i++) {
            m = session.createTextMessage();
            String group = UUID.randomUUID().toString();
            m.setStringProperty("JMSXGroupID", group);
            producer.send(m);
            if (i % 100 == 0) {
                groups.add(group);
            }
        }

        // start producers and consumers
        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>(); // create 500 groups
        List<Client> receivers = new ArrayList<Client>();

        Container serverToConnect = null;
        for (String group : groups) {
            if (groups.lastIndexOf(group) % 4 == 0) {
                serverToConnect = container(1);

            } else if (groups.lastIndexOf(group) % 4 == 1) {
                serverToConnect = container(2);
            } else if (groups.lastIndexOf(group) % 4 == 2) {
                serverToConnect = container(3);
            } else {
                serverToConnect = container(4);
            }

            ProducerTransAck producerToInQueue1 = new ProducerTransAck(serverToConnect, inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, group));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
            ReceiverTransAck receiver = new ReceiverTransAck(serverToConnect, inQueueJndiNameForMdb, 40000, 100, 10);
            receiver.start();
            receivers.add(receiver);
        }

        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 300, 120000);

        log.info("Kill server - " + serverToKill);
        // kill remote 1
        serverToKill.kill();
        Thread.sleep(3 * 1000);
        log.info("Killed server - " + serverToKill);

        log.info("Wait for - " + timeBetweenKillAndRestart);
        Thread.sleep(timeBetweenKillAndRestart);
        log.info("Finished waiting for - " + timeBetweenKillAndRestart);

        // start killed server
        log.info("Start server - " + serverToKill);
        serverToKill.start();
        log.info("Started server - " + serverToKill);

        // wait timeout time to get messages redistributed to the other node
        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(receivers, 600, 120000);

        int numberOfSendMessages = 0;
        int numberOfReceivedMessages = 0;

        // stop producers
        for (ProducerTransAck p : producers) {
            p.stopSending();
            p.join();
            numberOfSendMessages += p.getListOfSentMessages().size();
        }

        // wait for consumers to finish
        for (Client r : receivers) {
            r.join();
            numberOfReceivedMessages += ((ReceiverTransAck) r).getListOfReceivedMessages().size();
        }

        Assert.assertEquals("Number of send and received messages is different: ", numberOfSendMessages + 500,
                numberOfReceivedMessages);

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    /**
     * @tpTestDetails Start 2 servers in cluster with message grouping. First
     * server has LOCAL group handler second has REMOTE. Create one consumer on
     * node-2 and two producers (one for each server). Producers starts sending
     * messages to inQueue, each producer uses own group-id. After producers
     * finish, node-1 is killed and started again. Then two new consumers are
     * created with same configuration as previous one. Wait for consumer and
     * producers to finish
     * @tpProcedure <ul>
     * <li>Start two servers in cluster with message grouping. First is "LOCAL"
     * second is "REMOTE"</li>
     * <li>create one consumer on node-2 reading messages from inQueue</li>
     * <li>create two producers one for each node sending messages with two
     * different group-ids</li>
     * <li>when producer finishes, kill node-1 and start it again</li>
     * <li>create two other producers with the same configurations as first
     * couple had</li>
     * <li>wait for producers and receiver to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver get all send messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGrouping() throws Exception {

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);

        container(2).start();
        container(1).start();
        Thread.sleep(5000);
        FinalTestMessageVerifier verifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 30000, 100, 10);
        receiver.setMessageVerifier(verifier);
        receiver.start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue2 = new ProducerTransAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue1.start();
        producerToInQueue2.start();

        producerToInQueue1.join();
        producerToInQueue2.join();

        container(1).kill();

        container(1).start();
        Thread.sleep(5000);

        ProducerTransAck producerToInQueue3 = new ProducerTransAck(container(1), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue4 = new ProducerTransAck(container(2), inQueueJndiNameForMdb,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue3.start();
        producerToInQueue4.start();

        producerToInQueue3.join();
        producerToInQueue4.join();

        receiver.join();

        verifier.addSendMessages(producerToInQueue1.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue2.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue3.getListOfSentMessages());
        verifier.addSendMessages(producerToInQueue4.getListOfSentMessages());

        verifier.verifyMessages();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Number of sent and received messages is not correct. There should be " + 4
                        * NUMBER_OF_MESSAGES_PER_PRODUCER + " received but it's : " + receiver.getListOfReceivedMessages().size(),
                4 * NUMBER_OF_MESSAGES_PER_PRODUCER, receiver.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails Start 2 servers in cluster with message grouping. First
     * server has LOCAL group handler second has REMOTE. Create one consumer on
     * node-2 and producer on node-1. Producer sends messages with same
     * group-id, after producer finishes consumer starts receiving messages.
     * @tpProcedure <ul>
     * <li>start two servers in cluster with message grouping. First is "LOCAL"
     * second is "REMOTE"</li>
     * <li>crate producer on node-1 which starts sending messages with same
     * group-id for every message</li>
     * <li>wait for producer to finish</li>
     * <li>create one producer on node-2 which consumes all send messages</li>
     * <li>wait for consumer to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver get all send messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingSimple() throws Exception {

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 0;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout);

        container(2).start();
        container(1).start();

        log.info("Send messages to first server.");
        sendMessages(container(1), inQueueJndiNameForMdb, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        log.info("Send messages to first server - done.");

        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver after kill got: " + receiver.getListOfReceivedMessages().size());
        Assert.assertTrue(
                "Number received messages must be 0 as producer was not up in parallel with consumer. There should be 0"
                        + " received but it's: " + receiver.getListOfReceivedMessages().size(), receiver
                        .getListOfReceivedMessages().size() == 0);

        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails Start 2 servers in cluster with deployed destinations and
     * configured exclusive diverts on inQueue routing messages to outQueue on
     * both servers. Producer sends small messages to inQueue on node-1, two
     * receivers receive messages from inQueue and outQueue on node-2. When
     * clients finish, number of received messages is verified.
     * @tpProcedure <ul>
     * <li>start two serversin cluster with identically configured diverts on
     * both </li>
     * <li>divert configuration: messages from inQueue are routed to outQueue
     * exclusively</li>
     * <li>crate producer on node-1 which starts sending small (normal) messages
     * to inQueue</li>
     * <li>create two receivers, receiving messages from inQueue and outQueue on
     * node-2</li>
     * <li>wait for clients to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver on outQueue gets all send messages, receiver on
     * inQueue gets none.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsExclusiveSmallMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(1, 1));
    }

    /**
     * @tpTestDetails Start 2 servers in cluster with deployed destinations and
     * configured non-exclusive diverts on inQueue routing messages to outQueue
     * on both servers. Producer sends small messages to inQueue on node-1, two
     * receivers receive messages from inQueue and outQueue on node-2. When
     * clients finish, number of received messages is verified.
     * @tpProcedure <ul>
     * <li>start two serversin cluster with identically configured diverts on
     * both </li>
     * <li>divert configuration: messages from inQueue are routed to outQueue
     * non-exclusively</li>
     * <li>crate producer on node-1 which starts sending small (normal) messages
     * to inQueue</li>
     * <li>create two receivers, receiving messages from inQueue and outQueue on
     * node-2</li>
     * <li>wait for clients to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver on outQueue gets all send messages, receiver on
     * inQueue gets none.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsNotExclusiveSmallMessages() throws Exception {
        clusterTestWithDiverts(false, new ClientMixMessageBuilder(1, 1));
    }

    /**
     * @tpTestDetails Start 2 servers in cluster with deployed destinations and
     * configured exclusive diverts on inQueue routing messages to outQueue on
     * both servers. Producer sends large messages to inQueue on node-1, two
     * receivers receive messages from inQueue and outQueue on node-2. When
     * clients finish, number of received messages is verified.
     * @tpProcedure <ul>
     * <li>start two serversin cluster with identically configured diverts on
     * both </li>
     * <li>divert configuration: messages from inQueue are routed to outQueue
     * exclusively</li>
     * <li>crate producer on node-1 which starts sending large messages to
     * inQueue</li>
     * <li>create two receivers, receiving messages from inQueue and outQueue on
     * node-2</li>
     * <li>wait for clients to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver on outQueue gets all send messages, receiver on
     * inQueue gets none.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsExclusiveLargeMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(120, 120));
    }

    /**
     * @tpTestDetails Start 2 servers in cluster with deployed destinations and
     * configured non-exclusive diverts on inQueue routing messages to outQueue
     * on both servers. Producer sends large messages to inQueue on node-1, two
     * receivers receive messages from inQueue and outQueue on node-2. When
     * clients finish, number of received messages is verified.
     * @tpProcedure <ul>
     * <li>start two servers in cluster with identically configured diverts on
     * both </li>
     * <li>divert configuration: messages from inQueue are routed to outQueue
     * non-exclusively</li>
     * <li>crate producer on node-1 which starts sending large messages to
     * inQueue</li>
     * <li>create two receivers, receiving messages from inQueue and outQueue on
     * node-2</li>
     * <li>wait for clients to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receiver on outQueue gets all send messages, receiver on
     * inQueue gets none.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithDivertsNotExclusiveLargeMessages() throws Exception {
        clusterTestWithDiverts(true, new ClientMixMessageBuilder(120, 120));
    }

    /**
     * Start 2 servers in cluster - Create divert which sends message to queue -
     * exclusive + non-exclusive - Check that messages gets load-balanced
     */
    private void clusterTestWithDiverts(boolean isExclusive, MessageBuilder messageBuilder) throws Exception {

        int numberOfMessages = 100;

        prepareServers();

        String divertName = "myDivert";
        String divertAddress = "jms.queue." + inQueueNameForMdb;
        String forwardingAddress = "jms.queue." + outQueueNameForMdb;

        createDivert(container(1), divertName, divertAddress, forwardingAddress, isExclusive, null, "123", null);
        createDivert(container(2), divertName, divertAddress, forwardingAddress, isExclusive, null, "456", null);

        container(1).start();
        container(2).start();

        // start client
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiNameForMdb, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.start();

        ReceiverClientAck receiverOriginalAddress = new ReceiverClientAck(container(2), inQueueJndiNameForMdb, 30000, 100, 10);
        receiverOriginalAddress.start();

        ReceiverClientAck receiverDivertedAddress = new ReceiverClientAck(container(2), outQueueJndiNameForMdb, 30000, 100, 10);
        receiverDivertedAddress.start();

        producerToInQueue1.join(60000);
        receiverOriginalAddress.join(60000);
        receiverDivertedAddress.join(60000);

        // if exclusive check that messages are just in diverted address
        if (isExclusive) {
            Assert.assertEquals(
                    "In exclusive mode there must be messages just in diverted address only but there are messages in "
                            + "original address.", 0, receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In exclusive mode there must be messages in diverted address.", numberOfMessages,
                    receiverDivertedAddress.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("In non-exclusive mode there must be messages in " + "original address.", numberOfMessages,
                    receiverOriginalAddress.getListOfReceivedMessages().size());
            Assert.assertEquals("In non-exclusive mode there must be messages in diverted address.", numberOfMessages,
                    receiverDivertedAddress.getListOfReceivedMessages().size());
        }

        container(1).stop();
        container(2).stop();

    }

    private void createDivert(Container container, String divertName, String divertAddress, String forwardingAddress,
                              boolean isExclusive, String filter, String routingName, String transformerClassName) {

        container.start();
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.addDivert(divertName, divertAddress, forwardingAddress, isExclusive, filter, routingName,
                transformerClassName);
        jmsOperations.close();
        container.stop();
    }

    /**
     * @tpTestDetails Start 2 servers in cluster with deployed destinations and
     * message grouping, first server has LOCAL handler and second server has
     * REMOTE handler. Start four producers, sending messages to inQueue on
     * first server each producer uses different group-id. Two consumers are
     * created on the same server as producers and reads messages from inQueue
     * @tpProcedure <ul>
     * <li>start two servers in cluster with message grouping and deployed
     * destinations, first with local handler second with remote</li>
     * <li>4 producer starts sending messages to inQueue (each producer uses
     * different group-id)</li>
     * <li>create two receivers, receiving messages from inQueue and outQueue on
     * node-1</li>
     * <li>wait for clients to finish</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Receivers get all send messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWitMessageGroupingTestSingleLocalServer() throws Exception {
        clusterTestWitMessageGroupingTestSingleServer(container(1));
    }

    // /**
    // * Now stop local and start remote server and send messages to it
    // * Start consumers on remote and check messages.
    // */
    // @Test
    // @RunAsClient
    // @CleanUpBeforeTest
    // @RestoreConfigBeforeTest
    // public void clusterTestWitMessageGroupingTestSingleRemoteServer() throws Exception {
    // clusterTestWitMessageGroupingTestSingleServer(CONTAINER2_NAME);
    // }
    public void clusterTestWitMessageGroupingTestSingleServer(Container testedContainer) throws Exception {

        int numberOfMessages = 10;

        prepareServers();

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        Container serverWithLocalHandler = container(1);
        Container serverWithRemoteHandler = container(2);
        // set local grouping-handler on 1st node
        addMessageGrouping(serverWithLocalHandler, name, "LOCAL", address, timeout);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(serverWithRemoteHandler, name, "REMOTE", address, timeout);

        // first try just local
        testedContainer.start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();
        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(testedContainer, inQueueJndiNameForMdb, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }
        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier(ContainerUtils.getJMSImplementation(testedContainer));
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(testedContainer, inQueueJndiNameForMdb, 10000, 100, 10);
            receiver.setMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        int numberOfSentMessages = 0;
        for (ProducerTransAck p : producers) {
            p.join();
            for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
                verifier.addSendMessages(p.getListOfSentMessages());
            }
            numberOfSentMessages = numberOfSentMessages + p.getListOfSentMessages().size();
        }

        int numberOfReceivedMessages = 0;
        for (ReceiverTransAck r : receivers) {
            r.join();
            numberOfReceivedMessages = numberOfReceivedMessages + r.getListOfReceivedMessages().size();
        }

        for (FinalTestMessageVerifier verifier : groupMessageVerifiers) {
            verifier.verifyMessages();
        }

        if (serverWithLocalHandler.equals(testedContainer)) {
            Assert.assertEquals("There is different number of sent and received messages.", numberOfSentMessages,
                    numberOfReceivedMessages);
        } else if (serverWithRemoteHandler.equals(testedContainer)) {
            Assert.assertEquals("There must be 0 messages received.", 0, numberOfReceivedMessages);
        }

        testedContainer.stop();
    }

    private void sendMessages(Container cont, String queue, MessageBuilder messageBuilder) throws InterruptedException {

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(cont, queue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        log.info("Start producer to send messages to node " + cont.getName() + " to destination: " + queue);
        producerToInQueue1.start();
        producerToInQueue1.join();

    }

    private void addMessageGrouping(Container container, String name, String type, String address, long timeout) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, 500, 750);
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Create org.jboss.qa.hornetq.apps.clients with the given acknowledge mode
     * on topic or queue.
     *
     * @param acknowledgeMode can be Session.AUTO_ACKNOWLEDGE,
     *                        Session.CLIENT_ACKNOWLEDGE, Session.SESSION_TRANSACTED
     * @param topic           true for topic
     * @return org.jboss.qa.hornetq.apps.clients
     * @throws Exception
     */
    private Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
        messageBuilder.setAddDuplicatedHeader(true);
        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(100);
        clients.setReceivedMessagesAckCommitAfter(100);

        return clients;
    }

    /**
     * Prepares several servers for topology. Destinations will be created.
     */
    public void prepareServers() {
        prepareServers(true);
    }

    /**
     * Prepares several servers for topology.
     *
     * @param createDestinations Create destination topics and queues and topics
     *                           if true, otherwise no.
     */
    public void prepareServers(boolean createDestinations) {
        prepareServer(container(1), createDestinations);
        prepareServer(container(2), createDestinations);
        prepareServer(container(3), createDestinations);
        prepareServer(container(4), createDestinations);
    }

    /**
     * Prepares server for topology. Destination queues and topics will be
     * created.
     *
     * @param container The container - defined in arquillian.xml
     */
    private void prepareServer(Container container) {
        prepareServer(container, true);
    }

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics
     *                           if true, otherwise no.
     */
    private void prepareServer(Container container, boolean createDestinations) {
        prepareServer(container, createDestinations, -1);
    }

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics
     * @param reconnectAttempts  number of reconnect attempts in cluster connection
     *                           if true, otherwise no.
     */
    private void prepareServer(Container container, boolean createDestinations, int reconnectAttempts) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        try {

            if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
                jmsAdminOperations.setClustered(true);

            }
            jmsAdminOperations.setPersistenceEnabled(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                    connectorName);

            jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
            jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, reconnectAttempts);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();
            // jmsAdminOperations.setLoggingLevelForConsole("INFO");
            // jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
            if (createDestinations) {
                for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
                    jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
                }

                for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
                    jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
                }

                jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
                jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);
                jmsAdminOperations.createTopic(inTopicNameForMdb, inTopicJndiNameForMdb);
                jmsAdminOperations.createTopic(outTopicNameForMdb, outTopicJndiNameForMdb);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();

        }

    }

    @Before
    @After
    public void stopAllServers() {

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    public static JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnTempQueue1(Container container) {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(getJmsXmlWithQueues(container)), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/mdbOnQueue1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueue3() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue3.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueue4() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue4.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnTempQueue2(Container container) {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue2.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset(getJmsXmlWithQueues(container)), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueueWithSecurity() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public static JavaArchive createDeploymentMdbOnQueueWithSecurity2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1Security2.jar");
        mdbJar.addClass(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTopic1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @param container
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTempTopic1(Container container) {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopicToTopic.class);
        mdbJar.addAsManifestResource(new StringAsset(getJmsXmlWithTopic(container)), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @param container
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTempTopic2(Container container) {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopicToTopic.class);
        mdbJar.addAsManifestResource(new StringAsset(getJmsXmlWithTopic(container)), "hornetq-jms.xml");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTopic2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTopicWithSub1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopicWithSub1.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigTopic.class);
        log.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue and sends reply back to sender via tempQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1withTempQueue.jar");
        mdbJar.addClass(LocalMdbFromQueueToTempQueue.class);
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    // jms/queue/InQueue
    public static String getJmsXmlWithQueues(Container container) {
        if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">\n");
            sb.append("<hornetq-server>\n");
            sb.append("<jms-destinations>\n");
            sb.append("<jms-queue name=\"");
            sb.append(inQueueNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(inQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(inQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("<jms-queue name=\"");
            sb.append(outQueueNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(outQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(outQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("</jms-destinations>\n");
            sb.append("</hornetq-server>\n");
            sb.append("</messaging-deployment>\n");
            log.info(sb.toString());
            return sb.toString();

        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-activemq-deployment:1.0\">\n");
            sb.append("<server>\n");
            sb.append("<jms-destinations>\n");
            sb.append("<jms-queue name=\"");
            sb.append(inQueueNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(inQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(inQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("<jms-queue name=\"");
            sb.append(outQueueNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(outQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(outQueueJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("</jms-destinations>\n");
            sb.append("</server>\n");
            sb.append("</messaging-deployment>\n");
            log.info(sb.toString());
            return sb.toString();
        }

    }

    public static String getJmsXmlWithTopic(Container container) {
        if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-deployment:1.0\">\n");
            sb.append("<hornetq-server>\n");
            sb.append("<jms-destinations>\n");
            sb.append("<jms-topic name=\"");
            sb.append(inTopicNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(inTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(inTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("<jms-topic name=\"");
            sb.append(outTopicNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(outTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(outTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("</jms-destinations>\n");
            sb.append("</hornetq-server>\n");
            sb.append("</messaging-deployment>\n");
            log.info(sb.toString());
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<messaging-deployment xmlns=\"urn:jboss:messaging-activemq-deployment:1.0\">\n");
            sb.append("<server>\n");
            sb.append("<jms-destinations>\n");
            sb.append("<jms-topic name=\"");
            sb.append(inTopicNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(inTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(inTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("<jms-topic name=\"");
            sb.append(outTopicNameForMdb);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(outTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(outTopicJndiNameForMdb);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("</jms-destinations>\n");
            sb.append("</server>\n");
            sb.append("</messaging-deployment>\n");
            log.info(sb.toString());
            return sb.toString();
        }

    }

    public ArrayList<File> getAllPagingFiles(String pagingDirectoryPath) {
        File mainPagingDirectoryPath = new File(pagingDirectoryPath);
        ArrayList<File> out = new ArrayList<File>();
        // get all paging directories in main paging directory
        ArrayList<File> pagingDirectories = new ArrayList<File>(Arrays.asList(mainPagingDirectoryPath.listFiles()));
        for (File dir : pagingDirectories) {
            // if some file exists in main paging directory skip it
            if (dir.isDirectory()) {
                ArrayList<File> files = new ArrayList<File>(Arrays.asList(dir.listFiles()));
                for (File f : files) {
                    // name of file can not contain "address" because its file which doesn't contain paging data nad still
                    // exists in paging directories
                    if (f.isFile() && !f.getName().contains("address")) {
                        out.add(f);
                    }

                }

            }

        }

        return out;
    }

    private void removeInQueue(Container... containers) {
        for (Container container : containers) {
            container.start();
            JMSOperations ops = container.getJmsOperations();
            try {
                ops.removeQueue(inQueueNameForMdb);
            } catch (Exception e) {
                ///ignore
            }
            ops.close();
            container.stop();
        }
    }

}
