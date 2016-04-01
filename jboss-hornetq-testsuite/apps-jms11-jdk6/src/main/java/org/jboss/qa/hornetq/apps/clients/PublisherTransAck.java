package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private List<Map<String,String>>listOfSentMessages = new ArrayList<Map<String,String>>();
    private List<Message>  listOfMessagesToBeCommited = new ArrayList<Message> ();
    private List<FinalTestMessageVerifier> messageVerifiers;
    private Exception exception = null;
    private String clientId;
    private boolean stop = false;

    MessageProducer publisher;

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
     * @param container      container instance
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherTransAck(Container container, String topicNameJndi, int messages, String clientId) {
        this(container.getContainerType().toString(), container.getHostname(), container.getJNDIPort(), topicNameJndi, messages, clientId);
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

            publisher = session.createProducer(topic);

            Message msg = null;

            String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

            while (counter < messages && !stop) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                msg.setIntProperty("count", counter);

                sendMessage(msg);

                listOfMessagesToBeCommited.add(msg);

                Thread.sleep(getTimeout());

                if (counter % commitAfter == 0) {

                    commitSession(session);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (Message m : listOfMessagesToBeCommited) {
                        stringBuilder.append(m.getJMSMessageID());
                    }
                    logger.debug("Adding messages: " + stringBuilder.toString());
                    for (Message m : listOfMessagesToBeCommited)    {
                        m = cleanMessage(m);
                        addMessage(listOfSentMessages,m);
                    }

                    logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                            + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
                    listOfMessagesToBeCommited.clear();

                }
            }

            commitSession(session);

            StringBuilder stringBuilder = new StringBuilder();
            for (Message m : listOfMessagesToBeCommited) {
                stringBuilder.append(m.getJMSMessageID());
            }
            logger.debug("Adding messages: " + stringBuilder.toString());
            for (Message m : listOfMessagesToBeCommited)    {
                m = cleanMessage(m);
                addMessage(listOfSentMessages,m);
            }
