package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;

import javax.jms.*;
import java.util.concurrent.Semaphore;

/**
 * Special producer with semaphore.
 * <p/>
 * Producer contains semaphore which is released after specified
 * count of messages. Producer can be used to simulate gap between
 * producer and consumer.
 */
public class HighLoadProducerWithSemaphores extends HighLoadClientWithSemaphores {

    // Logger
    private static final Logger log = Logger.getLogger(HighLoadProducerWithSemaphores.class);

    // Release semaphore for the dependent object
    protected Semaphore releaseSemaphore;

    // Release semaphore after <code>releaseSemaphoreAt</code> messages
    protected int releaseSemaphoreAt;

    // Messages
    protected int messagesCount;

    // Sent messages
    protected volatile int sentMessages = 0;

    // Message builder
    protected MessageBuilder messageBuilder;

    /**
     * Constructor
     *
     * @param name               thread name
     * @param topic              target topic
     * @param cf                 connection factory
     * @param releaseSemaphore   instance of semaphore
     * @param releaseSemaphoreAt release semaphore after defined count of messages
     * @param messagesCount      total count of messages
     * @param messageBuilder     messages builder for this producer
     */
    public HighLoadProducerWithSemaphores(String name, Topic topic, ConnectionFactory cf, Semaphore releaseSemaphore,
                                          int releaseSemaphoreAt, int messagesCount, MessageBuilder messageBuilder) {
        super(name, topic, cf);
        this.releaseSemaphore = releaseSemaphore;
        this.releaseSemaphoreAt = releaseSemaphoreAt;
        this.messagesCount = messagesCount;
        this.messageBuilder = messageBuilder;
        if (this.messageBuilder == null) {
            this.messageBuilder = new ByteMessageBuilder();
        }
    }

    /**
     * @see {@link Thread#run()}
     */
    @Override
    public void run() {
        Connection connection = null;
        Session session = null;
        this.stopped = false;
        log.info(String.format("Starting producer for '%s' - '%s'", this.topic, this.getName()));
        try {
            connection = cf.createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.setTimeToLive(3600000);
            for (int i = 1; i <= this.messagesCount && !this.requestForStop; i++) {
                if (i >= this.releaseSemaphoreAt && this.releaseSemaphore != null) {
                    this.releaseSemaphore.release();
                }
                Message message = this.messageBuilder.createMessage(session);
                message.setIntProperty(ATTR_MSG_COUNTER, i);
                producer.send(message);
                if (i % 10 == 0) {
                    session.commit();
                }
                if (i % 10000 == 0) {
                    log.info(String.format("## '%s' '%s' send '%s'", this.getName(), this.topic, i));
                }
                this.sentMessages = i;
            }
        } catch (Exception e) {
            log.error(String.format("Exception in producer '%s' : %s", this.getName(), e.getMessage()));
        } finally {
            if (this.releaseSemaphore != null) {
                this.releaseSemaphore.release();
            }
            if (session != null) {
                try {
                    session.commit();
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
        log.info(String.format("Stopping producer for '%s' - '%s' sent messages %s", this.getName(), this.topic, getSentMessages()));
    }

    /**
     * Returns count of sent messages
     *
     * @return count of messages
     */
    public int getSentMessages() {
        return sentMessages;
    }
}
