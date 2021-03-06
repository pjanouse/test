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
import org.jboss.qa.hornetq.tools.HighCPUUtils;
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
public abstract class RemoteJcaWithHighCpuLoadAbstract extends RemoteJcaLoadTestBase {

    private static final Logger logger = Logger.getLogger(RemoteJcaWithHighCpuLoadAbstract.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadNormalMdbMixMessages() throws Exception {
        testLoad(mdb1);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadLodhMdbMixMessages() throws Exception {
        testLoad(lodhLikemdb);
    }

    private void testLoad(Archive mdbToDeploy) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        } else {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO);
        }

        // cluster A
        container(1).start();

        // cluster B
        container(2).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, 50000);
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(NORMAL_MESSAGE_SIZE_BYTES, LARGE_MESSAGE_SIZE_BYTES);
        messageBuilder.setAddDuplicatedHeader(false);
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(10);
        producer1.setTimeout(0);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
//        container(4).deploy(mdb1);

        Process highCpuLoader = null;
        try {
            // bind mdb EAP server to cpu core
            String cpuToBind = "0";
            highCpuLoader = HighCPUUtils.causeMaximumCPULoadOnContainer(container(2), cpuToBind);
            logger.info("High Cpu loader was bound to cpu: " + cpuToBind);
            Thread.sleep(300000);
        } finally {
            if (highCpuLoader != null) {
                highCpuLoader.destroy();
            }
        }

        // if messages are consumed from InQueue then we're ok, if no message received for 5 min time out then continue
        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");

        boolean areTherePreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1), 0, false);
        logger.info("Number of messages in DLQ is: " + JMSTools.countMessages(dlqQueueName, container(1)));
        logger.info("Number of messages in InQueue is: " + JMSTools.countMessages(inQueueName, container(1)));
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        long numberOfMessagesInInQueue = JMSTools.countMessages(inQueueName, container(1));
        long numberOfMessagesInOutQueue = JMSTools.countMessages(outQueueName, container(1));

        messageVerifier.verifyMessages();

        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size() + numberOfMessagesInInQueue + numberOfMessagesInOutQueue);
        Assert.assertTrue("There should be no prepared transactions in HornetQ/Artemis but there are!!! Number of prepared TXs is: "
                + areTherePreparedTransactions, areTherePreparedTransactions);

        container(2).undeploy(mdbToDeploy);
        container(2).stop();
        container(1).stop();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithNormalMdb10kbMessages() throws Exception {
        loadInClusterWithNormalMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithNormalMdbLargeMessages() throws Exception {
        loadInClusterWithNormalMdb(true);
    }

    private void loadInClusterWithNormalMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        loadInCluster(mdb1, container(2), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        loadInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        loadInClusterWithLodhLikeMdb(true);
    }

    private void loadInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        loadInCluster(lodhLikemdb, container(2), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdbMixMessages() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(NORMAL_MESSAGE_SIZE_BYTES, LARGE_MESSAGE_SIZE_BYTES);
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        loadInCluster(lodhLikemdb, container(2), messageBuilder, 10000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadOnJmsInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        loadOnJmsInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadOnJmsInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        loadOnJmsInClusterWithLodhLikeMdb(true);
    }

    private void loadOnJmsInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        loadInCluster(lodhLikemdb, container(3), messageBuilder, numberOfMessages);
    }

    private void loadInCluster(Archive mdbToDeploy, Container containerUnderLoad, MessageBuilder messageBuilder) throws Exception {
        loadInCluster(mdbToDeploy, containerUnderLoad, messageBuilder, 50000);
    }

    private void loadInCluster(Archive mdbToDeploy, Container containerUnderLoad, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {
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

        Process highCpuLoader1 = null;
        try {
            // bind mdb EAP server to cpu core
            String cpuToBind = "0";
            highCpuLoader1 = HighCPUUtils.causeMaximumCPULoadOnContainer(containerUnderLoad, cpuToBind);
            logger.info("High Cpu loader was bound to cpu: " + cpuToBind);
            Thread.sleep(300000);
        } finally {
            if (highCpuLoader1 != null) {
                highCpuLoader1.destroy();
                try {
                    ProcessIdUtils.killProcess(ProcessIdUtils.getProcessId(highCpuLoader1));
                } catch (Exception ex) {
                    // we just ignore it as it's not fatal not to kill it
                    logger.warn("Process high cpu loader could not be killed, we're ignoring it it's not fatal usually.", ex);
                }
            }
        }
        producer1.join();

        // Wait until some messages are consumes from InQueue
        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 400000, container(1), container(3));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");

        boolean areTherePreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(3), 0, false);

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
        Assert.assertTrue("There should be no prepared transactions in HornetQ/Artemis but there are!!!", areTherePreparedTransactions);

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
    public void loadInClusterWithRestartLodhMdb10kbMessages() throws Exception {
        loadInClusterWithRestart(lodhLikemdb, false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithRestartLodhMdbLargeMessages() throws Exception {
        loadInClusterWithRestart(lodhLikemdb, true);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithRestartNormalMdb10kbMessages() throws Exception {
        loadInClusterWithRestart(mdb1, false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithRestartNormalMdbLargeMessages() throws Exception {
        loadInClusterWithRestart(mdb1, true);
    }

    private void loadInClusterWithRestart(Archive mdbToDeploy, boolean isLargeMessage) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        final int numberOfMessages = isLargeMessage ? 15000 : 50000;
        final ClientMixedMessageTypeBuilder messageBuilder = isLargeMessage ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);

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
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(10);
        producer1.setTimeout(0);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        Process highCpuLoader = null;
        try {
            // bind mdb EAP server to cpu core
            String cpuToBind = "0,1";
            highCpuLoader = HighCPUUtils.causeMaximumCPULoadOnContainer(container(2), cpuToBind);
            logger.info("High Cpu loader was bound to cpu: " + cpuToBind);

            Thread.sleep(300000);
            logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");
        } finally {
            if (highCpuLoader != null) {
                highCpuLoader.destroy();
                try {
                    ProcessIdUtils.killProcess(ProcessIdUtils.getProcessId(highCpuLoader));
                } catch (Exception ex) {
                    // we just ignore it as it's not fatal not to kill it
                    logger.warn("Process high cpu loader could not be killed, we're ignoring it it's not fatal usually.", ex);
                }
            }
        }
        // Wait until some messages are consumed from InQueue
        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(1), 0, false);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(3), 0, false);

        restartServers();

        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(120000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(120000, container(3), 0, false);

        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        logger.info("Number of messages in InQueue is: " + JMSTools.countMessages(inQueueName, container(1), container(3)));
        logger.info("Number of messages in OutQueue is: " + JMSTools.countMessages(outQueueName, container(1), container(3)));
        logger.info("Number of messages in DLQ is: " + JMSTools.countMessages(dlqQueueName, container(1), container(3)));

        messageVerifier.verifyMessages();
        Assert.assertFalse("There are duplicated messages. Number of received messages is: " + receiver1.getListOfReceivedMessages().size(),
                producer1.getListOfSentMessages().size() < receiver1.getListOfReceivedMessages().size());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("There should be no prepared transactions in HornetQ/Artemis but there are!!!", noPreparedTransactions);

        container(2).undeploy(mdbToDeploy);
        container(4).undeploy(mdbToDeploy);
        container(2).stop();
        container(4).stop();
        container(3).stop();
        container(1).stop();
    }


}
