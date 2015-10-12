//TODO write duplicate detection
// problem when there is jmsexception during commit - was it successful?
// so when jmsexception retry operation receive
// when no message is duplicate (_HQ_DUPL_ID)
//
package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.naming.Context;
import java.util.*;

/**
 * Simple subscriber with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class SubscriberTransAck extends Client {

    private static final Logger logger = Logger.getLogger(SubscriberTransAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String topicNameJndi;
    private long receiveTimeOut;
    private int commitAfter;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private List<Message> listOfReceivedMessagesToBeCommited = new ArrayList<Message>();
    private int counter = 0;
    private Exception exception = null;
    private String subscriberName;
    private String clientId;
    private Context context;
    private ConnectionFactory cf;
    private Connection conn;
    private Session session;
    private Topic topic;
    private TopicSubscriber subscriber = null;
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicates = new HashSet<Message>();
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection = new HashSet<Message>();


    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param hostname      hostname
     * @param port          jndi port
     * @param topicJndiName jndi name of the topic
     */
    public SubscriberTransAck(String hostname, int port, String topicJndiName, String clientId, String subscriberName) {

        this(hostname, port, topicJndiName, 60000, 10, 30, clientId, subscriberName);

    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicJndiName  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    @Deprecated
    public SubscriberTransAck(String hostname, int port, String topicJndiName, long receiveTimeOut,
                              int commitAfter, int maxRetries, String clientId, String subscriberName) {
        this(EAP6_CONTAINER, hostname, port, topicJndiName, receiveTimeOut, commitAfter, maxRetries, clientId, subscriberName);
    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container container to connect to
     * @param topicJndiName  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public SubscriberTransAck(Container container, String topicJndiName, long receiveTimeOut,
                              int commitAfter, int maxRetries, String clientId, String subscriberName) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.topicNameJndi = topicJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.commitAfter = commitAfter;
        this.maxRetries = maxRetries;
        this.clientId = clientId;
        this.subscriberName = subscriberName;

        setTimeout(0); // set receive timeout to 0 to read with max speed
    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container     container
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicJndiName  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    @Deprecated
    public SubscriberTransAck(String container, String hostname, int port, String topicJndiName, long receiveTimeOut,
                              int commitAfter, int maxRetries, String clientId, String subscriberName) {

        super(container);
        this.hostname = hostname;
        this.port = port;
        this.topicNameJndi = topicJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.commitAfter = commitAfter;
        this.maxRetries = maxRetries;
        this.clientId = clientId;
        this.subscriberName = subscriberName;

        setTimeout(0); // set receive timeout to 0 to read with max speed
    }

    @Override
    public void run() {

        try {

            if (cf == null) {
                subscribe();
            }

            Message message = null;

            while ((message = receiveMessage(subscriber)) != null) {
                Thread.sleep(getTimeout());

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;

                logger.debug("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
                        + ". Received message - counter: "
                        + counter + ", messageId:" + message.getJMSMessageID()
                        + ", dupId: " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

                if (counter % commitAfter == 0) { // try to ack message
                    commitSession(session);
                }
            }

            commitSession(session);

            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
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
            throw new RuntimeException("Fatal exception was thrown in subscriber. Subscriber for node: " + getHostname());
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
                } catch (Exception e) {
                }
            }
        }
    }

//    /**
//     * Try to acknowledge a message.
//     *
//     * @param session session
//     * @throws JMSException
//     */
//    public void commitSession(Session session) throws Exception {
//
//        int numberOfRetries = 0;
//
//        while (numberOfRetries < maxRetries) {
//            try {
//
//                // if dups_id is used then check if we got duplicates after last failed ack
//                if (numberOfRetries == 0 && listOfReceivedMessages.size() > 0 && listOfReceivedMessages.get(0).get("_HQ_DUPL_ID") != null
//                        && setOfReceivedMessagesWithPossibleDuplicates.size() > 0) {
//                    if (areThereDuplicates()) {
//                        // decrease counter
//                        // add just new messages
//                        counter = counter - setOfReceivedMessagesWithPossibleDuplicates.size();
//
//                    } else {
//                        logger.info("No duplicates were found after JMSException/TransactionRollbackException - add messages from previous commit");
//                        addSetOfMessages(listOfReceivedMessages, setOfReceivedMessagesWithPossibleDuplicates);
//                        StringBuilder stringBuilder = new StringBuilder();
//                        for (Message m : listOfReceivedMessagesToBeCommited) {
//                            stringBuilder.append(m.getJMSMessageID());
//                        }
//                        logger.debug("Adding messages: " + stringBuilder.toString());
//                    }
//                    setOfReceivedMessagesWithPossibleDuplicates.clear();
//                }
//
//                session.commit();
//
//                logger.info("Receiver for node: " + hostname + ". Received message - counter: "
//                        + counter + " SENT COMMIT");
//
//                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
//                StringBuilder stringBuilder = new StringBuilder();
//                for (Message m : listOfReceivedMessagesToBeCommited) {
//                    stringBuilder.append(m.getJMSMessageID());
//                }
//                logger.debug("Adding messages: " + stringBuilder.toString());
//
//                return;
//
//            } catch (TransactionRolledBackException ex) {
//                logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
//                        + ". Received message - counter: " + counter + ", retrying receive", ex);
//                // all unacknowledge messges will be received again
//                ex.printStackTrace();
//                counter = counter - listOfReceivedMessagesToBeCommited.size();
//                setOfReceivedMessagesWithPossibleDuplicates.clear();
//
//                return;
//
//            } catch (JMSException ex) {
//                // we need to know which messages we got in the first try because we need to detect possible duplicates
//                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);
//
//                logger.error(" Receiver - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
//                        + ". Received message - counter: " + counter + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
//                ex.printStackTrace();
//                numberOfRetries++;
//            } finally {
//                // we clear this list because next time we get new or duplicated messages and we compare it with set possible duplicates
//                listOfReceivedMessagesToBeCommited.clear();
//            }
//        }
//
//        throw new Exception("FAILURE - MaxRetry reached for Subscriber - name: " + getSubscriberName() + " - for node: " + hostname + " during commit");
//    }
    /**
     * Try to commit session a message.
     *
     * @param session session
     * @throws javax.jms.JMSException
     */
    public void commitSession(Session session) throws Exception {

        int numberOfRetries = 0;

        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        while (numberOfRetries < maxRetries) {
            try {

//                // if dups_id is used then check if we got duplicates after last failed ack
//                if (numberOfRetries == 0 && listOfReceivedMessages.size() > 0 && listOfReceivedMessages.get(0).get("_HQ_DUPL_ID") != null
//                        && setOfReceivedMessagesWithPossibleDuplicates.size() > 0) {
//                    if (areThereDuplicates()) {
//                        // decrease counter
//                        // add just new messages
//                        counter = counter - setOfReceivedMessagesWithPossibleDuplicates.size();
//
//                    } else {
//                        // this actually does not mean that messages were successfully committed
//                        // in previous receive cycle
//                        // we just can receive it some time later but no one knows when
//                        // so add this message now and when we receive it later
//                        // we throw it away based on its dupId
//                        logger.info("No duplicates were found after JMSException/TransactionRollbackException - add messages from previous commit");
//                        addSetOfMessages(listOfReceivedMessages, setOfReceivedMessagesWithPossibleDuplicates);
//                        StringBuilder stringBuilder = new StringBuilder();
//                        for (Message m : listOfReceivedMessagesToBeCommited) {
//                            stringBuilder.append(m.getJMSMessageID());
//                        }
//                        logger.debug("Adding messages: " + stringBuilder.toString());
//                        setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection.addAll(listOfReceivedMessagesToBeCommited);
//                    }
//                    setOfReceivedMessagesWithPossibleDuplicates.clear();
//                }

                areThereDuplicatesInLaterDetection();

                session.commit();

                logger.info("Receiver for node: " + hostname + ". Received message - count: "
                        + counter + " SENT COMMIT");

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader + "\n"));
                }
                logger.debug("Adding messages: " + stringBuilder.toString());

                return;

            } catch (TransactionRolledBackException ex) {
                logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                        + ". Received message - count: " + counter + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                ex.printStackTrace();
                counter = counter - listOfReceivedMessagesToBeCommited.size();
//                setOfReceivedMessagesWithPossibleDuplicates.clear();

                return;

            } catch (JMSException ex) {
                // we need to know which messages we got in the first try because we need to detect possible duplicates
//                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);
                setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection.addAll(listOfReceivedMessagesToBeCommited);

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);
                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfReceivedMessagesToBeCommited) {
                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader + "\n"));
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

    private boolean areThereDuplicatesInLaterDetection() throws JMSException {
        boolean isDup = false;
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            setOfReceivedMessages.add(m.getStringProperty(duplicatedHeader));
        }
        StringBuilder foundDuplicates = new StringBuilder();
        for (Message m : setOfReceivedMessagesWithPossibleDuplicatesForLaterDuplicateDetection) {
            if (!setOfReceivedMessages.add(m.getStringProperty(duplicatedHeader))) {
                foundDuplicates.append(m.getJMSMessageID());
                counter -= 1;
                // remove this duplicate from the list
                List<Message> iterationList = new ArrayList<Message>(listOfReceivedMessagesToBeCommited);
                for (Message receivedMessage : iterationList)    {
                    if (receivedMessage.getStringProperty(duplicatedHeader).equals(m.getStringProperty(duplicatedHeader))) {
                        listOfReceivedMessagesToBeCommited.remove(receivedMessage);
                    }
                }

                isDup = true;
            }
        }
        if (!"".equals(foundDuplicates.toString())) {
            logger.info("Later detection found duplicates, will be discarded: " + foundDuplicates.toString());
            logger.info("List of messages to be added to list: " + listOfReceivedMessagesToBeCommited.toString());
        }
        return isDup;
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited)    {
            setOfReceivedMessages.add(m.getStringProperty(duplicatedHeader));
        }

        for (Message m : setOfReceivedMessagesWithPossibleDuplicates)   {
            if (!setOfReceivedMessages.add(m.getStringProperty(duplicatedHeader))) {
                isDup=true;
            }
        }

        if (isDup)  {
            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + hostname + " detected duplicates after failover.");
        }

        return isDup;
    }

    /**
     * Tries to receive message from server in specified timeout. If server crashes
     * then it retries for maxRetries. If even then fails to receive which means that
     * consumer.subscriber(timeout) throw JMSException maxRetries's times then throw Exception above.
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
                    logger.debug("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
                            + ". Received message - counter: "
                            + counter + ", messageId:" + msg.getJMSMessageID()
                            + ", dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                    msg = cleanMessage(msg);
                }
                return msg;

            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with counter: " + (counter + 1), ex);
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for subscriber - name: " + getSubscriberName() + " - for node: " + hostname);
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
     * @return the topicNameJndi
     */
    public String getTopicNameJndi() {
        return topicNameJndi;
    }

    /**
     * @param topicNameJndi the topicNameJndi to set
     */
    public void setTopicNameJndi(String topicNameJndi) {
        this.topicNameJndi = topicNameJndi;
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


    /**
     * @return the subscriberName
     */
    public String getSubscriberName() {
        return subscriberName;
    }

    /**
     * @param subscriberName the subscriberName to set
     */
    public void setSubscriberName(String subscriberName) {
        this.subscriberName = subscriberName;
    }

    public void subscribe() {

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = cf.createConnection();

            conn.setClientID(clientId);

            conn.start();

            topic = (Topic) context.lookup(getTopicNameJndi());

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            subscriber = session.createDurableSubscriber(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

    public static void main(String[] args) throws InterruptedException {

        SubscriberTransAck subscriber = new SubscriberTransAck("192.168.1.1", getJNDIPort(), "jms/topic/testTopic0", 10000, 100, 10,
                "testClientId", "testSubscriber");

        subscriber.start();

        subscriber.join();
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
