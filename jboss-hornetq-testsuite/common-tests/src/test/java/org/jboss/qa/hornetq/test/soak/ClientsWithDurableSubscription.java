package org.jboss.qa.hornetq.test.soak;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.HighLoadConsumerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.HighLoadProducerWithSemaphores;
import org.jboss.qa.hornetq.ContextProvider;
import org.jboss.qa.hornetq.JMSTools;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.fail;

/**
 * Clients with gaps
 */
public class ClientsWithDurableSubscription implements Runnable {

    // Logger
    private static final Logger log = Logger.getLogger(ClientsWithDurableSubscription.class);

    protected String connectionFactoryJNDI;
    protected String topicJNDI;
    protected int gapBetweenConsumers;
    protected int messagesCount;
    protected int consumersCount;
    protected int receiveTimeout;
    protected MessageBuilder messageBuilder;
    protected ContextProvider contextProvider;

    protected HighLoadProducerWithSemaphores producer;
    protected HighLoadConsumerWithSemaphores[] consumers;

    @Override
    public void run() {
        log.info("Starting ClientsWithDurableSubscription org.jboss.qa.hornetq.apps.clients ... ");
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
            context = contextProvider.getContextContainer1();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(connectionFactoryJNDI);
            Topic topic = (Topic) context.lookup(topicJNDI);

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
            JMSTools.cleanupResources(context, connection, session);
        }
        log.info(String.format("Ending ClientsWithDurableSubscription org.jboss.qa.hornetq.apps.clients %s ms", System.currentTimeMillis() - startTime));
    }
}
