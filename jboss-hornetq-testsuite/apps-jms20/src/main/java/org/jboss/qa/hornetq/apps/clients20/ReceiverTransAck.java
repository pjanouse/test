package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.naming.Context;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author eduda
 */
public class ReceiverTransAck extends Receiver20 {

    private static final Logger logger = Logger.getLogger(ReceiverTransAck.class);

    private int commitAfter;

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container container to which to connect
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverTransAck(Container container, String queueJndiName) {

        this(container, queueJndiName, 60000, 10000, 5);

    }
    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container container to which to connect
     * @param queueJndiName jndi name of the queue
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
        javax.jms.Queue queue = null;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            try (JMSContext jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED)) {
                jmsContext.start();
                queue = (javax.jms.Queue) context.lookup(destinationNameJndi);
                JMSConsumer receiver = jmsContext.createConsumer(queue);
                Message message = null;

                logger.debug("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                        + " was started.");

                boolean running = true;
                while (running) {

                    message = receiveMessage(receiver);

                    // in case that commit of last message fails then receive the whole message window again and commit again
                    if (message == null) {
                        if (commitSession(jmsContext)) {
                            running = false;
                        }
                        continue;
                    }

                    Thread.sleep(getTimeout());

                    listOfReceivedMessagesToBeCommited.add(message);

                    counter++;

                    logger.info("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                            + ". Received message - count: "
                            + counter + ", messageId:" + message.getJMSMessageID()
                            + " dupId: " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

                    if (counter % commitAfter == 0) {
                        commitSession(jmsContext);
                    }
                }

                addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

                logInDoubtMessages();

                counter = counter + listOfReceivedInDoubtMessages.size();

                logger.info("Receiver for node: " + hostname + " and queue: " + destinationNameJndi
                        + ". Received NULL - number of received messages: " + listOfReceivedMessages.size() + " should be equal to message counter: " + counter);

                addReceivedMessages(listOfReceivedMessages);
            }

        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + hostname);
        } finally {
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
