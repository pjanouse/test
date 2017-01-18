package org.jboss.qa.hornetq.test.paging;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadProducerWithSemaphores;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.fail;

/**
 * Test case covers tests for durable subscribers with the different speed page address full mode.
 * <p/>
 *
 * @author pslavice@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter PAGING - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpTestCaseDetails Test case covers tests for durable subscribers with the different speed page address full mode.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(Functional.class)
public class DurableSubscriptionsTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     * @tpTestDetails Start server with topic. Create number of durable subscription and start sending normal byte
     * messages to this topic. Wait until messages in subscription are paged to disk. Start subscribers one by one with
     * gaps so there is huge difference in number of messages between subscriptions. All subscribers must receive
     * correct number of messages.
     * @tpProcedure <ul>
     *     <li>start one server with deployed topic</li>
     *     <li>create subscription on topic</li>
     *     <li>send normal byte messages to topic so it gets paged</li>
     *     <li>start subscribers one by one so there is a huge difference in number of messages between subscriptions</li>
     * </ul>
     * @tpPassCrit subscribers must receive correct number of messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS, value = "jms.topic." + PrepareConstants.TOPIC_NAME),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 50),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 10),
            @Param(name = PrepareParams.DISABLE_TRACE_LOGGING, value = "true")
    })
    public void normalByteMessagesTest() throws InterruptedException {
        testLogic(5000, 30000, 10, 30000, new ByteMessageBuilder(512));
    }

    /**
     * Normal message (not large message), text message
     *
     * @throws InterruptedException if something is wrong
     * @tpTestDetails Start server with topic. Create number of durable subscription and start sending normal text
     * messages to this topic. Wait until messages in subscription are paged to disk. Start subscribers one by one with
     * gaps so there is huge difference in number of messages between subscriptions. All subscribers must receive
     * correct number of messages.
     * @tpProcedure <ul>
     *     <li>start one server with deployed topic</li>
     *     <li>create subscription on topic</li>
     *     <li>send normal text messages to topic so it gets paged</li>
     *     <li>start subscribers one by one so there is a huge difference in number of messages between subscriptions</li>
     * </ul>
     * @tpPassCrit subscribers must receive correct number of messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS, value = "jms.topic." + PrepareConstants.TOPIC_NAME),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 50),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 10),
            @Param(name = PrepareParams.DISABLE_TRACE_LOGGING, value = "true")
    })
    public void normalTextMessagesTest() throws InterruptedException {
        testLogic(5000, 30000, 10, 30000, new TextMessageBuilder(512));
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     * @tpTestDetails Start server with topic. Create number of durable subscription and start sending large byte
     * messages to this topic. Wait until messages in subscription are paged to disk. Start subscribers one by one with
     * gaps so there is huge difference in number of messages between subscriptions. All subscribers must receive
     * correct number of messages.
     * @tpProcedure <ul>
     *     <li>start one server with deployed topic</li>
     *     <li>create subscription on topic</li>
     *     <li>send large byte messages to topic so it gets paged</li>
     *     <li>start subscribers one by one so there is a huge difference in number of messages between subscriptions</li>
     * </ul>
     * @tpPassCrit subscribers must receive correct number of messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS, value = "jms.topic." + PrepareConstants.TOPIC_NAME),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 50),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 10),
            @Param(name = PrepareParams.DISABLE_TRACE_LOGGING, value = "true")
    })
    public void largeByteMessagesTest() throws InterruptedException {
        testLogic(500, 5000, 10, 30000, new ByteMessageBuilder(150 * 1024));
    }

    /**
     * Large message, text message
     *
     * @throws InterruptedException if something is wrong
     * @tpTestDetails Start server with topic. Create number of durable subscription and start sending large text
     * messages to this topic. Wait until messages in subscription are paged to disk. Start subscribers one by one with
     * gaps so there is huge difference in number of messages between subscriptions. All subscribers must receive
     * correct number of messages.
     * @tpProcedure <ul>
     *     <li>start one server with deployed topic</li>
     *     <li>create subscription on topic</li>
     *     <li>send large text messages to topic so it gets paged</li>
     *     <li>start subscribers one by one so there is a huge difference in number of messages between subscriptions</li>
     * </ul>
     * @tpPassCrit subscribers must receive correct number of messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ADDRESS, value = "jms.topic." + PrepareConstants.TOPIC_NAME),
            @Param(name = PrepareParams.MAX_SIZE_BYTES, value = "" + 1024 * 50),
            @Param(name = PrepareParams.PAGE_SIZE_BYTES, value = "" + 1024 * 10),
            @Param(name = PrepareParams.DISABLE_TRACE_LOGGING, value = "true")
    })
    public void largeTextMessagesTest() throws InterruptedException {
        testLogic(500, 5000, 10, 30000, new TextMessageBuilder(150 * 1024));
    }

    /**
     * Implementation of the test logic, logic is shared for all test scenarios
     *
     * @param gapBetweenConsumers gap between consumers and producer
     * @param messagesCount       total count of messages
     * @param consumersCount      count of consumers used in tests
     * @param receiveTimeout      receive timeout for consumers
     * @param messageBuilder      implementation of {@link MessageBuilder} used for test
     */
    private void testLogic(int gapBetweenConsumers, int messagesCount, int consumersCount,
                            int receiveTimeout, MessageBuilder messageBuilder) {

        if (prepareCoordinator.getParams().containsKey(PrepareParams.DATABASE)) {
            messagesCount = 5000;
        }

        container(1).start();

        // Clients and semaphores
        HighLoadProducerWithSemaphores producer;
        HighLoadConsumerWithSemaphores[] consumers;
        Semaphore[] semaphores;
        semaphores = new Semaphore[consumersCount];
        consumers = new HighLoadConsumerWithSemaphores[consumersCount];
        for (int i = 0; i < semaphores.length; i++) {
            semaphores[i] = new Semaphore(0);
        }

        Context context = null;
        Connection connection = null;
        Session session = null;
        long startTime = System.currentTimeMillis();
        try {
            context = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
            Topic topic = (Topic) context.lookup(PrepareConstants.TOPIC_JNDI);

            producer = new HighLoadProducerWithSemaphores("producer", topic, cf, semaphores[0], gapBetweenConsumers,
                    messagesCount, messageBuilder, ContainerUtils.getJMSImplementation(container(1)));
            for (int i = 0; i < consumers.length; i++) {
                consumers[i] = new HighLoadConsumerWithSemaphores("consumer " + i, topic, cf, semaphores[i],
                        (i + 1 < semaphores.length) ? semaphores[i + 1] : null,
                        gapBetweenConsumers, receiveTimeout);
                consumers[i].start();
                Thread.sleep(500); // TODO: in RHEL7: if many consumers try to create durable subscription at once, the session.crateDurableSubscriber fails
            }
            Thread.sleep(5000);
            producer.start();
            producer.join();
            for (HighLoadConsumerWithSemaphores consumer : consumers) {
                consumer.join();
            }

            ContainerUtils.printThreadDump(container(1));

            if (producer.getSentMessages() != messagesCount) {
                fail("Producer did not send defined count of messages");
            } else {
                for (int i = 0; i < consumers.length; i++) {
                    if (consumers[i].getReceivedMessages() != messagesCount) {
                        fail(String.format("Receiver #%s did not received defined count of messages", i));
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        } finally {
            JMSTools.cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending test after %s ms", System.currentTimeMillis() - startTime));
    }
}
