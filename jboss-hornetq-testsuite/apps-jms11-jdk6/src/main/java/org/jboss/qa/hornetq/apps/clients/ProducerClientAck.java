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
 * Simple sender with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mnovak
 */
public class ProducerClientAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(ProducerClientAck.class);

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerClientAck(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
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

            logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + destinationNameJndi);
            Queue queue = (Queue) context.lookup(destinationNameJndi);

            con = cf.createConnection();

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            Message msg = null;

            String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                msg.setIntProperty("count", counter);

                // send message in while cycle
                sendMessage(producer, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                logger.info("Producer for node: " + hostname + "and queue: " + destinationNameJndi + ". Sent message with property counter: "
                        + counter + ", messageId:" + msg.getJMSMessageID()
                        + ((msg.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + msg.getStringProperty(duplicatedHeader) :""));

                Thread.sleep(getTimeout());
            }

            producer.close();

            addSendMessages(listOfSentMessages);

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
}
