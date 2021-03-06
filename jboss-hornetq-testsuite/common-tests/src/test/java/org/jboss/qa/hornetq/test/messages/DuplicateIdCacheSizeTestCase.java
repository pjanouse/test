package org.jboss.qa.hornetq.test.messages;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.naming.Context;

/**
 * Created by mnovak on 9/22/14.
 *
 * Test whether there is dup id cache leak when queue is created and deleted
 * many times.
 *
 * This is basically JMS request/reply with JMS standalone
 * org.jboss.qa.hornetq.apps.clients.
 *
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTestCaseDetails Tests whether there is duplicate id cache leak when queue is
 * created and deleted many times. This is basically JMS request/reply with JMS
 * standalone org.jboss.qa.hornetq.apps.clients. 
 */
@RunWith(Arquillian.class)
public class DuplicateIdCacheSizeTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DuplicateIdCacheSizeTestCase.class);

    private String inQueue = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueue;

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }

    /**
     *
     * @tpTestDetails Server is started. Send messages with duplicated header to
     * the destination on the server, then receive them all. Once all messages
     * are received, delete queue. Repeat this procedure on the same server
     * instance without restarting the server.
     * @tpProcedure <ul>
     * <li>Start server</li>
     * <li>Create temporary queue</li>
     * <li>Connect to the server with the producer and send test messages to the
     * queue.</li>
     * <li>Connect to the server with consumer and receive all messages from the queue</li>
     * <li>Delete temporary queue</li>
     * <li>Check that consumer received all messages</li> 
     * <li>Repeat this procedure (starting with creating new temporary queue) for 300000 times</li>
     * </ul>
     * @tpPassCrit All messages are successfully received in every iteration
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare("OneNode")
    public void testDupIdCacheSizeWithDurableQueues() throws Exception {

        container(1).start();
        final long numberOfMessages = 300;
        final int numberOfIterations = 300000;

        Context ctx = null;
        Connection connection = null;
        Session session = null;
        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(true, Session.SESSION_TRANSACTED);

            for (int iterations = 0; iterations < numberOfIterations; iterations++) {
                TemporaryQueue tempQueue = session.createTemporaryQueue();
                MessageProducer producer = session.createProducer(tempQueue);
                MessageBuilder messageBuilder = new TextMessageBuilder(1);
                messageBuilder.setAddDuplicatedHeader(true);
                Message msg = messageBuilder.createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());

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
//                removeQueue(CONTAINER1_NAME_NAME, inQueue);
//                removeQueue(CONTAINER2_NAME, inQueue);
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
        container(1).stop();
    }
}
