package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class ReceiverClientAck extends Receiver11 {

    private static final Logger logger = Logger.getLogger(ReceiverClientAck.class);

    protected int ackAfter;

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container     container to which to connect
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverClientAck(Container container, String queueJndiName) {

        this(container, queueJndiName, 60000, 10, 30);

    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param container      container
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param ackAfter       send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverClientAck(Container container, String queueJndiName, long receiveTimeOut, int ackAfter, int maxRetries) {
        super(container, queueJndiName, receiveTimeOut, maxRetries);
        this.ackAfter = ackAfter;
    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf;
        Connection conn = null;
        Session session;
        Queue queue;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = getConnection(cf);

            conn.start();

            queue = (Queue) context.lookup(destinationNameJndi);

            session = conn.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message;

            Message lastMessage = null;

            String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

            boolean running = true;
            while (running) {

                message = receiveMessage(receiver);

                // in case that ack of last message fails then receive the whole message window again and ack again
                if (message == null) {
                    if (acknowledgeMessage(lastMessage)) {
                        running = false;
                    }
                    continue;
                }

                Thread.sleep(getTimeout());

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;

                logger.debug("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                        + ". Received message - count: "
                        + counter + ", message-counter: " + message.getStringProperty("counter")
                        + ", messageId:" + message.getJMSMessageID()
                        + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""));

                if (counter % ackAfter == 0) { // try to ack message
                    acknowledgeMessage(message);
                }

                // hold information about last message so we can ack it when null is received = queue empty
                lastMessage = message;
            }

            // add all in doubt messages
            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                    + ". Received NULL - number of received messages: " + counter);

            addReceivedMessages(listOfReceivedMessages);

        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + hostname, ex);

        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (JMSException ex) {
                    // ignore
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
    }

    public int getAckAfter() {
        return ackAfter;
    }

    public void setAckAfter(int ackAfter) {
        this.ackAfter = ackAfter;
    }
}
