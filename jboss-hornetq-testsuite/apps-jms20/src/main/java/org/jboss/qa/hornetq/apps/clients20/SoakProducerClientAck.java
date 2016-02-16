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

/**
 * Simple sender with client acknowledge session. Able to fail over.
 * <p/>
 * This producer does not remember all the send messages, just message id. This is for
 * memory reasons.
 * <p/>
 * This class extends Thread class and should be started as a thread using start().
 *
 * @author mnovak
 */
public class SoakProducerClientAck extends Client {

    private static final Logger logger = Logger.getLogger(SoakProducerClientAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<String> listOfSentMessages = new ArrayList<String>();
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    private int counter = 0;

    /**
     * @param container         EAP container
     * @param queueNameJndi     set jndi name of the queue to send messages
     * @param messages          number of messages to send
     */
    public SoakProducerClientAck(Container container, String queueNameJndi, int messages) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.queueNameJndi = queueNameJndi;
        this.messages = messages;
    }

    /**
     * Starts end messages to server. This should be started as Thread - producer.start();
     */
    public void run() {

        Context context = null;

        try {

            ConnectionFactory cf;

            context = getContext(hostname, port);
            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            
            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + queueNameJndi);

                Queue queue = (Queue) context.lookup(queueNameJndi);

                JMSProducer producer = jmsContext.createProducer();

                Message msg;

                while (getCounter() < messages && !stop) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", getCounter());

                    // send message in while cycle
                    sendMessage(producer, queue, msg);

                    Thread.sleep(getTimeout());

                    logger.debug("Producer for node: " + hostname + "and queue: " + queueNameJndi + ". Sent message with property my counter: " + getCounter()
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());
                }

            }


        } catch (Exception e) {
            exception = e;
            logger.error("Producer got exception and ended:", e);

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
     * @param producer producer
     * @param msg message to be sent
     */
    private void sendMessage(JMSProducer producer, Destination destination, Message msg) throws Exception {

        int numberOfRetries = 0;

        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        while (numberOfRetries < maxRetries) {

            try {

                producer.send(destination, msg);

                if (msg.getStringProperty(duplicatedHeader) != null)   {
                    listOfSentMessages.add(msg.getStringProperty(duplicatedHeader));
                }

                setCounter(getCounter() + 1);

                numberOfRetries = 0;

                return;

            } catch (JMSRuntimeException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + hostname
                            + ". Sent message with property count: " + getCounter()
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {
                } // ignore

                numberOfRetries++;
            }
        }

        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for producer for node: " + hostname
                + ". Sent message with property count: " + getCounter()
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
     * @return the queueNameJndi
     */
    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    /**
     * @param queueNameJndi the queueNameJndi to set
     */
    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
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
     * List of messageIds.
     *
     * @return the listOfSentMessages
     */
    public List<String> getListOfSentMessages() {
        return listOfSentMessages;
    }

    /**
     * @param listOfSentMessages the listOfSentMessages to set
     */
    public void setListOfSentMessages(List<String> listOfSentMessages) {
        this.listOfSentMessages = listOfSentMessages;
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
     * @return the messageBuilder
     */
    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    /**
     * @param messageBuilder the messageBuilder to set
     */
    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    /**
     * @return the counter
     */
    public int getCounter() {
        return counter;
    }

    /**
     * @param counter the counter to set
     */
    public void setCounter(int counter) {
        this.counter = counter;
    }
}

