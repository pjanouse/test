package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.Client;

import javax.jms.*;
import javax.naming.Context;
import java.util.*;

/**
 * Created by eduda on 4.8.2015.
 */
public class SubscriberClientAck extends Client {

    private static final Logger logger = Logger.getLogger(SubscriberClientAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String topicNameJndi;
    private long receiveTimeOut;
    private int ackAfter;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private List<Message> listOfReceivedMessagesToBeAcked = new ArrayList<Message>();
    private Exception exception = null;
    private String subscriberName;
    private String clientId;
    private Context context;
    private ConnectionFactory cf;
    private JMSContext jmsContext;
    private Topic topic;
    private JMSConsumer subscriber = null;
    private Set<Message> setOfReceivedMessagesWithPossibleDuplicates = new HashSet<Message>();
    private List<Message> listOfReceivedInDoubtMessages = new ArrayList<Message>();

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
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.topicNameJndi = topicNameJndi;
        this.receiveTimeOut = receiveTimeOut;
        this.ackAfter = ackAfter;
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

                listOfReceivedMessagesToBeAcked.add(message);

                counter++;

                if (counter % ackAfter == 0) { // try to ack message
                    acknowledgeMessage(message);
                    listOfReceivedMessagesToBeAcked.clear();
                } else { // i don't want to ack now
                    logger.debug("Subscriber: " + subscriberName + " for node: " + getHostname() + " and topic: " + getTopicNameJndi()
                            + ". Received message - count: "
                            + counter + ", messageId:" + message.getJMSMessageID());
                }

                // hold information about last message so we can ack it when null is received = topic empty
                lastMessage = message;
            }

            addMessages(listOfReceivedMessages, listOfReceivedInDoubtMessages);

            counter = counter + listOfReceivedInDoubtMessages.size();

            logger.info("Subscriber for node: " + hostname + " and queue: " + topicNameJndi
                    + ". Subscriber received NULL - number of received messages: " + counter);

            if (messageVerifier != null) {
                messageVerifier.addReceivedMessages(listOfReceivedMessages);
            }

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
                    // ignore
                }
            }
        }
    }

    /**
     * Try to acknowledge a message.
     *
     * @param message message to be acknowledged
     * @throws javax.jms.JMSException
     */
    public boolean acknowledgeMessage(Message message) throws Exception {

        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();
        boolean isAckSuccessful = true;

        try {

            checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts();

            message.acknowledge();

            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", message-counter: " + message.getStringProperty("counter")
                    + ", messageId:" + message.getJMSMessageID() + " ACKNOWLEDGED");

            addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeAcked);

            logListOfAddedMessages(listOfReceivedMessagesToBeAcked);

        } catch (TransactionRolledBackException ex) {
            logger.error("TransactionRolledBackException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);
            // all unacknowledge messges will be received again
            counter = counter - listOfReceivedMessagesToBeAcked.size();
            isAckSuccessful = false;

        } catch (JMSException ex) {

            logger.error("JMSException thrown during acknowledge. Receiver for node: " + hostname + ". Received message - count: "
                    + counter + ", messageId:" + message.getJMSMessageID()
                    + ((message.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + message.getStringProperty(duplicatedHeader) : ""), ex);

            listOfReceivedInDoubtMessages.addAll(listOfReceivedMessagesToBeAcked);
            counter = counter - listOfReceivedMessagesToBeAcked.size();
            isAckSuccessful = false;

        } finally {
            listOfReceivedMessagesToBeAcked.clear();
        }
        return isAckSuccessful;
    }

    private boolean areThereDuplicates() throws JMSException {
        boolean isDup = false;

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (Message m : listOfReceivedMessagesToBeAcked)    {
            setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()));
        }
        for (Message m : setOfReceivedMessagesWithPossibleDuplicates)   {
            if (!setOfReceivedMessages.add(m.getStringProperty(jmsImplementation.getDuplicatedHeader()))) {
                isDup=true;
            }
        }
        return isDup;
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
    public Message receiveMessage(JMSConsumer subscriber) throws Exception {

        Message msg = null;
        int numberOfRetries = 0;

        // receive message with retry
        while (numberOfRetries < maxRetries) {

            try {
                msg = subscriber.receive(receiveTimeOut);
                if (msg != null) {
                    msg = cleanMessage(msg);
                }
                return msg;

            } catch (JMSRuntimeException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (counter + 1)
                        + "ex: " + ex.getMessage());
            } catch (JMSException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with count: " + (counter + 1)
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

            topic = (Topic) context.lookup(getTopicNameJndi());

            subscriber = jmsContext.createDurableConsumer(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
    }

    public int getCount() {
        return counter;
    }

    private void checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts() throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        // clone list of inDoubtMessages
        List<Message> listCloneOfInDoubtMessages = new ArrayList<Message>();
        for (Message m : listOfReceivedInDoubtMessages) {
            listCloneOfInDoubtMessages.add(m);
        }

        // if duplicate received then remove from the list of in doubt messages
        String inDoubtMessageDupId = null;
        String receivedMessageDupId = null;
        for (Message inDoubtMessage : listCloneOfInDoubtMessages) {
            inDoubtMessageDupId = inDoubtMessage.getStringProperty(duplicatedHeader);
            for (Message receivedMessage : listOfReceivedMessagesToBeAcked) {
                if (((receivedMessageDupId = receivedMessage.getStringProperty(duplicatedHeader)) != null) &&
                        receivedMessageDupId.equalsIgnoreCase(inDoubtMessageDupId)) {
                    logger.info("Duplicated in doubt message was received. Removing message with dup id: " + inDoubtMessageDupId
                            + " and messageId: " + inDoubtMessage.getJMSMessageID() + " from list of in doubt messages");
                    listOfReceivedInDoubtMessages.remove(inDoubtMessage);
                }
            }
        }
    }

    private void logListOfAddedMessages(List<Message> listOfReceivedMessagesToBeCommited) throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        StringBuilder stringBuilder = new StringBuilder();
        for (Message m : listOfReceivedMessagesToBeCommited) {
            stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader) + ", \n");
        }
        logger.info("New messages added to list of received messages: \n" + stringBuilder.toString());
    }

}
