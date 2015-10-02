package org.jboss.qa.hornetq.test.domain;


import java.util.HashMap;
import java.util.Map;

import javax.jms.Session;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.DomainTests;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;


/**
 * @tpChapter   RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF STANDALONE JMS CLIENT WITH SHARED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY IN DOMAIN - TEST SCENARIOS
 * @tpJobLink TODO
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @tpTestCaseDetails HornetQ journal is located on GFS2 on SAN where journal type ASYNCIO must be used.
 * Or on NSFv4 where journal type is ASYNCIO or NIO.
 */
@RunWith(Arquillian.class)
@Category(DomainTests.class)
public class DomainDedicatedFailoverTestCase extends DomainHornetQTestCase {

    private static final Logger logger = Logger.getLogger(DomainDedicatedFailoverTestCase.class);
    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000000;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;
    protected static final int BYTEMAN_PORT_1 = 9091;

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
    public void testFailover(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {

        DomainContainer host = domainContainer();
        host.reloadDomain();
        prepareSimpleDedicatedTopology(host);

        DomainNode live = host.serverGroup("server-group-1").node("server-1");
        DomainNode backup = host.serverGroup("server-group-2").node("server-2");

        live.start();
        backup.start();

        Thread.sleep(10000);

        clients = createClients(live, acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            //RuleInstaller.installRule(this.getClass(), live.getHostname(), BYTEMAN_PORT_1);
            live.kill();
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            live.stop();
        }

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.",
                CheckServerAvailableUtils.waitHornetQToAlive(backup.getHostname(), backup.getHornetqPort(),
                        300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            live.start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                    live.getHostname(), live.getHornetqPort(), 300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(live.getHostname(), live.getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(backup, 60000);
            waitForClientsToFailover();
            Thread.sleep(5000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            backup.stop();
            logger.warn("########################################");
            logger.warn("failback - Backup server stopped");
            logger.warn("########################################");
        }

        Thread.sleep(5000);

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        live.stop();
        backup.stop();
    }


    /**
     * @tpTestDetails This scenario tests failover on dedicated topology with shared-store and kill with nodes in EAP domain. Clients
     * are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue. Divert is set on testQueue
     * directing to divertQueue.
     * @tpProcedure <ul>
     *     <li>start two nodes in a single domain in dedicated cluster topology with divert directed to divertQueue from inQueue</li>
     *     <li>start sending messages to inQueue on node-1 and receiving them from inQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>start receiver on divertQueue  and wait for him to finish</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit consumer received from diverQueue same amount of messages as was send to inQueue and same amount
     * as was received from inQueue
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverWithDivertsTransAckQueueKill() throws Exception {
        testFailoverWithDiverts(false, false, false);
    }

    /**
     * @tpTestDetails This scenario tests failover on dedicated topology with shared-store and clean shutdown in EAP domain. Clients
     * are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue. Divert is set on testQueue
     * directing to divertQueue.
     * @tpProcedure <ul>
     *     <li>start two nodes in a single domain in dedicated cluster topology with divert directed to divertQueue from testQueue</li>
     *     <li>start sending messages to inQueue on node-1 and receiving them from testQueue on node-1</li>
     *     <li>during sending and receiving shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>start receiver on divertQueue  and wait for him to finish</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit consumer received from diverQueue same amount of messages as was send to testQueue and same amount
     * as was received from testQueue
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverWithDivertsTransAckQueueShutdown() throws Exception {
        testFailoverWithDiverts(false, false, true);
    }

    /**
     * @tpTestDetails This scenario tests failover and failback on dedicated topology with shared-store and kill in EAP domain. Clients
     * are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue. Divert is set on testQueue
     * directing to divertQueue.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a single domain in dedicated cluster topology with divert directed to divertQueue from testQueue</li>
     *     <li>start sending messages to inQueue on node-1 and receiving them from testQueue on node-1</li>
     *     <li>during sending and receiving kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>start receiver on divertQueue  and wait for him to finish</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit consumer received from diverQueue same amount of messages as was send to testQueue and same amount
     * as was received from inQueue
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackWithDivertsTransAckQueueKill() throws Exception {
        testFailoverWithDiverts(true, false, false);
    }

    /**
     * @tpTestDetails  This scenario tests failover and failback on dedicated topology with shared-store and kill in EAP domain. Clients
     * are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue. Divert is set on testTopic
     * directing to divertQueue.
     * @tpProcedure <ul>
     *     <li>start two nodes in a single domain in dedicated cluster topology with divert directed to divertQueue from testTopic</li>
     *     <li>start sending messages to inQueue on node-1 and receiving them from testTopic on node-1</li>
     *     <li>during sending and receiving  kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>start receiver on divertQueue  and wait for him to finish</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit consumer received from diverQueue same amount of messages as was send to testTopic and same amount
     * as was received from testTopic
     */
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

        DomainContainer host = domainContainer();
        host.reloadDomain();
        prepareSimpleDedicatedTopology(host);

        DomainNode container1 = host.serverGroup("server-group-1").node("server-1");
        DomainNode container2 = host.serverGroup("server-group-2").node("server-2");

        container1.start();
        CheckServerAvailableUtils.waitHornetQToAlive(container1.getHostname(), container1.getHornetqPort(), 30000);
        addDivert(host.serverGroup("server-group-1"), divertedQueue, isExclusive, topic);
        container1.stop();

        container2.start();
        // backup won't open hornetq port until it's promoted to live
        //CheckServerAvailableUtils.waitHornetQToAlive(container2.getHostname(), container2.getHornetqPort(), 30000);
        addDivert(host.serverGroup("server-group-2"), divertedQueue, isExclusive, topic);
        container2.stop();

        container1.start();
        container2.start();

        Thread.sleep(10000);

        clients = createClients(container1, acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            container1.kill();
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            container1.stop();
        }

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.",
                CheckServerAvailableUtils.waitHornetQToAlive(container2.getHostname(), container2.getHornetqPort(),
                        300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        if (failback) {
            logger.warn("########################################");
            logger.warn("failback - Start live server again ");
            logger.warn("########################################");
            container1.start();
            Assert.assertTrue("Live did not start again - failback failed.",
                    CheckServerAvailableUtils.waitHornetQToAlive(container1.getHostname(), container1.getHornetqPort(),
                            300000));
            logger.warn("########################################");
            logger.warn("failback - Live started again ");
            logger.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container1.getHostname(), container1.getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(container2, 60000);
            waitForClientsToFailover();
            Thread.sleep(5000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            container2.stop();
            logger.warn("########################################");
            logger.warn("failback - Backup server stopped");
            logger.warn("########################################");
        }

        Thread.sleep(5000);

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        // compare numbers in original and diverted queue
        int sum = 0;
        for (Client c : clients.getProducers()) {
            if (c instanceof ProducerTransAck) {
                sum = sum + ((ProducerTransAck) c).getListOfSentMessages().size();
            }
            if (c instanceof PublisherTransAck) {
                sum = sum + ((PublisherTransAck) c).getListOfSentMessages().size();
            }
        }

        JMSOperations jmsOperations;
        if (failback) {
            jmsOperations = container1.getRuntimeJmsOperations();
        } else {
            jmsOperations = container2.getRuntimeJmsOperations();
        }
        long numberOfMessagesInDivertedQueue = jmsOperations.getCountOfMessagesOnQueue(divertedQueue);
        jmsOperations.close();

        Assert.assertEquals("There is different number of sent messages and received messages from diverted address",
                sum, numberOfMessagesInDivertedQueue);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container1.stop();
        container2.stop();
    }

    private void addDivert(DomainServerGroup serverGroup, String divertedQueue, boolean isExclusive, boolean isTopic) {
        JMSOperations jmsOperations = serverGroup.getJmsOperations();

        String divertAddress = isTopic ? "jms.topic." + topicNamePrefix + "0" : "jms.queue." + queueNamePrefix + "0";
        String forwardingAddress = "jms.queue." + divertedQueue;
        jmsOperations.addDivert("myDivert", divertAddress, forwardingAddress, isExclusive, null, null, null);

        jmsOperations.close();
    }


    /**
     * @throws Exception
     * @tpTestDetails Start live backup pair in a domain in dedicated topology with shared store. Start producers and consumer on testQueue on live
     * and call CLI operations :force-failover on messaging subsystem. Live should stop and org.jboss.qa.hornetq.apps.clients failover to backup,
     * backup activates.
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     *     <li>during sending and receiving call CLI operation: force-failover on messaging subsystem on node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore("force-failover returns error despite shutting down the server, see https://bugzilla.redhat.com/show_bug.cgi?id=1145107")
    public void testForceFailoverOperation() throws Exception {
        DomainContainer host = domainContainer();
        host.reloadDomain();
        prepareSimpleDedicatedTopology(host);

        DomainNode container1 = host.serverGroup("server-group-1").node("server-1");
        DomainNode container2 = host.serverGroup("server-group-2").node("server-2");

        container1.start();
        container2.start();

        int acknowledge = Session.SESSION_TRANSACTED;
        boolean topic = false;

        clients = createClients(container1, acknowledge, topic);
        clients.setProducedMessagesCommitAfter(2);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        // call force-failover operation
        JMSOperations jmsOperations = container1.getRuntimeJmsOperations();
        jmsOperations.forceFailover();
        jmsOperations.close();

        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.",
                CheckServerAvailableUtils.waitHornetQToAlive(container2.getHostname(), container2.getHornetqPort(),
                        300000));
        waitForClientsToFailover();
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 600, 300000);

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container1.stop();
        container2.stop();
    }


    private void waitHornetQBackupToBecomePassive(DomainNode node, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        while (CheckServerAvailableUtils.checkThatServerIsReallyUp(node.getHostname(), node.getHornetqPort())) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime < timeout) {
                Assert.fail("Server " + node.getName() + " should be down. Timeout was " + timeout);
            }
        }

    }

    private void waitForClientsToFailover() {

        long timeout = 120000;

        Map<Client, Integer> consumersCounts = new HashMap<Client, Integer>();
        for (Client c : clients.getConsumers()) {
            consumersCounts.put(c, c.getCount());
        }
        // wait for 2 min for consumers to receive more messages
        long startTime = System.currentTimeMillis();
        do {
            for (Client c : clients.getConsumers()) {
                if (c.getCount() > consumersCounts.get(c)) {
                    consumersCounts.remove(c);
                }
            }
            if (System.currentTimeMillis() - startTime > 120000) {
                Assert.fail("Clients - consumers - did not failover/failback in: " + timeout + " ms");
            }
        } while (consumersCounts.size() > 0);

        // wait for 2 min for producers to send more messages
        startTime = System.currentTimeMillis();

        int startValue = 0;
        for (Client c : clients.getProducers()) {

            startValue = c.getCount();

            while (c.getCount() <= startValue) {
                if (System.currentTimeMillis() - startTime > 120000) {
                    Assert.fail("Clients - producers - did not failover/failback in: " + timeout + " ms. Print bad producer: " + c);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    /*
     * This test will start two servers in dedicated topology - no cluster. Sent
     * some messages to first, kill first, receive messages from the second one
     *
     * @param acknowledge acknowledge type
     * @param failback    whether to test failback
     * @param topic       whether to test with topics
     * @throws Exception
     */
/*    public void testFailoverWithByteman(int acknowledge, boolean failback, boolean topic, boolean isReceiveFailure) throws Exception {
        DomainContainer host = domainContainer();
        host.reloadDomain();
        prepareSimpleDedicatedTopology(host);

        DomainNode container1 = host.serverGroup("server-group-1").node("server-1");
        DomainNode container2 = host.serverGroup("server-group-2").node("server-2");

        container1.start();
        container2.start();

        Thread.sleep(10000);

        clients = createClients(container1, acknowledge, topic);

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

        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_PORT_1);

        waitForServerToBeKilled(CONTAINER1_NAME, 60000);

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
            Thread.sleep(10000); // give it some time
            logger.warn("########################################");
            logger.warn("failback - Stop backup server");
            logger.warn("########################################");
            container(2).stop();
        }

        Thread.sleep(10000);
        clients.stopClients();

        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }*/

    public boolean waitForServerToBeKilled(String container, long timeout) throws Exception {

        boolean isRunning = false;

        long startTime = System.currentTimeMillis();

        while (isRunning && System.currentTimeMillis() - startTime < timeout) {
            isRunning = CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1).getHostname(), getHttpPort(container));
            logger.info("Container " + container + " is still running. Waiting for it to be killed.");
            Thread.sleep(1000);
        }

        return isRunning;
    }


/*    public void testFailoverWithProducers(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        DomainContainer host = domainContainer();
        host.reloadDomain();
        prepareSimpleDedicatedTopology(host);

        DomainNode live = host.serverGroup("server-group-1").node("server-1");
        DomainNode backup = host.serverGroup("server-group-2").node("server-2");

        live.start();
        backup.start();

        Thread.sleep(10000);

        ProducerTransAck p = new ProducerTransAck(live.getHostname(), live.getJNDIPort(),
                queueJndiNamePrefix + 0, NUMBER_OF_MESSAGES_PER_PRODUCER);
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
        live.stop();
        container(1).getPrintJournal().printJournal(JOURNAL_DIRECTORY_A + File.separator + "bindings", JOURNAL_DIRECTORY_A + File.separator + "journal",
                "journalAfterShutdownAndFailoverToBackup.txt");
        logger.warn("########################################");
        logger.warn("Live server shutdowned");
        logger.warn("########################################");


        logger.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(getHostname(
                CONTAINER2_NAME), container(2).getHornetqPort(), 300000));
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
            container(1).getPrintJournal().printJournal(JOURNAL_DIRECTORY_A + File.separator + "bindings", JOURNAL_DIRECTORY_A + File.separator + "journal",
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
            r = new ReceiverTransAck(CONTAINER1_NAME, container(1).getHostname(), container(1).getJNDIPort(), queueJndiNamePrefix + 0);
        } else {
            r = new ReceiverTransAck(CONTAINER2_NAME, container(2).getHostname(), container(2).getJNDIPort(), queueJndiNamePrefix + 0);
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
    }*/


/*
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
*/

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

    protected Clients createClients(DomainNode container, int acknowledgeMode, boolean topic) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container.getHostname(), container.getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container.getHostname(), container.getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container.getHostname(), container.getJNDIPort(), topicJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container.getHostname(), container.getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container.getHostname(), container.getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container.getHostname(), container.getJNDIPort(), queueJndiNamePrefix, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover on dedicated topology in EAP domain with shared-store and clean shutdown. Clients
     * are using CLIENT_ACKNOWLEDGE sessions to sending and receiving messages from testQueue.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions  sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean shutdown.
     * Clients are using CLIENT_ACKNOWLEDGE sessions to sending and receiving messages from testQueue.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testQueue on node-1 and receiving
     *     them from testQueue on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueueOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, false, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean
     * shutdown. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testQueue on node-1 and receiving
     *     them from testQueue on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean
     * shutdown. Clients are using CLIENT_ACKNOWLEDGE sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopicOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean
     * shutdown. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean
     * shutdown. Clients are using CLIENT_ACKNOWLEDGE sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopicOnShutdown() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and clean
     * shutdown. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>cleanly shut down node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
/*    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopicOnShutdownProducers() throws Exception {
        testFailoverWithProducers(Session.SESSION_TRANSACTED, false, true, true);
    }*/


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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover on dedicated topology in EAP domain with shared-store and a kill. Clients
     * are using CLIENT_ACKNOWLEDGE sessions to sending and receiving messages from testQueue.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start sending messages to testQueue on node-1 and receiving them from testQueue on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckQueue() throws Exception {

        testFailover(Session.CLIENT_ACKNOWLEDGE, false);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover on dedicated topology in EAP domain with shared-store and a kill. Clients
     * are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions  sending messages to testQueue on node-1 and receiving
     *     them from testQueue on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using CLIENT_ACKNOWLEDGE sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testQueue on node-1 and receiving
     *     them from testQueue on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckQueue() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testQueue on node-1 and receiving
     *     them from testQueue on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using CLIENT_ACKNOWLEDGE sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, false, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using CLIENT_ACKNOWLEDGE sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with CLIENT_ACKNOWLEDGE) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on Dedicated topology in EAP domain with shared-store and
     * a kill. Clients are using SESSION_TRANSACTED sessions.
     *
     * @tpProcedure <ul>
     *     <li>start two nodes in a domain in dedicated cluster topology</li>
     *     <li>start clients (with SESSION_TRANSACTED) sessions sending messages to testTopic on node-1 and receiving
     *     them from testTopic on node-1</li>
     *     <li>kill node-1</li>
     *     <li>clients make failover on backup and continue in sending and receiving messages</li>
     *     <li>start node-1 again and wait for failback</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
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


        domainContainer().serverGroup("server-group-1").stopAllNodes();
        domainContainer().serverGroup("server-group-2").stopAllNodes();

//        deleteFolder(new File(System.getProperty("JBOSS_HOME_1") + File.separator 
//                + "standalone" + File.separator + "data" + File.separator + JOURNAL_DIRECTORY_A));

    }

    private void prepareSimpleDedicatedTopology(DomainContainer domain) throws Exception {
        if (domain.getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_DOMAIN_CONTAINER)) {
            prepareSimpleDedicatedTopologyEAP6(domain);
        } else {
            prepareSimpleDedicatedTopologyEAP7(domain);
        }
    }

