package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Semaphore;

import static org.fusesource.hawtbuf.AsciiBuffer.ascii;
import static org.fusesource.stomp.client.Constants.*;

/**
 * Special producer with semaphore.
 * <p/>
 * Producer contains semaphore which is released after specified
 * count of messages. Producer can be used to simulate gap between
 * producer and consumer.
 */
public class HighLoadStompProducerWithSemaphores extends HighLoadProducerWithSemaphores {

    // Logger
    private static final Logger log = Logger.getLogger(HighLoadStompProducerWithSemaphores.class);

    protected String destination;

    protected Stomp stomp;

    protected int sizeOfMessage;

    /**
     * Constructor
     *
     * @param name               thread name
     * @param destination        target jms destination
     * @param stomp              instance of stomp
     * @param releaseSemaphore   instance of semaphore
     * @param releaseSemaphoreAt release semaphore after defined count of messages
     * @param messagesCount      total count of messages
     * @param sizeOfMessage      size of ByteMessage
     */
    public HighLoadStompProducerWithSemaphores(String name, String destination, Stomp stomp,
                                               Semaphore releaseSemaphore,
                                               int releaseSemaphoreAt, int messagesCount,
                                               int sizeOfMessage) {
        super(name, null, null, releaseSemaphore, releaseSemaphoreAt, messagesCount, null);
        this.destination = destination;
        this.stomp = stomp;
        this.sizeOfMessage = sizeOfMessage;
    }

    /**
     * @see {@link Thread#run()}
     */
    @Override
    public void run() {
        BlockingConnection connection = null;
        this.stopped = false;
        log.info(String.format("Starting producer for '%s' - '%s'", this.destination, this.getName()));
        try {
            connection = stomp.connectBlocking();
            AsciiBuffer transactionId = ascii("tr" + this.getName());
            StompFrame frame;

            byte[] messageBody = new byte[this.sizeOfMessage];
            new Random().nextBytes(messageBody);

            frame = new StompFrame(BEGIN_TRANSACTION);
            frame.addHeader(TRANSACTION, transactionId);
            connection.send(frame);
            for (int i = 1; i <= this.messagesCount && !this.requestForStop; i++) {
                if (i >= this.releaseSemaphoreAt && this.releaseSemaphore != null) {
                    this.releaseSemaphore.release();
                }
                frame = new StompFrame(SEND);
                frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
                frame.addHeader(TRANSACTION, transactionId);
                frame.addHeader(PERSISTENT, TRUE);
                frame.addHeader(ascii(ATTR_MSG_COUNTER), ascii(Integer.toString(i)));
                frame.content(new Buffer(messageBody));
                connection.send(frame);

                if (i % 10000 == 0) {
                    log.info(String.format("## '%s' '%s' send '%s'", this.getName(), this.destination, i));
                }
                this.sentMessages = i;
            }
            frame = new StompFrame(COMMIT_TRANSACTION);
            frame.addHeader(TRANSACTION, transactionId);
            connection.send(frame);
            Thread.sleep(100);
            connection.send(new StompFrame(DISCONNECT));
        } catch (Exception e) {
            log.error(String.format("Exception in producer '%s' : %s", this.getName(), e.getMessage()), e);
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
        log.info(String.format("Stopping producer for '%s' - '%s' sent messages %s", this.getName(), this.destination, getSentMessages()));
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
