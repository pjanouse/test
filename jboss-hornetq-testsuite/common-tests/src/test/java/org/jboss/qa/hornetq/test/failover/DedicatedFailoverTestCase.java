package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.PrintJournal;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.jms.Session;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class DedicatedFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DedicatedFailoverTestCase.class);
    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000000;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;

    protected static String NIO_JOURNAL_TYPE = "NIO";
    protected static String ASYNCIO_JOURNAL_TYPE = "ASYNCIO";

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String divertedQueue = "divertedQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String divertedQueueJndiName = "jms/queue/divertedQueue";

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
    //    MessageBuilder messageBuilder = new TextMessageBuilder(1024);
    Clients clients;

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

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFailover(acknowledge, failback, topic, false);
    }

    @Before
    @After
    public void makeSureAllClientsAreDead() throws InterruptedException {
        if (clients != null) {
            clients.stopClients();
            JMSTools.waitForClientsToFinish(clients, 300000);
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
            @BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>120",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFailover(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {

        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());
            container(1).kill();
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            container(1).stop();
        }

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 60000);
            waitForClientsToFailover();
            Thread.sleep(5000); // give it some time
//            logger.warn("########################################");
//            logger.warn("failback - Stop backup server");
//            logger.warn("########################################");
//            stopServer(CONTAINER2_NAME);
//            logger.warn("########################################");
//            logger.warn("failback - Backup server stopped");
//            logger.warn("########################################");
        }

        Thread.sleep(5000);

        waitForClientsToFailover();

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMultipleFailoverWithQueueKill() throws Exception {
        testMultipleFailover(Session.SESSION_TRANSACTED, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMultipleFailoverWithQueueShutdown() throws Exception {
        testMultipleFailover(Session.SESSION_TRANSACTED, false, true);
    }


    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param topic       whether to test with topics
     * @throws Exception
     */
    public void testMultipleFailover(int acknowledge, boolean topic, boolean shutdown) throws Exception {

        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);

        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        for (int numberOfFailovers = 0; numberOfFailovers < 50; numberOfFailovers++) {

            logger.warn("########################################");
            logger.warn("Running new cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            if (!shutdown) {

                logger.warn("########################################");
                logger.warn("Kill live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                container(1).kill();

            } else {

                logger.warn("########################################");
                logger.warn("Shutdown live server - number of failovers: " + numberOfFailovers);
                logger.warn("########################################");
                container(1).stop();
            }

            logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
            Assert.assertTrue("Backup did not start after failover - failover failed -  - number of failovers: "
                    + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(),
                        container(2).getHornetqPort(), 300000));

            waitForClientsToFailover();

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("failback - Start live server again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");
            container(1).start();

            Assert.assertTrue("Live did not start again - failback failed - number of failovers: " + numberOfFailovers, CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));

            logger.warn("########################################");
            logger.warn("failback - Live started again - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);

            // check that backup is really down
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 60000);

            waitForClientsToFailover();

            Thread.sleep(5000); // give it some time

            logger.warn("########################################");
            logger.warn("Ending cycle for multiple failover - number of failovers: " + numberOfFailovers);
            logger.warn("########################################");

        }

        waitForClientsToFailover();

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverWithDivertsTransAckQueueKill() throws Exception {
        testFailoverWithDiverts(false, false, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverWithDivertsTransAckQueueShutdown() throws Exception {
        testFailoverWithDiverts(false, false, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackWithDivertsTransAckQueueKill() throws Exception {
        testFailoverWithDiverts(true, false, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverWithDivertsTransAckTopicKill() throws Exception {
        testFailoverWithDiverts(false, true, false);
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first Receive messages from the second one
     *
     * @param failback whether to test failback
     * @param topic    whether to test with topics
     * @throws Exception
     */
    private void testFailoverWithDiverts(boolean failback, boolean topic, boolean shutdown) throws Exception {

        boolean isExclusive = false;
        int acknowledge = Session.SESSION_TRANSACTED;

        prepareSimpleDedicatedTopology();

        container(1).start();
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000);
        addDivert(container(1), divertedQueue, isExclusive, topic);
        container(1).stop();

        container(2).start(); // keep in mind that backup will not open port 5445
        addDivert(container(2), divertedQueue, isExclusive, topic);
        container(2).stop();

        container(1).start();
        container(2).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            container(1).kill();
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            container(1).stop();
        }

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(),
                    container(1).getHornetqPort(), 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 60000);
            waitForClientsToFailover();
            Thread.sleep(5000); // give it some time
//            logger.warn("########################################");
//            logger.warn("failback - Stop backup server");
//            logger.warn("########################################");
//            stopServer(CONTAINER2_NAME);
//            logger.warn("########################################");
//            logger.warn("failback - Backup server stopped");
//            logger.warn("########################################");
        }

        Thread.sleep(5000);

        waitForClientsToFailover();

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        // message verifiers for diverted messages - compares send and diverted messages
        FinalTestMessageVerifier sendDivertedMessageVerifier = new TextMessageVerifier();
        // compare received and diverted messages, to send  messages add messages from normal receiver, to received messages from diverted queue
        FinalTestMessageVerifier receivedDivertedMessageVerifier = new TextMessageVerifier();

        // add send messages to sendDivertedMessageVerifier
        for (Client c : clients.getProducers()) {

            if (c instanceof ProducerTransAck) {

                sendDivertedMessageVerifier.addSendMessages(((ProducerTransAck) c).getListOfSentMessages());

            }

            if (c instanceof PublisherTransAck) {

                sendDivertedMessageVerifier.addSendMessages(((PublisherTransAck) c).getListOfSentMessages());

            }
        }

        // count messages in test queue + add those messages to message verifier for receivedDivertedMessageVerifier
        int sum = 0;

        for (Client c : clients.getConsumers()) {

            if (c instanceof ReceiverTransAck) {

                sum += ((ReceiverTransAck) c).getListOfReceivedMessages().size();

                receivedDivertedMessageVerifier.addSendMessages(((ReceiverTransAck) c).getListOfReceivedMessages());

            }

            if (c instanceof SubscriberTransAck) {

                sum += ((SubscriberTransAck) c).getListOfReceivedMessages().size();

                receivedDivertedMessageVerifier.addSendMessages(((SubscriberTransAck) c).getListOfReceivedMessages());

            }
        }

        // check number of messages in diverted queue
        JMSOperations jmsOperations = failback ? container(1).getJmsOperations() : container(2).getJmsOperations();

        long numberOfMessagesInDivertedQueue = jmsOperations.getCountOfMessagesOnQueue(divertedQueue);

        jmsOperations.close();

        // receive messages from diverted queue
        ReceiverTransAck receiverFromDivertedQueue;

        if (failback) {

            receiverFromDivertedQueue = new ReceiverTransAck(container(1), divertedQueueJndiName, 5000, 100, 5);

        } else {

            receiverFromDivertedQueue = new ReceiverTransAck(container(2), divertedQueueJndiName, 5000, 100, 5);

        }
        receiverFromDivertedQueue.start();

        receiverFromDivertedQueue.join();

        sendDivertedMessageVerifier.addReceivedMessages(receiverFromDivertedQueue.getListOfReceivedMessages());

        receivedDivertedMessageVerifier.addReceivedMessages(receiverFromDivertedQueue.getListOfReceivedMessages());

        logger.info("Number of messages in diverted queue is: " + numberOfMessagesInDivertedQueue + ", number of messages in test queue: " + sum);

        Assert.assertEquals("There is different number of messages which are in test queue and diverted queue.", sum, numberOfMessagesInDivertedQueue);

        // do asserts
        Assert.assertTrue("There is different number of send messages and messages in diverted queue: ", sendDivertedMessageVerifier.verifyMessages());

        Assert.assertTrue("There is different number of received messages and messages in diverted queue: ", receivedDivertedMessageVerifier.verifyMessages());

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    private void addDivert(Container container, String divertedQueue, boolean isExclusive, boolean topic) {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addDivert("myDivert",
                topic ? "jms.topic." + topicNamePrefix + "0" : "jms.queue." + queueNamePrefix + "0", "jms.queue." + divertedQueue, isExclusive, null, null, null);

        jmsOperations.close();
    }


    /**
     * Start live backup pair in dedicated topology with shared store. Start producers and consumer on testQueue on live
     * and call CLI operations :force-failover on messaging subsystem. Live should stop and org.jboss.qa.hornetq.apps.clients failover to backup,
     * backup activates.
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testForceFailoverOperation() throws Exception {

        int acknowledge = Session.SESSION_TRANSACTED;
        boolean topic = false;

        prepareSimpleDedicatedTopology();

        container(1).start();
        container(2).start();

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        // call force-failover operation
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.forceFailover();
        jmsOperations.close();

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }


    protected void waitHornetQBackupToBecomePassive(Container container, int port, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        while (CheckServerAvailableUtils.checkThatServerIsReallyUp(container.getHostname(), port)) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime < timeout) {
                Assert.fail("Server " + container + " should be down. Timeout was " + timeout);
            }
        }

    }

    protected void waitForClientsToFailover() {

        long timeout = 180000;
        // wait for 2 min for producers to receive more messages
        long startTime = System.currentTimeMillis();

        int startValue = 0;
        for (Client c : clients.getProducers()) {

            startValue = c.getCount();

            while (c.getCount() <= startValue) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Assert.fail("Clients - producers - did not failover/failback in: " + timeout + " ms. Print bad producer: " + c);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        // wait for 2 min for consumers to send more messages
        startTime = System.currentTimeMillis();

        Map<Client, Integer> consumersCounts = new HashMap<Client, Integer>();
        for (Client c : clients.getConsumers()) {
            consumersCounts.put(c, c.getCount());
        }

        do {
            for (Client c : clients.getConsumers()) {
                if (c.getCount() > consumersCounts.get(c)) {
                    consumersCounts.remove(c);
                }
            }
            if (System.currentTimeMillis() - startTime > timeout) {
                Assert.fail("Clients - consumers - did not failover/failback in: " + timeout + " ms");
            }
        } while (consumersCounts.size() > 0);

    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first, kill first, receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
    public void testFailoverWithByteman(int acknowledge, boolean failback, boolean topic, boolean isReceiveFailure) throws Exception {

        prepareSimpleDedicatedTopology();

        container(2).start();

        container(1).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);

        if (isReceiveFailure) {
            clients.setProducedMessagesCommitAfter(100);
            clients.setReceivedMessagesAckCommitAfter(5);
        } else {
            clients.setProducedMessagesCommitAfter(5);
            clients.setReceivedMessagesAckCommitAfter(100);
        }

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 320, 300000);

        logger.warn("Deploy byteman rule:");

        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());

        waitForServerToBeKilled(container(1), 60000);

        container(1).kill();

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
//        Thread.sleep(20000); // give some time for org.jboss.qa.hornetq.apps.clients to failover
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 500, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 300000);
            Thread.sleep(5000); // give it some time

        }

        Thread.sleep(10000);

        waitForClientsToFailover();

        clients.stopClients();

        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    public boolean waitForServerToBeKilled(Container container, long timeout) throws Exception {

        boolean isRunning = false;

        long startTime = System.currentTimeMillis();

        while (isRunning && System.currentTimeMillis() - startTime < timeout) {
            isRunning = CheckServerAvailableUtils.checkThatServerIsReallyUp(container.getHostname(), container.getHttpPort());
            logger.info("Container " + container + " is still running. Waiting for it to be killed.");
            Thread.sleep(1000);
        }

        return isRunning;
    }


    public void testFailoverWithProducers(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        ProducerTransAck p = new ProducerTransAck(container(1), queueJndiNamePrefix + 0, NUMBER_OF_MESSAGES_PER_PRODUCER);
        FinalTestMessageVerifier queueTextMessageVerifier = new TextMessageVerifier();
        p.setMessageVerifier(queueTextMessageVerifier);
//        MessageBuilder messageBuilder = new TextMessageBuilder(20);
        p.setMessageBuilder(messageBuilder);
        p.setCommitAfter(2);
        p.start();

        long startTime = System.currentTimeMillis();
        while (p.getListOfSentMessages().size() < 120 && System.currentTimeMillis() - startTime < 60000) {
            Thread.sleep(1000);
        }
        logger.info("Producer sent more than 120 messages. Shutdown live server.");


        logger.warn("########################################");
        logger.warn("Shutdown live server");
        logger.warn("########################################");
        container(1).stop();
        PrintJournal.printJournal(container(1).getServerHome(), JOURNAL_DIRECTORY_A + File.separator + "bindings", JOURNAL_DIRECTORY_A + File.separator + "journal",
                "journalAfterShutdownAndFailoverToBackup.txt");
        logger.warn("########################################");
        logger.warn("Live server shutdowned");
        logger.warn("########################################");


        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        startTime = System.currentTimeMillis();
        while (p.getListOfSentMessages().size() < 300 && System.currentTimeMillis() - startTime < 120000) {
            Thread.sleep(1000);
        }
        logger.info("Producer sent more than 300 messages.");

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 300000));
            PrintJournal.printJournal(container(1).getServerHome(), JOURNAL_DIRECTORY_A + File.separator + "bindings", JOURNAL_DIRECTORY_A + File.separator + "journal",
                    "journalAfterStartingLiveAgain_Failback.txt");
            logger.warn("########################################");
            logger.warn("Live server started - this is failback");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
            Thread.sleep(10000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            container(2).stop();
            logger.warn("########################################");
            logger.warn("failback - Backup server stopped");
            logger.warn("########################################");
        }

        Thread.sleep(10000);

        p.stopSending();
        p.join(600000);
        ReceiverTransAck r;
        if (failback) {
            r = new ReceiverTransAck(container(1), queueJndiNamePrefix + 0);
        } else {
            r = new ReceiverTransAck(container(2), queueJndiNamePrefix + 0);
        }
        r.setMessageVerifier(queueTextMessageVerifier);
        r.setCommitAfter(100);
        r.setReceiveTimeOut(5000);
        r.start();
        r.join(300000);

        queueTextMessageVerifier.verifyMessages();

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", p.getListOfSentMessages().size() == r.getListOfReceivedMessages().size());

        container(1).stop();

        container(2).stop();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill before transaction commit is written into journal - receive",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "commit",
            action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    public void testFailoverTransAckQueueCommitNotStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "Kill before transaction commit is written into journal - receive",
            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
            targetMethod = "commit",
            action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
    public void testFailoverTransAckQueueCommitStored() throws Exception {
        testFailoverWithByteman(Session.SESSION_TRANSACTED, false, false, true);
    }

//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    @BMRule(name = "Kill before transaction commit is written into journal - receive",
//            targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
//            targetMethod = "commit",
//            action = "System.out.println(\"Byteman will invoke kill\");killJVM();")
//    public void testFailoverTransAckQueueCommitStoredProducers() throws Exception {
//        testFailoverWithProducers(Session.SESSION_TRANSACTED, false, false, true);
//    }

    protected Clients createClients(int acknowledgeMode, boolean topic) throws Exception {

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

        messageBuilder.setAddDuplicatedHeader(true);
        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(3);
        clients.setReceivedMessagesAckCommitAfter(5);

        return clients;
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckQueueOnShutdown() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false, false, true);
//    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueueOnShutdown() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false, false, true);
    }

//    /**
//     * Start simple failover test with trans_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void testFailoverTransAckQueueOnShutdown() throws Exception {
//        testFailover(Session.SESSION_TRANSACTED, false, false, true);
//    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailbackAutoAckQueueOnShutdown() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, true, false, true);
//    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueueOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, false, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueOnShutdown() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, false, true);
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailoverAutoAckTopicOnShutdown() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, false, true, true);
//    }

    /**
     * Start simple failover test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopicOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true, true);
    }

    /**
     * Start simple failover test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverTransAckTopicOnShutdown() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false, true, true);
    }

//    /**
//     * Start simple failback test with auto acknowledge on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailbackAutoAckTopicOnShutdown() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, true, true, true);
//    }

    /**
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopicOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true, true);
    }

    /**
     * Start simple failback test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopicOnShutdown() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true, true);
    }

    /**
     * Start simple failback test with transaction acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopicOnShutdownProducers() throws Exception {
        testFailoverWithProducers(Session.SESSION_TRANSACTED, false, true, true);
    }


    ////////////////////////////////////////////////////////
    // TEST KILL ////////////////////////
    ////////////////////////////////////////////////////////

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
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }

//    /**
//     * Start simple failover test with auto_ack on queues
//     */
//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest @RestoreConfigBeforeTest
//    public void testFailbackAutoAckQueue() throws Exception {
//        testFailover(Session.AUTO_ACKNOWLEDGE, true);
//    }

    /**
     * Start simple failover test with client_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueue() throws Exception {
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
     * Start simple failover test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * Start simple failover test with transaction acknowledge on queues
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
     * Start simple failback test with client acknowledge on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * Start simple failback test with transaction acknowledge on queues
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

        if (clients != null) {
            clients.stopClients();
            JMSTools.waitForClientsToFinish(clients, 300000);
        }

        container(1).stop();

        container(2).stop();

//        deleteFolder(new File(System.getProperty("JBOSS_HOME_1") + File.separator 
//                + "standalone" + File.separator + "data" + File.separator + JOURNAL_DIRECTORY_A));

    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {

        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(Container container, String bindingAddress, String journalDirectory) {
        prepareLiveServer(container, bindingAddress, journalDirectory, "ASYNCIO", false);
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     * @param journalType       ASYNCIO, NIO
     * @param useNIOConnectors  whether to use NIO in connectors for CF or old blocking IO
     *
     */
    protected void prepareLiveServer(Container container, String bindingAddress, String journalDirectory, String journalType, boolean useNIOConnectors) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        if (useNIOConnectors)   {
            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String,String> connectorParams = new HashMap<String,String>();
            connectorParams.put("use-nio","true");
            connectorParams.put("use-nio-global-worker-pool","true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String,String> acceptorParams = new HashMap<String,String>();
            acceptorParams.put("use-nio","true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();

        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareBackupServer(Container container, String bindingAddress, String journalDirectory) {

        prepareBackupServer(container, bindingAddress, journalDirectory, "ASYNCIO", false);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     * @param journalType       ASYNCIO, NIO
     * @param useNIOConnectors  whether to use NIO in connectors for CF or old blocking IO
     */
    protected void prepareBackupServer(Container container, String bindingAddress, String journalDirectory, String journalType, boolean useNIOConnectors) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        if (useNIOConnectors)   {
            // add connector with NIO
            jmsAdminOperations.removeRemoteConnector(connectorName);
            Map<String,String> connectorParams = new HashMap<String,String>();
            connectorParams.put("use-nio","true");
            connectorParams.put("use-nio-global-worker-pool","true");
            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);

            // add acceptor wtih NIO
            Map<String,String> acceptorParams = new HashMap<String,String>();
            acceptorParams.put("use-nio","true");
            jmsAdminOperations.removeRemoteAcceptor(connectorName);
            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);

        }


        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType(journalType);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

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
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue(divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();

        container.stop();
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
            @BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>120",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testFailoverNoPrepare(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        clients = createClients(acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());
            container(1).kill();
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            container(1).stop();
        }

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                container(2).getHostname(), container(2).getHornetqPort(), 300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                    container(1).getHostname(), container(1).getHornetqPort(), 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 60000);
            waitForClientsToFailover();
            Thread.sleep(5000); // give it some time
        }

        Thread.sleep(5000);

        waitForClientsToFailover();

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    /**
     * Start simple failback test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueNIOJournalNIOConnectors() throws Exception {
        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        testFailoverNoPrepare(Session.SESSION_TRANSACTED, true, false, false);
    }

    /**
     * Start simple failback test with trans_ack on queues with shutdown
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueOnShutdownNIOJournalNIOConnectors() throws Exception {
        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        testFailoverNoPrepare(Session.SESSION_TRANSACTED, true, false, true);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueueNIOJournalNIOConnectors() throws Exception {
        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        testFailoverNoPrepare(Session.CLIENT_ACKNOWLEDGE, true, false, false);
    }

    /**
     * Start simple failover test with trans_ack on queues
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueueOnShutdownNIOJournalNIOConnectors() throws Exception {
        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A, NIO_JOURNAL_TYPE, true);
        testFailoverNoPrepare(Session.CLIENT_ACKNOWLEDGE, true, false, true);
    }


}