//TODO write duplicate detection
package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.*;
import javax.naming.Context;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class ReceiverTransAck extends Receiver11 {

    private static final Logger logger = Logger.getLogger(ReceiverTransAck.class);

    protected int commitAfter = 10;

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container     container to which to connect
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverTransAck(Container container, String queueJndiName) {

        this(container, queueJndiName, 60000, 10000, 5);

    }

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container      container to which to connect
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverTransAck(Container container, String queueJndiName, long receiveTimeOut,
                            int commitAfter, int maxRetries) {
        super(container, queueJndiName, receiveTimeOut, maxRetries);
        this.commitAfter = commitAfter;
    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = getConnection(cf);

            conn.start();

            queue = (Queue) context.lookup(destinationNameJndi);

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiver = selector == null ? session.createConsumer(queue) : session.createConsumer(queue, selector);

            Message message = null;

            logger.info("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                    + " was started.");

            boolean running = true;
            while (running) {

                Thread.sleep(getTimeout());

                message = receiveMessage(receiver);

                // in case that commit of last message fails then receive the whole message window again and commit again
                if (message == null) {
                    if (commitSession(session)) {
                        running = false;
                    }
                    continue;
                }

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;

                logger.debug("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                        + ". Received message - count: "
                        + counter + ", messageId:" + message.getJMSMessageID()
                        + " dupId: " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

                if (counter % commitAfter == 0) {
                    commitSession(session);
                }
            }

            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            logInDoubtMessages();

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                    + ". Received NULL - number of received messages: " + listOfReceivedMessages.size() + " should be equal to message counter: " + counter);

            addReceivedMessages(listOfReceivedMessages);

        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + hostname);
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

    public int getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }
}

