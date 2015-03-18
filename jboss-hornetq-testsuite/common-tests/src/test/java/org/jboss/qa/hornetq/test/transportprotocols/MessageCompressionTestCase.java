package org.jboss.qa.hornetq.test.transportprotocols;


import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Testing compression of messages
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class MessageCompressionTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(MessageCompressionTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 200;

    String queueName = "testQueue";
    String queueJndiName = "jms/queue/testQueue";


    @After
    public void stopAllServers() {

        stopServer(CONTAINER1_NAME);

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testCompression() throws Exception {
        prepareServer(CONTAINER1_NAME);

        controller.start(CONTAINER1_NAME);
        // Send messages into input node and read from output node
        ProducerTransAck producer = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 1024 * 10)); // large messages have 100MB
        ReceiverTransAck receiver = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), queueJndiName, 10000, 10, 10);

        logger.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: " + producer.getListOfSentMessages().size()
                + "Received: " + receiver.getListOfReceivedMessages().size(), producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size()
                , producer.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size()
                , receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                , NUMBER_OF_MESSAGES_PER_PRODUCER);

        stopServer(CONTAINER1_NAME);
    }

    /**
     * Test all possible things. Failed operation simply throw RuntimeException
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    public void prepareServer(String containerName) throws IOException {

        String connectionFactoryName = "RemoteConnectionFactory";
        String serverName = "default";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSecurityEnabled(false);
        // set compression of large messages
        jmsAdminOperations.setCompressionOnConnectionFactory(connectionFactoryName, true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 5000 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.createQueue(serverName, queueName, queueJndiName, true);
        jmsAdminOperations.close();

        controller.stop(containerName);

    }

}