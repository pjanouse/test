package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixedMessageTypeBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
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
public abstract class RemoteJcaWithHighCpuLoadTestCase extends RemoteJcaLoadTestBase {

    private static final Logger logger = Logger.getLogger(RemoteJcaWithHighCpuLoadTestCase.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadNormalMdb() throws Exception {
        testLoad(mdb1);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadLodhMdb() throws Exception {
        testLoad(lodhLikemdb);
    }

    private void testLoad(Archive mdbToDeploy) throws Exception {

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
//        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(false);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.setMessageVerifier(messageVerifier);
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
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");

        boolean areTherePreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1), 0, false);
        logger.info("Number of messages in DLQ is: " + new JMSTools().countMessages(dlqQueueName, container(1)));
        logger.info("Number of messages in InQueue is: " + new JMSTools().countMessages(inQueueName, container(1)));
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        long numberOfMessagesInInQueue = new JMSTools().countMessages(inQueueName, container(1));
        long numberOfMessagesInOutQueue = new JMSTools().countMessages(outQueueName, container(1));

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
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_KB);
        loadInClusterWithNormalMdb(messageBuilder);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithNormalMdbLargeMessages() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_KB);
        loadInClusterWithNormalMdb(messageBuilder);
    }

    public void loadInClusterWithNormalMdb(ClientMixedMessageTypeBuilder messageBuilder) throws Exception {
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        loadInCluster(mdb1, container(2), messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_KB);
        loadInClusterWithLodhLikeMdb(messageBuilder);
    }
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_KB);
        loadInClusterWithLodhLikeMdb(messageBuilder);
    }

    public void loadInClusterWithLodhLikeMdb(ClientMixedMessageTypeBuilder messageBuilder) throws Exception {
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        loadInCluster(lodhLikemdb, container(2), messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithLodhLikeMdbMixMessages() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(NORMAL_MESSAGE_SIZE_KB, LARGE_MESSAGE_SIZE_KB);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
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
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_KB);
        loadOnJmsInClusterWithLodhLikeMdb(messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadOnJmsInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_KB);
        loadOnJmsInClusterWithLodhLikeMdb(messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadOnJmsInClusterWithLodhLikeMdb(ClientMixedMessageTypeBuilder messageBuilder) throws Exception {
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        loadInCluster(lodhLikemdb, container(3), messageBuilder);
    }

    private void loadInCluster(Archive mdbToDeploy, Container containerUnderLoad, MessageBuilder messageBuilder) throws Exception {
        loadInCluster(mdbToDeploy, containerUnderLoad, messageBuilder, 50000);
    }

    private void loadInCluster(Archive mdbToDeploy, Container containerUnderLoad, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {

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
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.setMessageVerifier(messageVerifier);
        producer1.start();

        new JMSTools().waitForMessages(inQueueName, numberOfMessages / 2, 600000, container(1), container(3));

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3));

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
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 400000, container(1), container(3));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");

        boolean areTherePreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(3), 0, false);

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        long numberOfMessagesInInQueue = new JMSTools().countMessages(inQueueName, container(1), container(3));
        long numberOfMessagesInOutQueue = new JMSTools().countMessages(outQueueName, container(1), container(3));
        logger.info("Number of messages in InQueue is: " + numberOfMessagesInInQueue);
        logger.info("Number of messages in OutQueue is: " + numberOfMessagesInOutQueue);
        logger.info("Number of messages in DLQ is: " + new JMSTools().countMessages(dlqQueueName, container(1), container(3)));

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
    public void loadInClusterWithRestartLodhMdb() throws Exception {
        loadInClusterWithRestart(lodhLikemdb);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void loadInClusterWithRestartNormalMdb() throws Exception {
        loadInClusterWithRestart(mdb1);
    }

    private void loadInClusterWithRestart(Archive mdbToDeploy) throws Exception {

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
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, 50000);
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.setMessageVerifier(messageVerifier);
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
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(400000, container(3), 0, false);


        restartServers();

        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        logger.info("Number of messages in InQueue is: " + new JMSTools().countMessages(inQueueName, container(1), container(3)));
        logger.info("Number of messages in OutQueue is: " + new JMSTools().countMessages(outQueueName, container(1), container(3)));
        logger.info("Number of messages in DLQ is: " + new JMSTools().countMessages(dlqQueueName, container(1), container(3)));

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
