package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests failover of remote JCA and replicated journal.
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF HORNETQ RESOURCE ADAPTER WITH SHARED STORE AND REPLICATED JOURNAL IN DEDICATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-mdb-replicated-journal/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails Tests failover of remote JCA and replicated journal. There
 * are two servers in dedicated HA topology and MDB deployed on another server.
 * Live server is shutdown/killed and correct failover/failback is tested. Live
 * and backup servers use replicated journal.
 * This test case implements the same tests as DedicatedFailoverTestCaseWithMdb
 */
@RunWith(Arquillian.class)
@Prepare("RemoteJCAReplicated")
public class ReplicatedDedicatedFailoverTestWithMdb extends DedicatedFailoverTestCaseWithMdb {

    private static final Logger logger = Logger.getLogger(ReplicatedDedicatedFailoverTestWithMdb.class);

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testStopLivesBackupStartLivesBackup() throws Exception {

        Assume.assumeFalse("BLOCK".equals(prepareCoordinator.getParams().get(PrepareParams.ADDRESS_FULL_POLICY)));

        int numberOfMessages = 3000;
        Archive mdb = mdbWithNORebalancing;

        // start live-backup servers
        container(1).start();
        container(2).start();
        container(4).start();

        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), PrepareConstants.IN_QUEUE_JNDI, numberOfMessages);
//        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(1, 200));
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setCommitAfter(100);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();
        producerToInQueue1.join();

        container(3).start();

        logger.info("Deploying MDB to mdb server.");
//        // start mdb server
        container(3).deploy(mdb);

        Assert.assertTrue("MDB on container 3 is not resending messages to outQueue. Method waitForMessagesOnOneNode(...) timeouted.",
                new JMSTools().waitForMessages(PrepareConstants.OUT_QUEUE_NAME, numberOfMessages / 20, 300000, container(1), container(4)));
        logger.info("#######################################");
        logger.info("Stopping backup");
        logger.info("#######################################");
        container(2).stop();
        logger.info("#######################################");
        logger.info("Backup stopped.");
        logger.info("#######################################");
        Thread.sleep(60000);
        logger.info("#######################################");
        logger.info("Stopping lives");
        logger.info("#######################################");
        container(1).stop();
        container(4).stop();
        logger.info("#######################################");
        logger.info("Lives stopped.");
        logger.info("#######################################");
        Thread.sleep(20000);
        logger.info("#######################################");
        logger.info("Start lives");
        logger.info("#######################################");
        container(1).start();
        container(4).start();
        logger.info("#######################################");
        logger.info("Lives started.");
        logger.info("#######################################");
        logger.info("Start backup");
        logger.info("#######################################");
        container(2).start();
        logger.info("#######################################");
        logger.info("Backup started");
        logger.info("#######################################");

        new JMSTools().waitForMessages(PrepareConstants.OUT_QUEUE_NAME, numberOfMessages, 600000, container(1), container(4));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(360000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(360000, container(4));

        printThreadDumpsOfAllServers();

        ReceiverClientAck receiver1 = new ReceiverClientAck(container(1), PrepareConstants.OUT_QUEUE_JNDI, 3000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());
        messageVerifier.verifyMessages();

        container(3).undeploy(mdb);

        container(3).stop();
        container(2).stop();
        container(1).stop();
        container(4).stop();
        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

    }

    private void printThreadDumpsOfAllServers() throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));
    }

}
