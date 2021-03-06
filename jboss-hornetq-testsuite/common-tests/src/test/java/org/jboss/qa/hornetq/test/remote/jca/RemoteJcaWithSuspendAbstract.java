package org.jboss.qa.hornetq.test.remote.jca;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixedMessageTypeBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Created by mnovak on 12/14/15.
 */
public abstract class RemoteJcaWithSuspendAbstract extends RemoteJcaLoadTestBase {
    private static final Logger logger = Logger.getLogger(RemoteJcaWithHighCpuLoadAbstract.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        suspendOfMdbInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        suspendOfMdbInClusterWithLodhLikeMdb(true);
    }

    private void suspendOfMdbInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES):new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInCluster(lodhLikemdb, container(2), messageBuilder,numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithLodhLikeMdbMixMessages() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(NORMAL_MESSAGE_SIZE_BYTES, LARGE_MESSAGE_SIZE_BYTES);
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInCluster(lodhLikemdb, container(2), messageBuilder, 10000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfJmsInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        suspendOfJmsInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfJmsInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        suspendOfJmsInClusterWithLodhLikeMdb(true);
    }

    private void suspendOfJmsInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES):new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInCluster(lodhLikemdb, container(3), messageBuilder,numberOfMessages);
    }

    private void suspendInCluster(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder) throws Exception {
        suspendInCluster(mdbToDeploy, containerToSuspend, messageBuilder, 50000);
    }

    private void suspendInCluster(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        } else {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO);
        }

        // cluster A
        container(1).start();
        container(3).start();

        // cluster B
        container(2).start();
        container(4).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(10);
        producer1.setTimeout(0);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();

        Assert.assertTrue(JMSTools.waitForMessages(inQueueName, numberOfMessages / 2, 600000, container(1), container(3)));

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3)));

        int containerToSuspenId = ProcessIdUtils.getProcessId(containerToSuspend);
        logger.info("Going to suspend server: " + containerToSuspend.getName());
        ProcessIdUtils.suspendProcess(containerToSuspenId);
        Thread.sleep(600000);
        logger.info("Going to resume server: " + containerToSuspend.getName());
        ProcessIdUtils.resumeProcess(containerToSuspenId);

        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 60000, container(1), container(3));
        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(3), 0, false);
        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        long numberOfMessagesInInQueue = JMSTools.countMessages(inQueueName, container(1), container(3));
        long numberOfMessagesInOutQueue = JMSTools.countMessages(outQueueName, container(1), container(3));
        logger.info("Number of messages in InQueue is: " + numberOfMessagesInInQueue);
        logger.info("Number of messages in OutQueue is: " + numberOfMessagesInOutQueue);
        logger.info("Number of messages in DLQ is: " + JMSTools.countMessages(dlqQueueName, container(1), container(3)));

        messageVerifier.verifyMessages();

        printThreadDumpsOfAllServers();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size() + numberOfMessagesInInQueue + numberOfMessagesInOutQueue);
        Assert.assertTrue("There should be no prepared transactions in HornetQ/Artemis but there are!!!", noPreparedTransactions);

        container(2).undeploy(mdbToDeploy);
        container(4).undeploy(mdbToDeploy);
        container(2).stop();
        container(4).stop();
        container(3).stop();
        container(1).stop();
    }

    private void printThreadDumpsOfAllServers() throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithRestartWithLodhLikeMdb10kbMessages() throws Exception {
        suspendOfMdbInClusterWithRestartWithLodhLikeMdb(false);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithRestartWithLodhLikeMdbLargeMessages() throws Exception {
        suspendOfMdbInClusterWithRestartWithLodhLikeMdb(true);
    }

    private void suspendOfMdbInClusterWithRestartWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES):new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInClusterWithRestart(lodhLikemdb, container(2), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithRestartWithLodhLikeMdbMixMessagesWithRestart() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 1000);
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInClusterWithRestart(lodhLikemdb, container(2), messageBuilder, 10000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestart10kbMessages() throws Exception{
        suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestart(false);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestartLargeMessages() throws Exception{
        suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestart(true);
    }

    private void suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestart(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES):new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInClusterWithRestart(lodhLikemdb, container(3), messageBuilder,numberOfMessages);
    }

    private void suspendInClusterWithRestart(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder) throws Exception {
        suspendInClusterWithRestart(mdbToDeploy, containerToSuspend, messageBuilder, 50000);
    }

    private void suspendInClusterWithRestart(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        } else {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO);
        }

        // cluster A
        container(1).start();
        container(3).start();

        // cluster B
        container(2).start();
        container(4).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(10);
        producer1.setTimeout(0);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();

        Assert.assertTrue(JMSTools.waitForMessages(inQueueName, numberOfMessages / 2, 600000, container(1), container(3)));

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3)));

        int containerToSuspenId = ProcessIdUtils.getProcessId(containerToSuspend);
        logger.info("Going to suspend server: " + containerToSuspend.getName());
        ProcessIdUtils.suspendProcess(containerToSuspenId);
        Thread.sleep(600000);
        logger.info("Going to resume server: " + containerToSuspend.getName());
        ProcessIdUtils.resumeProcess(containerToSuspenId);

        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 60000, container(1), container(3));
        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        logger.info("Number of messages in InQueue is: " + JMSTools.countMessages(inQueueName, container(1), container(3)));
        logger.info("Number of messages in OutQueue is: " + JMSTools.countMessages(outQueueName, container(1), container(3)));
        logger.info("Number of messages in DLQ is: " + JMSTools.countMessages(dlqQueueName, container(1), container(3)));

        logger.info("Restart servers.");
        restartServers();
        logger.info("Servers restarted.");

        logger.info("Wait more time for consumers on InQueue to process messages.");
        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        logger.info("Waiting is over now. Check if there are prepared transactions and 500 seconds for recovery.");
        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(3), 0, false);
        logger.info("Start receiving messages.");
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver2.start();
        receiver2.join();
        messageVerifier.addReceivedMessages(receiver2.getListOfReceivedMessages());

        messageVerifier.verifyMessages();
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        Assert.assertTrue("There should be no prepared transactions after restart in HornetQ/Artemis but there are!!!", noPreparedTransactions);

        container(2).undeploy(mdbToDeploy);
        container(4).undeploy(mdbToDeploy);
        container(2).stop();
        container(4).stop();
        container(3).stop();
        container(1).stop();
    }

}
