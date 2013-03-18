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
    private int count = 0;
    private Exception exception = null;
    private boolean possibleDuplicates = false;
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicates = new HashSet<Message>();


    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param hostname      hostname
     * @param port          jndi port
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverClientAck(String hostname, int port, String queueJndiName) {

        this(EAP6_CONTAINER, hostname, port, queueJndiName, 30000, 10, 30);

    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param container     container
     * @param hostname      hostname
     * @param port          jndi port
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverClientAck(String container, String hostname, int port, String queueJndiName) {

        this(container, hostname, port, queueJndiName, 30000, 10, 30);

    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param ackAfter       send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverClientAck(String hostname, int port, String queueJndiName, long receiveTimeOut,
                             int ackAfter, int maxRetries) {
        this(EAP6_CONTAINER, hostname, port, queueJndiName, receiveTimeOut, ackAfter, maxRetries);
    }

    /**
     * Creates a receiver to queue with client acknowledge.
     *
     * @param container      container
     * @param hostname       hostname
     * @param port           jndi port
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param ackAfter       send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverClientAck(String container, String hostname, int port, String queueJndiName, long receiveTimeOut,
                             int ackAfter, int maxRetries) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.ackAfter = ackAfter;
        this.maxRetries = maxRetries;

    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf;
        Connection conn = null;
        Session session;
        Queue queue;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueNameJndi);

            session = conn.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message;

            Message lastMessage = null;

            while ((message = receiveMessage(receiver)) != null) {

                listOfReceivedMessagesToBeAcked.add(message);

                count++;

                if (count % ackAfter == 0) { // try to ack message
                    acknowledgeMessage(message);

                    listOfReceivedMessagesToBeAcked.clear();

                } else { // i don't want to ack now
                    logger.debug("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                            + ". Received message - count: "
                            + count + ", message-counter: " + message.getStringProperty("counter")
                            + ", messageId:" + message.getJMSMessageID()
                            + ((message.getStringProperty("_HQ_DUPL_ID") != null) ? ", _HQ_DUPL_ID=" + message.getStringProperty("_HQ_DUPL_ID") :""));
                }

                // hold information about last message so we can ack it when null is received = queue empty
                lastMessage = message;
            }

            if (lastMessage != null) {
                acknowledgeMessage(lastMessage);
            }

            logger.info("Receiver for node: " + hostname + " and queue: " + queueNameJndi
                    + ". Received NULL - number of received messages: " + count);

            if (messageVerifier != null) {
                messageVerifier.addReceivedMessages(listOfReceivedMessages);
            }

        } catch (JMSException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            ex.printStackTrace();
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + hostname, ex);

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
     * Try to acknowledge a message.
     *
     * @param message message to be acknowledged
     * @throws JMSException
     */
    public void acknowledgeMessage(Message message) throws Exception {

        int numberOfRetries = 0;


        while (numberOfRetries < maxRetries) {
            try {
                // if dups_id is used then check if we got duplicates after last failed ack
                if (numberOfRetries == 0 && message.getStringProperty("_HQ_DUPL_ID") != null && setOfReceivedMessagesWithPossibleDuplicates.size() > 0)    {
                    if (areThereDuplicates())  {
                        // decrease counter
                        // add just new messages
//                        listOfReceivedMessagesToBeAcked.clear();
                        count = count - setOfReceivedMessagesWithPossibleDuplicates.size();

                    } else {
                        addSetOfMessages(listOfReceivedMessages, setOfReceivedMessagesWithPossibleDuplicates);
                    }
                    setOfReceivedMessagesWithPossibleDuplicates.clear();
                }

                message.acknowledge();

                logger.info("Receiver for node: " + hostname + ". Received message - count: "
                        + count + ", message-counter: " + message.getStringProperty("counter")
                        + ", messageId:" + message.getJMSMessageID() + " SENT ACKNOWLEDGE");

                if (numberOfRetries == 0)    {
                    addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeAcked);
                }

                return;

            } catch (TransactionRolledBackException ex) {
                logger.error("TransactionRolledBackException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                        + count + ", messageId:" + message.getJMSMessageID()
                        + ((message.getStringProperty("_HQ_DUPL_ID") != null) ? ", _HQ_DUPL_ID=" + message.getStringProperty("_HQ_DUPL_ID") :""), ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                count = count - listOfReceivedMessagesToBeAcked.size();

                return;

            } catch (JMSException ex) {
                // now it's screwed because we don't have response for sent ACK
                // next receive can have duplicates or new messages
                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeAcked);

                logger.error("JMSException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                        + count + ", messageId:" + message.getJMSMessageID()
                        + ((message.getStringProperty("_HQ_DUPL_ID") != null) ? ", _HQ_DUPL_ID=" + message.getStringProperty("_HQ_DUPL_ID") :""), ex);

                ex.printStackTrace();
                numberOfRetries++;
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + hostname + " during acknowledge");
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeAcked)    {
            setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"));
        }
        for (Message m : setOfReceivedMessagesWithPossibleDuplicates)   {
            if (!setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"))) {
                isDup=true;
            }
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

            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (count + 1));
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
        return count;
    }

    public static void main(String[] args) throws InterruptedException {

        ReceiverClientAck receiver = new ReceiverClientAck("localhost", 4447, "jms/queue/testQueue0", 1000000, 3, 100000);

        receiver.start();

        receiver.join();
    }

}
