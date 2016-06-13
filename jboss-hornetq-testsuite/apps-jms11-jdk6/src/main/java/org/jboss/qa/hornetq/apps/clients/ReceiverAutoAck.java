package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple receiver with auto acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class ReceiverAutoAck extends Receiver11 {

    private static final Logger logger = Logger.getLogger(ReceiverAutoAck.class);

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container     container
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverAutoAck(Container container, String queueJndiName) {

        this(container, queueJndiName, 30000, 30);

    }


    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container     container
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverAutoAck(Container container, String queueJndiName, long receiveTimeOut,
                           int maxRetries) {
        super(container, queueJndiName, receiveTimeOut, maxRetries);
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

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message = null;

            while ((message = receiveMessage(receiver)) != null) {
                Thread.sleep(getTimeout());

                addMessage(listOfReceivedMessages, message);

                counter++;

                logger.info("Receiver for node: " + getHostname() + " and queue: " + destinationNameJndi
                        + ". Received message - count: "
                        + counter + ", messageId:" + message.getJMSMessageID());
            }

            logger.info("Receiver for node: " + getHostname() + " and queue: " + destinationNameJndi
                    + ". Received NULL - number of received messages: " + counter);

            addReceivedMessages(listOfReceivedMessages);

        } catch (JMSException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + getHostname());
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
}
