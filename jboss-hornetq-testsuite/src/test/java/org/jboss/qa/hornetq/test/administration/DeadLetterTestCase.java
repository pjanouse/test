package org.jboss.qa.hornetq.test.administration;


import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import java.util.concurrent.TimeUnit;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class DeadLetterTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(DeadLetterTestCase.class);

    private static final int MAX_DELIVERY_ATTEMPTS = 2;

    private static final String QUEUE_NAME = "test.dlq.Queue";

    private static final String QUEUE_JNDI = "jms/queue/test/dlq/Queue";

    private static final String DLQ_NAME = "test.dlq.DeadLetterQueue";

    private static final String DLQ_JNDI = "jms/queue/test/dlq/DeadLetterQueue";

    private static final String EXPIRY_QUEUE_NAME = "test.dlq.ExpiryQueue";

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final ContainerInfo container = CONTAINER1_INFO;

    private final MessageBuilder messageBuilder = new TextMessageBuilder(1000);


    /**
     * Tests reading lost message from DLQ after max-deliver-attempts was reached.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDeliveryToExistingDLQ() throws Exception {
        this.controller.start(this.container.getName());

        JMSOperations ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        ops.createQueue(DLQ_NAME, DLQ_JNDI, true);
        ops.removeAddressSettings("#");
        ops.addAddressSettings("default", "#",
                "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024,
                "jms.queue." + EXPIRY_QUEUE_NAME, "jms.queue." + DLQ_NAME, MAX_DELIVERY_ATTEMPTS);
        ops.close();

        this.controller.stop(this.container.getName());
        this.controller.start(this.container.getName());

        this.testDLQDelivery();
    }


    /**
     * Tests reading lost message with max-deliver-attempts being set only for specific subaddress.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDeliveryToSubaddressDLQ() throws Exception {
        this.controller.start(this.container.getName());

        JMSOperations ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        ops.createQueue(DLQ_NAME, DLQ_JNDI, true);
        ops.addAddressSettings("default", "jms.queue.test.dlq.#",
                "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024,
                "jms.queue." + EXPIRY_QUEUE_NAME, "jms.queue." + DLQ_NAME, MAX_DELIVERY_ATTEMPTS);
        ops.close();

        this.controller.stop(this.container.getName());
        this.controller.start(this.container.getName());

        this.testDLQDelivery();
    }


    /**
     * Tests reading lost message from undefined DLQ.
     *
     * DLQ is defined in address-settings for test queue, but DLQ itself is not deployed on the server.
     */
    @Test(expected = NameNotFoundException.class)
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDeliveryToNonExistantDLQ() throws Exception {
        this.controller.start(this.container.getName());

        JMSOperations ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        ops.removeAddressSettings("#");
        ops.addAddressSettings("default", "#",
                "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024,
                "jms.queue." + EXPIRY_QUEUE_NAME, "jms.queue." + DLQ_NAME, MAX_DELIVERY_ATTEMPTS);

        this.controller.stop(this.container.getName());
        this.controller.start(this.container.getName());

        this.testDLQDelivery();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDLQDeployedDuringProcessing() throws Exception {
        this.controller.start(this.container.getName());

        // No DLQ deployed during initial setup
        JMSOperations ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI, true);
        ops.removeAddressSettings("#");
        ops.addAddressSettings("default", "#",
                "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024,
                "jms.queue." + EXPIRY_QUEUE_NAME, "jms.queue." + DLQ_NAME, MAX_DELIVERY_ATTEMPTS);

        this.controller.stop(this.container.getName());
        this.controller.start(this.container.getName());

        try {
            this.testDLQDelivery();
            Assert.fail("Message was not supposed to be re-routed to non-existent DLQ");
        } catch (NameNotFoundException ex) {
            // test couldn't find DLQ to read lost message from it
        }

        ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(DLQ_NAME, DLQ_JNDI, true);
        ops.close();

        // try again, message should be delivered to DLQ now
        this.testDLQDelivery();
    }


    private void testDLQDelivery() throws Exception {
        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = this.getContext(this.container.getIpAddress(), getJNDIPort(container.getName()));
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_FACTORY_JNDI_EAP6);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = (Queue) ctx.lookup(QUEUE_JNDI);

            Message msg;

            MessageProducer producer = session.createProducer(queue);
            msg = this.messageBuilder.createMessage(session);
            producer.send(msg);
            session.commit();
            LOG.info("Message sent with id " + msg.getJMSMessageID());

            MessageConsumer consumer = session.createConsumer(queue);
            msg = consumer.receive(RECEIVE_TIMEOUT);
            Assert.assertNotNull("There should be message in test queue", msg);
            LOG.info("1st delivery attempt, got id " + msg.getJMSMessageID());
            session.rollback();

            msg = consumer.receive(RECEIVE_TIMEOUT);
            Assert.assertNotNull("There should be message in test queue", msg);
            LOG.info("2nd delivery attempt, got id " + msg.getJMSMessageID());
            session.rollback();

            // 3rd try
            msg = consumer.receive(RECEIVE_TIMEOUT);
            Assert.assertNull("Message should not be delivered after max-delivery-attempts was reached", msg);

            // message should be moved to DLQ now
            Queue dlq = (Queue) ctx.lookup(DLQ_JNDI);
            MessageConsumer dlqConsumer = session.createConsumer(dlq);
            msg = dlqConsumer.receive(RECEIVE_TIMEOUT);
            Assert.assertNotNull("There should be lost message in DLQ", msg);
            LOG.info("Got message from DLQ to original destination " + msg.getStringProperty("_HQ_ORIG_ADDRESS"));
            session.commit();
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
    }

}
