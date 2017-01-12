package org.jboss.qa.hornetq.test.transportreliability;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Purpose of this test case is to test how jms client will handle server shutdown
 * and server kill. No message should be lost or duplicated.
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(Functional.class)
@Prepare("TwoNodes")
public class ServerUnavailableTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerUnavailableTestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES = 100000;

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

        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.QUEUE_JNDI, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);

        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), PrepareConstants.TOPIC_JNDI, 30000, 10, 30, "myClientId", "subscriber1");
        subscriber.subscribe();

        PublisherClientAck publisher = new PublisherClientAck(container(1), PrepareConstants.TOPIC_JNDI, numberOfMessages, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);

        publisher.start();
        producer.start();
        producer.join();
        publisher.join();

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, numberOfMessages, 18000, container(1)));

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), PrepareConstants.QUEUE_JNDI, 30000, 10, 30);
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

        container(2).start();

        container(1).start();

        ProducerClientAck producer = new ProducerClientAck(container(1), PrepareConstants.QUEUE_JNDI, NUMBER_OF_MESSAGES);
        SubscriberClientAck subscriber = new SubscriberClientAck(container(1), PrepareConstants.TOPIC_JNDI, 30000, 10, 30, "myClientId", "subscriber1");
        subscriber.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(container(1), PrepareConstants.TOPIC_JNDI, NUMBER_OF_MESSAGES, "myClientIdPublisher");
        publisher.setMessageBuilder(messageBuilder);
        publisher.start();

        producer.setMessageBuilder(messageBuilder);

        producer.start();

        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, 20, 120000, container(1)));

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
        Assert.assertTrue(JMSTools.waitForMessages(PrepareConstants.QUEUE_NAME, producer.getCount(), 180000, container(1)));
        log.info("Finished waiting for all messages in queue0");

        ReceiverClientAck receiver = new ReceiverClientAck(container(1), PrepareConstants.QUEUE_JNDI, 30000, 10, 30);
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
}