package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadProducerWithSemaphores;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.fail;

/**
 * Basic tests for transfer messages over core-bridge
 * <p/>
 *
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
public class TransferOverBridgeTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void basicSendMessagesViaBridge() throws InterruptedException {
        final String TEST_QUEUE = "dummyQueue";
        final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
        final int MESSAGES = 10;

        // Start servers
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // Create administration objects
        JMSAdminOperations jmsAdminContainer1 = new JMSAdminOperations();
        JMSAdminOperations jmsAdminContainer2 = new JMSAdminOperations("10.34.3.146", 9999);

        // Create queue
        jmsAdminContainer1.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer1.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminContainer2.cleanupQueue(TEST_QUEUE);
        jmsAdminContainer2.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);

        jmsAdminContainer2.removeRemoteConnector("bridge-connector");
        jmsAdminContainer2.removeBridge("myBridge");

        Map<String, String> params = new HashMap<String, String>();
        params.put("host", "127.0.0.1");
        params.put("port", "5445");
        jmsAdminContainer2.createRemoteConnector("bridge-connector", null);
        jmsAdminContainer2.createBridge("myBridge", "jms.queue." + TEST_QUEUE, null, -1, "bridge-connector");


        Thread.sleep(2000);
        /**
         SimpleJMSClient client1 = new SimpleJMSClient("127.0.0.1", 4447, MESSAGES, Session.AUTO_ACKNOWLEDGE, false);
         client1.sendMessages(TEST_QUEUE_JNDI);

         // Let's bridge to transfer messages
         Thread.sleep(1000);

         SimpleJMSClient client2 = new SimpleJMSClient("10.34.3.146", 4447, MESSAGES, Session.AUTO_ACKNOWLEDGE, false);
         client2.receiveMessages(TEST_QUEUE_JNDI);
         assertEquals(MESSAGES, client2.getReceivedMessages());
         **/

        jmsAdminContainer1.close();
        jmsAdminContainer2.close();
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
    }


    /**
     * Implementation of the test logic, logic is shared for all test scenarios
     *
     * @param gapBetweenConsumers gap between consumers and producer
     * @param messagesCount       total count of messages
     * @param consumersCount      count of consumers used in tests
     * @param receiveTimeout      receive timeout for consumers
     * @param messageBuilder      implementation of {@link org.jboss.qa.hornetq.apps.MessageBuilder} used for test
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
