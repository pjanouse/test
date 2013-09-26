package org.jboss.qa.tools.jms;


import java.util.concurrent.TimeUnit;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import org.apache.log4j.Logger;


/**
 * Helper methods for sending and receiving messages in JMS clients.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class ClientUtils {

    private static final Logger LOG = Logger.getLogger(ClientUtils.class);

    private static final int DEFAULT_MAX_RETRIES = 30;

    private static final long DEFAULT_RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);


    public static String sendMessage(final MessageProducer producer, final Message msg)
            throws JMSException {

        return sendMessage(producer, msg, DEFAULT_MAX_RETRIES);
    }


    public static String sendMessage(final MessageProducer producer, final Message msg,
            final int maxRetries) throws JMSException {

        int numberOfRetries = 0;
        Integer msgCounter;
        try {
            msgCounter = msg.getIntProperty("counter");
        } catch (JMSException ex) {
            msgCounter = null;
        }

        while (numberOfRetries < maxRetries) {
            try {
                producer.send(msg);

                if (msgCounter == null) {
                    LOG.info("SENT message with id " + msg.getJMSMessageID());
                } else {
                    LOG.info("SENT message with counter " + msgCounter
                            + " and id " + msg.getJMSMessageID());
                }

                if (msg.getStringProperty("_HQ_DUPL_ID") != null) {
                    return msg.getStringProperty("_HQ_DUPL_ID");
                } else {
                    return null;
                }
            } catch (JMSException ex) {
                if (msgCounter == null) {
                    LOG.info("SEND RETRY - Sent message with id " + msg.getJMSMessageID());
                } else {
                    LOG.info("SEND RETRY - Sent message with counter " + msgCounter
                            + " and id " + msg.getJMSMessageID());
                }
                numberOfRetries++;
            }
        }

        // this is an error - here we should never be because max retrie expired
        if (msgCounter == null) {
            throw new JMSException("FAILURE - MaxRetry reached for message with id"
                    + msg.getJMSMessageID());
        } else {
            throw new JMSException("FAILURE - MaxRetry reached for message counter " + msgCounter
                    + " and id " + msg.getJMSMessageID());
        }
    }


    public static Message receiveMessage(final MessageConsumer consumer, final int counter)
            throws JMSException {

        return receiveMessage(consumer, counter, DEFAULT_MAX_RETRIES, DEFAULT_RECEIVE_TIMEOUT);
    }


    public static Message receiveMessage(final MessageConsumer consumer, final int counter,
            final int maxRetries, final long timeout) throws JMSException {

        Message msg;
        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                msg = consumer.receive(timeout);
                return msg;
            } catch (JMSException ex) {
                numberOfRetries++;
                LOG.error("RETRY receive for message with counter " + counter);
            }
        }
        throw new JMSException("FAILURE - MaxRetry reached for message with counter " + counter);
    }

}
