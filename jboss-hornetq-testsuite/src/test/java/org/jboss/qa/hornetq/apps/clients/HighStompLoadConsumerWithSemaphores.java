package org.jboss.qa.hornetq.apps.clients;


import org.apache.log4j.Logger;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import static org.fusesource.stomp.client.Constants.*;
import static org.junit.Assert.assertTrue;

/**
 * Special consumer with semaphore for STOMP protocol.
 * <p/>
 * Consumer contains two semaphores. The first one blocks consumer
 * and the second one releases consumer after specified count of
 * messages. Consumer can be used to simulate gap between
 * producer and consumers.
 */
public class HighStompLoadConsumerWithSemaphores extends HighLoadConsumerWithSemaphores {

    // Logger
    private static final Logger log = Logger.getLogger(HighStompLoadConsumerWithSemaphores.class);

    protected String destination;

    protected Stomp stomp;

    /**
     * Constructor
     *
     * @param name                thread name
     * @param destination         target destination
     * @param stomp               Instance of STOMP
     * @param releaseSemaphore    instance of semaphore
     * @param releaseGapSemaphore instance of semaphore (for the following consumer)
     * @param releaseSemaphoreAt  release semaphore after defined count of messages
     * @param receiveTimeout      timeout for receiver
     */
    public HighStompLoadConsumerWithSemaphores(String name, String destination, Stomp stomp,
                                               Semaphore releaseSemaphore,
                                               Semaphore releaseGapSemaphore, int releaseSemaphoreAt, int receiveTimeout) {
        super(name, null, null, releaseSemaphore, releaseGapSemaphore, releaseSemaphoreAt, receiveTimeout);
        this.destination = destination;
        this.stomp = stomp;
    }

    /**
     * @see {@link Thread#run()}
     */
    @Override
    public void run() {
        this.stopped = false;
        this.requestForStop = false;
        BlockingConnection connection = null;
        log.info(String.format("Starting consumer for '%s' - '%s'", this.topic, this.getName()));
        try {
            int counter = 0;
            boolean continueWithConsuming = true;

            StompFrame frame;
            frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(ID, Buffer.ascii(getName()));
            connection.send(frame);

            releaseSemaphore.acquire();
            while (!this.requestForStop && continueWithConsuming) {
                if (counter == 0) {
                    log.info(String.format("Starting to consume for '%s' - '%s'", this.topic, this.getName()));
                }

                StompFrame received = null;
                try {
                    received = connection.receive();
                } catch (EOFException e) {
                    received = null;
                }
                assertTrue(received.action().equals(MESSAGE));

                if (counter % 100 == 0) {
                    log.info(String.format("Consumer for topic '%s' - with name '%s' received message: '%s'",
                            this.topic, this.getName(), counter));
                }
                if (received == null) {
                    log.info(String.format("Cannot get message in specified timeout '%s' - '%s'", this.topic, this.getName()));
                    continueWithConsuming = false;
                }
                if (counter >= this.releaseSemaphoreAt && this.releaseGapSemaphore != null) {
                    this.releaseGapSemaphore.release();
                }
                if (counter % 10000 == 0) {
                    log.info(String.format("## '%s' '%s' received '%s'", this.getName(), this.topic, counter));
                }
                this.receivedMessages = counter;
            }
            try {
                frame = new StompFrame(UNSUBSCRIBE);
                frame.addHeader(ID, Buffer.ascii(getName()));
                connection.send(frame);
            } catch (EOFException e) {
                // Ignore
            }
            Thread.sleep(100);
        } catch (Exception e) {
            log.error(String.format("Exception in consumer " + this.getName() + " : " + e.getMessage()), e);
        } finally {
            if (this.releaseSemaphore != null) {
                this.releaseSemaphore.release();
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
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



