package org.jboss.qa.hornetq.test.cluster;

import category.Cluster;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.ConfigurableMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
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
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
@Category(Cluster.class)
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare(value = "FourNodes", params = {
        @Param(name = PrepareParams.CLUSTER_TYPE, value = "MULTICAST")
})
public class ClusterTestCase extends HornetQTestCase {

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

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;


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

        container(2).start();
        container(1).start();

        ConfigurableMessageVerifier topicVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ConfigurableMessageVerifier queueVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

        Client queueProducer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0", NUMBER_OF_MESSAGES_PER_PRODUCER);
        Client topicProducer = new PublisherTransAck(container(2), PrepareConstants.TOPIC_JNDI_PREFIX + "0", NUMBER_OF_MESSAGES_PER_PRODUCER, "producer");
        Client queueConsumer = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI_PREFIX + "0");
        SubscriberTransAck topicSubscriber = new SubscriberTransAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX + "0", 60000, 100, 10, "subs", "name");
        topicSubscriber.subscribe();

        queueProducer.addMessageVerifier(queueVerifier);
        queueConsumer.addMessageVerifier(queueVerifier);
        topicProducer.addMessageVerifier(topicVerifier);
        topicSubscriber.addMessageVerifier(topicVerifier);

        queueProducer.start();
        topicSubscriber.start();
        topicProducer.start();
        queueConsumer.start();
        queueProducer.join();
        topicProducer.join();
        queueConsumer.join();
        topicSubscriber.join();

        //print number of added messages to nodes and destinations
        JMSTools.getAddedMessagesCount(PrepareConstants.TOPIC_NAME_PREFIX + "0", true, container(2));
        JMSTools.getAddedMessagesCount(PrepareConstants.TOPIC_NAME_PREFIX + "0", true, container(1));
        JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME_PREFIX + "0", container(1));
        JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME_PREFIX + "0", container(2));

        Assert.assertTrue(queueVerifier.verifyMessages());
        Assert.assertTrue(topicVerifier.verifyMessages());

        Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(), queueConsumer.getCount());
        Assert.assertEquals("Number of received messages form topic does not match: ", topicProducer.getCount(), topicSubscriber.getCount());

        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue InQueue and OutQueue to both of them.
     * Start producer which sends messages to InQueue to first server. Deploy MDB to 2nd server which
     * consumes messages from InQueue and for each message sends message to OutQueue.
     * When MDB is processing messages then shutdown node 2 (with the MDB)
     * @tpPassCrit Check there are not Exception in logs
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void shutdownNodeInClusterCheckNoExceptions() throws Exception {

        container(1).start();
        container(2).start();

        ProducerTransAck producer = new ProducerTransAck(container(2), PrepareConstants.IN_QUEUE_JNDI, 1000000);
        producer.start();
        usedClients.add(producer);
        ReceiverTransAck receiver = new ReceiverTransAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 10000, 4, 10);
        receiver.setTimeout(1000);
        receiver.start();
        usedClients.add(receiver);

        // deploy MDB
        container(1).deploy(MDB_ON_QUEUE1);

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.OUT_QUEUE_NAME, 50, 60000, container(1), container(2)));

        // shutdown node 1
        container(1).stop();

        // stop sending messages
        producer.stopSending();
        producer.join();
        receiver.join();

        container(2).stop();

        String stringToFind = "Exception";
        // check that logs does not contains Exceptions
        Assert.assertFalse("Server " + container(1).getName() + " cannot contain exceptions but there are. " +
                "Check logs of the server for details. Server logs for failed tests are archived in target directory " +
                "of the maven module with this test.", checkServerLogString(container(1), stringToFind));
        Assert.assertFalse("Server " + container(2).getName() + " cannot contain exceptions but there are. " +
                "Check logs of the server for details. Server logs for failed tests are archived in target " +
                "directory of the maven module with this test.", checkServerLogString(container(2), stringToFind));
    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and deploy queue InQueue and OutQueue to both of them.
     * Start producer which sends messages to InQueue to 2nd server. Deploy MDB to 1nd server which
     * consumes messages from InQueue and for each message sends message to OutQueue.
     * When MDB is processing messages then restart node 1 (with the MDB)
     * @tpPassCrit Check there are not Exception in logs
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void shutdownNodeInClusterWithRestart() throws Exception {


        container(1).start();
        container(2).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ProducerTransAck producer = new ProducerTransAck(container(2), PrepareConstants.IN_QUEUE_JNDI, 1000000);
        producer.addMessageVerifier(messageVerifier);
        producer.setCommitAfter(2);
        producer.start();
        usedClients.add(producer);
        ReceiverTransAck receiver = new ReceiverTransAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 30000, 5, 10);
        receiver.setTimeout(1000);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        usedClients.add(receiver);

        // deploy MDB
        container(1).deploy(MDB_ON_QUEUE1);

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, 50, 60000, container(1), container(2)));

        // restart node 1
        container(1).restart();

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, 100, 60000, container(1), container(2)));

        // stop sending messages
        producer.stopSending();
        receiver.setTimeout(0);
        receiver.setReceiveTimeout(5000);
        producer.join();
        receiver.join();

        container(2).stop();
        container(1).stop();

        Assert.assertEquals("Number of send and received messages is different.", producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());


    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster and restart 3 times, Check there are no errors or warnings from
     * Artemis/Netty.
     * @tpPassCrit Check there are not WARN/ERRORS in logs
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNoWarningErrorsDuringRestartingNodesInCluster() throws Exception {


        container(1).start();
        container(2).start();

        // restart servers 3 times
        for (int i = 0; i < 3; i++) {
            container(1).restart();
            container(2).restart();
        }

        container(2).stop();
        container(1).stop();

        String regex = "(.*)(WARN|ERROR)(.*)(org.apache.activemq.artemis|io.netty)(.*)";
        Assert.assertTrue("Server " + container(1).getName() + " must not contain ERRORS/WARNINGS from Artemis/Netty/JGroups but there are. " +
                "Check logs of the server for details. Server logs for failed tests are archived in target directory " +
                "of the maven module with this test. \n" + formatOutput(findRegexInFile(container(1), regex)), findRegexInFile(container(1), regex).size() == 0);
    }

    private String formatOutput(List<String> list) {
        StringBuilder output = new StringBuilder();
        output.append("###############################################");
        output.append("#################### BAD LOGS #################");
        output.append("###############################################");
        for (String s : list) {
            output.append(s + "\n");
        }
        output.append("###############################################");
        return output.toString();
    }

    private boolean checkServerLogString(Container container, String stringToFind) throws Exception {
        StringBuilder pathToServerLog = new StringBuilder(container.getServerHome());
        pathToServerLog.append(File.separator).append("standalone").append(File.separator)
                .append("log").append(File.separator).append("server.log");
        return CheckFileContentUtils.checkThatFileContainsGivenString(new File(pathToServerLog.toString()), stringToFind);
    }

    private List<String> findRegexInFile(Container container, String regex) throws Exception {
        StringBuilder pathToServerLog = new StringBuilder(container.getServerHome());
        pathToServerLog.append(File.separator).append("standalone").append(File.separator)
                .append("log").append(File.separator).append("server.log");
        return CheckFileContentUtils.findRegexInFile(new File(pathToServerLog.toString()), regex);
    }

    /**
     * @tpTestDetails Start two server in HornetQ cluster with redistributionDelay -1
     * and deploy queue and topic to each. Queue and topic are load-balanced.
     * Start producer which sends messages to queue on first server. Start publisher
     * which sends messages to topic on second server. After they finish, start consumer which reads messages
     * from queue on second server. Start subscriber, which reads messages from
     * topic on first server. Verify that consumers did not receive any message.
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with redistributionDelay -1 and one queue and one topic
     * </li>
     * <li>producer starts to send messages to queue on node-1</li>
     * <li>publisher starts to send messages to topic on node-2</li>
     * <li>consumer reads messages from queue on node-2</li>
     * <li>subscriber reads messages from topic on node-1</li>
     * <li>wait for receiver and subscriber to finish
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
    @Prepare(params = {
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "-1")
    })
    public void clusterTestWithNegativeRedistributionDelay() throws Exception {

        container(2).start();
        container(1).start();

        Client queueProducer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0", NUMBER_OF_MESSAGES_PER_PRODUCER);
        Client topicProducer = new PublisherTransAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX + "0", NUMBER_OF_MESSAGES_PER_PRODUCER, "producer");
        Client queueConsumer = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI_PREFIX + "0");
        SubscriberTransAck topicSubscriber = new SubscriberTransAck(container(2), PrepareConstants.TOPIC_JNDI_PREFIX + "0", 6000, 100, 10, "subs", "name");
        topicSubscriber.subscribe();
        topicSubscriber.close();

        queueProducer.start();
        topicProducer.start();

        queueProducer.join();
        topicProducer.join();

        queueConsumer.start();
        topicSubscriber.start();

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

        setClusterNetworkTimeOuts(container(1), 2000, 2000, 4000);
        setClusterNetworkTimeOuts(container(2), 2000, 2000, 4000);
        container(2).start();
        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        // A1 producer
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producer1.addMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(messageBuilder);
        producer1.start();

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, 300, 60000, container(1), container(2)));

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
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI, 30000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);
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
    public void testStopStartCluster() throws Exception {

        int numberOfMessages = 100000;

        container(1).start();
        container(2).start();

        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

        ProducerTransAck prod1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0", numberOfMessages);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        prod1.addMessageVerifier(messageVerifier);
        prod1.setMessageBuilder(messageBuilder);
        prod1.setTimeout(0);
        prod1.setCommitAfter(10);
        prod1.start();

        ProducerTransAck prod2 = new ProducerTransAck(container(2), PrepareConstants.QUEUE_JNDI_PREFIX + "0", numberOfMessages);
        prod2.addMessageVerifier(messageVerifier);
        prod2.setMessageBuilder(messageBuilder);
        prod2.setTimeout(0);
        prod2.setCommitAfter(5);
        prod2.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0", 120000, 10, 100);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI_PREFIX + "0", 120000, 10, 100);
        receiver2.setTimeout(0);
        receiver2.addMessageVerifier(messageVerifier);
        receiver2.start();

        ClientUtils.waitForReceiverUntil(receiver1, 300, 300000);
        ClientUtils.waitForReceiverUntil(receiver2, 300, 300000);

        // stop nodes
        container(1).stop();
        container(2).stop();
        // this is IMPORTANT
        Thread.sleep(60000);
        // start nodes
        container(1).start();
        container(2).start();

        receiver1.setReceiveTimeout(5000);
        receiver2.setReceiveTimeout(5000);
        ClientUtils.waitForReceiverUntil(receiver1, 500, 300000);
        ClientUtils.waitForReceiverUntil(receiver2, 500, 300000);

        prod1.stopSending();
        prod2.stopSending();
        prod1.join();
        prod2.join();
        receiver1.join();
        receiver2.join();

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", messageVerifier.verifyMessages());

        container(1).stop();

        container(2).stop();

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterTestWithKills() throws Exception {

        int numberOfMessages = 100000;

        setClusterNetworkTimeOuts(container(1), 10000, 10000, 20000);
        container(2).start();
        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        // A1 producer
        MessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producer1.addMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(messageBuilder);
        producer1.start();

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, 300, 10000, container(1), container(2)));

        for (int i = 0; i < 5; i++) {
            container(2).kill();
            Thread.sleep(20000);
            container(2).start();
            Thread.sleep(20000);
            container(1).kill();
            Thread.sleep(20000);
            container(1).start();
            Thread.sleep(20000);
        }

        producer1.stopSending();
        producer1.join();

        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI, 20000, 100, 10);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);


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
    @Prepare(params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, value = "1")
    })
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
    @Prepare(params = {
            @Param(name = PrepareParams.REMOTE_CONNECTION_FACTORY_RECONNECT_ATTEMPTS, value = "1")
    })
    public void clusterTestKillTargetServer1ReconnectAttemptWithRestart() throws Exception {
        clusterTestKillTargetServer1ReconnectAttempt(true);
    }

    public void clusterTestKillTargetServer1ReconnectAttempt(boolean withRestart) throws Exception {

        int numberOfMessages = 1000;

        container(2).start();
        container(1).start();

        // give it some time to create cluster
        Thread.sleep(5000);

        // stop server 2
        container(2).kill();

        //send messages
        ProducerTransAck queueProducer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0", numberOfMessages);
        queueProducer.setMessageBuilder(new TextMessageBuilder(1));
        queueProducer.setTimeout(0);
        queueProducer.setCommitAfter(10);
        queueProducer.start();
        queueProducer.join();

        // receive messages
        ReceiverTransAck queueConsumer1 = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0");
        queueConsumer1.setTimeout(0);
        queueConsumer1.setReceiveTimeout(10000);
        queueConsumer1.setCommitAfter(10);
        queueConsumer1.start();
        queueConsumer1.join();

        if (withRestart) {
            log.info("Restart 1st server and try to consume messages.");
            container(1).restart();
            // receive messages
            ReceiverTransAck queueConsumer2 = new ReceiverTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX + "0");
            queueConsumer2.setTimeout(0);
            queueConsumer2.setReceiveTimeout(10000);
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

        container(1).start();
        container(2).start();

        JMSTools.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        JMSTools.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new TextMessageBuilder(128));
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.addMessageVerifier(messageVerifier);
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

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(2), PrepareConstants.QUEUE_JNDI, 30000, 1000, 10);
        receiver1.addMessageVerifier(messageVerifier);
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

        container(2).start();

        container(1).start();

        // send messages without dup id -> load-balance to node 2
        ProducerTransAck producer1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
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

            queue = (Queue) context.lookup(PrepareConstants.QUEUE_JNDI);

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
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI, 10000, 10, 10);
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

        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_QUEUE1);

        container(2).deploy(MDB_ON_QUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 10000, 10, 10);

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

        container(4).start();
        container(3).start();
        container(2).start();
        container(1).start();


        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);
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


        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 10000, 10, 10);

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
    @Prepare(params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void clusterTestWithMdbOnQueueDeployAndUndeploy() throws Exception {
        int numberOfMessages = 30;
        container(2).start();

        container(1).start();

        container(1).deploy(MDB_ON_TEMPQUEUE1);

        container(2).deploy(MDB_ON_TEMPQUEUE2);

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 10000, 10, 10);

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
        boolean passed = false;

        container(1).start();

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
    @Prepare(params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void clusterTestWithMdbOnTopicDeployAndUndeployOneServerOnly() throws Exception {
        container(1).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(1), PrepareConstants.IN_TOPIC_JNDI, "subscriber1",
                "subscription1");
        subscriber.start();
        subscriber.join();
        container(1).undeploy(MDB_ON_TEMPTOPIC1);
        container(1).deploy(MDB_ON_TEMPTOPIC1);

        Assert.assertEquals("Number of subscriptions is not 0", 0,
                jmsAdminOperations.getNumberOfDurableSubscriptionsOnTopicForClient("mySubscription"));
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
    @Prepare(params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void clusterTestWithMdbOnTopicDeployAndUndeployTwoServers() throws Exception {
        container(1).start();
        container(2).start();
        container(1).deploy(MDB_ON_TEMPTOPIC1);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), PrepareConstants.OUT_TOPIC_JNDI, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), PrepareConstants.IN_TOPIC_JNDI, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();
        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopicForClient("subscriber1"));
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
    @Prepare(params = {
            @Param(name = "2." + PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void clusterTestWithMdbOnTopicCombinedDeployAndUndeployTwoServers() throws Exception {
        container(1).start();
        container(2).start();
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();
        SubscriberAutoAck subscriber = new SubscriberAutoAck(container(2), PrepareConstants.IN_TOPIC_JNDI, "subscriber1",
                "subscription1");
        PublisherAutoAck publisher = new PublisherAutoAck(container(1), PrepareConstants.IN_TOPIC_JNDI, 10, "publisher1");
        subscriber.start();
        publisher.start();
        publisher.join();
        subscriber.join();

        Assert.assertEquals("Number of delivered messages is not correct", 10, subscriber.getListOfReceivedMessages().size());
        container(2).undeploy(MDB_ON_TEMPTOPIC2);
        container(2).deploy(MDB_ON_TEMPTOPIC2);
        Assert.assertEquals("Number of subscriptions is not 0 on CLUSTER2", 0,
                jmsAdminOperations2.getNumberOfDurableSubscriptionsOnTopicForClient("subscriber1"));
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
        int number = 10;
        container(2).start();
        container(1).start();

        log.info("SERVERS ARE READY");
        log.info("STARTING CLIENTS");
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareConstants.QUEUE_JNDI);
        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(PrepareConstants.QUEUE_JNDI);
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
        container(2).start();
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        JMSOperations jmsAdminOperationsContainer1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperationsContainer2 = container(2).getJmsOperations();
        log.info("Starting producer");
        responsiveProducer.start();
        // Wait fro creating connections and send few messages
        Thread.sleep(5000);
        log.info("Producer sent " + responsiveProducer.getCount() + " to temp queue " + PrepareConstants.IN_QUEUE_JNDI);
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
    @Prepare(params = {
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 512 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 128 * 1024)
    })
    public void clusterTestPagingAfterFailOverTempQueue() throws Exception {
        container(1).start();
        String pagingPath = null;
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
    @Prepare(params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 512 * 1024),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 128 * 1024)
    })
    public void clusterTestPagingAfterFailOverNonDurableQueue() throws Exception {
        container(1).start();
        String pagingPath = null;
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(PrepareConstants.QUEUE_NAME, PrepareConstants.QUEUE_JNDI, false);
        pagingPath = jmsAdminOperations.getPagingDirectoryPath();
        Context context = container(1).getContext();
        Queue inqueue = (Queue) context.lookup(PrepareConstants.QUEUE_JNDI);
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
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
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), PrepareConstants.IN_QUEUE_JNDI, 1, 300);
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
        container(1).start();
        container(1).deploy(MDB_ON_QUEUE1_TEMP_QUEUE);

        int cont1Count = 0, cont2Count = 0;
        ProducerResp responsiveProducer = new ProducerResp(container(1), PrepareConstants.IN_QUEUE_JNDI, 1, 300);
        responsiveProducer.setMessageBuilder(new TextMessageBuilder(110 * 1024));
        responsiveProducer.start();
        responsiveProducer.setTimeout(600);
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
    @Prepare(params = {
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void clusterTestWithMdbHqXmlOnQueueRedeploy() throws Exception {

        container(1).start();
        container(1).deploy(MDB_ON_TEMPQUEUE1);
        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 10000, 10, 10);
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
        PublisherClientAck publisher = new PublisherClientAck(container(1), PrepareConstants.IN_TOPIC_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 10000, 10, 10);

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

        String divertName = "myDivert";
        String divertAddress = "jms.queue." + PrepareConstants.IN_QUEUE_NAME;
        String forwardingAddress = "jms.queue." + PrepareConstants.OUT_QUEUE_NAME;

        createDivert(container(1), divertName, divertAddress, forwardingAddress, isExclusive, null, "123", null);
        createDivert(container(2), divertName, divertAddress, forwardingAddress, isExclusive, null, "456", null);

        container(1).start();
        container(2).start();

        // start client
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setCommitAfter(10);
        producerToInQueue1.start();

        ReceiverClientAck receiverOriginalAddress = new ReceiverClientAck(container(2), PrepareConstants.IN_QUEUE_JNDI, 30000, 100, 10);
        receiverOriginalAddress.start();

        ReceiverClientAck receiverDivertedAddress = new ReceiverClientAck(container(2), PrepareConstants.OUT_QUEUE_JNDI, 30000, 100, 10);
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

    /**
     * @tpTestDetails Start 4 servers in cluster with deployed destinations and
     * configured redistribution delay to 0 and load balancing policy ON_DEMAND.
     * Send messages with property color set to GREEN or RED to node-1(ratio of
     * colors is 1:1). When all messages are send to node-1, create consumers
     * with selectors on color attribute. Attach red consumer to node2, green
     * consumer to node3 and blue consumer to node4. Blue consumer shouldn`t get
     * any message and no message should be routed to node4. Check that message
     * redistribution respects selectors and only messages matches consumers
     * selector are redistributed to node with appropriate consumer.
     * @tpProcedure <ul>
     * <li>start 4 servers in cluster</li>
     * <li>configure load balancing and redistribution delay</li>
     * <li>crate producer on node-1 which starts sending with color property</li>
     * <li>wait for producer finish</li>
     * <li>create 3 receivers. Red receiver on node2 receives red messages,
     * green receiver on node3 receives green messages, blue receiver on node4
     * should not receive any message</li>
     * <li>wait for clients to finish</li>
     * <li>check that messages are redistributed with respect of selectors (red
     * messages are send only to node with red consumer)</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Messages are redistributed only on servers with matching
     * consumers
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "0")
    })
    public void testRedistributionWithSelectors() throws Exception {

        int numberOfMessages = 1000;

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producer.setMessageBuilder(new ClientMixMessageBuilder(1, 150));
        producer.setCommitAfter(10);
        producer.start();
        producer.join();

        Assert.assertEquals("All messages should be on node 1, but some of them are missing", numberOfMessages, JMSTools.countMessages(PrepareConstants.QUEUE_NAME, container(1)));
        Assert.assertEquals("All messages should be on node 1, but some messages are on server 2", 0, JMSTools.countMessages(PrepareConstants.QUEUE_NAME, container(2)));
        Assert.assertEquals("All messages should be on node 1, but some messages are on server 3", 0, JMSTools.countMessages(PrepareConstants.QUEUE_NAME, container(3)));

        ReceiverTransAck redReceiver = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI);
        redReceiver.setSelector("color = 'RED'");
        redReceiver.start();

        ReceiverTransAck greenReceiver = new ReceiverTransAck(container(3), PrepareConstants.QUEUE_JNDI);
        greenReceiver.setSelector("color = 'GREEN'");
        greenReceiver.start();

        //we dont send blue msgs
        ReceiverTransAck blueReceiver = new ReceiverTransAck(container(4), PrepareConstants.QUEUE_JNDI);
        blueReceiver.setSelector("color = 'BLUE'");
        blueReceiver.start();

        redReceiver.join();
        blueReceiver.join();
        greenReceiver.join();


        Assert.assertEquals("Red consumer should receive all red msgs", numberOfMessages / 2, redReceiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Green consumer should receive all green msgs", numberOfMessages / 2, greenReceiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Blue consumer should not receive msgs", 0, blueReceiver.getListOfReceivedMessages().size());

        Assert.assertEquals("All messages should be redistributed from node without consumer", 0, JMSTools.countMessages(PrepareConstants.QUEUE_NAME, container(1)));
        Assert.assertEquals("Only messages matching selector should be send to this node", numberOfMessages / 2, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(2)));
        Assert.assertEquals("Only messages matching selector should be send to this node", numberOfMessages / 2, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(3)));
        Assert.assertEquals("None messages should be send to node with consumers selector not matching messages", 0, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(4)));


        stopAllServers();

    }

    /**
     * @tpTestDetails Start 4 servers in cluster with deployed destinations and
     * configured redistribution delay to -1 and load balancing policy ON_DEMAND.
     * create consumers with selectors on color attributeAttach red consumer to
     * node2, green consumer to node3 and blue consumer to node4. Blue consumer
     * shouldn`t get any message and no message should be routed to node4.
     * Send messages with property color set to GREEN or RED to node-1(ratio of
     * colors is 1:1). Check that serverside load balancing  respects selectors
     * and only messages matching consumers selector are redistributed to node
     * with appropriate consumer.
     * @tpProcedure <ul>
     * <li>start 4 servers in cluster</li>
     * <li>configure load balancing and redistribution delay</li>
     * <li>create 3 receivers. Red receiver on node2 receives red messages,
     * green receiver on node3 receives green messages, blue receiver on node4
     * should not receive any message</li>
     * <li>create producer on node-1 which starts sending with color property</li>
     * <li>wait for clients to finish</li>
     * <li>check that messages are load balanced with respect of selectors (red
     * messages are send only to node with red consumer)</li>
     * <li>Stop servers</li>
     * </ul>
     * @tpPassCrit Messages are loadbalanced only on servers with matching
     * consumers
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "-1")
    })
    public void testLoadBalancingWithSelectors() throws Exception {

        int numberOfMessages = 1000;

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        ReceiverTransAck redReceiver = new ReceiverTransAck(container(2), PrepareConstants.QUEUE_JNDI);
        redReceiver.setSelector("color = 'RED'");
        redReceiver.start();

        ReceiverTransAck greenReceiver = new ReceiverTransAck(container(3), PrepareConstants.QUEUE_JNDI);
        greenReceiver.setSelector("color = 'GREEN'");
        greenReceiver.start();

        //we dont send blue msgs
        ReceiverTransAck blueReceiver = new ReceiverTransAck(container(4), PrepareConstants.QUEUE_JNDI);
        blueReceiver.setSelector("color = 'BLUE'");
        blueReceiver.start();

        //give consumers time to connect and register on queue
        //once we start sending messages, all consumers must be
        //able to receive, redistribution is turned off.
        Thread.sleep(10*1000);

        ProducerTransAck producer = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producer.setMessageBuilder(new ClientMixMessageBuilder(1, 150));
        producer.setCommitAfter(10);
        producer.start();
        producer.join();

        redReceiver.join();
        blueReceiver.join();
        greenReceiver.join();

        Assert.assertEquals("Red consumer should receive all red msgs", numberOfMessages / 2, redReceiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Green consumer should receive all green msgs", numberOfMessages / 2, greenReceiver.getListOfReceivedMessages().size());
        Assert.assertEquals("Blue consumer should not receive msgs", 0, blueReceiver.getListOfReceivedMessages().size());

        Assert.assertEquals("All messages should be redistributed from node without consumer", 0, JMSTools.countMessages(PrepareConstants.QUEUE_NAME, container(1)));
        Assert.assertEquals("Only messages matching selector should be send to this node", numberOfMessages / 2, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(2)));
        Assert.assertEquals("Only messages matching selector should be send to this node", numberOfMessages / 2, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(3)));
        Assert.assertEquals("None messages should be send to node with consumers selector not matching messages", 0, JMSTools.getAddedMessagesCount(PrepareConstants.QUEUE_NAME, container(4)));


        stopAllServers();

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

    public JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue1.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public JavaArchive createDeploymentMdbOnTempQueue1(Container container) {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTempQueue1.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
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

    public JavaArchive createDeploymentMdbOnQueue3() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue3.jar");
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        log.info(mdbJar.toString(true));
        // File target = new File("/tmp/mdbOnQueue2.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public JavaArchive createDeploymentMdbOnQueue4() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue4.jar");
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
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
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("<jms-queue name=\"");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
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
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.IN_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("</jms-queue>\n");
            sb.append("<jms-queue name=\"");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.OUT_QUEUE_JNDI);
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
            sb.append(PrepareConstants.IN_TOPIC_NAME);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.IN_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.IN_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("<jms-topic name=\"");
            sb.append(PrepareConstants.OUT_TOPIC_NAME);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.OUT_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.OUT_TOPIC_JNDI);
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
            sb.append(PrepareConstants.IN_TOPIC_NAME);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.IN_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.IN_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("</jms-topic>\n");
            sb.append("<jms-topic name=\"");
            sb.append(PrepareConstants.OUT_TOPIC_NAME);
            sb.append("\">\n");
            sb.append("<entry name=\"java:jboss/exported/");
            sb.append(PrepareConstants.OUT_TOPIC_JNDI);
            sb.append("\"/>\n");
            sb.append("<entry name=\"");
            sb.append(PrepareConstants.OUT_TOPIC_JNDI);
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

}
