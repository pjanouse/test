package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple sender with auto acknowledge session, receiving responses to temporary queue
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 * <p/>
 * /**
 * Created by okalman on 8/13/14.
 */
public class ProducerResp extends Client {
    private static final Logger logger = Logger.getLogger(ProducerAutoAck.class);
    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = 4447;
    private String queueNameJndi = "jms/queue/testQueue1";
    private int messages = 1000;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Map<String, String>> listOfSentMessages = new ArrayList<Map<String, String>>();
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    private boolean securityEnabled = false;
    private String userName;
    private String password;
    private volatile TemporaryQueue tempQueue = null;
    private int count;
    private int waitBeforeReceive = 0;
    private boolean largeMessage = false;
    private boolean skipReceive = false;
    private String largeString="";

    /**
     * @param container     EAP container
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    public ProducerResp(Container container,String queueNameJndi, int messages){
        this(container,queueNameJndi,messages,0);

    }


    /**
     * @param container     EAP container
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param waitBeforeReceive sets delay before receiving replies with may cause expiration of replies with some mdbs (LocalMdbFromQueueToTempQueue)
     */
    public ProducerResp(Container container,String queueNameJndi, int messages, int waitBeforeReceive){
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
        this.waitBeforeReceive=waitBeforeReceive;

    }
    /**
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    @Deprecated
    public ProducerResp(String hostname, int port, String queueNameJndi, int messages) {
        this(EAP6_CONTAINER, hostname, port, queueNameJndi, messages);
    }

    /**
     * @param container     EAP container
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    @Deprecated
    public ProducerResp(String container, String hostname, int port, String queueNameJndi, int messages) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }
    /**
     * @param container     EAP container
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param waitBeforeReceive sets delay before receiving replies with may cause expiration of replies with some mdbs (LocalMdbFromQueueToTempQueue)
     */
    @Deprecated
    public ProducerResp(String container, String hostname, int port, String queueNameJndi, int messages, int waitBeforeReceive) {
        this(container, hostname, port, queueNameJndi, messages);
        this.waitBeforeReceive = waitBeforeReceive;
    }

    public void run()  {
        Context context = null;

        QueueConnection queueConnection = null;

        QueueSession session = null;

        String sMessage = new String();

        try {
            context = getContext(hostname, port);


            Queue queue = (Queue) context.lookup(queueNameJndi);

            QueueConnectionFactory cf = (QueueConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            queueConnection = cf.createQueueConnection();

            session = queueConnection.createQueueSession(false, QueueSession.AUTO_ACKNOWLEDGE);

            tempQueue = session.createTemporaryQueue();


            MessageProducer producer = session.createProducer(queue);

            Message msg;
            while (counter < messages && !stop) {

                msg = messageBuilder.createMessage(session);
                // send message in while cycle
                if(largeMessage) {
                    msg.setStringProperty("largeContent", largeString);

                }
                msg.setJMSReplyTo(tempQueue);
                if (waitBeforeReceive > 0) {
                    //ttl of replies will be same es thread sleep before receiving starts, message may expire
                    msg.setIntProperty("ttl", waitBeforeReceive);
                }
                sendMessage(producer, msg);

                Thread.sleep(getTimeout());

                logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            QueueReceiver receiver = session.createReceiver(tempQueue);
            queueConnection.start();
            Message response;
            if(waitBeforeReceive!=0){
                Thread.sleep(waitBeforeReceive+60000);
            }

            if(skipReceive==false) {
                while ((response = receiver.receive(10000)) != null) {
                    count++;
                    Thread.sleep(getTimeout() + 200);
                }
            }

        } catch (Exception e) {
           setException(e);
        } finally {
            try {
                queueConnection.close();
            } catch (Exception e) {
                logger.error(e);
            }
        }


    }

    private void sendMessage(MessageProducer producer, Message msg) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            try {

                producer.send(msg);

//                listOfSentMessages.add(msg);
                addMessage(listOfSentMessages, msg);

                counter++;

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + getHostname()
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException ignored) {
                } // ignore

                numberOfRetries++;
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
    public List<Map<String, String>> getListOfSentMessages() {
        return listOfSentMessages;
    }

    /**
     * @param listOfSentMessages the listOfSentMessages to set
     */
    public void setListOfSentMessages(List<Map<String, String>> listOfSentMessages) {
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

    public static void main(String[] args) throws InterruptedException {

        ProducerAutoAck producer = new ProducerAutoAck("192.168.1.1", 4447, "jms/queue/testQueue0", 10000);

        producer.start();

        producer.join();

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


    /**
     * @return tempQueue ist initialized after client starts
     */
    public TemporaryQueue getTempQueue() {
        return tempQueue;
    }

    public synchronized void countRecievedIncrement() {
        count++;
    }

    public synchronized int getRecievedCount() {
        return count;
    }

    /**
     * If not set, default value is false
     * @param b
     */
    public void setUseLargeMessage(boolean b){
        largeMessage=b;
        StringBuilder sb = new StringBuilder("");
        if(b=true && largeString.length()<1) {
            while (sb.length() < 200000) {
                sb.append("some really interesting text ");
            }
            largeString=sb.toString();
        }

    }

    /**
     * If not set default value is false
     * @param b if true receiver will not try to get responses from temporary queue
     */
    public void setSkipReceive(boolean b){
        skipReceive=b;
    }


}
