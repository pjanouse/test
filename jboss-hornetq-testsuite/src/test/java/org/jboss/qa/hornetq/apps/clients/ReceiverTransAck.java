//TODO write duplicate detection
package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;
import java.util.*;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
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

    private Exception exception = null;

    private int counter = 0;

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param hostname      hostname
     * @param port          jndi port
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverTransAck(String hostname, int port, String queueJndiName) {

        this(EAP6_CONTAINER, hostname, port, queueJndiName, 30000, 1000, 5);

    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param container     container
     * @param hostname      hostname
     * @param port          jndi port
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverTransAck(String container, String hostname, int port, String queueJndiName) {

        this(container, hostname, port, queueJndiName, 30000, 1000, 5);

    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverTransAck(String hostname, int port, String queueJndiName, long receiveTimeOut,
                            int commitAfter, int maxRetries) {
        this(EAP6_CONTAINER, hostname, port, queueJndiName, receiveTimeOut, commitAfter, maxRetries);
    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverTransAck(String container, String hostname, int port, String queueJndiName, long receiveTimeOut,
                            int commitAfter, int maxRetries) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.commitAfter = commitAfter;
        this.maxRetries = maxRetries;

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

            conn = (Connection) cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueNameJndi);

            session = (Session) conn.createSession(true, Session.SESSION_TRANSACTED);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message = null;

            while ((message = receiveMessage(receiver)) != null) {

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;
//                Thread.sleep(12);
                if (counter % commitAfter == 0) { // try to ack message

                    commitSession(session);

                    listOfReceivedMessagesToBeCommited.clear();

                } else { // i don't want to ack now

                    logger.debug("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                            + ". Received message - count: "
                            + counter + ", messageId:" + message.getJMSMessageID());
                }
            }

            commitSession(session);

            logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                    + ". Received NULL - number of received messages: " + counter);

            if (messageVerifier != null) {
                messageVerifier.addReceivedMessages(listOfReceivedMessages);
            }

        } catch (JMSException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
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

    /**
     * Try to commit session a message.
     *
     * @param session session
     * @throws JMSException
     */
    public void commitSession(Session session) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {

                // if dups_id is used then check if we got duplicates after last failed ack
                if (numberOfRetries == 0 && listOfReceivedMessages.size() > 0 && listOfReceivedMessages.get(0).get("_HQ_DUPL_ID") != null
                        && setOfReceivedMessagesWithPossibleDuplicates.size() > 0) {
                    if (areThereDuplicates()) {
                        // decrease counter
                        // add just new messages
                        counter = counter - setOfReceivedMessagesWithPossibleDuplicates.size();
//                        listOfReceivedMessagesToBeCommited.clear();
                    } else {
                        //listOfReceivedMessages.addAll(setOfReceivedMessagesWithPossibleDuplicates);
                        logger.info("No duplicates were found after JMSException/TransactionRollbackException.");
                    }
                    setOfReceivedMessagesWithPossibleDuplicates.clear();
                }

                session.commit();

                logger.info("Receiver for node: " + hostname + ". Received message - count: "
                        + counter + " SENT COMMIT");

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append(m.getJMSMessageID());
                }
                logger.debug("Adding messages: " + stringBuilder.toString());


                return;

            } catch (TransactionRolledBackException ex) {
                logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                        + ". Received message - count: " + counter + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                counter = counter - listOfReceivedMessagesToBeCommited.size();

                return;

            } catch (JMSException ex) {

                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);

                logger.error(" Receiver - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                        + ". Received message - count: " + counter + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
                ex.printStackTrace();
                numberOfRetries++;
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + hostname + " during acknowledge");
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicates) {
            if (!setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"))) {
                foundDuplicates.append(m.getJMSMessageID());
                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            logger.info("Duplicates detected: " + foundDuplicates.toString());
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
    public Message receiveMessage(MessageConsumer consumer) throws Exception {

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

            } catch (JMSException ex) {
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


    public static void main(String[] args) throws InterruptedException {

        ReceiverTransAck receiver = new ReceiverTransAck("10.34.3.191", 4447, "jms/queue/testQueue0", 3000, 1, 10);

        receiver.start();

        receiver.join();
        logger.error("number of messagges" + receiver.getListOfReceivedMessages().size());
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
}
