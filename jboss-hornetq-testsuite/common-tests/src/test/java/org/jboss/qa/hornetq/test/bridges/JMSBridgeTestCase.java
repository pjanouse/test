package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

/**
 * @author mnovak@redhat.com
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-integration-journal-and-jms-bridge-compatibility-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
public class JMSBridgeTestCase extends HornetQTestCase {

    protected static final Logger logger = Logger.getLogger(JMSBridgeTestCase.class);

    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;

    private static final String JMS_BRIDGE_NAME = "myBridge";


    protected MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

    protected FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

    // Queue to send messages in
    private String inQueueName = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    private String outQueueName = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueueName;

    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
        container(3).stop();
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "AT_MOST_ONCE" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     * <li>Configure jms bridge from older to newer server between inQueue and outQueue with "AT_MOST_ONCE" QoS</li>
     * <li>producer starts to send messages to inQueue on older server</li>
     * <li>receiver receives messages from outQueue on newer server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_AT_MOST_ONCE() throws Exception {

        testBridge(container(1), container(3), Constants.QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "ONCE_AND_ONLY_ONCE" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     * <li>Configure jms bridge from older to newer server between inQueue and outQueue with "ONCE_AND_ONLY_ONCE" QoS</li>
     * <li>producer starts to send messages to inQueue on older server</li>
     * <li>receiver receives messages from outQueue on newer server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(container(1), container(3), Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "DUPLICATES_OK" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is older and deploy destinations on both of them</li>
     * <li>Configure jms bridge from older to newer server between inQueue and outQueue with "DUPLICATES_OK" QoS</li>
     * <li>producer starts to send messages to inQueue on older server</li>
     * <li>receiver receives messages from outQueue on newer server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOldNew_DUPLICATES_OK() throws Exception {

        testBridge(container(1), container(3), Constants.QUALITY_OF_SERVICE.DUPLICATES_OK);
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "AT_MOST_ONCE" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     * <li>Configure jms bridge from newer to older server between inQueue and outQueue with "AT_MOST_ONCE" QoS</li>
     * <li>producer starts to send messages to inQueue on newer server</li>
     * <li>receiver receives messages from outQueue on older server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_AT_MOST_ONCE() throws Exception {

        testBridge(container(3), container(1), Constants.QUALITY_OF_SERVICE.AT_MOST_ONCE);
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "ONCE_AND_ONLY_ONCE" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     * <li>Configure jms bridge from newer to older server between inQueue and outQueue with "ONCE_AND_ONLY_ONCE" QoS</li>
     * <li>producer starts to send messages to inQueue on newer server</li>
     * <li>receiver receives messages from outQueue on older server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_ONCE_AND_ONLY_ONCE() throws Exception {

        testBridge(container(3), container(1), Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
    }

    /**
     * @tpTestDetails Start two EAP 7.x servers. First is old and second new. Destinations are deployed to both of them
     * Configure JMS bridge from InQueue to OutQueue(on other server) on one of them with "DUPLICATES_OK" quality of service.
     * @tpProcedure <ul>
     * <li>start two servers with different minor version of EAP7, first is newer and deploy destinations on both of them</li>
     * <li>Configure jms bridge from newer to older server between inQueue and outQueue with "DUPLICATES_OK" QoS</li>
     * <li>producer starts to send messages to inQueue on newer server</li>
     * <li>receiver receives messages from outQueue on older server</li>
     * <li>wait for producer and receiver to finish</li>
     * <li>verify messages count</li>
     * </ul>
     * @tpPassCrit receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNewOld_DUPLICATES_OK() throws Exception {

        testBridge(container(3), container(1), Constants.QUALITY_OF_SERVICE.DUPLICATES_OK);
    }
    /**
     * @throws Exception
     */
    public void testBridge(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService) throws Exception {

        prepareServers(inServer, outServer, qualityOfService);
        inServer.start();
        outServer.start();

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        sendReceiveSubTest(inServer, outServer);


        outServer.stop();
        inServer.stop();
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testStartStopJMSBridge() throws Exception {

        Container inServer = container(1);
        Container outServer = container(3);

        prepareServers(inServer, outServer, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
        outServer.start();
        inServer.start();

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(inServer,
                inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, outServer);

        logger.info("#############################");
        logger.info("JMS bridge stop is called.");
        logger.info("#############################");
        JMSOperations jmsOperations = inServer.getJmsOperations();
        jmsOperations.stopJMSBridge(JMS_BRIDGE_NAME);
        Thread.sleep(5000);

        logger.info("#############################");
        logger.info("JMS bridge start is called.");
        logger.info("#############################");
        jmsOperations.startJMSBridge(JMS_BRIDGE_NAME);
        jmsOperations.close();

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 180000, outServer);

        ReceiverClientAck receiver1 = new ReceiverClientAck(outServer,
                outQueueJndiName, 10000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
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

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testReloadOfServerWithJMSBridge() throws Exception {

        Container inServer = container(1);
        Container outServer = container(3);

        prepareServers(inServer, outServer, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE);
        outServer.start();
        inServer.start();

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        ProducerClientAck producerToInQueue1 = new ProducerClientAck(inServer,
                inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producerToInQueue1.setMessageBuilder(messageBuilder);
        producerToInQueue1.setTimeout(0);
        producerToInQueue1.addMessageVerifier(messageVerifier);
        producerToInQueue1.start();

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 100, 120000, outServer);

        logger.info("#############################");
        logger.info("Reload source server - " + inServer);
        logger.info("#############################");
        JMSOperations jmsOperations = inServer.getJmsOperations();
        jmsOperations.reloadServer();
        Thread.sleep(5000);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 180000, outServer);

        ReceiverClientAck receiver1 = new ReceiverClientAck(outServer,
                outQueueJndiName, 10000, 100, 10);
        receiver1.addMessageVerifier(messageVerifier);
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

    /**
     * @throws Exception
     */
    public void testStartStopJMSBridge(Container inServer, Container outServer, String qualityOfService) throws Exception {



    }

    protected void sendReceiveSubTest(Container inServer, Container outServer) throws Exception{
        ProducerClientAck producer = new ProducerClientAck(inServer,
                inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.addMessageVerifier(messageVerifier);
        producer.start();

        ReceiverClientAck receiver = new ReceiverClientAck(outServer,
                outQueueJndiName, 10000, 100, 10);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();
        producer.join();

        logger.info("Producer: " + producer.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertTrue("No send messages.", producer.getListOfSentMessages().size() > 0);

    }

    /**
     * Prepares servers
     * @param inServer source server
     * @param outServer targetServer
     * @param qualityOfService desired quality of service @see Constants.QUALITY_OF_SERVICE
     */
    private void prepareServers(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService) {
        if (ContainerUtils.isEAP6(inServer)) {
            prepareServersEAP6(inServer, outServer, qualityOfService);
        } else {
            prepareServersEAP7(inServer, outServer, qualityOfService);
        }
    }

    private void prepareServersEAP6(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService) {
        prepareServerEAP6(inServer);
        if (!inServer.getName().equals(outServer.getName())) {
            prepareServerEAP6(outServer);
        }
        deployBridgeEAP6(inServer, outServer, qualityOfService, -1);
    }

    private void prepareServersEAP7(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService) {
        prepareServerEAP7(inServer);
        if (!inServer.getName().equals(outServer.getName())) {
            prepareServerEAP7(outServer);
        }
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
//        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.disableSecurity();

        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

        createDestinations(jmsAdminOperations);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void createDestinations(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
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

        createDestinations(jmsAdminOperations);

        jmsAdminOperations.close();
        container.stop();
    }


    private void deployBridgeEAP6(Container containerToDeploy, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, int maxRetries) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String targetConnectionFactory = "jms/RemoteConnectionFactory";

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + outServer.getHostname() + ":" + outServer.getJNDIPort());

        if (qualityOfService == null || "".equals(qualityOfService.toString())) {
            qualityOfService = Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");
        jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, getSourceDestination(), null,
                targetConnectionFactory, getTargetDestination(), targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }

    private void deployBridgeEAP7(Container containerToDeploy, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, int maxRetries) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String targetConnectionFactory = "jms/RemoteConnectionFactory";

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        targetContext.put("java.naming.provider.url", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + outServer.getHostname() + ":" + outServer.getJNDIPort());

        if (qualityOfService == null || "".equals(qualityOfService.toString())) {
            qualityOfService = Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, getSourceDestination(), null,
                targetConnectionFactory, getTargetDestination(), targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }

    protected String getSourceDestination(){
        return inQueueJndiName;
    }

    protected String getTargetDestination(){
        return outQueueJndiName;
    }
}
