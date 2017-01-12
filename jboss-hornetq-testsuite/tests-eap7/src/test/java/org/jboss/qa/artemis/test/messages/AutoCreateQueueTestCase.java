package org.jboss.qa.artemis.test.messages;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import category.Functional;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;

/**
 * Tests for creating auto create and auto delete queues.
 *
 * @author mnovak@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE CONTENT - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails Tests for creating and manipulating Queues    .
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class AutoCreateQueueTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(AutoCreateQueueTestCase.class);

    /**
     * @tpTestDetails Start server.
     * @tpProcedure <ul>
     * <li>Start server with auto create queues disabled</li>
     * <li>Try to send message to a queue which does not exist in the server</li>
     * </ul>
     * @tpPassCrit JMSException will be thrown.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.AUTO_CREATE_JMS_QUEUES, value = "false"),
            @Param(name = PrepareParams.AUTO_DELETE_JMS_QUEUES, value = "false"),
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void testAutoCreateQueueDisabled() throws Exception {
        Exception exception = null;
        try {
            testAutoCreateQueue();
        } catch (JMSException ex) {
            log.info("JMSException was thrown - this is expected");
            exception = ex;
        }
        Assert.assertNotNull("No JMSException was thrown when trying to send message to non-existent queue.", exception);
    }

    /**
     * @tpTestDetails Start server.
     * @tpProcedure <ul>
     * <li>Start server with auto create queues enabled</li>
     * <li>Try to send message and receive message to a queue which does not exist in the server</li>
     * </ul>
     * @tpPassCrit Message can be sent and received.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.AUTO_CREATE_JMS_QUEUES, value = "true"),
            @Param(name = PrepareParams.AUTO_DELETE_JMS_QUEUES, value = "false"),
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void testAutoCreateQueueEnabled() throws Exception {
        testAutoCreateQueue();
    }

    /**
     * @tpTestDetails Start server.
     * @tpProcedure <ul>
     * <li>Start server with auto create queues enabled and auto remove queue enabled</li>
     * <li>Try to send message and receive message to a queue which does not exist in the server</li>
     * <li>Check that queue was removed when consumer finished.</li>
     * </ul>
     * @tpPassCrit Message can be sent and received and queue removed.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.AUTO_CREATE_JMS_QUEUES, value = "true"),
            @Param(name = PrepareParams.AUTO_DELETE_JMS_QUEUES, value = "true"),
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void testAutoDeleteQueueEnabled() throws Exception {
        Exception exception = null;
        try {
            testAutoDeleteQueue();
        } catch (Exception ex) {
            log.info("Exception was thrown - this is expected");
            exception = ex;
        }
        Assert.assertNotNull("No JMSException was thrown when trying to send message to non-existent queue.", exception);
    }

    /**
     * @tpTestDetails Start server.
     * @tpProcedure <ul>
     * <li>Start server with auto create queues enabled and auto remove queue disabled</li>
     * <li>Try to send message and receive message to a queue which does not exist in the server</li>
     * </ul>
     * @tpPassCrit Message can be sent and received. Message will be removed.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.AUTO_CREATE_JMS_QUEUES, value = "true"),
            @Param(name = PrepareParams.AUTO_DELETE_JMS_QUEUES, value = "false"),
            @Param(name = PrepareParams.PREPARE_DESTINATIONS, value = "false")
    })
    public void testAutoDeleteQueueDisabled() throws Exception {
            testAutoDeleteQueue();
    }

    public void testAutoCreateQueue() throws Exception {

        Container container = container(1);

        container.start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container.getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container.getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue testQueue = session.createQueue("testQueue");

            MessageProducer producer = session.createProducer(testQueue);
            MapMessage msg = session.createMapMessage();
            producer.send(msg);
            producer.close();

            MessageConsumer consumer = session.createConsumer(testQueue);
            Message receivedMsg = consumer.receive(3000);
            Assert.assertNotNull("Message was not received. We expect that message will be received when auto create queues is enabled.", receivedMsg);
            consumer.close();


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

    public void testAutoDeleteQueue() throws Exception {

        String queueName = "testQueue";

        Container container = container(1);

        container.start();

        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container.getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container.getConnectionFactoryName());
            connection = cf.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue testQueue = session.createQueue(queueName);

            MessageProducer producer = session.createProducer(testQueue);
            MapMessage msg = session.createMapMessage();
            producer.send(msg);
            producer.close();

            MessageConsumer consumer = session.createConsumer(testQueue);
            Message receivedMsg = consumer.receive(3000);
            Assert.assertNotNull("Message was not received. We expect that message will be received when auto create queues is enabled.", receivedMsg);
            consumer.close();

            JMSOperations jmsOperations = container.getJmsOperations();
            jmsOperations.getCountOfMessagesOnRuntimeQueue("jms.queue." + queueName);
            jmsOperations.close();


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

