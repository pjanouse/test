package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.Context;

public class SubscriberClientAck extends Receiver20 {

    private static final Logger logger = Logger.getLogger(SubscriberClientAck.class);

    private int ackAfter;
    private String subscriberName;
    private String clientId;
    private Context context;
    private ConnectionFactory cf;
    private JMSContext jmsContext;
    private Topic topic;
    private JMSConsumer subscriber = null;

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container      container to which to connect
     * @param topicNameJndi  jndi name of the topic
     * @param subscriberName name of the subscriber
     */
    public SubscriberClientAck(Container container, String topicNameJndi, String clientId, String subscriberName) {

        this(container, topicNameJndi, 60000, 10, 30, clientId, subscriberName);

    }

    /**
     * Creates a subscriber to topic with client acknowledge.
     *
     * @param container container to connect
     * @param topicNameJndi  jndi name of the topic
     * @param receiveTimeOut how long to wait to receive message
     * @param ackAfter       send ack after how many messages
     * @param maxRetries     how many times to retry receive before giving up
     * @param subscriberName name of the subscriber
     */
    public SubscriberClientAck(Container container, String topicNameJndi, long receiveTimeOut,
                               int ackAfter, int maxRetries, String clientId, String subscriberName) {
        super(container, topicNameJndi, receiveTimeOut, maxRetries);
        this.ackAfter = ackAfter;
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
            Message lastMessage = null;
            boolean running = true;
            while (running) {

                message = receiveMessage(subscriber);

                // in case that ack of last message fails then receive the whole message window again and ack again
                if (message == null) {
                    if (acknowledgeMessage(lastMessage)) {
                        running = false;
                    }
                    continue;
                }

                Thread.sleep(getTimeout());

                listOfReceivedMessagesToBeCommited.add(message);

                counter++;

                if (counter % ackAfter == 0) { // try to ack message
                    acknowledgeMessage(message);
                    listOfReceivedMessagesToBeCommited.clear();
                } else { // i don't want to ack now
                    logger.debug("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getDestinationNameJndi()
                            + ". Received message - count: "
                            + counter + ", messageId:" + message.getJMSMessageID());
                }

                // hold information about last message so we can ack it when null is received = topic empty
                lastMessage = message;
            }

            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Subscriber for node: " + hostname + " and queue: " + destinationNameJndi
                    + ". Subscriber received NULL - number of received messages: " + counter);

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
                } catch (Exception ex) {
                    logger.error(ex);
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

    public int getAckAfter() {
        return ackAfter;
    }

    public void setAckAfter(int ackAfter) {
        this.ackAfter = ackAfter;
    }

    public void close() {
        jmsContext.close();
    }

    /**
     * I don't want to have synchronization between publishers and subscribers.
     */
    public void subscribe() {

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE);

            jmsContext.setClientID(clientId);

            jmsContext.start();

            topic = (Topic) context.lookup(getDestinationNameJndi());

            subscriber = jmsContext.createDurableConsumer(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

}
