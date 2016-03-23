package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.Client;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class SoakReceiverClientAck extends Client {

    private static final Logger logger = Logger.getLogger(SoakReceiverClientAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String queueNameJndi = "jms/queue/testQueue0";
    private long receiveTimeOut;
    private int ackAfter;
    private List<String> listOfReceivedMessages = new ArrayList<String>();
    private Exception exception = null;

    /**
     * @param container         EAP container
     * @param queueJndiName     jndi name of the queue
     * @param receiveTimeOut    how long to wait to receive message
     * @param ackAfter          send ack after how many messages
     * @param maxRetries        how many times to retry receive before giving up
     */
    public SoakReceiverClientAck(Container container, String queueJndiName, long receiveTimeOut,
                                 int ackAfter, int maxRetries) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.ackAfter = ackAfter;
        this.maxRetries = maxRetries;
    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf;
        Queue queue;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                jmsContext.start();

                queue = (Queue) context.lookup(queueNameJndi);

                JMSConsumer receiver = jmsContext.createConsumer(queue);

                Message message;

                Message lastMessage = null;

                while ((message = receiveMessage(receiver)) != null) {

                    counter++;
                    if (counter % ackAfter == 0) { // try to ack message
                        acknowledgeMessage(message);
                    } else { // i don't want to ack now
                        logger.debug("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                                + ". Received message - count: "
                                + counter + ", message-counter: " + message.getStringProperty("counter")
                                + ", messageId:" + message.getJMSMessageID());
                    }
                    listOfReceivedMessages.add(message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                    // hold information about last message so we can ack it when null is received = queue empty
                    lastMessage = message;
                }

                if (lastMessage != null) {
                    acknowledgeMessage(lastMessage);
                }

                logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                        + ". Received NULL - number of received messages: " + counter);
            }


        } catch (JMSRuntimeException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
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

    /**
     * Try to acknowledge a message.
     *
     * @param message message to be acknowledged
     * @throws JMSException
     */
    public void acknowledgeMessage(Message message) throws JMSException {
        try {
            logger.info("Try to ack message: " + message);
            message.acknowledge();
            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", message-counter: " + message.getStringProperty("counter")
                    + ", messageId:" + message.getJMSMessageID() + " SENT ACKNOWLEDGE");

        } catch (Exception ex) {
            logger.error("Exception thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", messageId:" + message.getJMSMessageID());
            ex.printStackTrace();
            setCount(counter - ackAfter);
        }
    }

    /**
     * Tries to receive message from server in specified timeout. If server crashes
     * then it retries for maxRetries. If even then fails to receive which means that
     * consumer.receiver(timeout) throw JMSException maxRetries's times then throw Exception above.
     *
     * @param consumer consumer message consumer
     * @return message or null
     * @throws Exception when maxRetries was reached
     */
    public Message receiveMessage(JMSConsumer consumer) throws Exception {

        Message msg;
        int numberOfRetries = 0;

        // receive message with retry
        while (numberOfRetries < maxRetries) {

            try {

                msg = consumer.receive(receiveTimeOut);
                return msg;

            } catch (JMSRuntimeException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (counter + 1));
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + hostname);
    }

    /**
     * @return the maxRetries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * @param maxRetries the maxRetries to set
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the queueNameJndi
     */
    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    /**
     * @param queueNameJndi the queueNameJndi to set
     */
    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * @return the listOfReceivedMessages
     */
    public List<String> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    /**
     * @param listOfReceivedMessages the listOfReceivedMessages to set
     */
    public void setListOfReceivedMessages(List<String> listOfReceivedMessages) {
        this.listOfReceivedMessages = listOfReceivedMessages;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * @return the count
     */
    public int getCount() {
        return counter;
    }

    /**
     * @param count the count to set
     */
    public void setCount(int count) {
        this.counter = count;
    }

    public long getReceiveTimeOut() {
        return receiveTimeOut;
    }

    public void setReceiveTimeOut(long receiveTimeOut) {
        this.receiveTimeOut = receiveTimeOut;
    }
}
