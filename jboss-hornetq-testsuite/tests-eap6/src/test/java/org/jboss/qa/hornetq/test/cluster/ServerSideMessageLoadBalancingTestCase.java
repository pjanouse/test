package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopic;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by mnovak on 5/3/16.
 */
public class ServerSideMessageLoadBalancingTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerSideMessageLoadBalancingTestCase.class);

    private final String inQueueName = "InQueue";
    private final String inQueueJndiName = "jms/queue/" + inQueueName;
    private final String inTopicName = "InTopic";
    private final String inTopicJndiName = "jms/topic/" + inTopicName;
    private final String outQueueName = "OutQueue";
    private final String outQueueJndiName = "jms/queue/" + outQueueName;

    private final JavaArchive MDB_ON_TOPIC = createDeploymentMdbOnTopic();


    MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 150);

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNoLoadBalancingToNodesWithNoConsumerRedistributionDisabledCorrectSemantics() throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = -1;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(true);

        ProducerTransAck producer3 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMesasages);
        producer3.setCommitAfter(3);
        producer3.setTimeout(0);
        producer3.setMessageBuilder(messageBuilder);
        producer3.setMessageVerifier(messageVerifier);
        producer3.start();
        producer3.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), inQueueJndiName);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setReceiveTimeOut(10000);
        receiver1.setTimeout(0);
        receiver1.start();

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), inQueueJndiName);
        receiver2.setMessageVerifier(messageVerifier);
        receiver2.setReceiveTimeOut(10000);
        receiver2.setTimeout(0);
        receiver2.start();

        receiver1.join();
        receiver2.join();

        long numberOfMessagesOnNode3 = new JMSTools().countMessages(inQueueName, container(3));

        ReceiverTransAck receiver3 = new ReceiverTransAck(container(3), inQueueJndiName);
        receiver3.setMessageVerifier(messageVerifier);
        receiver3.setReceiveTimeOut(10000);
        receiver3.setTimeout(0);
        receiver3.start();
        receiver3.join();

        Assert.assertTrue("All messages should be on node 3. Number of messages on node 3 is: " + numberOfMessagesOnNode3
                + " and there should be " + numberOfMesasages + "messages.", numberOfMessagesOnNode3 == numberOfMesasages);

        Assert.assertTrue("Receiver on node 1 cannot receive any messages. Number of received messages is: " + receiver1.getListOfReceivedMessages().size()
                , receiver1.getListOfReceivedMessages().size() == 0);
        Assert.assertTrue("Receiver on node 2 cannot receive any messages. Number of received messages is: " + receiver2.getListOfReceivedMessages().size()
                , receiver2.getListOfReceivedMessages().size() == 0);
        Assert.assertTrue("Receiver on node 3 must receive all messages. Number of received messages is: " + receiver3.getListOfReceivedMessages().size()
                , receiver3.getListOfReceivedMessages().size() == numberOfMesasages);
        Assert.assertTrue("There are lost or duplicated messages. Check logs for details.", messageVerifier.verifyMessages());

        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingToNodesWithConsumerRedistributionEnabledCorrectSemantics() throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = 0;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(true);

        ProducerTransAck producer3 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMesasages);
        producer3.setCommitAfter(3);
        producer3.setTimeout(0);
        producer3.setMessageBuilder(messageBuilder);
        producer3.setMessageVerifier(messageVerifier);
        producer3.start();
        producer3.join();

        long numberOfMessagesOnNode3 = new JMSTools().countMessages(inQueueName, container(3));
        long numberOfAddedMessagesOnNode2 = new JMSTools().getAddedMessagesCount(inQueueName, container(2));
        long numberOfAddedMessagesOnNode1 = new JMSTools().getAddedMessagesCount(inQueueName, container(1));

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), inQueueJndiName);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setReceiveTimeOut(10000);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        Assert.assertTrue("All messages should be on node 3. Number of messages on node 3 is: " + numberOfMessagesOnNode3
                + " and there should be " + numberOfMesasages + "messages.", numberOfMessagesOnNode3 == numberOfMesasages);
        Assert.assertTrue("There should be no added messages on node 2. Number of added messages on node 2 is: " + numberOfAddedMessagesOnNode2
                , numberOfAddedMessagesOnNode2 == 0);
        Assert.assertTrue("There should be no added messages on node 1. Number of added messages on node 1 is: " + numberOfAddedMessagesOnNode1
                , numberOfAddedMessagesOnNode1 == 0);

        Assert.assertEquals("Receiver on node 1 could not receive any messages. Number of received messages is: " + receiver1.getListOfReceivedMessages().size(),
                numberOfMesasages, receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("There are lost or duplicated messages. Check logs for details.", messageVerifier.verifyMessages());

        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingToNodesWithConsumerRedistributionEnabledOriginalSemantics() throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = 0;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(false);

        ProducerTransAck producer3 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMesasages);
        producer3.setCommitAfter(3);
        producer3.setTimeout(0);
        producer3.setMessageBuilder(messageBuilder);
        producer3.setMessageVerifier(messageVerifier);
        producer3.start();
        producer3.join();

        long numberOfMessagesOnNode3 = new JMSTools().countMessages(inQueueName, container(3));
        long numberOfMessagesOnNode2 = new JMSTools().countMessages(inQueueName, container(2));
        long numberOfMessagesOnNode1 = new JMSTools().countMessages(inQueueName, container(1));

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), inQueueJndiName);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setReceiveTimeOut(10000);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        Assert.assertTrue("All messages should be on node 3. Number of messages on node 3 is: " + numberOfMessagesOnNode3
                + " and there should be " + numberOfMesasages + "messages.", numberOfMessagesOnNode3 > 0);
        Assert.assertTrue("There should be no added messages on node 2. Number of added messages on node 2 is: " + numberOfMessagesOnNode2
                , numberOfMessagesOnNode2 > 0);
        Assert.assertTrue("There should be no added messages on node 1. Number of added messages on node 1 is: " + numberOfMessagesOnNode1
                , numberOfMessagesOnNode1 > 0);
        Assert.assertEquals("Receiver on node 1 could not receive any messages. Number of received messages is: " + receiver1.getListOfReceivedMessages().size(),
                numberOfMesasages, receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("There are lost or duplicated messages. Check logs for details.", messageVerifier.verifyMessages());
        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNoLoadBalancingToNodeWithNoConsumerRedistributionDisabledCorrectSemantics() throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = -1;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(true);

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), inQueueJndiName);
        receiver2.setMessageVerifier(messageVerifier);
        receiver2.setReceiveTimeOut(10000);
        receiver2.setTimeout(0);
        receiver2.start();

        ReceiverTransAck receiver3 = new ReceiverTransAck(container(3), inQueueJndiName);
        receiver3.setMessageVerifier(messageVerifier);
        receiver3.setReceiveTimeOut(10000);
        receiver3.setTimeout(0);
        receiver3.start();

        Thread.sleep(3000);

        ProducerTransAck producer3 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMesasages);
        producer3.setCommitAfter(3);
        producer3.setTimeout(0);
        producer3.setMessageBuilder(messageBuilder);
        producer3.setMessageVerifier(messageVerifier);
        producer3.start();

        producer3.join();
        receiver2.join();
        receiver3.join();

        long numberOfMessagesOnNode1 = new JMSTools().countMessages(inQueueName, container(1));
        long numberOfAddedMessagesOnNode1 = new JMSTools().getAddedMessagesCount(inQueueName, container(1));
        long numberOfAddedMessagesOnNode2 = new JMSTools().getAddedMessagesCount(inQueueName, container(2));
        long numberOfAddedMessagesOnNode3 = new JMSTools().getAddedMessagesCount(inQueueName, container(3));


        Assert.assertTrue("No messages should be on node 1. Number of messages on node 1 is: " + numberOfMessagesOnNode1
                , numberOfMessagesOnNode1 == 0);

        Assert.assertTrue("There should be no added messages on node 1. Number of added messages on node 1 is: " + numberOfAddedMessagesOnNode1
                , numberOfAddedMessagesOnNode1 == 0);
        Assert.assertTrue("There should be added messages on node 2. Number of added messages on node 2 is: " + numberOfAddedMessagesOnNode2
                , numberOfAddedMessagesOnNode2 > 0);
        Assert.assertTrue("There should be added messages on node 3. Number of added messages on node 3 is: " + numberOfAddedMessagesOnNode3
                , numberOfAddedMessagesOnNode3 > 0);

        Assert.assertTrue("Receiver on node 2 cannot receive any messages. Number of received messages is: " + receiver2.getListOfReceivedMessages().size()
                , receiver2.getListOfReceivedMessages().size() > 0);
        Assert.assertTrue("Receiver on node 3 must receive all messages. Number of received messages is: " + receiver3.getListOfReceivedMessages().size()
                , receiver3.getListOfReceivedMessages().size() > 0);

        Assert.assertTrue("There are lost or duplicated messages. Check logs for details.", messageVerifier.verifyMessages());

        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMdbOnTopicCorrectSemantics() throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = -1;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(true);

        container(1).deploy(MDB_ON_TOPIC);
        container(2).deploy(MDB_ON_TOPIC);
        container(1).undeploy(MDB_ON_TOPIC);
        container(2).undeploy(MDB_ON_TOPIC);

        Thread.sleep(3000);

        PublisherTransAck publisher2 = new PublisherTransAck(container(2), inTopicJndiName, numberOfMesasages, "publisher-id");
        publisher2.setCommitAfter(3);
        publisher2.setTimeout(0);
        publisher2.setMessageBuilder(messageBuilder);
        publisher2.start();
        publisher2.join();

        container(1).deploy(MDB_ON_TOPIC);

        boolean messageLoadBalancedToNode1 = new JMSTools().waitForMessages(outQueueName, 1, 10000, container(1));

        container(2).deploy(MDB_ON_TOPIC);

        boolean allMessagesOnNode2 = new JMSTools().waitForMessages(outQueueName, numberOfMesasages, 120000, container(2));

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), outQueueJndiName);
        receiver2.setMessageVerifier(messageVerifier);
        receiver2.setReceiveTimeOut(10000);
        receiver2.setTimeout(0);
        receiver2.start();
        receiver2.join();

        Assert.assertFalse("No messages should be on node 1.", messageLoadBalancedToNode1);
        Assert.assertTrue("All messages should be in outQueue on node-2.", allMessagesOnNode2);

        Assert.assertEquals("Receiver on node 2 cannot receive any messages. Number of received messages is: "
                        + receiver2.getListOfReceivedMessages().size(),
                numberOfMesasages, receiver2.getListOfReceivedMessages().size());

        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMdbOnTopicOriginalSemantics() throws Exception {


        int numberOfMesasages = 200;

        long redistributionDelay = -1;
        boolean forwardWhenNoConsumers = false;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(false);

        container(1).deploy(MDB_ON_TOPIC);
        container(2).deploy(MDB_ON_TOPIC);
        container(3).deploy(MDB_ON_TOPIC);
        Thread.sleep(5000);
        container(1).undeploy(MDB_ON_TOPIC);
        container(2).undeploy(MDB_ON_TOPIC);
        container(3).undeploy(MDB_ON_TOPIC);

        Thread.sleep(10000);

        PublisherTransAck publisher2 = new PublisherTransAck(container(2), inTopicJndiName, numberOfMesasages, "publisher-id");
        publisher2.setCommitAfter(3);
        publisher2.setTimeout(0);
        publisher2.setMessageBuilder(messageBuilder);
        publisher2.start();
        publisher2.join();

        container(1).deploy(MDB_ON_TOPIC);
        container(2).deploy(MDB_ON_TOPIC);
        container(3).deploy(MDB_ON_TOPIC);

        boolean messageLoadBalancedToNode1 = new JMSTools().waitForMessages(outQueueName, numberOfMesasages / 4, 60000, container(1));
        boolean messageLoadBalancedToNode2 = new JMSTools().waitForMessages(outQueueName, numberOfMesasages / 4, 60000, container(2));
        boolean messageLoadBalancedToNode3 = new JMSTools().waitForMessages(outQueueName, numberOfMesasages / 4, 60000, container(3));

        long numberOfMessagesOnNode1 = new JMSTools().countMessages(outQueueName, container(1));
        long numberOfMessagesOnNode2 = new JMSTools().countMessages(outQueueName, container(2));
        long numberOfMessagesOnNode3 = new JMSTools().countMessages(outQueueName, container(3));

        Assert.assertTrue("No messages are on node 1.", messageLoadBalancedToNode1);
        Assert.assertTrue("No messages are on node 2.", messageLoadBalancedToNode2);
        Assert.assertTrue("No messages are on node 3.", messageLoadBalancedToNode3);
        Assert.assertEquals("Sum of messsages is not correct.", numberOfMesasages,
                numberOfMessagesOnNode1 + numberOfMessagesOnNode2 + numberOfMessagesOnNode3);

        stopServers();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testStrictLoadBalancingCorrectSemantics() throws Exception {
        testStrictLoadBalancing(true);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testStrictLoadBalancingOriginalSemantics() throws Exception {
        testStrictLoadBalancing(false);
    }

    public void testStrictLoadBalancing(boolean correctSemantics) throws Exception {


        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier(ContainerUtils.getJMSImplementation(container(1)));
        int numberOfMesasages = 200;

        long redistributionDelay = 0;
        boolean forwardWhenNoConsumers = true;
        prepareServers(redistributionDelay, forwardWhenNoConsumers);

        startServers(correctSemantics);

        ProducerTransAck producer3 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMesasages);
        producer3.setCommitAfter(3);
        producer3.setTimeout(0);
        producer3.setMessageBuilder(messageBuilder);
        producer3.setMessageVerifier(messageVerifier);
        producer3.start();
        producer3.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), inQueueJndiName);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setReceiveTimeOut(10000);
        receiver1.setTimeout(0);
        receiver1.start();

        ReceiverTransAck receiver2 = new ReceiverTransAck(container(2), inQueueJndiName);
        receiver2.setMessageVerifier(messageVerifier);
        receiver2.setReceiveTimeOut(10000);
        receiver2.setTimeout(0);
        receiver2.start();

        receiver1.join();
        receiver2.join();

        ReceiverTransAck receiver3 = new ReceiverTransAck(container(3), inQueueJndiName);
        receiver3.setMessageVerifier(messageVerifier);
        receiver3.setReceiveTimeOut(10000);
        receiver3.setTimeout(0);
        receiver3.start();
        receiver3.join();

        Assert.assertTrue("Receiver on node 1 cannot receive any messages. Number of received messages is: " + receiver1.getListOfReceivedMessages().size()
                , receiver1.getListOfReceivedMessages().size() > 0);
        Assert.assertTrue("Receiver on node 2 cannot receive any messages. Number of received messages is: " + receiver2.getListOfReceivedMessages().size()
                , receiver2.getListOfReceivedMessages().size() > 0);
        Assert.assertTrue("Receiver on node 2 cannot receive any messages. Number of received messages is: " + receiver3.getListOfReceivedMessages().size()
                , receiver3.getListOfReceivedMessages().size() > 0);

        Assert.assertTrue("There are lost or duplicated messages. Check logs for details.", messageVerifier.verifyMessages());

        stopServers();

    }


    private void startServers(boolean withCorrectSemanticsForForwardWhenNoConsumers) {
        if (withCorrectSemanticsForForwardWhenNoConsumers) {
            startServersWithActivatedCorrectSemanticsForForwardWhenNoConsumer(container(1));
            startServersWithActivatedCorrectSemanticsForForwardWhenNoConsumer(container(2));
            startServersWithActivatedCorrectSemanticsForForwardWhenNoConsumer(container(3));
        } else {
            container(1).start();
            container(2).start();
            container(3).start();
        }
    }

    private void startServersWithActivatedCorrectSemanticsForForwardWhenNoConsumer(Container container) {
        String s = null;

        ContainerDef containerDef = container.getContainerDefinition();
        if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
            s = containerDef.getContainerProperties().get("javaVmArguments");
            s = s.concat(" -Dactivate.correct.semantics.for.forward.when.no.consumers=true");
            containerDef.getContainerProperties().put("javaVmArguments", s);
        }

        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        container.start(properties);
    }

    private void stopServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
    }

    private void prepareServers(long redistributionDelay, boolean forwardWhenNoConsumers) {
        prepareServer(container(1), redistributionDelay, forwardWhenNoConsumers);
        prepareServer(container(2), redistributionDelay, forwardWhenNoConsumers);
        prepareServer(container(3), redistributionDelay, forwardWhenNoConsumers);
    }

    /**
     * Prepares server for topology.
     *
     * @param container The container - defined in arquillian.xml
     *                  if true, otherwise no.
     */
    protected void prepareServer(Container container, long redistributionDelay, boolean forwardWhenNoConsumers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        try {

            if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
                jmsAdminOperations.setClustered(true);

            }
            jmsAdminOperations.setPersistenceEnabled(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, forwardWhenNoConsumers, 1, 1000, true,
                    connectorName);

            jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
            jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, redistributionDelay, 512 * 1024);
            jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
            jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
            jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();

        }
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to
     * jms/queue/OutQueue
     *
     * @return mdb
     */
    public static JavaArchive createDeploymentMdbOnTopic() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopic.class);
        log.info(mdbJar.toString(true));
        return mdbJar;
    }
}

