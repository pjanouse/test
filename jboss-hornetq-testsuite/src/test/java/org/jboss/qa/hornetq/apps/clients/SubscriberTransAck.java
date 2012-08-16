package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    private List<Message> listOfReceivedMessages = new ArrayList<Message>();
    ;
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
                } else { // i don't want to ack now
                    logger.info("Subscriber for node: " + getHostname() + " and topic: " + topicNameJndi
                            + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
                }
            }

            commitSession(session);

            logger.info("Subscriber for node: " + getHostname() + " and topic: " + topicNameJndi
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
        try {

            session.commit();

            logger.info("Subscriber for node: " + getHostname() + ". Received message - count: "
                    + count + " SENT COMMIT");

            listOfReceivedMessages.addAll(listOfReceivedMessagesToBeCommited);

        } catch (TransactionRolledBackException ex) {
            logger.error(" Subscriber - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage()
                    + ". Subscriber for node: " + hostname
                    + ". Received message - count: " + count + ", retrying receive", ex);
            // all uncommited messges will be received again
            count = count - listOfReceivedMessagesToBeCommited.size();

        } catch (JMSException ex) {
            throw new Exception("Subscriber got JMSException during commit and has underterministic result."
                    + " Node: " + hostname +
                    ". Received message - count: " + count + ", messages will be received again. Supposed to be commited.", ex);

        } finally {
            listOfReceivedMessagesToBeCommited.clear();
        }


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
                return msg;

            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (count + 1), ex);
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for subscriber for node: " + hostname);
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
    public List<Message> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    /**
     * @param listOfReceivedMessages the listOfReceivedMessages to set
     */
    public void setListOfReceivedMessages(List<Message> listOfReceivedMessages) {
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

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = cf.createConnection();

            conn.setClientID(clientId);

            conn.start();

            topic = (Topic) context.lookup(getTopicNameJndi());

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            subscriber = session.createDurableSubscriber(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        } finally {
            if (conn != null)   {
                try {
                    conn.close();
                } catch (JMSException ignored) {}
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {

        SubscriberTransAck subscriber = new SubscriberTransAck("192.168.1.1", 4447, "jms/topic/testTopic0", 10000, 100, 10,
                "testClientId", "testSubscriber");

        subscriber.start();

        subscriber.join();
    }
}
