package org.jboss.qa.hornetq.test.transportreliability;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Purpose of this test case is to test how jms client will handle server shutdown
 * and server kill. No message should be lost or duplicated.
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
@Ignore(value = "What is purpose of test?")
public class ServerNetworkUnavailableTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerNetworkUnavailableTestCase.class);

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
        testWithConsumer(new TextMessageBuilder(1000), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages1MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages20MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerNormalMessages1KB() throws Exception {
        testWithProducer(new TextMessageBuilder(1000), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerLargeMessages1MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithProducerLargeMessages20MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerNormalMessages1KB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1000), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerLargeMessages1MB() throws Exception {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), false);
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testKillWithConsumerLargeMessages20MB() throws Exception {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }

    /**
     * Server A is stopped/killed.
     */
    @BMRules({
            @BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>124",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testWithConsumer(MessageBuilder messageBuilder, boolean testKill) throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiNamePrefix + "0", 500);
        producer.setMessageBuilder(messageBuilder);

        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), topicJndiNamePrefix + "0", "myClientId", "subscriber1");
        subscriber.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(container(1), topicJndiNamePrefix + "0", 500, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);
        publisher.start();

        producer.start();
        producer.join();
        publisher.join();

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0");
        receiver.start();
        subscriber.start();

        if (testKill) {

            RuleInstaller.installRule(ServerNetworkUnavailableTestCase.class);
            Thread.sleep(5000);
            log.info("############# Kill server 1.");
            container(1).waitForKill();
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");

        } else {

            Thread.sleep(10000);
            log.info("############# Stopping server 1.");
            container(1).stop();
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");
        }

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

        container(1).stop();

        container(2).stop();

    }


    @BMRules({
            @BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                            + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition = "readCounter(\"counter\")>124",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testWithProducer(MessageBuilder messageBuilder, boolean testKill) throws Exception {

        prepareServers();

        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiNamePrefix + "0", NUMBER_OF_MESSAGES);
        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), topicJndiNamePrefix + "0", "myClientId", "subscriber1");
        subscriber.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(container(1), topicJndiNamePrefix + "0", NUMBER_OF_MESSAGES, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);
        publisher.start();

        producer.setMessageBuilder(messageBuilder);

        producer.start();

        if (testKill) {

            RuleInstaller.installRule(ServerNetworkUnavailableTestCase.class);
            Thread.sleep(10000);
            log.info("############# Kill server 1.");
            container(1).waitForKill();
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");

        } else {

            Thread.sleep(10000);
            log.info("############# Stopping server 1.");
            container(1).stop();
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            container(1).start();
            log.info("############# Server 1 started.");
        }

        producer.stopSending();
        publisher.stopSending();

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiNamePrefix + "0");
        receiver.start();
        receiver.join();

        subscriber.start();
        subscriber.join();

        Assert.assertEquals("There is different number sent and received messages. Sent messages" + producer.getListOfSentMessages().size() +
                "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertEquals("There is different number sent and received messages. Publisher sent messages" + publisher.getListOfSentMessages().size() +
                        "Subscriber: " + subscriber.getListOfReceivedMessages().size(),
                publisher.getListOfSentMessages().size(),
                subscriber.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();

    }

    public void prepareServers() {
        prepareServer(container(1));
        prepareServer(container(2));
    }



    /**
     * Prepares server for topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServer(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
//        String connectionFactoryName = "RemoteConnectionFactory";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);

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

    @Before
    @After
    public void stopAllServers() {

        container(1).stop();

        container(2).stop();

    }
}