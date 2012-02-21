package org.jboss.qa.hornetq.apps.clients;

import java.util.HashMap;
import java.util.Observable;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.log4j.Logger;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import java.util.Observable;

/**
 * Simple receiver with client acknowledge session. ABLE to failover.
 *
 * @author mnovak
 */
public class ReceiverClientAckHA extends Thread   {

    private static final Logger logger = Logger.getLogger(ProducerClientAckHA.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private long receiveTimeOut = 30000;
    private int ackAfter = 10;

    public ReceiverClientAckHA(String hostname, int port, String queueJndiName) {
        
        this.hostname = hostname;
        this.port = port;
        this.queueNameJndi = queueJndiName;
        
    }

    @Override
    public void run() {

        Context context = null;
        Connection conn = null;
        Session session = null;
        Queue queue = null;

        try {

            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + getHostname() + ":" + getPort());
            context = new InitialContext(env);

            ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

            conn = cf.createConnection();

            conn.start();

            queue = (Queue) context.lookup(queueNameJndi);

            session = conn.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);

            MessageConsumer receiver = session.createConsumer(queue);

            Message message = null;
            
            Message lastMessage = null;
            
            int count = 0;
            
            while ((message = receiveMessage(receiver, count)) != null) {

                count++;
                
                if (count % ackAfter == 0) { // try to ack message
                    
                    try {
                        message.acknowledge();
                    } catch(Exception ex)   {
                       logger.error("Exception thrown during acknowledge. Receiver for node: " + getHostname() + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
                       // all unacknowledge messges will be received again
                       count = count - ackAfter;
                    }
                    logger.info("Receiver for node: " + getHostname() + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID() + " SENT ACKNOWLEDGE");
                    
                } else { // i don't want to ack now
                    
                    logger.info("Receiver for node: " + getHostname() + ". Received message - count: "
                            + count + ", messageId:" + message.getJMSMessageID());
                }
                // hold information about last message so we can ack it when null is received = queue empty
                lastMessage = message;

            }

            logger.info("Receiver for node: " + getHostname() + ". Received NULL - number of received messages: " + count);
            
            lastMessage.acknowledge();

        } catch (JMSException ex) {

            logger.error("JMSException was thrown during receiving messages:", ex);

        } catch (Exception ex) {

            logger.error("Exception was thrown during receiving messages:", ex);
            throw new RuntimeException("Fatal exception was thrown in receiver. Receiver for node: " + getHostname());

        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (JMSException ex) {
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
     * @param count counter
     * @return message or null
     * @throws Exception when maxRetries was reached
     * 
     */
    public Message receiveMessage(MessageConsumer consumer, int count) throws Exception {
        
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

}
