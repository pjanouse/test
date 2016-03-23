package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.Client;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;
import java.util.*;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author eduda
 */
public class ReceiverClientAck extends Client {

    private static final Logger logger = Logger.getLogger(ReceiverClientAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String queueNameJndi = "jms/queue/testQueue0";
    private long receiveTimeOut;
    private int ackAfter;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private List<Message> listOfReceivedMessagesToBeAcked = new ArrayList<Message>();
    private Exception exception = null;
    private List<Message> listOfReceivedInDoubtMessages = new ArrayList<Message>();


    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container container to which to connect
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
    public ReceiverClientAck(Container container, String queueJndiName , long receiveTimeOut,int ackAfter, int maxRetries){
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.ackAfter = ackAfter;
        this.maxRetries = maxRetries;
        setTimeout(0); // set receive timeout to 0 to read with max speed
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

                    listOfReceivedMessagesToBeAcked.add(message);

                    counter++;

                    logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
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

                logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                        + ". Received NULL - number of received messages: " + counter);

                if (messageVerifier != null) {
                    messageVerifier.addReceivedMessages(listOfReceivedMessages);
                }
            }

        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            ex.printStackTrace();
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + hostname, ex);

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
     * @throws javax.jms.JMSException
     */
    public boolean acknowledgeMessage(Message message) throws Exception {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();
        boolean isAckSuccessful = true;

        try {

            checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts();

            message.acknowledge();

            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", message-counter: " + message.getStringProperty("counter")
                    + ", messageId:" + message.getJMSMessageID() + " ACKNOWLEDGED");

            addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeAcked);

            logListOfAddedMessages(listOfReceivedMessagesToBeAcked);

        } catch (TransactionRolledBackException ex) {
            logger.error("TransactionRolledBackException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - counter: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);
            // all unacknowledge messges will be received again
            counter = counter - listOfReceivedMessagesToBeAcked.size();
            isAckSuccessful = false;

        } catch (JMSException ex) {

            logger.error("JMSException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);

            listOfReceivedInDoubtMessages.addAll(listOfReceivedMessagesToBeAcked);
            counter = counter - listOfReceivedMessagesToBeAcked.size();
            isAckSuccessful = false;

        } finally {
            listOfReceivedMessagesToBeAcked.clear();
        }
        return isAckSuccessful;
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
     * @return the messageVerifier
     */
    public FinalTestMessageVerifier getMessageVerifier() {
        return messageVerifier;
    }

    /**
     * @param messageVerifier the messageVerifier to set
     */
    public void setMessageVerifier(FinalTestMessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
    }

    /**
     * @return the listOfReceivedMessages
     */
    public List<Map<String,String>> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    /**
     * @param listOfReceivedMessages the listOfReceivedMessages to set
     */
    public void setListOfReceivedMessages(List<Map<String,String>> listOfReceivedMessages) {
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


    public int getAckAfter() {
        return ackAfter;
    }

    public void setAckAfter(int ackAfter) {
        this.ackAfter = ackAfter;
    }
    public int getCount() {
        return counter;
    }

    private void checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts() throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        // clone list of inDoubtMessages
        List<Message> listCloneOfInDoubtMessages = new ArrayList<Message>();
        for (Message m : listOfReceivedInDoubtMessages) {
            listCloneOfInDoubtMessages.add(m);
        }

        // if duplicate received then remove from the list of in doubt messages
        String inDoubtMessageDupId = null;
        String receivedMessageDupId = null;
        for (Message inDoubtMessage : listCloneOfInDoubtMessages) {
            inDoubtMessageDupId = inDoubtMessage.getStringProperty(duplicatedHeader);
            for (Message receivedMessage : listOfReceivedMessagesToBeAcked) {
                if (((receivedMessageDupId = receivedMessage.getStringProperty(duplicatedHeader)) != null) &&
                        receivedMessageDupId.equalsIgnoreCase(inDoubtMessageDupId)) {
                    logger.info("Duplicated in doubt message was received. Removing message with dup id: " + inDoubtMessageDupId
                            + " and messageId: " + inDoubtMessage.getJMSMessageID() + " from list of in doubt messages");
                    listOfReceivedInDoubtMessages.remove(inDoubtMessage);
                }
            }
        }
    }

    private void logListOfAddedMessages(List<Message> listOfReceivedMessagesToBeCommited) throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        StringBuilder stringBuilder = new StringBuilder();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader) + ", \n");
        }
        logger.info("New messages added to list of received messages: \n" + stringBuilder.toString());
    }

}
