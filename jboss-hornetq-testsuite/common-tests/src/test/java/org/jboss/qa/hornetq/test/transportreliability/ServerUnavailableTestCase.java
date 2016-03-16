package org.jboss.qa.hornetq.test.transportreliability;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Purpose of this test case is to test how jms client will handle server shutdown
 * and server kill. No message should be lost or duplicated.
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class ServerUnavailableTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerUnavailableTestCase.class);

    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES = 100000;

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithProducerNormalMessages1KB() throws Exception {
        testWithProducer(new TextMessageBuilder(1000), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithProducerLargeMessages1MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024), false);
    }

    @Ignore("Testing only normal 1kb and large 1mb messages is sufficient")
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithProducerLargeMessages20MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithConsumerNormalMessages1KB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1000), false, false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages1MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), false, true);
    }

    @Ignore("Testing only normal 1kb and large 1mb messages is sufficient")
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages20MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024 * 20), false, true);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerNormalMessages1KB() throws Exception {
        testWithProducer(new TextMessageBuilder(1000), true);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerLargeMessages1MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024), true);
    }

    @Ignore("Testing only normal 1kb and large 1mb messages is sufficient")
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerLargeMessages20MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), true);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerNormalMessages1KB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1000), true, false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerLargeMessages1MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), true, true);
    }

    @Ignore("Testing only normal 1kb and large 1mb messages is sufficient")
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerLargeMessages20MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024 * 20), true, true);
    }

    public void testWithConsumer(MessageBuilder messageBuilder, boolean testKill, boolean isLargeMessages) throws Exception {

        final int numberOfMessages = isLargeMessages ? 80 : 500;

        prepareServers();
        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiNamePrefix + "0", numberOfMessages);
        producer.setMessageBuilder(messageBuilder);

        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), topicJndiNamePrefix + "0", 30000, 10, 30, "myClientId", "subscriber1");
        subscriber.subscribe();

        PublisherClientAck publisher = new PublisherClientAck(container(1), topicJndiNamePrefix + "0", numberOfMessages, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);

        publisher.start();
        producer.start();
        producer.join();
        publisher.join();

        new JMSTools().waitForMessages(queueNamePrefix + "0", numberOfMessages, 18000, container(1));

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0", 30000, 10, 30);
        receiver.setTimeout(1000);
        subscriber.setTimeout(1000);
        receiver.start();
        subscriber.start();

        List<Client> clientList = new ArrayList<Client>();
        clientList.add(receiver);
        clientList.add(subscriber);

        JMSTools.waitForAtLeastOneReceiverToConsumeNumberOfMessages(clientList, 10, 120000);


        if (testKill) {
            log.info("############# Kill server 1.");
            container(1).kill();
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");
        } else {
            log.info("############# Stopping server 1.");
            container(1).stop();
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");
        }

        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 120000);

        receiver.setTimeout(0);
        subscriber.setTimeout(0);

        receiver.join();
        subscriber.join();

        Assert.assertEquals("There is differen number sent and recieved messages. Sent messages" + producer.getListOfSentMessages().size() +
                        "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("There is different number sent and received messages. Publisher sent messages" + publisher.getListOfSentMessages().size() +
                        "Subscriber: " + subscriber.getListOfReceivedMessages().size(),
                publisher.getListOfSentMessages().size(),
                subscriber.getListOfReceivedMessages().size());
        Assert.assertTrue("Producer did not sent any message", producer.getListOfSentMessages().size() > 0);
        Assert.assertTrue("Publisher did not sent any message", publisher.getListOfSentMessages().size() > 0);

        container(1).stop();

        container(2).stop();

    }

    public void testWithProducer(MessageBuilder messageBuilder, boolean testKill) throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiNamePrefix + "0", NUMBER_OF_MESSAGES);
        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), topicJndiNamePrefix + "0", 30000, 10, 30, "myClientId", "subscriber1");
        subscriber.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(container(1), topicJndiNamePrefix + "0", NUMBER_OF_MESSAGES, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);
        publisher.start();

        producer.setMessageBuilder(messageBuilder);

        producer.start();

        new JMSTools().waitForMessages(queueNamePrefix + "0", 20, 120000, container(1));

        if (testKill) {
            log.info("############# Kill server 1.");
            container(1).kill();
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");

        } else {
            log.info("############# Stopping server 1.");
            container(1).stop();
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");
        }

        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), 120000);

        Thread.sleep(20000);

        producer.stopSending();
        publisher.stopSending();
        producer.join();
        publisher.join();

        log.info("Waiting for all messages in queue0");
        new JMSTools().waitForMessages(queueNamePrefix + "0", producer.getCount(), 180000, container(1));
        log.info("Finished waiting for all messages in queue0");

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0", 30000, 10, 30);
        receiver.start();
        receiver.setTimeout(0);
        receiver.join();

        subscriber.start();
        subscriber.setTimeout(0);
        subscriber.join();

        Assert.assertEquals("There is different number sent and received messages. Sent messages" + producer.getListOfSentMessages().size() +
                        "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("There is different number sent and received messages. Publisher sent messages" + publisher.getListOfSentMessages().size() +
                        "Subscriber: " + subscriber.getListOfReceivedMessages().size(),
                publisher.getListOfSentMessages().size(),
                subscriber.getListOfReceivedMessages().size());
        Assert.assertTrue("Producer did not sent any message", producer.getListOfSentMessages().size() > 0);
        Assert.assertTrue("Publisher did not sent any message", publisher.getListOfSentMessages().size() > 0);

        container(1).stop();
        container(2).stop();

    }

    public void prepareServers() {
        if (ContainerUtils.isEAP7(container(1))) {
            prepareServerEAP7(container(1));
            prepareServerEAP7(container(2));
        } else {
            prepareServerEAP6(container(1));
            prepareServerEAP6(container(2));
        }
    }


    /**
     * Prepares server for topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
//        String connectionFactoryName = "RemoteConnectionFactory";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, container.getHostname(), broadcastBindingPort, container.MCAST_ADDRESS, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, container.getHostname(), container.MCAST_ADDRESS, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
//        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
//        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
//        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
//        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares server for topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServerEAP7(Container container) {

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
            if (JMSTools.isIpv6Address(container.getHostname())) {
                jmsAdminOperations.setMulticastAddressOnSocketBinding("modcluster", System.getenv("MCAST_ADDRV6"));
            }
            jmsAdminOperations.setPersistenceEnabled(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                    connectorName);

            jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
            jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();
            // jmsAdminOperations.setLoggingLevelForConsole("INFO");
            // jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

            for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
                jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
            }

            for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
                jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
            }


        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();

        }
    }

    @Before
    @After
    public void stopAllServers() {

        container(1).stop();

        container(2).stop();

    }
}