package org.jboss.qa.hornetq.test.failover;

import category.FailoverColocatedClusterReplicatedJournal;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Session;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF  STANDALONE JMS CLIENT WITH REPLICATED JOURNAL IN DEDICATED/COLLOCATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-colocated-cluster-replicated-journal-win/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails This test case implements  all tests from ColocatedClusterFailoverTestCase class with only one
 * difference in these tests: testKillInClusterLargeMessages, testShutdownInClusterLargeMessages, testShutdownInClusterSmallMessages,
 * testKillInClusterSmallMessages. Difference is, that node-2 is not started after kill/shutdown.
 */
@RunWith(Arquillian.class)
@Prepare("ColocatedReplicatedHA")
@Category(FailoverColocatedClusterReplicatedJournal.class)
public class ReplicatedColocatedClusterFailoverTestCase extends ColocatedClusterFailoverTestCase {


    private static final Logger logger = Logger.getLogger(ReplicatedColocatedClusterFailoverTestCase.class);

    /**
     * @throws Exception
     * @tpTestDetails This test scenario tests whether the synchronization between Lives and Backups
     * will be successfully performed even the journal-min-files will be 100.
     * @tpProcedure <ul>
     * <li>start two nodes in colocated cluster topology with journal-min-files=100</li>
     * <li>start sending and receiving messages</li>
     * <li>shutdown node-1</li>
     * <li>start node-1</li>
     * <li>stop sending messages</li>
     * <li>wait until all messages are received</li>
     * </ul>
     * @tpPassCrit Replication is not stopped between Live and Backup (all checks whether brokers are active or inactive pass).
     * All sent messages are properly delivered. There are no losses or duplicates.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSynchronizationWithBigJournalMinFiles() throws Exception {

        JMSOperations jmsOperations;

        container(1).start();
        jmsOperations = container(1).getJmsOperations();
        jmsOperations.setJournalMinFiles(50);
        jmsOperations.setJournalMinFiles("backup", 50);
        jmsOperations.setClusterConnectionCallTimeout("my-cluster", 60000);
        jmsOperations.setClusterConnectionCallTimeout("backup", "my-cluster", 60000);
        container(1).stop();

        container(2).start();
        jmsOperations = container(2).getJmsOperations();
        jmsOperations.setJournalMinFiles(50);
        jmsOperations.setJournalMinFiles("backup", 50);
        jmsOperations.setClusterConnectionCallTimeout("backup", "my-cluster", 60000);
        container(2).stop();

        container(2).start();

        container(1).start();

        // give some time for servers to find each other
        Thread.sleep(10000);

        messageBuilder.setAddDuplicatedHeader(true);

        clients = createClients(Session.SESSION_TRANSACTED, false, messageBuilder);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(10);

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 120, 300000);

        logger.info("########################################");
        logger.info("shutdown - first server");
        logger.info("########################################");
        container(1).stop();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 500, 300000);
        Assert.assertTrue("Backup on second server did not start - failover failed.", CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(),
                container(2).getHornetqBackupPort(), 300000));

        logger.info("########################################");
        logger.info("failback - Start first server again ");
        logger.info("########################################");
        container(1).start();
        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 300000);
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

    @BMRules({
            @BMRule(name = "Kill server after the backup is synced with live EAP 6",
                    targetClass = "org.hornetq.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();"),
            @BMRule(name = "Kill server after the backup is synced with live  EAP 7",
                    targetClass = "org.apache.activemq.artemis.core.replication.ReplicationManager",
                    targetMethod = "appendUpdateRecord",
                    condition = "!$0.isSynchronizing()",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    public void testFail(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {
        testFailInternal(acknowledge, failback, topic, shutdown);
    }


    /**
     * In case of replication we don't want to start second server again. We expect that colocated backup
     * will have all load-balanced messages.
     *
     * @param shutdown
     * @param messageBuilder
     * @throws Exception
     */
    public void testFailInCluster(boolean shutdown, MessageBuilder messageBuilder) throws Exception {

        container(1).start();
        container(2).start();

        // give some time for servers to find each other
        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

        int numberOfMessages = 6000;
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(1000);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        producerToInQueue1.addMessageVerifier(messageVerifier);
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

//        logger.info("########################################");
//        logger.info("Start again - second server");
//        logger.info("########################################");
//        controller.start(CONTAINER2_NAME);
//        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000);
//        logger.info("########################################");
//        logger.info("Second server started");
//        logger.info("########################################");

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.QUEUE_JNDI, 30000, 1000, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setAckAfter(1000);

        receiver1.start();
        receiver1.join();

        logger.info("Number of sent messages: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number messages: ", producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }
}
