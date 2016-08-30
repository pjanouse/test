package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.Context;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SubscriberTransAck extends Receiver20 {

    private static final Logger logger = Logger.getLogger(SubscriberTransAck.class);

    private int commitAfter;
    private String subscriberName;
    private String clientId;
    private Context context;
    private ConnectionFactory cf;
    private JMSContext jmsContext;
    private Topic topic;
    private JMSConsumer subscriber = null;
    private CountDownLatch subscribeLatch = new CountDownLatch(1);

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
                    if (commitSession(jmsContext)) {
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
                    commitSession(jmsContext);
                }
            }

            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            logInDoubtMessages();

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + destinationNameJndi
                    + ". Received NULL - number of received messages: " + counter);

            addReceivedMessages(listOfReceivedMessages);


        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in subscriber. Subscriber for node: " + getHostname());
        } finally {
            if (jmsContext != null) {
                jmsContext.close();
            }
            if (context != null) {
                try {
                    context.close();
                } catch (Exception e) {
                    logger.error(e);
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

            jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED);
            jmsContext.setClientID(clientId);
            jmsContext.start();
            topic = (Topic) context.lookup(getDestinationNameJndi());
            subscriber = jmsContext.createDurableConsumer(topic, subscriberName);
            subscribeLatch.countDown();

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

    public boolean waitOnSubscribe(long timeout, TimeUnit unit) throws InterruptedException {
        return subscribeLatch.await(timeout, unit);
    }

    public int getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

}
