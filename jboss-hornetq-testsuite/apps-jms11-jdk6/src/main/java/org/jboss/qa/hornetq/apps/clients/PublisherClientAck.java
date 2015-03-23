package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publisher with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class PublisherClientAck extends Client {

    private static final Logger logger = Logger.getLogger(PublisherClientAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String topicNameJndi;
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();
    private List<FinalTestMessageVerifier> messageVerifiers;
    private Exception exception = null;
    private String clientId;
    private boolean stop = false;

    /**
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherClientAck(String hostname, int port, String topicNameJndi, int messages, String clientId) {
        this(EAP6_CONTAINER,hostname, port, topicNameJndi, messages, clientId);
    }

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherClientAck(Container container, String topicNameJndi, int messages, String clientId) {
        this(container.getContainerType().toString(), container.getHostname(), container.getJNDIPort(), topicNameJndi, messages, clientId);
    }

    /**
     * @param container     EAP container
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherClientAck(String container, String hostname, int port, String topicNameJndi, int messages, String clientId) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.topicNameJndi = topicNameJndi;
        this.clientId = clientId;
    }

    /**
     * Starts end messages to server. This should be started as Thread - publisher.start();
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Topic topic = (Topic) context.lookup(getTopicNameJndi());

            con = cf.createConnection();

            con.setClientID(clientId);

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer publisher = session.createProducer(topic);

            Message msg = null;

            while (counter < messages && !stop) {

                msg = messageBuilder.createMessage(session);
                // send message in while cycle
                sendMessage(publisher, msg);

                Thread.sleep(getTimeout());

                logger.debug("Publisher with clientId: " + clientId + " for node: " + hostname
                        + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            publisher.close();

            if (messageVerifiers != null) {
                for (FinalTestMessageVerifier finalTestMessageVerifier : messageVerifiers) {
                    finalTestMessageVerifier.addSendMessages(listOfSentMessages);
                }

            }

        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

        } finally {

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                }
            }
        }
    }

    /**
     * Send message to server. Try send message and if succeed than return. If
     * send fails and exception is thrown it tries send again until max retry is
     * reached. Then throws new Exception.
     *
     * @param publisher
     * @param msg
     */
    private void sendMessage(MessageProducer publisher, Message msg) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            try {

                publisher.send(msg);

                msg = cleanMessage(msg);

//                listOfSentMessages.add(msg);
                addMessage(listOfSentMessages, msg);

                counter++;

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {
                ex.printStackTrace();
                try {
                    logger.info("SEND RETRY - Publisher for node: " + hostname
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID(), ex);
                } catch (JMSException e) {
                } // ignore

                numberOfRetries++;
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
     * @param listOfSentMessages the listOfSentMessages to set
     */
    public void setListOfSentMessages(List<Map<String,String>> listOfSentMessages) {
        this.listOfSentMessages = listOfSentMessages;
    }

    /**
     * @return the messageVerifier
     */
    public List<FinalTestMessageVerifier> getMessageVerifiers() {
        return messageVerifiers;
    }

    /**
     * @param messageVerifier the messageVerifier to set
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
     * @return the topicNameJndi
     */
    public String getTopicNameJndi() {
        return topicNameJndi;
    }

    @Override
    public int getCount() {
        return counter;
    }

    /**
     * @param topicNameJndi the topicNameJndi to set
     */
    public void setTopicNameJndi(String topicNameJndi) {
        this.topicNameJndi = topicNameJndi;
    }

    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }
}


