package org.jboss.qa.hornetq.test.cli.operations;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.impl.DelayedTextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by okalman on 2/9/15. // TODO EAP7 list-delivering-messages operation
 * missing
 *
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails Test case simulates using CLI queue operations.There is
 * only 1 server with deployed queue and CLI queue operations are executed.
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class RuntimeQueueOperationsTestCaseCli extends CliTestBase {

    private static final Logger log = Logger.getLogger(RuntimeQueueOperationsTestCaseCli.class);

    String queueName = "testQueue";
    String queueJndiName = "jms/queue/" + queueName;

    private final String ADDRESS_EAP6 = "/subsystem=messaging/hornetq-server=default/runtime-queue=jms.queue." + queueName;
    private final String ADDRESS_EAP7 = "/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue." + queueName;
    private final CliClient cli = new CliClient(new CliConfiguration(container(1).getHostname(), container(1).getPort(),
            container(1).getUsername(), container(1).getPassword()));

    @After
    @Before
    public void stopServerAfterTest() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 100
     * messages to queue. Once producer finishes, create transacted session
     * which uses local transactions, create consumer and start receiving
     * messages. Commit transaction after 50 received messages and use CLI
     * operation listDeliveringMessages to check number of delivering messages
     * @tpProcedure <ul>
     * <li>Start one server with deployed queue</li>
     * <li>Create producer and send messages to queue</li>
     * <li>Wait for producer finish</li>
     * <li>Create transacted session (local transactions)</li>
     * <li>Create consumer and start receiving messages</li>
     * <li>After some messages received, commit transaction</li>
     * <li>Use operation listDeliveringMessages to check number of delivering
     * messages</li>
     * </ul>
     * @tpPassCrit CLI operation listDeliveringMessages returns list which
     * contains correct number of delivering messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listDeliveringMessagesTestCase() throws Exception {

        prepareServer(container(1));

        container(1).start();

        Context context = null;
        ConnectionFactory cf = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;

        int numberOfMessages = 100;
        int commitAfter = 50;

        ProducerAutoAck producer = new ProducerAutoAck(container(1), queueJndiName, numberOfMessages);
        producer.start();
        producer.join();

        try {
            context = container(1).getContext();

            cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueJndiName);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiverTransAck = session.createConsumer(queue);

            int counter = 0;
            int listDeliveringMessagesSize = -1;
            while (receiverTransAck.receive(500) != null) {
                counter++;
                if (counter % commitAfter == 0) {
                    session.commit();
                }
                if (counter == commitAfter) {
                    listDeliveringMessagesSize = this.getListDeliveringMessagesSize();
                }
            }
            receiverTransAck.close();
            ;
            Assert.assertEquals("Number of delivering messages does not match", commitAfter, listDeliveringMessagesSize);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);

        } finally {
            session.commit();
            session.close();
            conn.close();
        }

        container(1).stop();

    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 5GB of
     * messages to queue. Once producer finishes, try to delete all messages from the queue.
     * @tpProcedure <ul>
     * <li>Start one server with deployed queue</li>
     * <li>Send 5GB messages to queue</li>
     * <li>Remove messages from queue</li>
     * </ul>
     * @tpPassCrit All messages will be removed from queue.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void removeMessagesFromLargeQueueTest() throws Exception {

        prepareServer(container(1));

        container(1).start();

        int messageSize = 40 * 1024; // 40KB
        int numberOfMessages = 5000 * 1024 * 1024 / messageSize; //5 GB
        int commitAfter = 50;

        ProducerTransAck producer = new ProducerTransAck(container(1), queueJndiName, numberOfMessages);
        MessageBuilder messageBuilder = new TextMessageBuilder(1024 * 40);
        producer.setMessageBuilder(messageBuilder);
        producer.setCommitAfter(commitAfter);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.removeMessagesFromQueue(queueName);
        long numberOfMessagesInQueue = jmsOperations.getCountOfMessagesOnQueue(queueName);
        jmsOperations.close();

        Assert.assertEquals("Not all messages were removed from queue.", 0, numberOfMessages);

        container(1).stop();

    }

    /**
     * @tpTestDetails Server with queue is started. Create producer and send 10
     * messages to queue. Once producer finishes, use CLI operation
     * listScheduledMessages to check number of scheduled messages.
     * @tpProcedure <ul>
     * <li>start one server with deployed queue</li>
     * <li>create producer and send messages to queue</li>
     * <li>Wait for producer finish</li>
     * <li>Use CLI operation listScheduledMessages to check number of scheduled
     * messages</li>
     * </ul>
     * @tpPassCrit CLI operation listScheduledMessages returns list which
     * contains correct number of scheduled messages
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listScheduledMessagesTestCase() throws Exception {

        int numberOfMessages = 10;

        prepareServer(container(1));

        container(1).start();
        ProducerAutoAck producer = new ProducerAutoAck(container(1), queueJndiName, numberOfMessages);
        DelayedTextMessageBuilder delayedTextMessageBuilder = new DelayedTextMessageBuilder(512, 100000);
        producer.setMessageBuilder(delayedTextMessageBuilder);
        producer.start();
        producer.join();
        try {
            Assert.assertEquals("Number of delivering messages does not match", numberOfMessages, getListScheduledMessagesSize());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue("Exception was caught", false);
        }
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void listMessagesBeforeReceiveTest() throws Exception {
        final int numberOfMessages = 300;
        final JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));

        prepareServer(container(1));
        container(1).start();

        TextMessageVerifier messageVerifier = new TextMessageVerifier(jmsImplementation);
        ProducerTransAck producerTransAck = new ProducerTransAck(container(1), queueJndiName, numberOfMessages);
        addClient(producerTransAck);
        producerTransAck.setTimeout(0);
        producerTransAck.setCommitAfter(10);
        producerTransAck.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producerTransAck.addMessageVerifier(messageVerifier);

        producerTransAck.start();
        producerTransAck.join();

        Set<String> duplicates = new HashSet<String>();
        boolean duplicationsDetected = false;

        JMSOperations jmsOperations = container(1).getJmsOperations();
        for (Map<String, String> message: jmsOperations.listMessages(queueName)) {
            if (!duplicates.add(message.get(jmsImplementation.getDuplicatedHeader()))) {
                log.error("Duplication detected: " + message);
                duplicationsDetected = true;
            }
        }
        jmsOperations.close();

        ReceiverTransAck receiverTransAck = new ReceiverTransAck(container(1), queueJndiName, 10000, 10, 10);
        addClient(receiverTransAck);
        receiverTransAck.addMessageVerifier(messageVerifier);
        receiverTransAck.start();
        receiverTransAck.join();

        boolean verified = messageVerifier.verifyMessages();

        container(1).stop();

        Assert.assertFalse(duplicationsDetected);
        Assert.assertTrue(verified);
    }

    private void prepareServer(Container container) {
        container.start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(queueName, queueJndiName);
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        ops.close();
        container.stop();
    }

    public int getListScheduledMessagesSize() throws Exception {
        CLI.Result r = runOperation("list-scheduled-messages");
        return r.getResponse().get("result").asList().size();
    }

    public int getListDeliveringMessagesSize() throws Exception {
        CLI.Result r = runOperation("list-delivering-messages");
        return r.getResponse().get("result").get(0).get("elements").asList().size();
    }

    private CLI.Result runOperation(final String operation, final String... params) {
        String cmd;
        if (container(1).getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
            cmd = CliUtils.buildCommand(ADDRESS_EAP6, ":" + operation, params);
        } else {
            cmd = CliUtils.buildCommand(ADDRESS_EAP7, ":" + operation, params);
        }

        return this.cli.executeCommand(cmd);
    }

}
