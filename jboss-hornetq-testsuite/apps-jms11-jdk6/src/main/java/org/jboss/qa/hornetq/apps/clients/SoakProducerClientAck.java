package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Simple sender with client acknowledge session. Able to fail over.
 * <p/>
 * This producer does not remember all the send messages, just message id. This is for
 * memory reasons.
 * <p/>
 * This class extends Thread class and should be started as a thread using start().
 *
 * @author mnovak
 */
public class SoakProducerClientAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(SoakProducerClientAck.class);

    public SoakProducerClientAck(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    /**
     * Starts end messages to server. This should be started as Thread - producer.start();
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            ConnectionFactory cf;

            context = getContext(hostname, port);
            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + destinationNameJndi);

            Queue queue = (Queue) context.lookup(destinationNameJndi);

            con = cf.createConnection();

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            Message msg;

            while (!stopSending.get() && getCounter() < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                msg.setIntProperty("count", getCounter());

                // send message in while cycle
                sendMessage(producer, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                Thread.sleep(getTimeout());

                if (getCounter() % 1000 == 0) {
                    logger.info("Producer for node: " + hostname + "and queue: " + destinationNameJndi + ". Sent message with property my counter: " + getCounter()
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());
                } else {
                    logger.debug("Producer for node: " + hostname + "and queue: " + destinationNameJndi + ". Sent message with property my counter: " + getCounter()
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());
                }
            }

            producer.close();

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

