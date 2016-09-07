package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.test.prepares.PrepareBase;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

@Prepare("ReplicatedHAFourNodes")
public class ReplicatedDedicatedFourNodesFailoverTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ReplicatedDedicatedFourNodesFailoverTestCase.class);

    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000000;

    protected MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

    /**
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO")
    })
    public void testStopLiveAndBackupStartBackupAndLiveInCluster() throws Exception {
        testStopLiveAndBackupStartBackupAndLiveInClusterInternal();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO"),
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
    })
    public void testStopLiveAndBackupStartBackupAndLiveInClusterBlockPolicy() throws Exception {
        testStopLiveAndBackupStartBackupAndLiveInClusterInternal();
    }

    public void testStopLiveAndBackupStartBackupAndLiveInClusterInternal() throws Exception {


        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        Thread.sleep(5000);

        ProducerTransAck prod1 = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        prod1.addMessageVerifier(messageVerifier);
        prod1.setMessageBuilder(messageBuilder);
        prod1.setTimeout(0);
        prod1.setCommitAfter(10);
        prod1.start();

        ProducerTransAck prod2 = new ProducerTransAck(container(3), PrepareBase.QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        prod2.addMessageVerifier(messageVerifier);
        prod2.setMessageBuilder(messageBuilder);
        prod2.setTimeout(0);
        prod2.setCommitAfter(5);
        prod2.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareBase.QUEUE_JNDI, 120000, 10, 100);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(3), PrepareBase.QUEUE_JNDI, 120000, 10, 100);
        receiver2.setTimeout(0);
        receiver2.addMessageVerifier(messageVerifier);
        receiver2.start();

        ClientUtils.waitForReceiverUntil(receiver1, 100, 300000);
        ClientUtils.waitForReceiverUntil(receiver2, 100, 300000);

        // stop backups
        logger.info("#########################################");
        logger.info("Stopping backups!!!");
        logger.info("#########################################");
        container(2).stop();
        container(4).stop();
        logger.info("#########################################");
        logger.info("Backups stopped!!!");
        logger.info("#########################################");
        // this is IMPORTANT for lives to realize that backup are dead
        Thread.sleep(60000);
        // stop lives
        logger.info("#########################################");
        logger.info("Stopping lives!!!");
        logger.info("#########################################");
        container(1).stop();
        container(3).stop();
        logger.info("#########################################");
        logger.info("Lives stopped!!!");
        logger.info("#########################################");
        // start lives
        logger.info("#########################################");
        logger.info("Starting lives!!!");
        logger.info("#########################################");
        container(1).start();
        container(3).start();
        logger.info("#########################################");
        logger.info("Lives started!!!");
        logger.info("#########################################");
        // start backups
        logger.info("#########################################");
        logger.info("Starting backups!!!");
        logger.info("#########################################");
        container(2).start();
        container(4).start();
        logger.info("#########################################");
        logger.info("Backups started!!!");
        logger.info("#########################################");

        Thread.sleep(60000); // just wait a little more time how futher processing will continue
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

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }


    /**
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO")
    })
    public void testReplicationWithLargeJournal() throws Exception {
        testReplicationWithLargeJournalInternal();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO"),
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
    })
    public void testReplicationWithLargeJournalBlockPolicy() throws Exception {
        testReplicationWithLargeJournalInternal();
    }

    public void testReplicationWithLargeJournalInternal() throws Exception {

        container(1).start();
        container(2).start();

        Thread.sleep(5000);

        ProducerTransAck prod1 = new ProducerTransAck(container(1), PrepareBase.QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));
        prod1.addMessageVerifier(messageVerifier);
        prod1.setMessageBuilder(messageBuilder);
        prod1.setTimeout(0);
        prod1.setCommitAfter(10);
        prod1.start();

        // wait 5 minutes to generate large journal
        Thread.sleep(180000);

        // start receiver
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), PrepareBase.QUEUE_JNDI, 120000, 10, 100);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        ClientUtils.waitForReceiverUntil(receiver1, 50, 120000);

        logger.info("#########################################");
        logger.info("Kill live server !!!");
        logger.info("#########################################");
        container(1).kill();
        logger.info("#########################################");
        logger.info("Live server Killed !!!");
        logger.info("#########################################");

        ClientUtils.waitForClientToFailover(receiver1, 300000);
        ClientUtils.waitForReceiverUntil(receiver1, 150, 120000);
        // slow down receiver so failback takes a lot of time
        receiver1.setTimeout(100);
        prod1.setTimeout(1000);

        logger.info("#########################################");
        logger.info("Starting live server so failback occur!!!");
        logger.info("#########################################");
        container(1).start();
        logger.info("#########################################");
        logger.info("Live server started - but not synced - no failback!!!");
        logger.info("#########################################");
        // start backups
        logger.info("#########################################");
        logger.info("Live started and failback happened!!!");
        logger.info("#########################################");

        // chack that live is active
        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 1200000);

        receiver1.setReceiveTimeout(5000);
        receiver1.setTimeout(0);
        ClientUtils.waitForReceiverUntil(receiver1, 500, 300000);

        prod1.stopSending();
        prod1.join();
        receiver1.join();

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", messageVerifier.verifyMessages());

        container(2).stop();
        container(1).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO")
    })
    public void testDurableSubscriptionInCluster() throws Exception {
        testDurableSubscriptionInClusterInternal();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(params = {
            @Param(name = PrepareParams.CONNECTOR_TYPE, value = "NETTY_NIO"),
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
    })
    public void testDurableSubscriptionInClusterBlockPolicy() throws Exception {
        testDurableSubscriptionInClusterInternal();
    }

    public void testDurableSubscriptionInClusterInternal() throws Exception {

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        // Give them some time to sync
        Thread.sleep(10000);

        TextMessageVerifier messageVerifier1 = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        TextMessageVerifier messageVerifier2 = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));

        SubscriberTransAck subscriber1 = new SubscriberTransAck(container(1), PrepareBase.TOPIC_JNDI, 60000, 10, 10, "client-1", "subscriber-1");
        SubscriberTransAck subscriber2 = new SubscriberTransAck(container(3), PrepareBase.TOPIC_JNDI, 60000, 10, 10, "client-2", "subscriber-2");
        PublisherTransAck publisher = new PublisherTransAck(container(1), PrepareBase.TOPIC_JNDI, 1000, "publisher-1");

        subscriber1.addMessageVerifier(messageVerifier1);
        subscriber2.addMessageVerifier(messageVerifier2);
        publisher.addMessageVerifier(messageVerifier1);
        publisher.addMessageVerifier(messageVerifier2);

        publisher.start();
        subscriber1.start();
        subscriber2.start();

        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(Arrays.asList((Client) subscriber1, subscriber2), 100, 15000);

        container(4).stop();
        container(3).stop();

        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(Arrays.asList((Client) subscriber1), 100, 15000);

        container(3).start();
        container(4).start();

        boolean verifier1 = messageVerifier1.verifyMessages();
        boolean verifier2 = messageVerifier2.verifyMessages();

        Assert.assertTrue("There are failures detected by clients. More information in log - search for \"Lost\" or \"Duplicated\" messages", verifier1);
        Assert.assertTrue("There are failures detected by clients. More information in log - search for \"Lost\" or \"Duplicated\" messages", verifier2);
    }

}
