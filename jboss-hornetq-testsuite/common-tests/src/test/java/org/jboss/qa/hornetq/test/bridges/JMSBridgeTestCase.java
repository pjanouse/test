package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author mnovak@redhat.com
 * @tpChapter   BACKWARD COMPATIBILITY TESTING
 * @tpSubChapter COMPATIBILITY OF JMS BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-integration-journal-and-jms-bridge-compatibility-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5534/hornetq-integration#testcases
 */
public class JMSBridgeTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JMSBridgeTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;

    // quality services
    public final static String AT_MOST_ONCE = "AT_MOST_ONCE";
    public final static String DUPLICATES_OK = "DUPLICATES_OK";
    public final static String ONCE_AND_ONLY_ONCE = "ONCE_AND_ONLY_ONCE";

    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10,200);

    FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();

    // Queue to send messages in
    String inQueueName = "InQueue";
    String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    String outQueueName = "OutQueue";
    String outQueueJndiName = "jms/queue/" + outQueueName;

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "AT_MOST_ONCE" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from older to newer server between inQueue and outQueue with "AT_MOST_ONCE" QoS</li>
     *     <li>producer starts to send messages to inQueue on older server</li>
     *     <li>receiver receives messages from outQueue on newer server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_AT_MOST_ONCE() throws Exception {

        testBridge(container(1), container(3), AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "ONCE_AND_ONLY_ONCE" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from older to newer server between inQueue and outQueue with "ONCE_AND_ONLY_ONCE" QoS</li>
     *     <li>producer starts to send messages to inQueue on older server</li>
     *     <li>receiver receives messages from outQueue on newer server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(container(1), container(3), ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "DUPLICATES_OK" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from older to newer server between inQueue and outQueue with "DUPLICATES_OK" QoS</li>
     *     <li>producer starts to send messages to inQueue on older server</li>
     *     <li>receiver receives messages from outQueue on newer server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_DUPLICATES_OK() throws Exception {

        testBridge(container(1), container(3), DUPLICATES_OK);
    }

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "AT_MOST_ONCE" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from newer to older server between inQueue and outQueue with "AT_MOST_ONCE" QoS</li>
     *     <li>producer starts to send messages to inQueue on newer server</li>
     *     <li>receiver receives messages from outQueue on older server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_AT_MOST_ONCE() throws Exception {

        testBridge(container(3), container(1), AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "ONCE_AND_ONLY_ONCE" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from newer to older server between inQueue and outQueue with "ONCE_AND_ONLY_ONCE" QoS</li>
     *     <li>producer starts to send messages to inQueue on newer server</li>
     *     <li>receiver receives messages from outQueue on older server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(container(3), container(1), ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails  Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "DUPLICATES_OK" quality of service.
     * @tpProcedure <ul>
     *     <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     *     <li>Configure jms bridge from newer to older server between inQueue and outQueue with "DUPLICATES_OK" QoS</li>
     *     <li>producer starts to send messages to inQueue on newer server</li>
     *     <li>receiver receives messages from outQueue on older server</li>
     *     <li>wait for producer and receiver to finish</li>
     *     <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_DUPLICATES_OK() throws Exception {

        testBridge(container(3), container(1), DUPLICATES_OK);
    }

    /**
     * @throws Exception
     */
    public void     testBridge(Container inServer, Container outServer, String qualityOfService) throws Exception {

        prepareServers(inServer, outServer, qualityOfService);
        inServer.start();
        outServer.start();

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(inServer,
                inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.setMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        ReceiverClientAck receiver1 = new ReceiverClientAck(outServer,
                outQueueJndiName, 10000, 100, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.start();
        receiver1.join();
        producerToInQueue1.join();

        logger.info("Producer: " + producerToInQueue1.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver1.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producerToInQueue1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        outServer.stop();
        inServer.stop();
    }

    private void prepareServers(Container inServer, Container outServer, String qualityOfService) {
        if (ContainerUtils.isEAP6(inServer))    {
            prepareServersEAP6(inServer, outServer, qualityOfService);
        } else {
            prepareServersEAP7(inServer, outServer, qualityOfService);
        }
    }

    private void prepareServersEAP6(Container inServer, Container outServer, String qualityOfService) {
        prepareServerEAP6(inServer);
        prepareServerEAP6(outServer);
        deployBridgeEAP6(inServer, outServer, qualityOfService, -1);
    }

    private void prepareServersEAP7(Container inServer, Container outServer, String qualityOfService) {
        prepareServerEAP7(inServer);
        prepareServerEAP7(outServer);
        deployBridgeEAP7(inServer, outServer, qualityOfService, -1);
    }

    protected void prepareServerEAP6(Container container) {

        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "RemoteConnectionFactory";
        String clusterName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);
        jmsAdminOperations.removeClusteringGroup(clusterName);
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
        container.stop();
    }

    protected void prepareServerEAP7(Container container) {

        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "RemoteConnectionFactory";
        String clusterName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeClusteringGroup(clusterName);
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
        container.stop();
    }


    private void deployBridgeEAP6(Container containerToDeploy, Container outServer, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;

        String targetConnectionFactory = "jms/RemoteConnectionFactory";
        String targetDestination = outQueueJndiName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + outServer.getHostname() + ":" + outServer.getJNDIPort());

        if (qualityOfService == null || "".equals(qualityOfService))
        {
            qualityOfService = ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }

    private void deployBridgeEAP7(Container containerToDeploy, Container outServer, String qualityOfService, int maxRetries) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = inQueueJndiName;

        String targetConnectionFactory = "jms/RemoteConnectionFactory";
        String targetDestination = outQueueJndiName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        targetContext.put("java.naming.provider.url", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + outServer.getHostname() + ":" + outServer.getJNDIPort());

        if (qualityOfService == null || "".equals(qualityOfService))
        {
            qualityOfService = ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }
}
