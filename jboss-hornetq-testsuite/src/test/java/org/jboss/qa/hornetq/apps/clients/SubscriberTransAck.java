//TODO write duplicate detection
// problem when there is jmsexception during commit - was it successful?
// so when jmsexception retry operation receive
// when no message is duplicate (_HQ_DUPL_ID)
//
package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
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
    private int count = 0;
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


    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param hostname      hostname
     * @param port          jndi port
     * @param topicJndiName jndi name of the topic
     */
    public SubscriberTransAck(String hostname, int port, String topicJndiName, String clientId, String subscriberName) {

        this(hostname, port, topicJndiName, 30000, 10, 30, clientId, subscriberName);

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
    public SubscriberTransAck(String hostname, int port, String topicJndiName, long receiveTimeOut,
                              int commitAfter, int maxRetries, String clientId, String subscriberName) {
        this(EAP6_CONTAINER, hostname, port, topicJndiName, receiveTimeOut, commitAfter, maxRetries, clientId, subscriberName);
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
    }

    @Override
    public void run() {

        try {

            if (cf == null) {
                subscribe();
            }

            Message message = null;

            while ((message = receiveMessage(subscriber)) != null) {

                listOfReceivedMessagesToBeCommited.add(message);

                count++;

                if (count % commitAfter == 0) { // try to ack message
                    commitSession(session);
                    listOfReceivedMessagesToBeCommited.clear();
                } else { // i don't want to ack now
                    logger.debug("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
                            + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
                }
            }

            commitSession(session);

            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
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

    /**
     * Try to acknowledge a message.
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
                        && setOfReceivedMessagesWithPossibleDuplicates.size() > 0)    {
                    if (areThereDuplicates())  {
                        // decrease counter
                        // add just new messages
//                        listOfReceivedMessagesToBeCommited.clear();
                        count = count - setOfReceivedMessagesWithPossibleDuplicates.size();
                    } else {
                        //listOfReceivedMessages.addAll(setOfReceivedMessagesWithPossibleDuplicates);
                        //logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + ". Adding messages: " +
                         //   setOfReceivedMessagesWithPossibleDuplicates.toString());
                        logger.info("No duplicates were found after JMSException/TransactionRollbackException.");
                    }
                    setOfReceivedMessagesWithPossibleDuplicates.clear();
                }

                session.commit();

                logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + ". Received message - count: "
                        + count + " SENT COMMIT");

                addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);

                return;

            } catch (TransactionRolledBackException ex) {
                logger.error(" Subscriber - name: " + getSubscriberName() + " - - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Subscriber for node: " + hostname
                        + ". Received message - count: " + count + ", retrying receive", ex);
                // all unacknowledge messges will be received again
                count = count - listOfReceivedMessagesToBeCommited.size();

                return;

            } catch (JMSException ex) {

                setOfReceivedMessagesWithPossibleDuplicates.addAll(listOfReceivedMessagesToBeCommited);

                logger.error(" Subscriber - name: " + getSubscriberName() + " -- JMSException thrown during commit: " + ex.getMessage() + ". Subscriber for node: " + hostname
                        + ". Received message - count: " + count + ", COMMIT will be tried again - TRY:" + numberOfRetries, ex);
                numberOfRetries++;
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for Subscriber - name: " + getSubscriberName() + " - for node: " + hostname + " during commit");
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeCommited)    {
            setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"));
        }

        for (Message m : setOfReceivedMessagesWithPossibleDuplicates)   {
            if (!setOfReceivedMessages.add(m.getStringProperty("_HQ_DUPL_ID"))) {
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
                    msg = cleanMessage(msg);
                }
                return msg;

            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (count + 1), ex);
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

        SubscriberTransAck subscriber = new SubscriberTransAck("192.168.1.1", 4447, "jms/topic/testTopic0", 10000, 100, 10,
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
        return count;
    }
}
