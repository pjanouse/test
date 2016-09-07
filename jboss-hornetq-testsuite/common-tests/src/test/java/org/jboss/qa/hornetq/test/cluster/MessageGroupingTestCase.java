package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.GroupColoredMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.GroupMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MixMessageGroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueueToQueueWithSelectorAndSecurity;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.security.AddressSecuritySettings;
import org.jboss.qa.hornetq.test.security.PermissionGroup;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * These tests serve topology with messaging grouping handlers.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare(value = "FourNodes", params = {
        @Param(name = PrepareParams.CLUSTER_TYPE, value = "MULTICAST")
})
public class MessageGroupingTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(MessageGroupingTestCase.class);

    private final JavaArchive MDB_ON_QUEUE1_SECURITY = createDeploymentMdbOnQueueWithSecurity();

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

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

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        Container serverWithLocalHandler = container(1);
        Container serverWithRemoteHandler = container(2);
        // set local grouping-handler on 1st node
        addMessageGrouping(serverWithLocalHandler, name, "LOCAL", address, timeout, 5000, 7500);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(serverWithRemoteHandler, name, "REMOTE", address, timeout, 2500, 0);

        // first try just local
        testedContainer.start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();
        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(testedContainer, PrepareBase.QUEUE_JNDI, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id" + i));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
        }
        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier(ContainerUtils.getJMSImplementation(testedContainer));
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(testedContainer, PrepareBase.QUEUE_JNDI, 10000, 100, 10);
            receiver.addMessageVerifier(groupMessageVerifier);
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

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout, 5000, 7500);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout, 2500, 0);

        container(2).start();
        container(1).start();

        log.info("Send messages to first server.");
        sendMessages(container(1), PrepareBase.QUEUE_JNDI, new MixMessageGroupMessageBuilder(10, 120, "id1"));
        log.info("Send messages to first server - done.");

        // try to read them from 2nd node
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareBase.QUEUE_JNDI, 10000, 100, 10);
        receiver.start();
        receiver.join();

        log.info("Receiver got messages: " + receiver.getListOfReceivedMessages().size());
        log.info("Messages on servers " + new JMSTools().countMessages(PrepareBase.QUEUE_NAME, container(1),container(2)));
        Assert.assertEquals(
                "Number received messages must be 0 as producer was not up in parallel with consumer. There should be 0"
                        + " received but it's: " + receiver.getListOfReceivedMessages().size(), 0, receiver.getListOfReceivedMessages().size());

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

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout, 5000, 7500);

        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), name, "REMOTE", address, timeout, 2500, 0);

        container(2).start();
        container(1).start();
        Thread.sleep(5000);
        FinalTestMessageVerifier verifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ReceiverClientAck receiver = new ReceiverClientAck(container(2), PrepareBase.QUEUE_JNDI, 120000, 100, 10);
        receiver.addMessageVerifier(verifier);
        receiver.start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue2 = new ProducerTransAck(container(2), PrepareBase.QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue2.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

        producerToInQueue1.start();
        producerToInQueue2.start();

        producerToInQueue1.join();
        producerToInQueue2.join();

        container(1).kill();

        container(1).start();
        Thread.sleep(5000);

        ProducerTransAck producerToInQueue3 = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue3.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id1"));

        ProducerTransAck producerToInQueue4 = new ProducerTransAck(container(2), PrepareBase.QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue4.setMessageBuilder(new MixMessageGroupMessageBuilder(10, 120, "id2"));

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
        log.info("Messages on servers " + new JMSTools().countMessages(PrepareBase.QUEUE_NAME, container(1),container(2)));
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

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout, 5000, 7500);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout, 2500, 0);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout, 2500, 0);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout, 2500, 0);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<String> groups = new ArrayList<String>();

        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        FinalTestMessageVerifier verifier = MessageVerifierFactory.getBasicVerifier(jmsImplementation);
        List<Map<String, String>> sendMessages = new ArrayList<Map<String, String>>();

        Context context = container(1).getContext();
        ConnectionFactory factory = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = (Queue) context.lookup(PrepareBase.QUEUE_JNDI);
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
            //add messages to message verifier
            Map<String, String> mapOfPropertiesOfTheMessage = new HashMap<String, String>();
            mapOfPropertiesOfTheMessage.put("messageId", m.getJMSMessageID());
            mapOfPropertiesOfTheMessage.put(jmsImplementation.getDuplicatedHeader(), m.getStringProperty(jmsImplementation.getDuplicatedHeader()));
            mapOfPropertiesOfTheMessage.put("JMSXGroupID", group);
            sendMessages.add(mapOfPropertiesOfTheMessage);
        }
        verifier.addSendMessages(sendMessages);
        connection.close();

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

            ProducerTransAck producerToInQueue1 = new ProducerTransAck(serverToConnect, PrepareBase.QUEUE_JNDI, numberOfMessages);
            producerToInQueue1.setMessageBuilder(new MixMessageGroupMessageBuilder(20, 120, group));
            producerToInQueue1.setCommitAfter(10);
            producerToInQueue1.addMessageVerifier(verifier);
            producerToInQueue1.start();
            producers.add(producerToInQueue1);
            ReceiverTransAck receiver = new ReceiverTransAck(serverToConnect, PrepareBase.QUEUE_JNDI, 40000, 100, 10);
            receiver.addMessageVerifier(verifier);
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

        // stop producers
        for (ProducerTransAck p : producers) {
            p.stopSending();
            p.join();
        }
        // wait for consumers to finish
        for (Client r : receivers) {
            r.join();
        }

        verifier.verifyMessages();

        log.info("Messages on servers " + new JMSTools().countMessages(PrepareBase.QUEUE_NAME, container(1), container(2), container(3), container(4)));
        Assert.assertEquals("Number of send and received messages is different: ", verifier.getSentMessages().size(),
                verifier.getReceivedMessages().size());
        Assert.assertNotEquals("No message send.", verifier.getSentMessages(), 0);

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
    @Prepare(params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true")
    })
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGrouping() throws Exception {
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

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

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareBase.OUT_QUEUE_JNDI, 10000, 10, 10);
        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), PrepareBase.OUT_QUEUE_JNDI, 10000, 10, 10);

        // setup producers and receivers
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), PrepareBase.IN_QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));
        ProducerClientAck producerRedG2 = new ProducerClientAck(container(2), PrepareBase.IN_QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG2.setMessageBuilder(new GroupColoredMessageBuilder("g2", "RED"));
        ProducerClientAck producerBlueG1 = new ProducerClientAck(container(1), PrepareBase.IN_QUEUE_JNDI,
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
    @Prepare(params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true")
    })
    public void clusterTestWithMdbWithSelectorAndSecurityTwoServersWithMessageGroupingToFail() throws Exception {
        container(1).start();
        container(2).start();
        JMSOperations jmsAdminOperations1 = container(1).getJmsOperations();
        JMSOperations jmsAdminOperations2 = container(2).getJmsOperations();

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
        ProducerClientAck producerRedG1 = new ProducerClientAck(container(1), PrepareBase.IN_QUEUE_JNDI,
                NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerRedG1.setMessageBuilder(new GroupColoredMessageBuilder("g1", "RED"));

        ReceiverClientAck receiver2 = new ReceiverClientAck(container(2), PrepareBase.OUT_QUEUE_JNDI, 10000, 10, 10);

        producerRedG1.start();
        receiver2.start();

        producerRedG1.join();
        receiver2.join();

        Assert.assertEquals("Number of received messages does not match on receiver2", 0, receiver2.getListOfReceivedMessages()
                .size());
        container(1).stop();
        container(2).stop();
    }

    public void clusterWitMessageGroupingCrashServerWithNoConsumer(Container serverToKill, long timeBetweenKillAndRestart)
            throws Exception {
        testMessageGrouping(serverToKill, timeBetweenKillAndRestart, container(3));
    }

    public void testMessageGrouping(Container serverToKill, long timeBetweenKillAndRestart, Container serverWithConsumer)
            throws Exception {

        int numberOfMessages = 10000;

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), name, "LOCAL", address, timeout, 5000, 7500);

        // set remote grouping-handler on others
        addMessageGrouping(container(2), name, "REMOTE", address, timeout, 2500, 0);
        addMessageGrouping(container(3), name, "REMOTE", address, timeout, 2500, 0);
        addMessageGrouping(container(4), name, "REMOTE", address, timeout, 2500, 0);

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        List<ProducerTransAck> producers = new ArrayList<ProducerTransAck>();

        List<ReceiverTransAck> receivers = new ArrayList<ReceiverTransAck>();
        List<FinalTestMessageVerifier> groupMessageVerifiers = new ArrayList<FinalTestMessageVerifier>();
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        groupMessageVerifiers.add(messageVerifier);

        for (int i = 0; i < 2; i++) {
            GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier(ContainerUtils.getJMSImplementation(serverWithConsumer));
            groupMessageVerifiers.add(groupMessageVerifier);
            ReceiverTransAck receiver = new ReceiverTransAck(serverWithConsumer, PrepareBase.QUEUE_JNDI, 40000, 100, 10);
            receiver.addMessageVerifier(groupMessageVerifier);
            receiver.start();
            receivers.add(receiver);
        }

        for (int i = 0; i < 4; i++) {
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), PrepareBase.QUEUE_JNDI, numberOfMessages);
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
            ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(2), PrepareBase.QUEUE_JNDI, numberOfMessages);
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

    private void addMessageGrouping(Container container, String name, String type, String address, long timeout, long groupTimeout, long reaperPeriod) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, groupTimeout, reaperPeriod);
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

}
