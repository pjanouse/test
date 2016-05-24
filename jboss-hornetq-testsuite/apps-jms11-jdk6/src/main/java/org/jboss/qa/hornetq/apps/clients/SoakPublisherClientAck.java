package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Publisher with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class SoakPublisherClientAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(SoakPublisherClientAck.class);

    private String clientId;

    public SoakPublisherClientAck(Container container, String topicNameJndi, int messages, String clientId) {
        super(container, topicNameJndi, messages);
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

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer publisher = session.createProducer(topic);

            Message msg = null;

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                // send message in while cycle
                sendMessage(publisher, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                logger.info("Publisher with clientId: " + clientId + " for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            publisher.close();

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


