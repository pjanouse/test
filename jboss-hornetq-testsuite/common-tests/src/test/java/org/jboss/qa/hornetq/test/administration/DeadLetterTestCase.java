package org.jboss.qa.hornetq.test.administration;


import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.specific.DeadLetterPrepare;
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
 *
 * @tpChapter Functional testing
 * @tpSubChapter DEAD LETTER ADDRESS - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails Verifies correct behavior of dead letter queueu.
 *
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class DeadLetterTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(DeadLetterTestCase.class);

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final MessageBuilder messageBuilder = new TextMessageBuilder(1000);

    /**
     * Tests reading lost message from DLQ after max-deliver-attempts was reached.
     *
     * @tpTestDetails To server are deployed two queues. TestQueue and DLQ. Address settings is configured for all address (#) configured to use DLQ as
     *  dead letter queue for testQueue and max delivery attempts is set to 2.
     *  Start producer which sends 1 message to testQueue. Then start consumer which receives message from testQueue in transacted session and
     *  roll-backs this session. Repeat the last operation with consumer. ( message should be sent to DLQ )
     *
     * @tpPassCrit message is in DLQ
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "DeadLetterPrepare", params = {
            @Param(name = PrepareParams.ADDRESS, value = "#"),
            @Param(name = DeadLetterPrepare.DEPLOY_DLQ, value = "true")
    })
    public void testDeliveryToExistingDLQ() throws Exception {

        container(1).start();
        this.testDLQDelivery();
    }


    /**
     * Tests reading lost message with max-deliver-attempts being set only for specific subaddress.
     *
     * @tpTestDetails To server are deployed two queues. TestQueue and DLQ. Address settings is just for testQueue to use DLQ as
     *  dead letter queue for testQueue and max delivery attempts is set to 2.
     *  Start producer which sends 1 message to testQueue. Then start consumer which receives message from testQueue in transacted session and
     *  roll-backs this session. Repeat the last operation with consumer. ( message should be sent to DLQ )
     *
     * @tpPassCrit message is in DLQ
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "DeadLetterPrepare", params = {
            @Param(name = PrepareParams.ADDRESS, value = "jms.queue.#"),
            @Param(name = DeadLetterPrepare.DEPLOY_DLQ, value = "true")
    })
    public void testDeliveryToSubaddressDLQ() throws Exception {
        container(1).start();
        this.testDLQDelivery();
    }


    /**
     * Tests reading lost message from undefined DLQ.
     *
     * DLQ is defined in address-settings for test queue, but DLQ itself is not deployed on the server.
     *
     * @tpTestDetails To server is deployed just testQueue. Address settings is configured to use DLQ as
     *  dead letter queue for testQueue and max delivery attempts is set to 2.
     *  Start producer which sends 1 message to testQueue. Then start consumer which receives message from testQueue in transacted session and
     *  roll-backs this session. Repeat the last operation with consumer. ( message should be sent to DLQ )
     *
     * @tpPassCrit there is no DLQ confgirud so message will be dropped
     *
     */
    @Test(expected = NameNotFoundException.class)
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "DeadLetterPrepare", params = {
            @Param(name = PrepareParams.ADDRESS, value = "#"),
            @Param(name = DeadLetterPrepare.DEPLOY_DLQ, value = "false")
    })
    public void testDeliveryToNonExistantDLQ() throws Exception {
        container(1).start();
        this.testDLQDelivery();
    }


    /**
     * @tpTestDetails To server is deployed queue testQueue. Address settings is configured for all addresses (#) to use DLQ as
     *  dead letter queue for testQueue and max delivery attempts is set to 2.
     *  Start producer which sends 1 message to testQueue. Then start consumer which receives message from testQueue in transacted session and
     *  roll-backs this session. Repeat the last operation with consumer. Message will be dropped as DLQ is not deployed.
     *  Deploy DLQ and start producer which sends 1 message to testQueue. Then start consumer which receives message from testQueue in transacted session and
     *  roll-backs this session. Repeat the last operation with consumer. ( message should be sent to DLQ )
     *
     * @tpPassCrit message is in DLQ
     *
     * @throws Exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "DeadLetterPrepare", params = {
            @Param(name = PrepareParams.ADDRESS, value = "#"),
            @Param(name = DeadLetterPrepare.DEPLOY_DLQ, value = "false")
    })
    public void testDLQDeployedDuringProcessing() throws Exception {
        container(1).start();

        try {
            this.testDLQDelivery();
            Assert.fail("Message was not supposed to be re-routed to non-existent DLQ");
        } catch (NameNotFoundException ex) {
            // test couldn't find DLQ to read lost message from it
        }

        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue(DeadLetterPrepare.DLQ_NAME, DeadLetterPrepare.DLQ_JNDI, true);
        ops.close();

        // try again, message should be delivered to DLQ now
        this.testDLQDelivery();
    }


    private void testDLQDelivery() throws Exception {
        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_FACTORY_JNDI_EAP6);
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = (Queue) ctx.lookup(PrepareConstants.QUEUE_JNDI);

            Message msg;

            MessageProducer producer = session.createProducer(queue);
            msg = this.messageBuilder.createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
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
            Queue dlq = (Queue) ctx.lookup(DeadLetterPrepare.DLQ_JNDI);
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
