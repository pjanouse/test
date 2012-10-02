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

/**
 * Simple sender with transaction acknowledge session. Able to fail over.
 * <p/>
 * This class extends Thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class PublisherTransAck extends Client {

    private static final Logger logger = Logger.getLogger(PublisherTransAck.class);
    private int maxRetries = 30;
    private String hostname;
    private int port;
    private String topicNameJndi;
    private int messages;
    private int commitAfter = 100;
    private MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    private List<Message> listOfSentMessages = new ArrayList<Message>();
    private List<Message> listOfMessagesToBeCommited = new ArrayList<Message>();
    private List<FinalTestMessageVerifier> messageVerifiers;
    private Exception exception = null;
    private String clientId;
    private boolean stop = false;

    private int counter = 0;

    /**
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherTransAck(String hostname, int port, String topicNameJndi, int messages, String clientId) {
        this(EAP6_CONTAINER,hostname, port, topicNameJndi, messages, clientId);
    }

    /**
     * @param container     EAP container
     * @param hostname       hostname
     * @param port           port
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherTransAck(String container, String hostname, int port, String topicNameJndi, int messages, String clientId) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.topicNameJndi = topicNameJndi;
        this.clientId = clientId;
    }


    /**
     * Starts end messages to server. This should be started as Thread - publisher.start();
     */
    @Override
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Topic topic = (Topic) context.lookup(topicNameJndi);

            con = cf.createConnection();

            con.setClientID(clientId);

            session = con.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer publisher = session.createProducer(topic);

            Message msg = null;

            while (counter < messages && !stop) {

                msg = messageBuilder.createMessage(session);

                // send message in while cycle
                sendMessage(publisher, msg);

                if (counter % commitAfter == 0) {

                    commitSession(session, publisher);

                }

            }

            commitSession(session, publisher);

            publisher.close();

            if (messageVerifiers != null) {
                for (FinalTestMessageVerifier verifier : messageVerifiers) {
                    verifier.addSendMessages(listOfSentMessages);
                }
            }

        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

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
     * @param publisher
     * @param msg
     */
    private void sendMessage(MessageProducer publisher, Message msg) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            try {

                publisher.send(msg);

                counter++;

                logger.info("Publisher for node: " + hostname + ". Sent message: " + counter + ", messageId:" + msg.getJMSMessageID());

                listOfMessagesToBeCommited.add(msg);

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Publisher for node: " + getHostname()
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {
                } // ignore

                numberOfRetries++;
            }
        }

        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for publisher for node: " + getHostname()
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
     * @return the messageVerifiers
     */
    public List<FinalTestMessageVerifier> getMessageVerifiers() {
        return messageVerifiers;
    }

    /**
     * @param messageVerifier list of message verifiers from each subscriber
     */
    public void setMessageVerifiers(List<FinalTestMessageVerifier> messageVerifier) {
        this.messageVerifiers = messageVerifier;
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
     * @param session   session
     * @param publisher publisher
     * @throws Exception
     */
    private void commitSession(Session session, MessageProducer publisher) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            // try commit
            try {

                session.commit();

                logger.info("COMMIT - Publisher for node: " + getHostname()
                        + ". Sent message with property count: " + counter);

                listOfSentMessages.addAll(listOfMessagesToBeCommited);

                listOfMessagesToBeCommited.clear();

                return;

            } catch (TransactionRolledBackException ex) {
                logger.error("COMMIT Failed - Publisher for node: " + getHostname()
                        + ". Sent message with property count: " + counter + " doing RollBack and retrying send", ex);

                counter = counter - listOfMessagesToBeCommited.size();
                numberOfRetries++;

                resendMessages(publisher);
            } catch (JMSException ex) {
                logger.error("COMMIT failed but transaction rollback exception was NOT thrown - this means that publisher "
                        + "is not able to determine whether commit was successful. Commit will be retried but messages will not be resent."
                        + "Publisher for node: " + getHostname()
                        + ". Sent message with property count: " + counter, ex);
                numberOfRetries++;
            }
        }
        // maxretry reached then throw exception above
        throw new Exception("FAILURE in COMMIT - MaxRetry reached for publisher for node: " + getHostname()
                + ". Sent message with property count: " + counter);

    }

    /**
     * Resends messages when transaction rollback exception was thrown.
     *
     * @param publisher publisher
     */
    private void resendMessages(MessageProducer publisher) throws Exception {

        // there can be problem during resending messages so we need to know which messages were resend
        for (Message msg : listOfMessagesToBeCommited) {

            resendMessage(publisher, msg);

        }
    }

    private void resendMessage(MessageProducer publisher, Message msg) throws Exception {

        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {

            try {

                publisher.send(msg);

                counter++;

                logger.info("Publisher resent message for node: " + hostname + ". Sent message: " + counter + ", messageId:" + msg.getJMSMessageID());

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Publisher for node: " + getHostname()
                            + ". Sent message with property count: " + counter
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {
                } // ignore

                numberOfRetries++;
            }
        }

        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for publisher for node: " + getHostname()
                + ". During SEND (not commit) of message with property count: " + counter
                + ", messageId:" + msg.getJMSMessageID());
    }

    /**
     * @return the topicNameJndi
     */
    public String getTopicNameJndi() {
        return topicNameJndi;
    }

    /**
     * @param topicNameJndi the topicNameJndi to set
     */
    public void setTopicNameJndi(String topicNameJndi) {
        this.topicNameJndi = topicNameJndi;
    }

    /**
     * @return the clientId
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @param clientId the clientId to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    public static void main(String[] args) throws InterruptedException {

        PublisherTransAck publisher = new PublisherTransAck("192.168.1.1", 4447, "jms/topic/testTopic0", 20000, "supercoolclientId1");

        publisher.start();

        publisher.join();
    }


}

