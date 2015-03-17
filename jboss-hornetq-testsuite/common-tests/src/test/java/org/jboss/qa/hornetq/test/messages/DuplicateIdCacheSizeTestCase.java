package org.jboss.qa.hornetq.test.messages;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.*;
import javax.naming.Context;

/**
 * Created by mnovak on 9/22/14.
 *
 * Test whether there is dup id cache leak when queue is created and deleted many times.
 *
 * This is basically JMS request/reply with JMS standalone org.jboss.qa.hornetq.apps.clients.
 *
 */
public class DuplicateIdCacheSizeTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DuplicateIdCacheSizeTestCase.class);

    private String inQueue = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueue;

    /**
     * Start producer
     * @throws Exception
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testDupIdCacheSizeWithDurableQueues() throws Exception {

        prepareServer(CONTAINER1);
        prepareServer(CONTAINER2);

        controller.start(CONTAINER1);
        final long numberOfMessages = 300;
        final int numberOfIterations = 300000;

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        try {
            ctx = this.getContext(CONTAINER1);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(true, Session.SESSION_TRANSACTED);

            for (int iterations = 0; iterations < numberOfIterations; iterations++) {
                TemporaryQueue tempQueue = session.createTemporaryQueue();
                MessageProducer producer = session.createProducer(tempQueue);
                MessageBuilder messageBuilder = new TextMessageBuilder(1);
                messageBuilder.setAddDuplicatedHeader(true);
                Message msg = messageBuilder.createMessage(session);

                logger.info("Iteration: " + iterations + " Send " + numberOfMessages + " to temp queue.");
                for (int i = 0; i < numberOfMessages; i++) {
                    producer.send(msg);
                }
                session.commit();
                logger.info("Iteration: " + iterations + " Commit send of " + numberOfMessages + " to temp queue.");
                producer.close();

                MessageConsumer consumer = session.createConsumer(tempQueue);
                logger.info("Iteration: " + iterations + " Receive " + numberOfMessages + " from temp queue.");
                long count = 0;
                while (consumer.receive(1000) != null) {
                    count++;
                }
                session.commit();
                logger.info("Iteration: " + iterations + " Commit receive " + numberOfMessages + " from temp queue.");
                Assert.assertEquals(count, numberOfMessages);
                consumer.close();
//                removeQueue(CONTAINER1, inQueue);
//                removeQueue(CONTAINER2, inQueue);
                tempQueue.delete();
            }

        } finally {
            if (session != null) {
                session.close();
            }

            if (connection != null) {
                connection.stop();
                connection.close();
            }

            if (ctx != null) {
                ctx.close();
            }
        }
        stopServer(CONTAINER1);
    }

    private void prepareServer(String containerName) {
        controller.start(containerName);

        JMSOperations jmsOperations = getJMSOperations(containerName);
        jmsOperations.createQueue(inQueue, inQueueJndiName);
        jmsOperations.close();
        stopServer(containerName);

    }
}