package org.jboss.qa.hornetq.test.remote.jca;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
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
 * Created by mnovak on 1/4/16.
 */
public abstract class RemoteJcaWithOOMAbstract extends RemoteJcaLoadTestBase {

    private static final Logger logger = Logger.getLogger(RemoteJcaWithOOMAbstract.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        oomOfJmsServerInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        oomOfJmsServerInClusterWithLodhLikeMdb(true);
    }

    private void oomOfJmsServerInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(lodhLikemdb, container(3), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithLodhLikeMdb10kbMessages() throws Exception {
        oomOfMdbServerInClusterWithLodhLikeMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithLodhLikeMdbLargeMessages() throws Exception {
        oomOfMdbServerInClusterWithLodhLikeMdb(true);
    }

    private void oomOfMdbServerInClusterWithLodhLikeMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(lodhLikemdb, container(2), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithNormalMdb10kbMessages() throws Exception {
        oomOfJmsServerInClusterWithNormalMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithNormalMdbLargeMessages() throws Exception {
        oomOfJmsServerInClusterWithNormalMdb(true);
    }

    private void oomOfJmsServerInClusterWithNormalMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(mdb1, container(3), messageBuilder, numberOfMessages);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithNormalMdb10kbMessages() throws Exception {
        oomOfMdbServerInClusterWithNormalMdb(false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithNormalMdbLargeMessages() throws Exception {
        oomOfMdbServerInClusterWithNormalMdb(true);
    }

    private void oomOfMdbServerInClusterWithNormalMdb(boolean isLargeMessages) throws Exception {
        ClientMixedMessageTypeBuilder messageBuilder = isLargeMessages ? new ClientMixedMessageTypeBuilder(LARGE_MESSAGE_SIZE_BYTES) : new ClientMixedMessageTypeBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = isLargeMessages ? LARGE_MESSAGE_TEST_MESSAGES : NORMAL_MESSAGE_TEST_MESSAGES;
        Map<String, String> jndiProperties = JMSTools.getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(mdb1, container(2), messageBuilder, numberOfMessages);
    }

    private void oomInClusterWithRestart(Archive mdbToDeploy, Container containerForOOM, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.JGROUPS_TCP);

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

        Assert.assertTrue(JMSTools.waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3)));

        int pid = ProcessIdUtils.getProcessId(containerForOOM);
        logger.info("Going to cause oom on server: " + containerForOOM.getName());
        containerForOOM.fail(Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
        logger.info("OOM was caused on server: " + containerForOOM.getName());

        logger.info("Wait for 5 min and restart.");
        Thread.sleep(300000);
        logger.info("Restarting server with OOM: " + containerForOOM.getName());
        ProcessIdUtils.killProcess(pid);
        containerForOOM.waitForKill();

        // this is a workaround - arquillian sometimes fails to start server and calls Proccess.destroy()
        // this for some reason does not take effect and hangs in Process.waitFor() forever
        // so wrap this call and try to kill this server again and start
        containerForOOM.start();

        logger.info("Server with OOM was restarted: " + containerForOOM.getName());

        logger.info("Wait more time for consumers on InQueue to process messages.");
        JMSTools.waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
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

//    @Override
//    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
//        jmsAdminOperations.removeAddressSettings("#");
//        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 60000, 2000, 100 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue", 10);
//    }
}
