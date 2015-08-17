package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.clients.Client;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by eduda on 4.8.2015.
 */
public class ReceiverAutoAck extends Client {

    private static final Logger logger = Logger.getLogger(ReceiverAutoAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String queueNameJndi = "jms/queue/testQueue1";
    private long receiveTimeOut;
    private FinalTestMessageVerifier messageVerifier;
    private List<Map<String,String>> listOfReceivedMessages = new ArrayList<Map<String,String>>();
    private int count = 0;
    private Exception exception = null;
    private boolean securityEnabled = false;
    private String userName;
    private String password;

    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container container to which to connect
     * @param queueJndiName jndi name of the queue
     */
    public ReceiverAutoAck(Container container, String queueJndiName) {
        this(container, queueJndiName, 30000, 30);

    }


    /**
     * Creates a receiver to queue with auto acknowledge.
     *
     * @param container     container
     * @param queueJndiName  jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param maxRetries     how many times to retry receive before giving up
     */
    public ReceiverAutoAck(Container container, String queueJndiName, long receiveTimeOut,
                           int maxRetries) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.maxRetries = maxRetries;

        setTimeout(0); // set receive timeout to 0 to read with max speed
    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf = null;
        Queue queue = null;

        try {

            context = getContext(hostname, port);

            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                jmsContext.start();;
                queue = (Queue) context.lookup(queueNameJndi);
                JMSConsumer receiver = jmsContext.createConsumer(queue);
                Message message = null;

                while ((message = receiveMessage(receiver)) != null) {
                    Thread.sleep(getTimeout());

                    addMessage(listOfReceivedMessages, message);

                    count++;

                    logger.debug("Receiver for node: " + getHostname() + " and queue: " + queueNameJndi
                            + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
                }

                logger.info("Receiver for node: " + getHostname() + " and queue: " + queueNameJndi
                        + ". Received NULL - number of received messages: " + count);

                if (messageVerifier != null) {
                    messageVerifier.addReceivedMessages(listOfReceivedMessages);
                }
            }



        } catch (JMSException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + getHostname());
        } finally {
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
     * Tries to receive message from server in specified timeout. If server crashes
     * then it retries for maxRetries. If even then fails to receive which means that
     * consumer.receiver(timeout) throw JMSException maxRetries's times then throw Exception above.
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
                return msg;

            } catch (JMSRuntimeException ex) {
                numberOfRetries++;
                logger.error("RETRY receive for host: " + getHostname() + ", Trying to receive message with count: " + (count + 1));
            }
        }

        throw new Exception("FAILURE - MaxRetry reached for receiver for node: " + getHostname());
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
     * Returns connection.
     *
     * @param cf
     * @return
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

    public int getCount() {
        return count;
    }

}
