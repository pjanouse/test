package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.Producer;

import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.TransactionRolledBackRuntimeException;

public class Producer20 extends Producer {

    private static final Logger logger = Logger.getLogger(Producer20.class);

    public Producer20(Container container, String destinationNameJndi, int messages) {
        super(container, destinationNameJndi, messages, 10000);
    }

    /**
     * Send message to server. Try send message and if succeed than return. If
     * send fails and exception is thrown it tries send again until max retry is
     * reached. Then throws new Exception.
     *
     * @param producer producer
     * @param msg message
     */
    protected void sendMessage(JMSProducer producer, Destination destination, Message msg) throws Exception {
        int numberOfRetries = 0;

        while (running.get() && numberOfRetries < maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                producer.send(destination, msg);
                counter++;
                return;

            } catch (JMSRuntimeException ex) {
                logger.error("Failed to send message - counter: " + counter, ex);
            } catch (JMSException ex) {
                logger.error("Failed to clean message - counter: " + counter, ex);
            }
        }
        if (running.get()) {
            // this is an error - here we should never be because max retrie expired
            throw new Exception("FAILURE - MaxRetry reached for producer for node: " + getHostname()
                    + ". Sent message with property count: " + counter
                    + ", messageId:" + msg.getJMSMessageID());
        } else {
            throw new Exception("Producer was stopped.");
        }
    }

    protected void commitJMSContext(JMSContext jmsContext, Destination destination, JMSProducer producer) throws Exception {
        int numberOfRetries = 0;
        while (true) {
            try {

                // try commit
                jmsContext.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackRuntimeException ex) {
                // if transaction rollback exception -> send messages again and commit
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once, this can't happen
                if (numberOfRetries > 2) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(producer, destination, m);
                }

                numberOfRetries++;

            } catch (JMSRuntimeException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                ex.printStackTrace();

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0 && ex.getCause().getMessage().contains("Duplicate message detected")) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(producer, destination, m);
                }
                numberOfRetries++;
            }
        }
    }

}
