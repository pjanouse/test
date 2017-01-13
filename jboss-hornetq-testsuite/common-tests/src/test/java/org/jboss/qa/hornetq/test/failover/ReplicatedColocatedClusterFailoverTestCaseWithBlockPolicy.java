package org.jboss.qa.hornetq.test.failover;

import category.FailoverColocatedClusterReplicatedJournal;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.QueueClientsTransAck;
import org.jboss.qa.hornetq.apps.clients.Receiver;
import org.jboss.qa.hornetq.apps.clients.TopicClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.TopicClientsTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.jms.Session;

/**
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5535/hornetq-high-availability#testcases
 * @tpTestCaseDetails This test case tests BLOCK policy from HA point of view.
 */
@Category(FailoverColocatedClusterReplicatedJournal.class)
@Prepare(value = "ColocatedReplicatedHA", params = {
        @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
        @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 10 * 1024 * 1024)
})
public class ReplicatedColocatedClusterFailoverTestCaseWithBlockPolicy extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ReplicatedColocatedClusterFailoverTestCase.class);

    protected static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;
    protected static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 3;
    protected static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 120);
    //    MessageBuilder messageBuilder = new TextMessageBuilder(1024);
    Clients clients = null;

    @Before
    @After
    public void makeSureAllClientsAreDead() throws InterruptedException {
        if (clients != null) {
            clients.stopClients();
            ClientUtils.waitForClientsToFinish(clients, 600000);
        }
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
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
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO"),
            @Param(name = PrepareParams.JOURNAL_TYPE, value = "NIO")
    })
    public void testFailbackClientAckQueueNIO() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
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
     * on node which is killed in colocated cluster topology. NIO journal type is used.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology with NIO journal type</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and consumer make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
     * <li>stop producer and consumer</li>
     * </ul>
     * @tpPassCrit receiver get all sent messages, none of clients gets any exception, failback was successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO"),
            @Param(name = PrepareParams.JOURNAL_TYPE, value = "NIO")
    })
    public void testFailbackTransAckQueueNIO() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true);
    }

    /**
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
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
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
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
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * Both clients are using CLIENT_ACKNOWLEDGE session. During this process node-1 is killed, after while node-1 is started again
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
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
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using SESSION_TRANSACTED session)
     * on node which is killed in colocated cluster topology
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
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
     * @tpTestDetails This test scenario tests failover of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is killed in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inTopic on node-1 and receiving them from inTopic on node-1</li>
     * <li>after producers are blocked, kill node-1</li>
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
     * <li>after producers are blocked, kill node-1</li>
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

    /**
     * @tpTestDetails This test scenario tests failover and failback of clients connected to topic (using CLIENT_ACKNOWLEDGE session)
     * on node which is shut down in colocated cluster topology
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, shut down node-1</li>
     * <li>producer and subscriber make failover on backup and continue in sending and receiving messages</li>
     * <li>after producers are blocked start node-1 again and wait for failback</li>
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
     * @tpTestDetails This test scenario tests failover of clients connected to queue (using CLIENT_ACKNOWLEDGE session)
     * on node which is shut down in colocated cluster topology.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology</li>
     * <li>start sending large messages with group id to inQueue on node-1 and receiving them from inQueue on node-1</li>
     * <li>after producers are blocked, shut down node-1</li>
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

    public void testFailover(int acknowledge, boolean failback) throws Exception {
        testFailover(acknowledge, failback, false);
    }

    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, false);
    }

    public void testFailoverWithShutDown(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFail(acknowledge, failback, topic, true);
    }

    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {


        container(2).start();

        container(1).start();

        // give some time for servers to find each other
        Thread.sleep(10000);

        messageBuilder.setAddDuplicatedHeader(true);

        clients = createClients(acknowledge, topic, messageBuilder);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(10);

        clients.startClients();

        for (Client client : clients.getConsumers()) {
            client.setTimeout(60000);
        }

        ClientUtils.waitUntilProducersAreBlocked(clients,300000);

        logger.info("########################################");
        logger.info("kill - first server");
        logger.info("########################################");
        if (shutdown) {
            container(1).stop();
        } else {
            container(1).kill();
        }
        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), PrepareConstants.BACKUP_SERVER_NAME, 300000);
        for (Client client : clients.getConsumers()) {
            client.setTimeout(100);
        }

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 100, 300000);

        ClientUtils.waitForProducersToFailover(clients, 60000);

        if (failback) {
            for (Client client : clients.getConsumers()) {
                client.setTimeout(60000);
            }

            ClientUtils.waitUntilProducersAreBlocked(clients,300000);

            logger.info("########################################");
            logger.info("failback - Start first server again ");
            logger.info("########################################");
            container(1).start();
            CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 300000);
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(2), PrepareConstants.BACKUP_SERVER_NAME, 30000);
            CheckServerAvailableUtils.waitForBrokerToActivate(container(2), 30000);
            CheckServerAvailableUtils.waitForBrokerToDeactivate(container(1), PrepareConstants.BACKUP_SERVER_NAME, 30000);
//            Thread.sleep(10000);
//            logger.info("########################################");
//            logger.info("failback - Stop second server to be sure that failback occurred");
//            logger.info("########################################");
//            stopServer(CONTAINER2_NAME);
        }
        for (Client client : clients.getConsumers()) {
            client.setTimeout(0);
        }

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

    protected Clients createClients(int acknowledgeMode, boolean topic, MessageBuilder messageBuilder) throws Exception {

        Clients clients;

        if (topic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container(1), PrepareConstants.TOPIC_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container(1), PrepareConstants.QUEUE_JNDI_PREFIX, NUMBER_OF_DESTINATIONS, NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION, NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }

        clients.setMessageBuilder(messageBuilder);
        clients.setProducedMessagesCommitAfter(10);
        clients.setReceivedMessagesAckCommitAfter(5);

        return clients;
    }

}
