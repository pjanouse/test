package org.jboss.qa.hornetq.test.messages;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerPriority;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.Map;

/**
 * TODO add tests in cluster
 *
 * Created by mstyk on 6/27/16.
 */
@Category(FunctionalTests.class)
public class MessagePriorityTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(MessageOrderingTestCase.class);

    private String inQueue = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueue;

    /**
     * @tpTestDetails Server is started. Configure server to force paging. Mix of large and normal messages are send to
     * queue and then received, Message priority respect is checked
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send mix of large and normal messages to queue</li>
     * <li>Receive messages from the queue</li>
     * <li>Check message priority order</li>
     * </ul>
     * @tpPassCrit All messages are successfully received according to priority.
     *
     * @ignore https://issues.jboss.org/browse/JBEAP-5196
     */
    @Ignore
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "PAGE"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 10 * 1024),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "100"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "0"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "1024")

    })
    public void checkPriorityOrderingWithPaging() throws Exception {
        testPriorityOrder(true);
    }

    /**
     * @tpTestDetails Server is started. Mix of large and normal messages are send to
     * queue and then received, Message priority respect is checked
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send mix of large and normal messages to queue</li>
     * <li>Receive messages from the queue</li>
     * <li>Check message priority order</li>
     * </ul>
     * @tpPassCrit All messages are successfully received according to priority.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK")
    })
    public void checkPriorityOrdering() throws Exception {
        testPriorityOrder(false);
    }

    public void testPriorityOrder(boolean paging) throws Exception {
        int numberOfMessages = 10;

        JMSTools jmsTools = new JMSTools();

        container(1).start();

        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 150);

        ProducerPriority producer = new ProducerPriority(container(1), inQueueJndiName, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);
        producer.start();
        producer.join();

        Thread.sleep(5000);

        Assert.assertEquals("Producer should send all messages to inQueue", numberOfMessages, jmsTools.countMessages(inQueue, container(1)));

        ReceiverTransAck receiver = new ReceiverTransAck(container(1), inQueueJndiName, 10000, 5, 5);
        receiver.setTimeout(2000);
        receiver.start();
        receiver.join();

        Assert.assertEquals("Receiver should receive all messages from inQueue", numberOfMessages, receiver.getCount());

        boolean isPriorityOk = checkSingleClientPriority(receiver.getListOfReceivedMessages());
        Assert.assertTrue("Incorrect priority order", isPriorityOk);

        container(1).stop();
    }


    private boolean checkSingleClientPriority(List<Map<String, String>> listOfReceivedMessages) {

        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        int previous = 10;
        boolean isOk = true;

        for (int indexReceived = 0; indexReceived < listOfReceivedMessages.size(); indexReceived++) {
            Map<String, String> receivedMessage = listOfReceivedMessages.get(indexReceived);
            int priority = Integer.valueOf(receivedMessage.get("messagePriority"));
            logger.info(priority);
            if (priority > previous) {
                logger.info("Received message " + receivedMessage.get(jmsImplementation.getDuplicatedHeader()) + " with priority " + priority + " which is higher than previous " + previous);
                isOk = false;
            }
            previous = priority;
        }


        return isOk;
    }
}
