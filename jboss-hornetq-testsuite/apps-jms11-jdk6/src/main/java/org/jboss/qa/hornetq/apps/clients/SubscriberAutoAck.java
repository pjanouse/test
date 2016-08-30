package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import javax.jms.*;
import javax.naming.Context;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple subscriber with auto acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class SubscriberAutoAck extends Receiver11 {

    private static final Logger logger = Logger.getLogger(SubscriberAutoAck.class);

    private String subscriberName;
    private String clientId;
    private Context context;
    private ConnectionFactory cf;
    private Connection conn;
    private Session session;
    private Topic topic;
    private TopicSubscriber subscriber = null;
    private CountDownLatch subscribeLatch = new CountDownLatch(1);

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
        super(container, topicNameJndi, receiveTimeOut, maxRetries);
        this.clientId = clientId;
        this.subscriberName = subscriberName;
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
    public SubscriberAutoAck(String container, String hostname, int port, String topicNameJndi, String clientId, String subscriberName) {

        super(container, hostname, port, topicNameJndi, 30);
        this.clientId = clientId;
        this.subscriberName = subscriberName;

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

                addMessage(listOfReceivedMessages, message);

                counter++;

                logger.debug("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getDestinationNameJndi()
                        + ". Received message - count: "
                        + counter + ", messageId:" + message.getJMSMessageID());
            }

            logger.info("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getDestinationNameJndi()
                    + ". Received NULL - number of received messages: " + counter);

            addReceivedMessages(listOfReceivedMessages);

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

            topic = (Topic) context.lookup(getDestinationNameJndi());

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            subscriber = session.createDurableSubscriber(topic, subscriberName);

            subscribeLatch.countDown();

        } catch (Exception e) {
            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

    public boolean waitOnSubscribe(long timeout, TimeUnit unit) throws InterruptedException {
        return subscribeLatch.await(timeout, unit);
    }
}
