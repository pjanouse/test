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
 * Simple sender with transaction acknowledge session. Able to fail over.
 * <p/>
 * This class extends Thread class and should be started as a thread using start().
 *
 * @author mnovak
 */
public class ProducerTransAckOld extends Producer11 {

    private static final Logger logger = Logger.getLogger(ProducerTransAckOld.class);

    private int commitAfter = 1000;

    public ProducerTransAckOld(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    /**
     * Starts end messages to server. This should be started as Thread - producer.start();
     */
    @Override
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(destinationNameJndi);

            con = cf.createConnection();

            session = con.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(queue);

            Message msg;

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);

                // send message in while cycle
                sendMessage(producer, msg);

                Thread.sleep(getTimeout());

                if (counter % commitAfter == 0) {

                    commitSession(session, producer);

                }
            }

            commitSession(session, producer);

            producer.close();

            addSendMessages(listOfSentMessages);

        } catch (Exception e) {
            exception = e;
            e.printStackTrace();
            logger.error("Producer got exception and ended:", e);

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

}

