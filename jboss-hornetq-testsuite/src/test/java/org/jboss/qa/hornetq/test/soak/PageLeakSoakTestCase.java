package org.jboss.qa.hornetq.test.soak;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.MemoryCpuMeasuring;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

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

        int numberOfMessages = 10000000; // 10 M messages
        int counter = 0;

        prepareJmsServer(CONTAINER1);

        controller.start(CONTAINER1);

        SubscriberTransAck fastSubscriber = new SubscriberTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inTopicJndiName, 30000, 100, 10, "fastSubscriber-connid", "fastSubscriber");
        fastSubscriber.subscribe();
        fastSubscriber.setTimeout(0);

        SubscriberTransAck slowSubscriber = new SubscriberTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inTopicJndiName, 30000, 1, 10, "slowSubscriber-connid", "slowSubscriber");
        slowSubscriber.subscribe();
        slowSubscriber.setTimeout(1000);

        PublisherTransAck publisher = new PublisherTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), inTopicJndiName, numberOfMessages, "publisherID");
        MessageBuilder builder = new ClientMixMessageBuilder(30, 30);
        builder.setAddDuplicatedHeader(true);
        publisher.setMessageBuilder(builder);
        publisher.setTimeout(0);
        publisher.setCommitAfter(1000);
        publisher.start();

        fastSubscriber.start();
        slowSubscriber.start();

        Thread.sleep(60 * 60 * 1000); // 1hour

        publisher.stopSending();
        publisher.join();

        fastSubscriber.join();
        slowSubscriber.join();

        stopServer(CONTAINER1);

        // start measuring of
        MemoryCpuMeasuring jmsServerMeasurement = new MemoryCpuMeasuring(getProcessId(CONTAINER1), "jms-server");

        jmsServerMeasurement.startMeasuring();

        Assert.assertEquals("There must be same number of send and received messages from topic for fast subscriber.",
                fastSubscriber.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size());
//        Assert.assertEquals("There must be same number of send and received messages from topic for slow subscriber.",
//                slowSubscriber.getListOfReceivedMessages().size(), publisher.getListOfSentMessages().size());

        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("CHECK MEMORY AND CPU GRAPH FOR NUMBER OF INSTANCES IN MEMORY!!!");
        logger.error("################################################################");
        logger.error("################################################################");
        logger.error("################################################################");
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareJmsServer(String containerName) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 2000 * 1024, 0, 0, 250 * 1024);
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

        controller.stop(containerName);

    }

}
