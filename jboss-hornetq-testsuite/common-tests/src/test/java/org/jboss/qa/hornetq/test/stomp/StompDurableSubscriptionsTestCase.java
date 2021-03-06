package org.jboss.qa.hornetq.test.stomp;

import org.fusesource.stomp.client.Stomp;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadStompProducerWithSemaphores;
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
        container(1).stop();
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
    public void normalByteMessagesTest() throws Exception {
        testLogic(5000, 30000, 1, 10000, 512, 1024 * 50, 1024 * 10);
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
    public void largeByteMessagesTest() throws Exception {
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
        final String TOPIC_JNDI = "jms/topic/pageTopic";
        final String ADDRESS = "#";
        final String STOMP_SOCKET_BINDING_NAME = "messaging-stomp";

        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.cleanupTopic(TOPIC);
        jmsAdminOperations.createTopic(TOPIC, TOPIC_JNDI);
        jmsAdminOperations.removeAddressSettings(ADDRESS);
        jmsAdminOperations.addAddressSettings(ADDRESS, "PAGE", maxSizeBytes, 1000, 1000, pageSizeBytes);

        // Create HQ acceptor for Stomp
        jmsAdminOperations.createSocketBinding(STOMP_SOCKET_BINDING_NAME, STOMP_PORT);
        Map<String, String> params = new HashMap<String, String>();
        params.put("protocols", "STOMP");
        jmsAdminOperations.createRemoteAcceptor("stomp-acceptor", "messaging-stomp", params);

        // Disable security on HQ
        jmsAdminOperations.setSecurityEnabled(false);

        container(1).restart();

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
            Stomp stomp = new Stomp(container(1).getHostname(), STOMP_PORT);
            context = container(1).getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
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

        jmsAdminOperations.close();
        container(1).stop();
    }
}
