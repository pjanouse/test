package org.jboss.qa.hornetq.test.messages;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.GroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.Map;

/**
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP-7.0.x-CP/view/EAP-70x-jobs/job/eap-70x-artemis-message-grouping/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-message-grouping-tests/
 * <p>
 * Created by mstyk on 6/27/16.
 */
@Category(Functional.class)
public class MessageOrderingTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(MessageOrderingTestCase.class);

    private String inQueue = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueue;

    /**
     * @tpTestDetails Server is started. Messages are send to queue and then received,
     * Message ordering is checked.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send messages to queue</li>
     * <li>Receive messages from the queue</li>
     * <li>Check message ordering</li>
     * </ul>
     * @tpPassCrit All messages are successfully received in correct order.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 150 * 1024 * 1024),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "60000"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "2000"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 1024 * 3)
    })
    public void checkOrdering() throws Exception {

        int numberOfMessages = 500;

        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getOrderingVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerAutoAck producer = new ProducerAutoAck(container(1), inQueueJndiName, numberOfMessages);
        producer.setTimeout(0);
        producer.addMessageVerifier(messageVerifier);
        producer.start();
        producer.join();

        Assert.assertEquals("Producer should send all messages to inQueue", numberOfMessages, JMSTools.countMessages(inQueue, container(1)));

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), inQueueJndiName);
        receiver.setTimeout(0);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();

        Assert.assertEquals("Receiver should receive all messages from inQueue", numberOfMessages, receiver.getCount());

        boolean isEverythingOk = messageVerifier.verifyMessages();
        Assert.assertTrue("Failure detected by messageVerifier", isEverythingOk);

        container(1).stop();
    }

    /**
     * @tpTestDetails Server is started. Mix of large and normal messages are send to
     * queue and then received, Message ordering is checked.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send mix of large and normal messages to queue</li>
     * <li>Receive messages from the queue</li>
     * <li>Check message ordering</li>
     * </ul>
     * @tpPassCrit All messages are successfully received in correct order.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 150 * 1024 * 1024),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "60000"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "2000"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 1024 * 3)
    })
    public void checkOrderingLargeMessages() throws Exception {

        int numberOfMessages = 500;

        container(1).start();

        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getOrderingVerifier(ContainerUtils.getJMSImplementation(container(1)));

        ProducerAutoAck producer = new ProducerAutoAck(container(1), inQueueJndiName, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.addMessageVerifier(messageVerifier);
        producer.start();
        producer.join();

        Assert.assertEquals("Producer should send all messages to inQueue", numberOfMessages, JMSTools.countMessages(inQueue, container(1)));

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), inQueueJndiName);
        receiver.setTimeout(0);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();

        Assert.assertEquals("Receiver should receive all messages from inQueue", numberOfMessages, receiver.getCount());

        boolean isEverythingOk = messageVerifier.verifyMessages();
        Assert.assertTrue("Failure detected by messageVerifier", isEverythingOk);

        container(1).stop();
    }

    /**
     * @tpTestDetails Server is started. Mix of large and normal messages are send to
     * queue,Messages are received by multiple consumers. It is checked, that every consumer receives message in ascending order.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Send mix of large and normal messages to queue</li>
     * <li>Receive messages from the queue by multiple consumers</li>
     * <li>Check message ordering for every consumer</li>
     * </ul>
     * @tpPassCrit All messages are successfully received in correct order.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 150 * 1024 * 1024),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "60000"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "2000"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 1024 * 3)
    })
    public void checkOrderingWithMultipleReceivers() throws Exception {

        int numberOfMessages = 500;

        container(1).start();

        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

        ProducerAutoAck producer = new ProducerAutoAck(container(1), inQueueJndiName, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        ReceiverAutoAck receiver1 = new ReceiverAutoAck(container(1), inQueueJndiName);
        ReceiverAutoAck receiver2 = new ReceiverAutoAck(container(1), inQueueJndiName);
        ReceiverAutoAck receiver3 = new ReceiverAutoAck(container(1), inQueueJndiName);


        receiver1.start();
        receiver2.start();
        receiver3.start();

        receiver1.join();
        receiver2.join();
        receiver3.join();

        Assert.assertEquals("All messages should be received", numberOfMessages, receiver1.getCount() + receiver2.getCount() + receiver3.getCount());

        Assert.assertTrue("receiver1 should receive ordered messages", checkSingleClientOrder(producer.getListOfSentMessages(), receiver1.getListOfReceivedMessages()));
        Assert.assertTrue("receiver2 should receive ordered messages", checkSingleClientOrder(producer.getListOfSentMessages(), receiver2.getListOfReceivedMessages()));
        Assert.assertTrue("receiver3 should receive ordered messages", checkSingleClientOrder(producer.getListOfSentMessages(), receiver3.getListOfReceivedMessages()));

        container(1).stop();
    }


    /**
     * Reproduces https://issues.jboss.org/browse/JBEAP-5127
     *
     * @tpTestDetails 3 servers are started in cluster with message ordering. Mix of large and normal messages are send to
     * queue on node1, Messages are received by consumers on node2 and node3. It is checked, that every consumer receives message in ascending order.
     * @tpProcedure <ul>
     * <li>Start servers in cluster with message grouping</li>
     * <li>Send mix of large and normal messages to queue on node1</li>
     * <li>Receive messages from the queue on node2 and node3</li>
     * <li>Check message ordering for every consumer</li>
     * </ul>
     * @tpPassCrit All messages are successfully received in correct order.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "ThreeNodes", params = {
            @Param(name = PrepareParams.ADDRESS_FULL_POLICY, value = "BLOCK"),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 150 * 1024 * 1024),
            @Param(name = PrepareParams.REDELIVERY_DELAY, value = "60000"),
            @Param(name = PrepareParams.REDISTRIBUTION_DELAY, value = "2000"),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 1024 * 3)
    })
    public void checkOrderingWithMultipleReceiversInCluster() throws Exception {

        int numberOfMessages = 500;

        // set local grouping-handler on 1st node
        addMessageGrouping(container(1), "my-grouping-handler", "LOCAL", "jms", 20000, 10000, 7500);
        // set remote grouping-handler on 2nd node
        addMessageGrouping(container(2), "my-grouping-handler", "REMOTE", "jms", 10000, 5000, 0);
        // set remote grouping-handler on 3rd node
        addMessageGrouping(container(3), "my-grouping-handler", "REMOTE", "jms", 10000, 5000, 0);

        container(1).start();
        container(2).start();
        container(3).start();

        int numberOfConnections = countNumberOfConnections(container(2), container(3));
        ReceiverAutoAck receiver2 = new ReceiverAutoAck(container(2), inQueueJndiName);
        receiver2.setReceiveTimeout(10000);
        receiver2.setTimeout(5000);
        ReceiverAutoAck receiver3 = new ReceiverAutoAck(container(3), inQueueJndiName);
        receiver3.setReceiveTimeout(10000);
        receiver3.setTimeout(5000);

        receiver2.start();
        receiver3.start();

        int count = countNumberOfConnections(container(2), container(3));
        long startTime = System.currentTimeMillis();
        while (numberOfConnections + 2 > count) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime > 30000) {
                Assert.fail("Receivers did not connect to servers.");
            }
            count = countNumberOfConnections(container(2), container(3));
        }

        GroupMessageBuilder messageBuilder = new GroupMessageBuilder("myJMSXGroupID");
        messageBuilder.setSize(1024 * 50);
        ProducerTransAck producer = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.setCommitAfter(50);
        producer.start();
        producer.join();

        receiver2.setTimeout(0);
        receiver2.join();
        receiver3.setTimeout(0);
        receiver3.join();

        Assert.assertEquals("All messages should be received", numberOfMessages, receiver2.getCount() + receiver3.getCount());
        Assert.assertTrue("receiver2 should receive ordered messages", checkSingleClientOrder(producer.getListOfSentMessages(), receiver2.getListOfReceivedMessages()));
        Assert.assertTrue("receiver3 should receive ordered messages", checkSingleClientOrder(producer.getListOfSentMessages(), receiver3.getListOfReceivedMessages()));

        container(1).stop();
    }

    private int countNumberOfConnections(Container... containers) {
        int sum = 0;
        for (Container c : containers) {
            JMSOperations jmsOperations = c.getJmsOperations();
            int count = jmsOperations.countConnections();
            sum += count;
            jmsOperations.close();
        }
        return sum;
    }


    private boolean checkSingleClientOrder(List<Map<String, String>> listOfSentMessages, List<Map<String, String>> listOfReceivedMessages) {

        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));

        int count = 0;
        int indexSent = 0;

        for (int indexReceived = 0; indexReceived < listOfReceivedMessages.size(); indexReceived++) {
            Map<String, String> receivedMessage = listOfReceivedMessages.get(indexReceived);

            while (indexSent < listOfSentMessages.size()) {
                Map<String, String> sentMessage = listOfSentMessages.get(indexSent);

                if (sentMessage.get(jmsImplementation.getDuplicatedHeader()).equals(
                        receivedMessage.get(jmsImplementation.getDuplicatedHeader()))) {
                    count++;
                    indexSent++;
                    break;
                }
                indexSent++;
            }
        }
        return count == listOfReceivedMessages.size();
    }

    private void addMessageGrouping(Container container, String name, String type, String address, long timeout, long groupTimeout, long reaperPeriod) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addMessageGrouping("default", name, type, address, timeout, groupTimeout, reaperPeriod);
        jmsAdminOperations.close();
        container.stop();
    }

}
