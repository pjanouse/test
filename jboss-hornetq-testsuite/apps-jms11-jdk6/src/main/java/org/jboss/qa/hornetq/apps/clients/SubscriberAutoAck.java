package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple subscriber with auto acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class SubscriberAutoAck extends Client {

    private static final Logger logger = Logger.getLogger(SubscriberAutoAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String topicNameJndi;
    private long receiveTimeOut;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private List<Message> listOfReceivedMessagesToBeAcked = new ArrayList<Message>();
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
     * @param container      container to which to connect
     * @param topicNameJndi  jndi name of the topic
     * @param subscriberName name of the subscriber
     */
    public SubscriberAutoAck(Container container, String topicNameJndi, String clientId, String subscriberName) {

        this(container, topicNameJndi, 30000, 30, clientId, subscriberName);

    }
    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container     container
     * @param topicNameJndi  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param maxRetries     how many times to retry receive before giving up
     * @param subscriberName name of the subscriber
     */
    public SubscriberAutoAck(Container container, String topicNameJndi, long receiveTimeOut, int maxRetries, String clientId, String subscriberName){
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.topicNameJndi = topicNameJndi;
        this.receiveTimeOut = receiveTimeOut;
        this.maxRetries = maxRetries;
        this.clientId = clientId;
        this.subscriberName = subscriberName;

        setTimeout(0); // set receive timeout to 0 to read with max speed



    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicNameJndi  jndi name of the topic
     * @param subscriberName name of the subscriber
     */
    @Deprecated
    public SubscriberAutoAck(String hostname, int port, String topicNameJndi, String clientId, String subscriberName) {

        this(EAP6_CONTAINER, hostname, port, topicNameJndi, 30000, 30, clientId, subscriberName);

    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container      container
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicNameJndi  jndi name of the topic
     * @param subscriberName name of the subscriber
     */
    @Deprecated
    public SubscriberAutoAck(String container, String hostname, int port, String topicNameJndi, String clientId, String subscriberName) {

        this(container, hostname, port, topicNameJndi, 30000, 30, clientId, subscriberName);

    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicNameJndi  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param maxRetries     how many times to retry receive before giving up
     * @param subscriberName name of the subscriber
     */
    @Deprecated
    public SubscriberAutoAck(String hostname, int port, String topicNameJndi, long receiveTimeOut,
                             int maxRetries, String clientId, String subscriberName) {
        this(EAP6_CONTAINER, hostname, port, topicNameJndi, receiveTimeOut, maxRetries, clientId, subscriberName);
    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container     container
     * @param hostname       hostname
     * @param port           jndi port
     * @param topicNameJndi  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param maxRetries     how many times to retry receive before giving up
     * @param subscriberName name of the subscriber
     */
    @Deprecated
    public SubscriberAutoAck(String container, String hostname, int port, String topicNameJndi, long receiveTimeOut,
                             int maxRetries, String clientId, String subscriberName) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.topicNameJndi = topicNameJndi;
        this.receiveTimeOut = receiveTimeOut;
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

            Message message;

            while ((message = receiveMessage(subscriber)) != null) {
                Thread.sleep(getTimeout());

//                listOfReceivedMessages.add(message);
                addMessage(listOfReceivedMessages, message);

                count++;

                logger.debug("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getTopicNameJndi()
                        + ". Received message - count: "
                        + count + ", messageId:" + message.getJMSMessageID());
            }

            logger.info("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getTopicNameJndi()
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
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
    }

    /**
     * Tries to receive message from server in specified timeout. If server crashes
     * then it retries for maxRetries. If even then fails to receive which means that
     * subscriber.subscriber(timeout) throw JMSException maxRetries's times then throw Exception above.
     *
     * @param subscriber subscriber message subscriber
     * @return message or null
     * @throws Exception when maxRetries was reached
     */
    public Message receiveMessage(TopicSubscriber subscriber) throws Exception {

        Message msg;
        int numberOfRetries = 0;

        // receive message with retry
        while (numberOfRetries < maxRetries) {

            try {

                msg = subscriber.receive(receiveTimeOut);
                return msg;

            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (count + 1)
                        + "ex: " + ex.getMessage());
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for subscriber Subscriber: " + subscriberName + " for node: " + hostname);
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

    public int getCount() {
        return count;
    }

    /**
     * I don't want to have synchronization between publishers and subscribers.
     */
    public void subscribe() {

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = cf.createConnection();

            conn.setClientID(clientId);

            conn.start();

            topic = (Topic) context.lookup(getTopicNameJndi());

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            subscriber = session.createDurableSubscriber(topic, subscriberName);

        } catch (Exception e) {
            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }
}
