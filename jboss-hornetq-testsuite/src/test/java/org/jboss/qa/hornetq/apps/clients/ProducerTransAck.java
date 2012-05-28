package org.jboss.qa.hornetq.apps.clients;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

/**
 * 
 * Simple sender with transaction acknowledge session. Able to fail over.
 * 
 * This class extends Thread class and should be started as a thread using start().
 * 
 * @author mnovak
 */
public class ProducerTransAck extends Thread {

    private static final Logger logger = Logger.getLogger(ProducerTransAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue0";
    private int messages = 1000;
    private int commitAfter = 100;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Message> listOfSentMessages = new ArrayList<Message>();
    private List<Message> listOfMessagesToBeCommited = new ArrayList<Message>();
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    
    private int counter = 0;
    
    /**
     * 
     * @param hostname hostname
     * @param port port
     * @param messages number of messages to send
     * @param messageBuilder message builder
     * @param maxRetries number of retries to send message after server fails
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    public ProducerTransAck(String hostname, int port, String queueNameJndi, int messages) {
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }
    
    /**
     * Starts end messages to server. This should be started as Thread - producer.start();
     * 
     */
    @Override
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {
            
            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + port);
            context = new InitialContext(env);

            ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");

            Queue queue = (Queue) context.lookup(queueNameJndi);

            con = cf.createConnection();

            session = con.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(queue);

            Message msg = null;

            while (counter < messages && !stop) {
                
                msg = messageBuilder.createMessage(session);
                
                // send message in while cycle
                sendMessage(producer, msg);
                
                if (counter % commitAfter == 0) { 
                    
                    commitSession(session, producer); 
                    
                }

            }
            
            commitSession(session, producer);

            producer.close();
            
            if (messageVerifier != null)    {
                messageVerifier.addSendMessages(listOfSentMessages);
            }

        } catch (Exception e) {
            exception = e;
            e.printStackTrace();
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
    private void sendMessage(MessageProducer producer, Message msg) throws Exception {
        
        int numberOfRetries = 0;
        
        while (numberOfRetries < maxRetries) {
            
            try {
               
                producer.send(msg);
                
                counter++;
                
                logger.info("Producer for node: " + hostname + ". Sent message: " + counter + ", messageId:" + msg.getJMSMessageID());
                
                listOfMessagesToBeCommited.add(msg);
                
                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + hostname
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {} // ignore 

                numberOfRetries++;
            }
        }
        
        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for producer for node: " + hostname
                + ". Sent message with property count: " + counter
                + ", messageId:" + msg.getJMSMessageID());

    }
    
    /**
     * Stop producer
     */
    public void stopSending()  {
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
    public List<Message> getListOfSentMessages() {
        return listOfSentMessages;
    }

    /**
     * @param listOfSentMessages the listOfSentMessages to set
     */
    public void setListOfSentMessages(List<Message> listOfSentMessages) {
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

    /**
     * Commits current session - commits messages which were sent.
     * 
     * @param session session
     * @param producer producer
     * @throws Exception 
     */
    private void commitSession(Session session, MessageProducer producer) throws Exception {
        
        int numberOfRetries = 0;
        
        while (numberOfRetries < maxRetries)    {
            
            // try commit
            try {
                
                session.commit();
                
                listOfSentMessages.addAll(listOfMessagesToBeCommited);
                
                listOfMessagesToBeCommited.clear();
                
                logger.info("Producer for node: " + hostname
                            + ". Sent message with property count: " + counter
                            + " - COMMIT");   
                
                return;
                
            } catch (TransactionRolledBackException ex) {
                // if rollbackException then send all messages again and try commit
                counter = counter - listOfMessagesToBeCommited.size();
                numberOfRetries++;
                logger.info("COMMIT Failed - Producer for node: " + hostname
                            + ". Sent message with property count: " + counter);                
                resendMessages(producer);
            
            } catch (JMSException ex) {
                logger.error("COMMIT Failed but transaction rollback exception was NOT thrown - Publisher for node: " + getHostname()
                            + ". Sent message with property count: " + counter + ". Operation will not be retried.", ex);
                return;
            }
            
        }
            // maxretry reached then throw exception above 
            throw new Exception("FAILURE in COMMIT - MaxRetry reached for producer for node: " + hostname
                + ". Sent message with property count: " + counter);
        
    }
    
    /**
     * Resends messages when transaction rollback exception was thrown.
     * 
     * @param producer producer
     */
    private void resendMessages(MessageProducer producer) throws Exception {
        
        // there can be problem during resending messages so we need to know which messages were resend
        List<Message> messagesToBeResend = new ArrayList<Message>(listOfMessagesToBeCommited);
        
        listOfMessagesToBeCommited.clear();
        
        for (Message msg : messagesToBeResend)  {
            
            sendMessage(producer, msg);
            
        }
        
    }
    
    public static void main(String[] args) throws InterruptedException  {
        
        ProducerTransAck producer = new ProducerTransAck("192.168.1.1", 4447, "jms/queue/testQueue0", 20000);
        
        producer.start();
        
        producer.join();
    }
}

