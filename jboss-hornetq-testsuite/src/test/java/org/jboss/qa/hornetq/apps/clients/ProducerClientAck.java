package org.jboss.qa.hornetq.apps.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

/**
 *
 * Simple sender with client acknowledge session. Able to fail over.
 *
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mnovak
 */
public class ProducerClientAck extends Thread {

    private static final Logger logger = Logger.getLogger(ProducerClientAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Message> listOfSentMessages = new ArrayList<Message>();
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
    public ProducerClientAck(String hostname, int port, String queueNameJndi, int messages) {
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * Starts end messages to server. This should be started as Thread -
     * producer.start();
     *
     */
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

            logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + queueNameJndi);
            Queue queue = (Queue) context.lookup(queueNameJndi);

            con = cf.createConnection();

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            Message msg = null;

            while (counter < messages && !stop) {

                msg = messageBuilder.createMessage(session);
                msg.setIntProperty("count", counter);

                // send message in while cycle
                sendMessage(producer, msg);

                logger.info("Producer for node: " + hostname + "and queue: " + queueNameJndi + ". Sent message with property my counter: " + counter
                        + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());

            }

            producer.close();

            if (messageVerifier != null) {
                messageVerifier.addSendMessages(listOfSentMessages);
            }

        } catch (Exception e) {
            exception = e;
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

                listOfSentMessages.add(msg);

                counter++;

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + hostname
                            + ". Sent message with property count: " + counter
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID(), ex);
                } catch (JMSException e) {
                } // ignore 

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

    public static void main(String[] args) throws InterruptedException {

        ProducerClientAck producer = new ProducerClientAck("192.168.1.1", 4447, "jms/queue/InQueue", 10);
//        ProducerClientAck producer = new ProducerClientAck("192.168.1.3", 4447, "jms/queue/InQueue", 10000);
//        producer.setMessageBuilder(new MessageBuilderForInfo());
        producer.setMessageBuilder(new InfoMessageBuilder());
        producer.start();

        producer.join();
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
}