    private void prepareSimpleDedicatedTopologyEAP6(DomainContainer domain) throws Exception {
        prepareLiveServerEAP6(domain.serverGroup("server-group-1"), JOURNAL_DIRECTORY_A);
        prepareBackupServerEAP6(domain.serverGroup("server-group-2"), JOURNAL_DIRECTORY_A);
    }

    private void prepareSimpleDedicatedTopologyEAP7(DomainContainer domain) throws Exception {

    }

    private void prepareLiveServerEAP6(DomainServerGroup liveServerGroup, String journalDirectory) throws Exception {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        final DomainNode liveServer = liveServerGroup.node("server-1");
        liveServer.start();

        JMSOperations jmsAdminOperations = liveServerGroup.getJmsOperations();

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        if (useNIOConnectors)   {
//            // add connector with NIO
//            jmsAdminOperations.removeRemoteConnector(connectorName);
//            Map<String,String> connectorParams = new HashMap<String,String>();
//            connectorParams.put("use-nio","true");
//            connectorParams.put("use-nio-global-worker-pool","true");
//            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);
//
//            // add acceptor wtih NIO
//            Map<String,String> acceptorParams = new HashMap<String,String>();
//            acceptorParams.put("use-nio","true");
//            jmsAdminOperations.removeRemoteAcceptor(connectorName);
//            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);
//
//        }

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

        liveServer.stop();
    }

    private void prepareBackupServerEAP6(DomainServerGroup backupServerGroup, String journalDirectory) throws Exception {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingForConnector = "messaging";

        final DomainNode backupServer = backupServerGroup.node("server-2");
        backupServer.start();
        JMSOperations jmsAdminOperations = backupServerGroup.getJmsOperations();

//        if (useNIOConnectors)   {
//            // add connector with NIO
//            jmsAdminOperations.removeRemoteConnector(connectorName);
//            Map<String,String> connectorParams = new HashMap<String,String>();
//            connectorParams.put("use-nio","true");
//            connectorParams.put("use-nio-global-worker-pool","true");
//            jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, connectorParams);
//
//            // add acceptor wtih NIO
//            Map<String,String> acceptorParams = new HashMap<String,String>();
//            acceptorParams.put("use-nio","true");
//            jmsAdminOperations.removeRemoteAcceptor(connectorName);
//            jmsAdminOperations.createRemoteAcceptor(connectorName, messagingGroupSocketBindingForConnector, acceptorParams);
//
//        }


        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

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
        backupServer.stop();
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
//    public void prepareSimpleDedicatedTopology(DomainContainer domain) throws Exception {
//        DomainServerGroup serverGroup1 = domain.serverGroup("server-group-1");
//        DomainServerGroup serverGroup2 = domain.serverGroup("server-group-2");
//
//        prepareLiveServerEAP6(serverGroup1, domain.getHostname(), JOURNAL_DIRECTORY_A);
//        prepareBackupServerEAP6(serverGroup2, domain.getHostname(), JOURNAL_DIRECTORY_A);
//
//        serverGroup1.startAllNodes();
//        deployDestinations(serverGroup1);
//        serverGroup1.stopAllNodes();
//
//        serverGroup2.startAllNodes();
//        deployDestinations(serverGroup2);
//        serverGroup2.stopAllNodes();
//    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param serverGroup      server group with live server
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(DomainServerGroup serverGroup, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        JMSOperations jmsAdminOperations = serverGroup.getJmsOperations();
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
        jmsAdminOperations.setJournalType("ASYNCIO");

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
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();


    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param serverGroup Server group with backup server
     */
    protected void prepareBackupServer(DomainServerGroup serverGroup, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        JMSOperations jmsAdminOperations = serverGroup.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

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

        jmsAdminOperations.close();
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param serverGroup Server group to deploy the destinations in.
     */
    protected void deployDestinations(DomainServerGroup serverGroup) {
        deployDestinations(serverGroup, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param serverGroup   Server group for the destinations
     * @param serverName    server name of the hornetq server
     */
    protected void deployDestinations(DomainServerGroup serverGroup, String serverName) {

        JMSOperations jmsAdminOperations = serverGroup.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }
        jmsAdminOperations.createQueue(serverName, divertedQueue, divertedQueueJndiName, true);

        jmsAdminOperations.close();
    }
}