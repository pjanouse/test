package org.jboss.qa.hornetq.test.stomp;

import org.apache.log4j.Logger;
import org.fusesource.stomp.client.Stomp;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadStompProducerWithSemaphores;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.fail;

/**
 * Test case covers tests for durable subscribers with the different speed page address full mode.
 * <p/>
 *
 * @author pslavice@redhat.com
 * TODO
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class StompDurableSubscriptionsTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    // Default port for STOMP
    protected static final int STOMP_PORT = 61613;

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void normalByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(5000, 30000, 10, 10000, 512, 1024 * 50, 1024 * 10);
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesTest() throws InterruptedException {
        deleteDataFolderForJBoss1();
        testLogic(500, 5000, 10, 10000, 150 * 1024, 1024 * 50, 1024 * 10);
    }

    /**
     * Implementation of the test logic, logic is shared for all test scenarios
     *
     * @param gapBetweenConsumers gap between consumers and producer
     * @param messagesCount       total count of messages
     * @param consumersCount      count of consumers used in tests
     * @param receiveTimeout      receive timeout for consumers
     * @param messageSize         size of used messages
     * @param maxSizeBytes        max size in bytes for address configurations
     * @param pageSizeBytes       page size in bytes for address configurations
     */
    private void testLogic(int gapBetweenConsumers, int messagesCount, int consumersCount,
                           int receiveTimeout, int messageSize,
                           int maxSizeBytes, int pageSizeBytes) {
        final String TOPIC = "pageTopic";
        final String TOPIC_JNDI = "/topic/pageTopic";
        final String ADDRESS = "#";
        final String STOMP_SOCKET_BINDING_NAME = "messaging-stomp";

        controller.start(CONTAINER1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        jmsAdminOperations.cleanupTopic(TOPIC);
        jmsAdminOperations.createTopic(TOPIC, TOPIC_JNDI);
        jmsAdminOperations.removeAddressSettings(ADDRESS);
        jmsAdminOperations.addAddressSettings(ADDRESS, "PAGE", maxSizeBytes, 1000, 1000, pageSizeBytes);

        // Create HQ acceptor for Stomp
        jmsAdminOperations.createSocketBinding(STOMP_SOCKET_BINDING_NAME, STOMP_PORT);
        Map<String, String> params = new HashMap<String, String>();
        params.put("protocol", "stomp");
        jmsAdminOperations.createRemoteAcceptor("stomp-acceptor", "messaging-stomp", params);

        // Disable security on HQ
        jmsAdminOperations.setSecurityEnabled(false);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);

        // Clients and semaphores
        HighLoadStompProducerWithSemaphores producer;
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
            Stomp stomp = new Stomp(CONTAINER1_IP, STOMP_PORT);
            context = getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(this.getConnectionFactoryName());
            Topic topic = (Topic) context.lookup(TOPIC_JNDI);

            producer = new HighLoadStompProducerWithSemaphores("producer", TOPIC_JNDI, stomp,
                    semaphores[0], gapBetweenConsumers, messagesCount, messageSize);
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
            JMSTools.cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending test after %s ms", System.currentTimeMillis() - startTime));

        jmsAdminOperations.removeTopic(TOPIC);
        jmsAdminOperations.close();
        stopServer(CONTAINER1);
    }
}
