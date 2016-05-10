package org.jboss.qa.artemis.test.messages;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Test for last value queue.
 *
 * @author Martin Styk &lt;mstyk@redhat.com&gt;
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails Test for last value queue
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class LastValueQueuesTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(LastValueQueuesTestCase.class);
    private String QUEUE_NAME = "testQueue";
    private String QUEUE_JNDI_NAME = "jms/queue/" + QUEUE_NAME;

    /**
     * @tpTestDetails Server is started and queue is deployed. Address setting is configured to recognize queue as
     * a last value queue. Producer sends 10 messages with last value property configured. Consumer receives only
     * the last message
     * @tpProcedure <ul>
     * <li>Start server and deploy queue</li>
     * <li>Send 10 messages to queue configured as a last value queue </li>
     * <li>Receive messages from last value queue</li>
     * <li>Check that only last message was received</li>
     * </ul>
     * @tpPassCrit Only last message is received
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void lastValueQueueTest() throws Exception {
        prepareServer();

        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(Constants.CONNECTION_FACTORY_JNDI_EAP7);
        Connection connection = cf.createConnection();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = (Destination) context.lookup(QUEUE_JNDI_NAME);

        MessageProducer producer = session.createProducer(destination);

        connection.start();
        for (int i = 0; i < 10; i++) {
            TextMessage message = session.createTextMessage(i + " message");
            message.setStringProperty("_AMQ_LVQ_NAME", "MY_MESSAGE");
            producer.send(message);
        }

        MessageConsumer consumer = session.createConsumer(destination);

        List<Message> receivedMessages = new ArrayList<Message>();
        Message message;
        do{
            message = consumer.receive(5000);
            log.info("Received " + message);
            if(message != null){
                receivedMessages.add(message);
            }
        }while (message != null);

        connection.close();

        Assert.assertEquals("Consumer should receive only 1 message",1, receivedMessages.size());
        Assert.assertEquals("Last send message should be received", "9 message", ((TextMessage) receivedMessages.get(0)).getText());

        container(1).stop();
    }

    private void prepareServer() {
        container(1).start();
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.createQueue(QUEUE_NAME, QUEUE_JNDI_NAME);
        jmsOperations.addAddressSettings("default", "jms.queue.testQueue", "PAGE", 1024 * 1024, 0, 0, 512 * 1024, true);
        jmsOperations.close();
        container(1).stop();
    }

}