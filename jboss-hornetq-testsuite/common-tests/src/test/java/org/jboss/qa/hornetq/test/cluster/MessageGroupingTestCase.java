package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.GroupMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MixMessageGroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * These tests serve topology with messaging grouping handlers.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class MessageGroupingTestCase extends ClusterTestBase {

    private static final Logger log = Logger.getLogger(MessageGroupingTestCase.class);

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

    private void addMessageGrouping(Container container, String name, String type, String address, long timeout) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, 500, 750);
        jmsAdminOperations.close();
        container.stop();
    }

    private void sendMessages(Container cont, String queue, MessageBuilder messageBuilder) throws InterruptedException {

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(cont, queue, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        log.info("Start producer to send messages to node " + cont.getName() + " to destination: " + queue);
        producerToInQueue1.start();
        producerToInQueue1.join();

    }

}
