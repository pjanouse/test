package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixedMessageTypeBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
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
 * Created by mstyk on 24/5/16.
 */
public abstract class RemoteJcaWithSocketExhaustionAbstract extends RemoteJcaLoadTestBase {

    private static final Logger logger = Logger.getLogger(RemoteJcaWithSocketExhaustionAbstract.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfJmsServerInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        socketExhaustionOfJmsServerInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfJmsServerInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        socketExhaustionOfJmsServerInClusterWithLodhLikeMdb(true);
    }

    private void socketExhaustionOfJmsServerInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        socketExhaustionInCluster(lodhLikemdb, container(3), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfMdbServerInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        socketExhaustionOfMdbServerInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfMdbServerInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        socketExhaustionOfMdbServerInClusterWithLodhLikeMdb(true);
    }

    private void socketExhaustionOfMdbServerInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        socketExhaustionInCluster(lodhLikemdb, container(2), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfJmsServerInClusterWithNormalMdb10kbMessages() throws Exception {
        socketExhaustionOfJmsServerInClusterWithNormalMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfJmsServerInClusterWithNormalMdbLargeMessages() throws Exception {
        socketExhaustionOfJmsServerInClusterWithNormalMdb(true);
    }

    private void socketExhaustionOfJmsServerInClusterWithNormalMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        socketExhaustionInCluster(mdb1, container(3), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfMdbServerInClusterWithNormalMdb10kbMessages() throws Exception {
        socketExhaustionOfMdbServerInClusterWithNormalMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void socketExhaustionOfMdbServerInClusterWithNormalMdbLargeMessages() throws Exception {
        socketExhaustionOfMdbServerInClusterWithNormalMdb(true);
    }

    private void socketExhaustionOfMdbServerInClusterWithNormalMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        socketExhaustionInCluster(mdb1, container(2), messageBuilder, numberOfMessages);
    }

    private void socketExhaustionInCluster(Archive mdbToDeploy, Container containerForSocketExhaustion, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.JGROUPS_TCP);
        } else {
            prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.JGROUPS_TCP);
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
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3));

        int pid = ProcessIdUtils.getProcessId(containerForSocketExhaustion);
        logger.info("Going to cause socket exhaustion on server: " + containerForSocketExhaustion.getName());
        containerForSocketExhaustion.fail(Constants.FAILURE_TYPE.SOCKET_EXHAUSTION);
        logger.info("socket exhaustion was caused on server: " + containerForSocketExhaustion.getName());


        logger.info("Wait more time for consumers on InQueue to process messages.");
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        logger.info("Waiting is over now. Check if there are prepared transactions and 500 seconds for recovery.");

        boolean noPreparedTransactions = new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(1), 0, false) &&
                new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(500000, container(3), 0, false);
        logger.info("Start receiving messages.");
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 70000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.setCommitAfter(100);
        receiver1.start();
        receiver1.join();
        messageVerifier.addReceivedMessages(receiver1.getListOfReceivedMessages());

        printThreadDumpsOfAllServers();

        Assert.assertTrue("There is different number of sent and received messages. Check logs for message IDs of missing/lost messages.",
                messageVerifier.verifyMessages());
        Assert.assertTrue("There should be no prepared transactions after restart in HornetQ/Artemis but there are!!!", noPreparedTransactions);

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

}
