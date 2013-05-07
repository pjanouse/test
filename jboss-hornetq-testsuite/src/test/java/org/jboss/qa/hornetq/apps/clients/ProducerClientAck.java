package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
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
 * Simple sender with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mnovak
 */
public class ProducerClientAck extends Client {

    private static final Logger logger = Logger.getLogger(ProducerClientAck.class);
    private int maxRetries = 10000;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue0";
    private int messages = 10000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    private int counter = 0;

    /**
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerClientAck(String hostname, int port, String queueNameJndi, int messages) {
        this(EAP6_CONTAINER, hostname, port, queueNameJndi, messages);
    }

    /**
     * @param container      EAP container
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerClientAck(String container, String hostname, int port, String queueNameJndi, int messages) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * Starts end messages to server. This should be started as Thread -
     * producer.start();
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

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

                Thread.sleep(getTimeout());

                logger.debug("Producer for node: " + hostname + "and queue: " + queueNameJndi + ". Sent message with property counter: "
                        + msg.getStringProperty("count") + ", messageId:" + msg.getJMSMessageID()
                        + ((msg.getStringProperty("_HQ_DUPL_ID") != null) ? ", _HQ_DUPL_ID=" + msg.getStringProperty("_HQ_DUPL_ID") :""));

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

                msg = cleanMessage(msg);

                addMessage(listOfSentMessages, msg);

                counter++;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + hostname
                            + ". Sent message with property count: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID()
                            + ((msg.getStringProperty("_HQ_DUPL_ID") != null) ? ", _HQ_DUPL_ID=" + msg.getStringProperty("_HQ_DUPL_ID") :""), ex);
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

    public static void main(String[] args) throws InterruptedException {

        ProducerClientAck producer = new ProducerClientAck("messaging-20", 4447, "jms/queue/testQueue0", 500);
//        ProducerClientAck producer = new ProducerClientAck("192.168.1.3", 4447, "jms/queue/InQueue", 10000);
//        producer.setMessageBuilder(new MessageBuilderForInfo());
        MessageBuilder builder = new TextMessageBuilder(10);
        builder.setAddDuplicatedHeader(true);
        producer.setMessageBuilder(builder);
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
