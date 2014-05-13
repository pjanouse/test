package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * Created by mnovak on 5/13/14.
 */
public class JMSBridgeTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JMSBridgeTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;

    // quality services
    public final static String AT_MOST_ONCE = "AT_MOST_ONCE";
    public final static String DUPLICATES_OK = "DUPLICATES_OK";
    public final static String ONCE_AND_ONLY_ONCE = "ONCE_AND_ONLY_ONCE";

    public final static String OLD_SERVER = CONTAINER1;
    public final static String NEW_SERVER = CONTAINER3;

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10,200);

    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();

    // Queue to send messages in
    String inQueueName = "InQueue";
    String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    String outQueueName = "OutQueue";
    String outQueueJndiName = "jms/queue/" + outQueueName;

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_AT_MOST_ONCE() throws Exception {

        testBridge(OLD_SERVER, NEW_SERVER, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(OLD_SERVER, NEW_SERVER, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_DUPLICATES_OK() throws Exception {

        testBridge(OLD_SERVER, NEW_SERVER, DUPLICATES_OK);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_AT_MOST_ONCE() throws Exception {

        testBridge(NEW_SERVER, OLD_SERVER, AT_MOST_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(NEW_SERVER, OLD_SERVER, ONCE_AND_ONLY_ONCE);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_DUPLICATES_OK() throws Exception {

        testBridge(NEW_SERVER, OLD_SERVER, DUPLICATES_OK);
    }

    /**
     * @throws Exception
     */
    public void testBridge(String inServer, String outServer, String qualityOfService) throws Exception {

        prepareServers(inServer, outServer, qualityOfService);
        controller.start(inServer);
        controller.start(outServer);

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(getHostname(inServer), getJNDIPort(inServer), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(getHostname(outServer), getJNDIPort(outServer), outQueueJndiName, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        stopServer(outServer);
        stopServer(inServer);
    }

    private void prepareServers(String inServer, String outServer, String qualityOfService) {
        prepareServer(inServer);
        prepareServer(outServer);
        deployBridge(inServer, outServer, qualityOfService, -1);
    }

    protected void prepareServer(String containerName) {

        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.start(containerName);
        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setFactoryType(inVmConnectionFactory, "XA_GENERIC");
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        jmsAdminOperations.close();
        stopServer(containerName);

    }

    private void deployBridge(String containerToDeploy, String outServer, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;

        String targetConnectionFactory = "jms/RemoteConnectionFactory";
        String targetDestination = outQueueJndiName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + getHostname(outServer) + ":4447");

        if (qualityOfService == null || "".equals(qualityOfService))
        {
            qualityOfService = "ONCE_AND_ONLY_ONCE";
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        controller.start(containerToDeploy);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerToDeploy);

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();

        stopServer(containerToDeploy);
    }
}
