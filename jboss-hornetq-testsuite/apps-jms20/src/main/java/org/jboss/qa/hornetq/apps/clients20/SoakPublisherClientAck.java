package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Publisher with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class SoakPublisherClientAck extends Client {

    private static final Logger logger = Logger.getLogger(SoakPublisherClientAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = getJNDIPort();
    private String topicNameJndi;
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private Exception exception = null;
    private String clientId;
    private boolean stop = false;

    private int counter = 0;

    /**
     * @param container         EAP container
     * @param topicNameJndi     jndi name of topic to publish messages
     * @param messages          number of messages to send
     * @param clientId          ID of client
     */
    public SoakPublisherClientAck(Container container, String topicNameJndi, int messages, String clientId) {
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
    public void run() {

        Context context = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                Topic topic = (Topic) context.lookup(getTopicNameJndi());

                jmsContext.setClientID(clientId);

                JMSProducer publisher = jmsContext.createProducer();

                Message msg = null;

                while (counter < messages && !stop) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    // send message in while cycle
                    sendMessage(publisher, topic, msg);

                    logger.info("Publisher with clientId: " + clientId + " for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

                }
            }


        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

        } finally {

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
    private void sendMessage(JMSProducer publisher, Destination destination, Message msg) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            try {

                publisher.send(destination, msg);

                counter++;

                numberOfRetries = 0;

                return;

            } catch (JMSRuntimeException ex) {
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

    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }
}


