package org.jboss.qa.hornetq.apps.clients;


import org.apache.log4j.Logger;

import javax.jms.*;
import java.util.concurrent.Semaphore;

/**
 * Special consumer with semaphore.
 * <p/>
 * Consumer contains two semaphores. The first one blocks consumer
 * and the second one releases consumer after specified count of
 * messages. Consumer can be used to simulate gap between
 * producer and consumers.
 */
public class HighLoadConsumerWithSemaphores extends HighLoadClientWithSemaphores {

    // Logger
    private static final Logger log = Logger.getLogger(HighLoadConsumerWithSemaphores.class);

    protected Semaphore releaseSemaphore;
    protected Semaphore releaseGapSemaphore;
    protected int releaseSemaphoreAt;

    protected volatile int receivedMessages = 0;
    protected int receiveTimeout = 0;

    /**
     * Constructor
     *
     * @param name                thread name
     * @param topic               target topic
     * @param cf                  connection factory
     * @param releaseSemaphore    instance of semaphore
     * @param releaseGapSemaphore instance of semaphore (for the following consumer)
     * @param releaseSemaphoreAt  release semaphore after defined count of messages
     * @param receiveTimeout      timeout for receiver
     */
    public HighLoadConsumerWithSemaphores(String name, Topic topic, ConnectionFactory cf, Semaphore releaseSemaphore,
                                          Semaphore releaseGapSemaphore, int releaseSemaphoreAt, int receiveTimeout) {
        super(name, topic, cf);
        this.releaseSemaphore = releaseSemaphore;
        this.releaseGapSemaphore = releaseGapSemaphore;
        this.releaseSemaphoreAt = releaseSemaphoreAt;
        this.receiveTimeout = receiveTimeout;
    }

    /**
     * @see {@link Thread#run()}
     */
    @Override
    public void run() {
        Connection connection = null;
        Session session = null;
        this.stopped = false;
        this.requestForStop = false;
        log.info(String.format("Starting consumer for '%s' - '%s'", this.topic, this.getName()));
        try {
            connection = cf.createConnection();
            connection.setClientID(getName());
            connection.start();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            TopicSubscriber subscriber = session.createDurableSubscriber(this.topic, this.getName());
            int counter = 0;
            int invalidOrderCounter = 0;
            boolean continueWithConsuming = true;
            releaseSemaphore.acquire();
            while (!this.requestForStop && continueWithConsuming) {
                if (counter == 0) {
                    log.info(String.format("Starting to consume for '%s' - '%s'", this.topic, this.getName()));
                }
                Message msg = subscriber.receive(this.receiveTimeout);
                if (counter % 100 == 0) {
                    log.info(String.format("Consumer for topic '%s' - with name '%s' received message: '%s'",
                            this.topic, this.getName(), counter));
                }
                if (msg == null) {
                    log.info(String.format("Cannot get message in specified timeout '%s' - '%s'", this.topic, this.getName()));
                    continueWithConsuming = false;
                } else {
                    counter++;
                    if (msg.propertyExists(ATTR_MSG_COUNTER) && msg.getIntProperty(ATTR_MSG_COUNTER) != counter) {
                        if (invalidOrderCounter < 10) {
                            int actualProp = msg.getIntProperty(ATTR_MSG_COUNTER);
                            log.warn(String.format("Invalid messages order. Expected: %s, received %s '%s' - '%s'",
                                    counter, actualProp, this.topic, this.getName()));
                            invalidOrderCounter++;
                        }
                    }
                }
                if (counter >= this.releaseSemaphoreAt && this.releaseGapSemaphore != null) {
                    this.releaseGapSemaphore.release();
                }
                if (counter % 10 == 0) {
                    session.commit();
                }
                if (counter % 10000 == 0) {
                    log.info(String.format("## '%s' '%s' received '%s'", this.getName(), this.topic, counter));
                }
                this.receivedMessages = counter;
            }
            session.commit();
        } catch (Exception e) {
            log.error(String.format("Exception in consumer " + this.getName() + " : " + e.getMessage()), e);
        } finally {
            if (this.releaseSemaphore != null) {
                this.releaseSemaphore.release();
            }
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    log.error(String.format("Cannot close session %s ", e.getMessage()));
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    log.error(String.format("Cannot close connection %s ", e.getMessage()));
                }
            }
        }
        this.stopped = true;
        log.info(String.format("Stopping consumer for '%s' - '%s' received messages %s", this.getName(), this.topic, getReceivedMessages()));
    }

    /**
     * Returns count of received messages
     *
     * @return count of messages
     */
    public int getReceivedMessages() {
        return receivedMessages;
    }
}



