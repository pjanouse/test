package org.jboss.qa.hornetq.apps.clients;

import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

/**
 * 
 * Simple sender with client acknowledge session. Able to fail over.
 * 
 * This class extends Thread class and should be started as a thread using start().
 * 
 * @author mnovak
 */
public class ProducerClientAckHA extends Thread {

    private static final Logger logger = Logger.getLogger(ProducerClientAckHA.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);

    /**
     * 
     * @param hostname hostname
     * @param port port
     * @param messages number of messages to send
     * @param messageBuilder message builder
     * @param maxRetries number of retries to send message after server fails
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    public ProducerClientAckHA(String hostname, int port, String queueNameJndi, int messages) {
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }
    
    /**
     * Starts end messages to server. This should be started as Thread - producerer.start();
     * 
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {
            
            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + getHostname() + ":" + getPort());
            context = new InitialContext(env);

            ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

            Queue queue = (Queue) context.lookup(getQueueNameJndi());

            con = cf.createConnection();

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            int counter = 1;

            Message msg = null;

            while (counter <= getMessages()) {

//                msg = this.messageBuilder.createMessage(session);
                msg = session.createTextMessage("counter: " + counter);
                // send message in while cycle
                sendMessage(producer, msg, counter);

                logger.info("Producer for node: " + getHostname() + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

                counter++;

            }

            producer.close();

        } catch (Exception e) {

            logger.error("Producer got exception and ended:", e);

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
     * @param producer
     * @param msg
     */
    private void sendMessage(MessageProducer producer, Message msg, int counter) throws Exception {
        
        int numberOfRetries = 0;
        
        while (numberOfRetries < maxRetries) {
            try {

                producer.send(msg);
                
                // notify all observers about sent message

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + getHostname()
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {} // ignore 

                numberOfRetries++;

            }
        }
        
        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for producer for node: " + getHostname()
                + ". Sent message with property count: "
                + ", messageId:" + msg.getJMSMessageID());

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
}