//                    StringBuilder stringBuilder2 = new StringBuilder();
//                    for (Map<String,String> m : listOfSentMessages) {
//                        stringBuilder2.append("messageId: " + m.get("messageId") + "dupId: " + m.get("_HQ_DUPL_ID") + "##");
//                    }
//                    logger.debug("List of sent messages: " + stringBuilder2.toString());
//                    listOfSentMessages.addAll(listOfMessagesToBeCommited);
            logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                    + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
            listOfMessagesToBeCommited.clear();


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
                } catch (JMSException ignored) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException ignored) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    private void sendMessage(Message msg) throws Exception {
        int numberOfRetries = 0;

        while (numberOfRetries <= maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                publisher.send(msg);
                logger.debug("Sent message with property counter: " + counter + ", messageId:" + msg.getJMSMessageID()
                        + " dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                counter++;
                return;

            } catch (JMSException ex) {
                logger.error("Failed to send message - counter: " + counter, ex);
            }
        }
        //in case of maxRetries reached
        throw new Exception("Number of retries (" + numberOfRetries + ") is greater than limit (" + maxRetries + ").");
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
    public List<Map<String,String>> getListOfSentMessages() {
        return listOfSentMessages;
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

//    /**
//     * Commits current session - commits messages which were sent.
//     *
//     * @param session   session
//     * @param publisher publisher
//     * @throws Exception
//     */
//    private void commitSession(Session session, MessageProducer publisher) throws Exception {
//
//        int numberOfRetries = 0;
//
//        while (numberOfRetries < maxRetries) {
//
//            // try commit
//            try {
//
//                session.commit();
//
//                logger.info("COMMIT - Publisher for node: " + getHostname()
//                        + ". Sent message with property count: " + counter);
//
//                StringBuilder stringBuilder = new StringBuilder();
//                for (Message m : listOfMessagesToBeCommited) {
//                    stringBuilder.append("messageId: ").append(m.getJMSMessageID()).append(", dupId: ").append(m.getStringProperty("_HQ_DUPL_ID"));
//                }
//
//                logger.debug("Adding messages: " + stringBuilder.toString());
//
//                for (Message m : listOfMessagesToBeCommited)    {
//
//                    m = cleanMessage(m);
//
//                    addMessage(listOfSentMessages, m);
//                }
//
//                listOfMessagesToBeCommited.clear();
//
//                return;
//
//            } catch (TransactionRolledBackException ex) {
//                logger.error("COMMIT Failed - Publisher for node: " + getHostname()
//                        + ". Sent message with property count: " + counter + " doing RollBack and retrying send", ex);
//
//                counter = counter - listOfMessagesToBeCommited.size();
//                numberOfRetries++;
//
//                resendMessages(publisher);
//            } catch (JMSException ex) {
//                if (numberOfRetries > 3)    {
//                    // this is actually JMSException called because we sent duplicates
//                    logger.error("COMMIT failed because duplicates were sent - server will throw away all duplicates. Publisher for node: " + getHostname()
//                            + ". Sent message with property count: " + counter, ex);
//                    return;
//                } else {
//                    logger.error("COMMIT failed but transaction rollback exception was NOT thrown - this means that publisher "
//                        + "is not able to determine whether commit was successful. Commit will be retried and messages WILL BE resent. " +
//                        "Server will throw away all duplicates. Publisher for node: " + getHostname()
//                        + ". Sent message with property count: " + counter, ex);
//                    numberOfRetries++;
//                    resendMessages(publisher);
//                }
//            }
//        }
//        // maxretry reached then throw exception above
//        throw new Exception("FAILURE in COMMIT - MaxRetry reached for publisher for node: " + getHostname()
//                + ". Sent message with property count: " + counter);
//
//    }

    private void commitSession(Session session) throws Exception {
        int numberOfRetries = 0;
        while (true) {
            try {

                // try commit
                session.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackException ex) {
                // if transaction rollback exception -> send messages again and commit
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once, this can't happen
                if (numberOfRetries > 2) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(m);
                }

                numberOfRetries++;

            } catch (JMSException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                ex.printStackTrace();

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0 && ex.getCause().getMessage().contains("Duplicate message detected")) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(m);
                }
                numberOfRetries++;
            }
        }
    }

//    /**
//     * Resends messages when transaction rollback exception was thrown.
//     *
//     * @param publisher publisher
//     */
//    private void resendMessages(MessageProducer publisher) throws Exception {
//
//        // there can be problem during resending messages so we need to know which messages were resend
//        for (Message msg : listOfMessagesToBeCommited) {
//
//            resendMessage(publisher, msg);
//
//        }
//    }
//
//    private void resendMessage(MessageProducer publisher, Message msg) throws Exception {
//
//        int numberOfRetries = 0;
//
//        while (numberOfRetries < maxRetries) {
//
//            try {
//
//                publisher.send(msg);
//
//                counter++;
//
//                logger.info("Publisher resent message for node: " + hostname + ". Sent message: " + counter + ", messageId:" + msg.getJMSMessageID());
//
//                numberOfRetries = 0;
//
//                return;
//
//            } catch (JMSException ex) {
//
//                try {
//                    logger.info("SEND RETRY - Publisher for node: " + getHostname()
//                            + ". Sent message with property count: " + counter
//                            + ", messageId:" + msg.getJMSMessageID());
//                } catch (JMSException ignored) {
//                } // ignore
//
//                numberOfRetries++;
//            }
//        }
//
//        // this is an error - here we should never be because max retrie expired
//        throw new Exception("FAILURE - MaxRetry reached for publisher for node: " + getHostname()
//                + ". During SEND (not commit) of message with property count: " + counter
//                + ", messageId:" + msg.getJMSMessageID());
//    }

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

    @Override
    public int getCount() {
        return counter;
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

        PublisherTransAck publisher = new PublisherTransAck("10.34.3.187", 4447, "jms/topic/InTopic", 200, "supercoolclientId1");

        publisher.start();

        publisher.join();
    }


}

