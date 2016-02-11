package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerResp;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherAutoAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberAutoAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToTempQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicToTopic;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigTopic;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
public class ClusterTestCase extends ClusterTestBase {

    private static final Logger log = Logger.getLogger(ClusterTestCase.class);

    private final JavaArchive MDB_ON_QUEUE1 = createDeploymentMdbOnQueue1();
    private final JavaArchive MDB_ON_QUEUE2 = createDeploymentMdbOnQueue2();
    private final JavaArchive MDB_ON_QUEUE3 = createDeploymentMdbOnQueue3();
    private final JavaArchive MDB_ON_QUEUE4 = createDeploymentMdbOnQueue4();

    private final JavaArchive MDB_ON_TEMPQUEUE1 = createDeploymentMdbOnTempQueue1(container(1));
    private final JavaArchive MDB_ON_TEMPQUEUE2 = createDeploymentMdbOnTempQueue2(container(2));

    private final JavaArchive MDB_ON_TOPIC1 = createDeploymentMdbOnTopic1();
    private final JavaArchive MDB_ON_TOPIC2 = createDeploymentMdbOnTopic2();

    private final JavaArchive MDB_ON_TEMPTOPIC1 = createDeploymentMdbOnTempTopic1(container(1));
    private final JavaArchive MDB_ON_TEMPTOPIC2 = createDeploymentMdbOnTempTopic2(container(2));

    private final JavaArchive MDB_ON_QUEUE1_TEMP_QUEUE = createDeploymentMdbOnQueue1Temp();

    private final JavaArchive MDB_ON_TOPIC_WITH_DIFFERENT_SUBSCRIPTION = createDeploymentMdbOnTopicWithSub1();



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

    /**
     * @tpTestDetails Start two server in HornetQ cluster with redistributionDelay -1
     * and deploy queue and topic to each. Queue and topic are load-balanced.
     * Start producer which sends messages to queue on first server. Start publisher
     * which sends messages to topic on second server. Start consumer which reads messages
     * from queue on second server. Start subscriber, which reads messages from
     * topic on first server. Verify that consumers did not receive any message.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with redistributionDelay -1 and one queue and one topic
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
     * @tpPassCrit receiver and subscriber receives no message
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithNegativeRedistributionDelay() throws Exception {

        prepareServers();

        setRedistributionDelay(-1, container(1), container(2));

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

        Assert.assertEquals("Produced did not send expected count of messages", NUMBER_OF_MESSAGES_PER_PRODUCER, queueProducer.getCount());
        Assert.assertEquals("Publisher did not send expected count of messages", NUMBER_OF_MESSAGES_PER_PRODUCER, topicProducer.getCount());

        Assert.assertEquals("Number of received messages from queue does not match: ", 0, queueConsumer.getCount());
        Assert.assertEquals("Number of received messages form topic does not match: ", 0, topicSubscriber.getCount());

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
        setClusterNetworkTimeOuts(container(2), 2000, 2000, 4000);
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
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), inQueueJndiNameForMdb, 30000, 100, 10);
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

    private void setRedistributionDelay(int redistributionDelay, Container... containers) {
        for (Container container : containers) {
            container.start();
            JMSOperations ops = container.getJmsOperations();
            ops.removeAddressSettings("#");
            ops.addAddressSettings("#", "PAGE", 1024 * 1024, 0, redistributionDelay, 512 * 1024);
            ops.close();
            container.stop();
        }
    }

}
