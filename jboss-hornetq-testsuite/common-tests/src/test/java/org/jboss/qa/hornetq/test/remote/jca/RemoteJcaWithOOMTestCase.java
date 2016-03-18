package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
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
 * Created by mnovak on 1/4/16.
 */
public class RemoteJcaWithOOMTestCase extends RemoteJcaLoadTestBase {

    private static final Logger logger = Logger.getLogger(RemoteJcaWithOOMTestCase.class);

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithLodhLikeMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(10);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(lodhLikemdb, container(3), messageBuilder, 50000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithLodhLikeMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(10);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(lodhLikemdb, container(2), messageBuilder, 50000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfJmsServerInClusterWithNormalMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(10);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(mdb1, container(3), messageBuilder, 50000);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void oomOfMdbServerInClusterWithNormalMdb() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(10);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        oomInClusterWithRestart(mdb1, container(2), messageBuilder, 50000);
    }

    private void oomInClusterWithRestart(Archive mdbToDeploy, Container containerForOOM, MessageBuilder messageBuilder, int numberOfMessages) throws Exception {

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
        producer1.setCommitAfter(100);
        producer1.setTimeout(0);
        producer1.setMessageVerifier(messageVerifier);
        producer1.start();
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 600000, container(1), container(3));

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
        // so wrap this call and try to kill server this server again and start
        containerForOOM.start();
        logger.info("Server with OOM was restarted: " + containerForOOM.getName());

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

    @Override
    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 60000, 2000, 100 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue", 10);
    }
}
