package org.jboss.qa.hornetq.test.soak;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.MemoryMeasuring;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 *
 * Created by mnovak on 12/12/14.
 */
public class PageLeakSoakTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(PageLeakSoakTestCase.class);

    // queue to send messages in
    static String inTopic = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopic;

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPageLeaking() throws Exception {

        int numberOfMessages = 5000000; // 10 M messages
        int counter = 0;

        prepareJmsServer(container(1));

        container(1).start();

        SubscriberTransAck fastSubscriber = new SubscriberTransAck(container(1), inTopicJndiName, 30000, 10, 10, "fastSubscriber-connid", "fastSubscriber");
        fastSubscriber.subscribe();
        fastSubscriber.setTimeout(0);

        SubscriberTransAck slowSubscriber = new SubscriberTransAck(container(1), inTopicJndiName, 30000, 1, 10, "slowSubscriber-connid", "slowSubscriber");
        slowSubscriber.subscribe();
        slowSubscriber.setTimeout(1000);

        PublisherTransAck publisher = new PublisherTransAck(container(1), inTopicJndiName, numberOfMessages, "publisherID");
        MessageBuilder builder = new ClientMixMessageBuilder(30, 30);
        builder.setAddDuplicatedHeader(true);
        publisher.setMessageBuilder(builder);
        publisher.setTimeout(0);
        publisher.setCommitAfter(100);
        publisher.start();

        fastSubscriber.start();
        slowSubscriber.start();

        //Thread.sleep(60 * 60 * 1000); // 1hour

//        publisher.stopSending();
        publisher.join();

        fastSubscriber.join();
        slowSubscriber.join(60);

        container(1).stop();

        // start measuring of
        File jmsServerCsv = new File("jms-server-memory.csv");
        MemoryMeasuring jmsServerMeasurement = new MemoryMeasuring(container(1), jmsServerCsv);

        jmsServerMeasurement.start();

        Assert.assertEquals("There must be same number of send and received messages from topic for fast subscriber.",
                fastSubscriber.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size());
//        Assert.assertEquals("There must be same number of send and received messages from topic for slow subscriber.",
//                slowSubscriber.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size());

        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("CHECK MEMORY GRAPH FOR NUMBER OF INSTANCES IN MEMORY!!!");
        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("################################################################");
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServer(Container container) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 250857600, 0, 0, 25085760);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.setNodeIdentifier(1234567);

        try {
            jmsAdminOperations.removeQueue(inTopic);
        } catch (Exception e) {
            // Ignore it
        }
        jmsAdminOperations.createTopic("default", inTopic, inTopicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

}
