package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Publisher with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class PublisherAutoAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(PublisherAutoAck.class);

    private String clientId;

    /**
     * @param container      container instance
     * @param topicNameJndi  set jndi name of the topic to send messages
     * @param messages       number of messages to send
     * @param clientId       clientID
     */
    public PublisherAutoAck(Container container, String topicNameJndi, int messages, String clientId) {
        super(container, topicNameJndi, messages);
        this.clientId = clientId;
    }

    @Deprecated
    public PublisherAutoAck(String container, String hostname, int jndiPort, String topicNameJndi, int messages, String clientId) {
        super(container, hostname, jndiPort, topicNameJndi, messages);
        this.clientId = clientId;
    }

    /**
     * Starts end messages to server. This should be started as Thread - publisher.start();
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Topic topic = (Topic) context.lookup(getDestinationNameJndi());

            con = cf.createConnection();

            con.setClientID(clientId);

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer publisher = session.createProducer(topic);

            Message msg = null;

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                // send message in while cycle
                sendMessage(publisher, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                Thread.sleep(getTimeout());

                logger.debug("Publisher for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            publisher.close();

            addSendMessages(listOfSentMessages);

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
}


