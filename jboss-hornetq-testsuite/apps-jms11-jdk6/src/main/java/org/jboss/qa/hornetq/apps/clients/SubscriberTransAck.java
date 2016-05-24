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
import javax.naming.NamingException;
import java.util.*;

/**
 * Simple subscriber with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class SubscriberTransAck extends Receiver11 {

    private static final Logger logger = Logger.getLogger(SubscriberTransAck.class);

    private int commitAfter;
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
     * @param container      container to connect to
     * @param topicJndiName  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param commitAfter    send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     */
    public SubscriberTransAck(Container container, String topicJndiName, long receiveTimeOut,
                              int commitAfter, int maxRetries, String clientId, String subscriberName) {
        super(container, topicJndiName, receiveTimeOut, maxRetries);
        this.commitAfter = commitAfter;
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

            boolean running = true;
            while (running) {

                message = receiveMessage(subscriber);

                // in case that commit of last message fails then receive the whole message window again and commit again
                if (message == null) {
                    if (commitSession(session)) {
                        running = false;
                    }
                    continue;
                }

                Thread.sleep(getTimeout());

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;

                logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + destinationNameJndi
                        + ". Received message - counter: "
                        + counter + ", messageId:" + message.getJMSMessageID()
                        + ", dupId: " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

                if (counter % commitAfter == 0) { // try to ack message
                    commitSession(session);
                }
            }

            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            logInDoubtMessages();

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + destinationNameJndi
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
                } catch (Exception e) {
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

    public void subscribe() {

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            conn = cf.createConnection();

            conn.setClientID(clientId);

            conn.start();

            topic = (Topic) context.lookup(getDestinationNameJndi());

            session = conn.createSession(true, Session.SESSION_TRANSACTED);

            subscriber = session.createDurableSubscriber(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

    public void close() throws Exception {
        if (context != null) {
            context.close();
        }
        if (conn != null) {
            conn.close();
        }
    }

    public int getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

}
