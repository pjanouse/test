package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.Receiver;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.TransactionRolledBackException;
import javax.jms.TransactionRolledBackRuntimeException;

public class Receiver20 extends Receiver {

    private static final Logger logger = Logger.getLogger(Receiver20.class);

    public Receiver20(Container container, String destinationNameJndi, long receiveTimeOut, int maxRetries) {
        super(container, destinationNameJndi, receiveTimeOut, maxRetries);
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

        Message msg = null;
        int numberOfRetries = 0;

        // receive message with retry
        while (running.get() && numberOfRetries < maxRetries) {
            try {

                msg = consumer.receive(receiveTimeout);
                if (msg != null) {
                    msg = cleanMessage(msg);
                }
                return msg;

            } catch (JMSRuntimeException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (counter + 1), ex);
            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (counter + 1), ex);
            }
        }

        if (running.get()) {
            throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + hostname);
        } else {
            throw new Exception("Receiver was stopped.");
        }
    }

    /**
     * Try to commit session a message.
     *
     * @param jmsContext jmsContext
     * @throws javax.jms.JMSException
     */
    public boolean commitSession(JMSContext jmsContext) throws Exception {

        boolean commitSuccessful = true;
        try {
            checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts();

            jmsContext.commit();

            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + " COMMIT");

            addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);

            logListOfAddedMessages(listOfReceivedMessagesToBeCommited);

        } catch (TransactionRolledBackRuntimeException ex) {
            logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                    + ". Received message - count: " + counter + ", retrying receive", ex);
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            commitSuccessful = false;

        } catch (JMSRuntimeException ex) {
            logger.error(" Receiver - COMMIT FAILED - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                    + ". Received message - count: " + counter + ", retrying receive", ex);
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            // if JMSException is thrown then it's not clear if messages were committed or not
            // we add them to the list of in doubt messages and if duplicates will be received in next
            // receive phase then we remove those messages from this list (compared by DUP ID)
            // if not duplicates will be received then we add this list to the list of received messages
            // when NULL is returned from consumer.receive(timeout)
            listOfReceivedInDoubtMessages.addAll(listOfReceivedMessagesToBeCommited);
            logInDoubtMessages();
            commitSuccessful = false;

        } finally {
            listOfReceivedMessagesToBeCommited.clear();
        }
        return commitSuccessful;
    }

    /**
     * Try to acknowledge a message.
     *
     * @param message message to be acknowledged
     * @throws javax.jms.JMSException
     */
    protected boolean acknowledgeMessage(Message message) throws Exception {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();
        boolean isAckSuccessful = true;

        try {

            checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts();

            message.acknowledge();

            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", message-counter: " + message.getStringProperty("counter")
                    + ", messageId:" + message.getJMSMessageID() + " ACKNOWLEDGED");

            addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);

            logListOfAddedMessages(listOfReceivedMessagesToBeCommited);

        } catch (TransactionRolledBackException ex) {
            logger.error("TransactionRolledBackException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - counter: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);
            // all unacknowledge messges will be received again
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            isAckSuccessful = false;

        } catch (JMSException ex) {

            logger.error("JMSException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);

            listOfReceivedInDoubtMessages.addAll(listOfReceivedMessagesToBeCommited);
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            isAckSuccessful = false;

        } finally {
            listOfReceivedMessagesToBeCommited.clear();
        }
        return isAckSuccessful;
    }

}
