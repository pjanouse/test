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
public class PublisherTransAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(PublisherTransAck.class);

    private int commitAfter = 100;
    private String clientId;

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param topicNameJndi  set jndi name of the topic to send messages
     */
    public PublisherTransAck(Container container, String topicNameJndi, int messages, String clientId) {
        super(container, topicNameJndi, messages);
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

            Topic topic = (Topic) context.lookup(destinationNameJndi);

            con = getConnection(cf);

            con.setClientID(clientId);

            session = con.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer publisher = session.createProducer(topic);

            Message msg = null;

            String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                msg.setIntProperty("count", counter);

                sendMessage(publisher, msg);

                listOfMessagesToBeCommited.add(msg);

                Thread.sleep(getTimeout());

                if (counter % commitAfter == 0) {

                    commitSession(session, publisher);
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

            commitSession(session, publisher);

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


            addSendMessages(listOfSentMessages);

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

}

