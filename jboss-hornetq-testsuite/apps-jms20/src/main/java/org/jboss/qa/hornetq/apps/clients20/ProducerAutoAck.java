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
public class ProducerAutoAck extends Client {

    private static final Logger logger = Logger.getLogger(ProducerAutoAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    private boolean securityEnabled = false;
    private String userName;
    private String password;

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerAutoAck(Container container, String queueNameJndi, int messages) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * Starts end messages to server. This should be started as Thread -
     * producer.start();
     */
    public void run() {

        Context context = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(queueNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                JMSProducer producer = jmsContext.createProducer();
                Message msg;

                while (counter < messages && !stop) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    // send message in while cycle
                    sendMessage(producer, queue, msg);

                    Thread.sleep(getTimeout());

                    logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

                }

                if (messageVerifier != null) {
                    messageVerifier.addSendMessages(listOfSentMessages);
                }
            }



        } catch (Exception e) {
            exception = e;
            logger.error("Producer got exception and ended:", e);

        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
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
     * @param msg message
     */
    private void sendMessage(JMSProducer producer, Destination destination, Message msg) throws Exception {
        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                producer.send(destination, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);
                counter++;
                return;

            } catch (JMSRuntimeException ex) {
                logger.error("Failed to send message - counter: " + counter, ex);
            } catch (JMSException ex) {
                logger.error("Failed to clean message - counter: " + counter, ex);
            }
        }
        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for producer for node: " + getHostname()
                + ". Sent message with property count: " + counter
                + ", messageId:" + msg.getJMSMessageID());
    }

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
     * Returns connection.
     *
     * @param cf
     * @return connection
     * @throws javax.jms.JMSException
     */
    private Connection getConnection(ConnectionFactory cf) throws JMSException {

        // if there is username and password and security enabled then use it
        if (isSecurityEnabled() && getUserName() != null && !"".equals(userName) && getPassword() != null) {
            return cf.createConnection(getUserName(), getPassword());

        }
        // else it's guest user or security disabled
        return cf.createConnection();
    }

    /**
     * @return the securityEnabled
     */
    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    /**
     * @param securityEnabled the securityEnabled to set
     */
    public void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    /**
     * @return the userName
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    @Override
    public int getCount() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

}
