package org.jboss.qa.hornetq.test.paging;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadProducerWithSemaphores;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
 */
@RunWith(Arquillian.class)
public class DurableSubscriptionsTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        controller.stop(CONTAINER1);
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void normalByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(5000, 30000, 10, 10000, new ByteMessageBuilder(512), 1024 * 50, 1024 * 10);
    }

    /**
     * Normal message (not large message), text message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void normalTextMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(5000, 30000, 10, 10000, new TextMessageBuilder(512), 1024 * 50, 1024 * 10);
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(5000, 500, 10, 10000, new ByteMessageBuilder(150 * 1024), 1024 * 50, 1024 * 10);
    }

    /**
     * Large message, text message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void largeTextMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(5000, 500, 10, 10000, new TextMessageBuilder(150 * 1024), 1024 * 50, 1024 * 10);
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
                           int receiveTimeout, MessageBuilder messageBuilder,
                           int maxSizeBytes, int pageSizeBytes) {
        final String TOPIC = "pageTopic";
        final String TOPIC_JNDI = "/topic/pageTopic";
        final String ADDRESS = "jms.topic." + TOPIC;

        controller.start(CONTAINER1);
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        jmsAdminOperations.cleanupTopic(TOPIC);
        jmsAdminOperations.createTopic(TOPIC, TOPIC_JNDI);
        jmsAdminOperations.removeAddressSettings(ADDRESS);
        jmsAdminOperations.addAddressSettings(ADDRESS, "PAGE", maxSizeBytes, 1000, 1000, pageSizeBytes);

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
            context = getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI);
            Topic topic = (Topic) context.lookup(TOPIC_JNDI);

            producer = new HighLoadProducerWithSemaphores("producer", topic, cf, semaphores[0], gapBetweenConsumers,
                    messagesCount, messageBuilder);
            for (int i = 0; i < consumers.length; i++) {
                consumers[i] = new HighLoadConsumerWithSemaphores("consumer " + i, topic, cf, semaphores[i],
                        (i + 1 < semaphores.length) ? semaphores[i + 1] : null,
                        gapBetweenConsumers, receiveTimeout);
                consumers[i].start();
            }
            Thread.sleep(5000);
            producer.start();
            producer.join();
            for (HighLoadConsumerWithSemaphores consumer : consumers) {
                consumer.join();
            }
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
            cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending test after %s ms", System.currentTimeMillis() - startTime));

        jmsAdminOperations.removeTopic(TOPIC);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }
}
