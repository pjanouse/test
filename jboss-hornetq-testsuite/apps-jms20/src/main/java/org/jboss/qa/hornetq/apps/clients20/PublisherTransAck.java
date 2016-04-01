package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by eduda on 4.8.2015.
 */
public class PublisherTransAck extends Client {

    private static final Logger logger = Logger.getLogger(PublisherTransAck.class);
    private int maxRetries = 30;
    private String hostname;
    private int port;
    private String topicNameJndi;
    private int messages;
    private int commitAfter = 100;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();
    private List<Message>  listOfMessagesToBeCommited = new ArrayList<Message> ();
    private List<FinalTestMessageVerifier> messageVerifiers;
    private Exception exception = null;
    private String clientId;
    private boolean stop = false;

    JMSProducer publisher;

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherTransAck(Container container, String topicNameJndi, int messages, String clientId) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.topicNameJndi = topicNameJndi;
        this.messages = messages;
        this.clientId = clientId;
    }


    /**
     * Starts end messages to server. This should be started as Thread - publisher.start();
     */
    @Override
    public void run() {

        Context context = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Topic topic = (Topic) context.lookup(topicNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED)) {
                jmsContext.setClientID(clientId);
                publisher = jmsContext.createProducer();

                Message msg = null;

                String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

                while (counter < messages && !stop) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", counter);

                    sendMessage(topic, msg);

                    listOfMessagesToBeCommited.add(msg);

                    Thread.sleep(getTimeout());

                    if (counter % commitAfter == 0) {

                        commitJMSContext(topic, jmsContext);
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Message m : listOfMessagesToBeCommited) {
                            stringBuilder.append(m.getJMSMessageID());
                        }
                        logger.debug("Adding messages: " + stringBuilder.toString());
                        for (Message m : listOfMessagesToBeCommited)    {
                            m = cleanMessage(m);
                            addMessage(listOfSentMessages,m);
                        }

                        logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                                + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
                        listOfMessagesToBeCommited.clear();

                    }
                }

                commitJMSContext(topic, jmsContext);

                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfMessagesToBeCommited) {
                    stringBuilder.append(m.getJMSMessageID());
                }
                logger.debug("Adding messages: " + stringBuilder.toString());
                for (Message m : listOfMessagesToBeCommited)    {
                    m = cleanMessage(m);
                    addMessage(listOfSentMessages,m);
                }

                logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                        + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
                listOfMessagesToBeCommited.clear();


                if (messageVerifiers != null) {
                    for (FinalTestMessageVerifier verifier : messageVerifiers) {
                        verifier.addSendMessages(listOfSentMessages);
                    }
                }
            }



        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    private void sendMessage(Destination destination, Message msg) throws Exception {
        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                publisher.send(destination, msg);
                counter++;
                return;

            } catch (JMSRuntimeException ex) {
                logger.error("Failed to send message - counter: " + counter, ex);
            }
        }
        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for publisher for node: " + hostname
                + ". Sent message with property count: " + counter
                + ", messageId:" + msg.getJMSMessageID());
    }

    /**
     * Stop producer
     */
    public void stopSending() {
        this.stop = true;
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
     * @return the messages
     */
    public int getMessages() {
        return messages;
    }

    /**
     * @param messages the messages to set
     */
    public void setMessages(int messages) {
        this.messages = messages;
    }

    /**
     * @return the listOfSentMessages
     */
    public List<Map<String,String>> getListOfSentMessages() {
        return listOfSentMessages;
    }

    /**
     * @return the messageVerifiers
     */
    public List<FinalTestMessageVerifier> getMessageVerifiers() {
        return messageVerifiers;
    }

    /**
     * @param messageVerifier list of message verifiers from each subscriber
     */
    public void setMessageVerifiers(List<FinalTestMessageVerifier> messageVerifier) {
        this.messageVerifiers = messageVerifier;
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
     * @return the commitAfter
     */
    public int getCommitAfter() {
        return commitAfter;
    }

    /**
     * Number of messages to be commited at once.
     *
     * @param commitAfter the commitAfter to set
     */
    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

    private void commitJMSContext(Destination destination, JMSContext jmsContext) throws Exception {
        int numberOfRetries = 0;
        while (true) {
            try {

                // try commit
                jmsContext.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackRuntimeException ex) {
                // if transaction rollback exception -> send messages again and commit
                ex.printStackTrace();

                // don't repeat this more than once, this can't happen
                if (numberOfRetries > 0) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(destination, m);
                }

                numberOfRetries++;

            } catch (JMSRuntimeException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                ex.printStackTrace();

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0 && ex.getCause().getMessage().contains("Duplicate message detected")) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(destination, m);
                }
                numberOfRetries++;
            }
        }
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
     * @return the clientId
     */
    public String getClientId() {
        return clientId;
    }

    @Override
    public int getCount() {
        return counter;
    }

    /**
     * @param clientId the clientId to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

}
