package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
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
public class RemoteJcaWithSuspendTestCase extends RemoteJcaLoadTestBase {
    private static final Logger logger = Logger.getLogger(RemoteJcaWithHighCpuLoadTestCase.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithLodhLikeMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInCluster(lodhLikemdb, container(2), messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithLodhLikeMdbMixMessages() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 1000);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
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
    public void suspendOfJmsInClusterWithLodhLikeMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInCluster(lodhLikemdb, container(3), messageBuilder);
    }

    private void suspendInCluster(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder) throws Exception {
        suspendInCluster(mdbToDeploy, containerToSuspend, messageBuilder, 50000);
    }

    private void suspendInCluster(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {

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

        int containerToSuspenId = ProcessIdUtils.getProcessId(containerToSuspend);
        logger.info("Going to suspend server: " + containerToSuspend.getName());
        ProcessIdUtils.suspendProcess(containerToSuspenId);
        Thread.sleep(600000);
        logger.info("Going to resume server: " + containerToSuspend.getName());
        ProcessIdUtils.resumeProcess(containerToSuspenId);

        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 60000, container(1), container(3));
        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(3), 0, false);
        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        long numberOfMessagesInInQueue = new JMSTools().countMessages(inQueueName, container(1), container(3));
        logger.info("Number of messages in InQueue is: " + numberOfMessagesInInQueue);
        logger.info("Number of messages in OutQueue is: " + new JMSTools().countMessages(outQueueName, container(1), container(3)));
        logger.info("Number of messages in DLQ is: " + new JMSTools().countMessages(dlqQueueName, container(1), container(3)));

        messageVerifier.verifyMessages();

        printThreadDumpsOfAllServers();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size() + numberOfMessagesInInQueue);
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
    public void suspendOfMdbInClusterWithRestartWithLodhLikeMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInClusterWithRestart(lodhLikemdb, container(2), messageBuilder);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void suspendOfMdbInClusterWithRestartWithLodhLikeMdbMixMessagesWithRestart() throws Exception {
        ClientMixMessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 1000);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
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
    public void suspendOfJmsInClusterWithRestartWithLodhLikeMdbWithRestart() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(true);
        messageBuilder.setJndiProperties(jndiProperties);
        suspendInClusterWithRestart(lodhLikemdb, container(3), messageBuilder);
    }

    private void suspendInClusterWithRestart(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder) throws Exception {
        suspendInClusterWithRestart(mdbToDeploy, containerToSuspend, messageBuilder, 50000);
    }

    private void suspendInClusterWithRestart(Archive mdbToDeploy, Container containerToSuspend, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {

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

        int containerToSuspenId = ProcessIdUtils.getProcessId(containerToSuspend);
        logger.info("Going to suspend server: " + containerToSuspend.getName());
        ProcessIdUtils.suspendProcess(containerToSuspenId);
        Thread.sleep(600000);
        logger.info("Going to resume server: " + containerToSuspend.getName());
        ProcessIdUtils.resumeProcess(containerToSuspenId);

        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 60000, container(1), container(3));
        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        logger.info("Number of messages in InQueue is: " + new JMSTools().countMessages(inQueueName, container(1), container(3)));
        logger.info("Number of messages in OutQueue is: " + new JMSTools().countMessages(outQueueName, container(1), container(3)));
        logger.info("Number of messages in DLQ is: " + new JMSTools().countMessages(dlqQueueName, container(1), container(3)));

        logger.info("Restart servers.");
        restartServers();
        logger.info("Servers restarted.");

        logger.info("Wait more time for consumers on InQueue to process messages.");
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
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
