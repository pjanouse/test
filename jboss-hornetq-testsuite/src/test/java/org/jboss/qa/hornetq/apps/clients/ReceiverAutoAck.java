package org.jboss.qa.hornetq.apps.clients;

import java.util.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.log4j.Logger;
import javax.jms.Queue;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

/**
 * Simple receiver with auto acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class ReceiverAutoAck extends Thread {

    private static final Logger logger = Logger.getLogger(ReceiverAutoAck.class);
    private int maxRetries;
    private String hostname;
    private int port;
    private String queueNameJndi = "jms/queue/testQueue1";
    private long receiveTimeOut;
    private FinalTestMessageVerifier messageVerifier;
    private List<Message> listOfReceivedMessages = new ArrayList<Message>();;
    private int count = 0;
    private Exception exception = null;
    private boolean securityEnabled = false;
    private String userName;
    private String password;

    /**
     * Creates a receiver to queue with auto acknowledge.
     * 
     * @param hostname hostname
     * @param port jndi port
     * @param queueJndiName jndi name of the queue    
     */
    public ReceiverAutoAck(String hostname, int port, String queueJndiName) {
        
        this(hostname, port, queueJndiName, 30000, 30);
        
    }
    
    /**
     * Creates a receiver to queue with auto acknowledge.
     * 
     * @param hostname hostname
     * @param port jndi port
     * @param queueJndiName jndi name of the queue
     * @param receiveTimeOut how long to wait to receive message
     * @param ackAfter send ack after how many messages
     * @param maxRetries how many times to retry receive before giving up
     */
    public ReceiverAutoAck(String hostname, int port, String queueJndiName, long receiveTimeOut,
            int maxRetries) {
        
        this.hostname = hostname;
        this.port = port;
        this.queueNameJndi = queueJndiName;
        this.receiveTimeOut = receiveTimeOut;
        this.maxRetries = maxRetries;
        
    }

    @Override
    public void run() {

        Context context = null;
        ConnectionFactory cf = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;

        try {

            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + port);
            context = new InitialContext(env);

            cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

            conn = getConnection(cf);
            
            conn.start();

            queue = (Queue) context.lookup(queueNameJndi);

            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message = null;
            
            while ((message = receiveMessage(receiver)) != null) {
                
                listOfReceivedMessages.add(message);
                
                count++;
                
                logger.info("Receiver for node: " + getHostname() + " and queue: " + queueNameJndi 
                            + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
            }

            logger.info("Receiver for node: " + getHostname() + " and queue: " + queueNameJndi 
                    +". Received NULL - number of received messages: " + count);
            
            if (messageVerifier != null)    {
                messageVerifier.addReceivedMessages(listOfReceivedMessages);
            }
            
        } catch (JMSException ex) {
            logger.error("JMSException was thrown during receiving messages:", ex);
            exception = ex;
        } catch (Exception ex) {
            logger.error("Exception was thrown during receiving messages:", ex);
            exception = ex;
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + getHostname());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (JMSException ex) {
                    // ignore
                }
            }
            if (context != null)    {
                try {
                    context.close();
                } catch (Exception ex)  {
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
     * 
     */
    public Message receiveMessage(MessageConsumer consumer) throws Exception {
        
        Message msg = null;
        int numberOfRetries = 0;
        
        // receive message with retry
        while (numberOfRetries < maxRetries)    {
            
            try {
                
                msg = consumer.receive(receiveTimeOut);
                return msg;
                
            } catch (JMSException ex)   {
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
    public List<Message> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    /**
     * @param listOfReceivedMessages the listOfReceivedMessages to set
     */
    public void setListOfReceivedMessages(List<Message> listOfReceivedMessages) {
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

    public static void main(String[] args) throws InterruptedException  {
        
        ReceiverAutoAck receiver = new ReceiverAutoAck("192.168.1.1", 4447, "jms/queue/testQueue0", 30000, 10);
        
        receiver.start();
        
        receiver.join();
    }

    /**
     * Returns connection.
     * 
     * @param cf
     * @return
     * @throws JMSException 
     */
    private Connection getConnection(ConnectionFactory cf) throws JMSException {
        
        // if there is username and password and security enabled then use it
        if (isSecurityEnabled() && getUserName() != null && !"".equals(userName) && getPassword() != null)   {
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
}
