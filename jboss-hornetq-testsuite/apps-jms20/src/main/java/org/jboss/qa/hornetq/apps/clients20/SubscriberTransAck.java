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
    private List<Message> listOfReceivedInDoubtMessages = new ArrayList<Message>();
    private Exception exception = null;
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

                logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
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

            logger.info("Subscriber - name: " + getSubscriberName() + " - for node: " + getHostname() + " and topic: " + topicNameJndi
                    + ". Received NULL - number of received messages: " + counter);

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
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        }
    }

    /**
     * Try to commit session a message.
     *
     * @param jmsContext jmsContext
     * @throws javax.jms.JMSException
     */
    public boolean commitSession(JMSContext jmsContext) throws Exception {

        boolean commitSuccessful = true;
        try {
            checkIfInDoubtMessagesReceivedAgainAndRemoveThemFromTheListOfInDoubts();

            jmsContext.commit();

            logger.info("Receiver for node: " + hostname + ". Received message - count: "
                    + counter + " COMMIT");

            addMessages(listOfReceivedMessages, listOfReceivedMessagesToBeCommited);

            logListOfAddedMessages(listOfReceivedMessagesToBeCommited);

        } catch (TransactionRolledBackRuntimeException ex) {
            logger.error(" Receiver - COMMIT FAILED - TransactionRolledBackException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                    + ". Received message - count: " + counter + ", retrying receive", ex);
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            commitSuccessful = false;

        } catch (JMSRuntimeException ex) {
            logger.error(" Receiver - COMMIT FAILED - JMSException thrown during commit: " + ex.getMessage() + ". Receiver for node: " + hostname
                    + ". Received message - count: " + counter + ", retrying receive", ex);
            counter = counter - listOfReceivedMessagesToBeCommited.size();
            // if JMSException is thrown then it's not clear if messages were committed or not
            // we add them to the list of in doubt messages and if duplicates will be received in next
            // receive phase then we remove those messages from this list (compared by DUP ID)
            // if not duplicates will be received then we add this list to the list of received messages
            // when NULL is returned from consumer.receive(timeout)
            listOfReceivedInDoubtMessages.addAll(listOfReceivedMessagesToBeCommited);
            logInDoubtMessages();
            commitSuccessful = false;

        } finally {
            listOfReceivedMessagesToBeCommited.clear();
        }
        return commitSuccessful;
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
    public Message receiveMessage(JMSConsumer consumer) throws Exception {

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

            } catch (JMSRuntimeException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + hostname + ", Trying to receive message with counter: " + (counter + 1), ex);
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

            jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED);
            jmsContext.setClientID(clientId);
            jmsContext.start();
            topic = (Topic) context.lookup(getTopicNameJndi());
            subscriber = jmsContext.createDurableConsumer(topic, subscriberName);

        } catch (Exception e) {

            logger.error("Exception thrown during subsribing.", e);
            exception = e;
        }
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

    private void logInDoubtMessages() throws JMSException {
        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        StringBuilder stringBuilder = new StringBuilder();
        for (Message m : listOfReceivedInDoubtMessages) {
            stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(" dupId: ").append(m.getStringProperty(duplicatedHeader) + ", \n");
        }
        logger.info("List of  in doubt messages: \n" + stringBuilder.toString());
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
            for (Message receivedMessage : listOfReceivedMessagesToBeCommited) {
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
