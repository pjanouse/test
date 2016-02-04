package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.Client;

import javax.jms.*;
import javax.naming.Context;
import java.util.*;

/**
 * Created by eduda on 4.8.2015.
 */
public class ReceiverTransAck extends Client {

    private static final Logger logger = Logger.getLogger(ReceiverTransAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String queueNameJndi = "jms/queue/testQueue0";
    private long receiveTimeOut;
    private int commitAfter;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private List<Message> listOfReceivedMessagesToBeCommited = new ArrayList<Message>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicates = new HashSet<Message>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection = new HashSet<Message>();

    private Exception exception = null;

    private int counter = 0;

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
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.commitAfter = commitAfter;
        this.maxRetries = maxRetries;

        setTimeout(0); // set receive timeout to 0 to read with max speed


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
                queue = (javax.jms.Queue) context.lookup(queueNameJndi);
                JMSConsumer receiver = jmsContext.createConsumer(queue);
                Message message = null;

                logger.debug("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                        + " was started.");

                while ((message = receiveMessage(receiver)) != null) {
                    Thread.sleep(getTimeout());

                    listOfReceivedMessagesToBeCommited.add(message);

                    counter++;

                    logger.debug("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                            + ". Received message - count: "
                            + counter + ", messageId:" + message.getJMSMessageID()
                            + " dupId: " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

                    if (counter % commitAfter == 0) { // try to ack message

                        commitJMSContext(jmsContext);

                    }
                }

                commitJMSContext(jmsContext);

                logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                        + ". Received NULL - number of received messages: " + counter);

                if (messageVerifier != null) {
                    messageVerifier.addReceivedMessages(listOfReceivedMessages);
                }
            }

        } catch (JMSException ex) {
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
     * Try to commit session a message.
     *
     * @param jmsContext session
     * @throws javax.jms.JMSException
     */
    public void commitJMSContext(JMSContext jmsContext) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                areThereDuplicatesInLaterDetection();

                jmsContext.commit();

                logger.info("Receiver for node: " + hostname + ". Received message - count: "
                        + counter + " SENT COMMIT");

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(
                            m.getStringProperty(jmsImplementation.getDuplicatedHeader() + ", \n"));
                }
                logger.debug("Adding messages: " + stringBuilder.toString());

                return;

            } catch (TransactionRolledBackRuntimeException ex) {
                logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                        + ". Received message - count: " + counter + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                counter = counter - listOfReceivedMessagesToBeCommited.size();
                setOfReceivedMessagesWithPossibleDuplicates.clear();
                listOfReceivedMessagesToBeCommited.clear();

                return;

            } catch (JMSRuntimeException ex) {
                // we need to know which messages we got in the first try because we need to detect possible duplicates
//                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);
                setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection.addAll(listOfReceivedMessagesToBeCommited);

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(
                            m.getStringProperty(jmsImplementation.getDuplicatedHeader() + "\n"));
                }
                logger.debug("Adding messages: " + stringBuilder.toString());

                logger.error(" Receiver - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                        + ". Received message - count: " + counter + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
                ex.printStackTrace();
                numberOfRetries++;
            } finally {
                // we clear this list because next time we get new or duplicated messages and we compare it with set possible duplicates
                listOfReceivedMessagesToBeCommited.clear();
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + hostname + " during acknowledge");
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicates) {
            if (!setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()))) {
                foundDuplicates.append(m.getJMSMessageID());
                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            logger.info("Duplicates detected: " + foundDuplicates.toString());
        }
        return isDup;
    }

    private boolean areThereDuplicatesInLaterDetection() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection) {
            if (!setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()))) {
                foundDuplicates.append(m.getJMSMessageID());
                counter -= 1;
                // remove this duplicate from the list
                List<Message> iterationList = new ArrayList<Message>(listOfReceivedMessagesToBeCommited);
                for (Message receivedMessage : iterationList)    {
                    if (receivedMessage.getStringProperty(jmsImplementation.getDuplicatedHeader()).equals(
                            m.getStringProperty(jmsImplementation.getDuplicatedHeader()))) {
                        listOfReceivedMessagesToBeCommited.remove(receivedMessage);
                    }
                }

                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            logger.info("Later detection found duplicates: " + foundDuplicates.toString());
            logger.info("List of messages to be added to list: " + listOfReceivedMessagesToBeCommited.toString());
        }
        return isDup;
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

    public int getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

    public int getCount() {
        return counter;
    }

    public long getReceiveTimeOut() {
        return receiveTimeOut;
    }

    public void setReceiveTimeOut(long receiveTimeOut) {
        this.receiveTimeOut = receiveTimeOut;
    }

}
