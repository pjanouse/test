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

public class ProducerTransAck extends Producer11 {

    private static final Logger logger = Logger.getLogger(ProducerTransAck.class);

    private int commitAfter = 10;

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerTransAck(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    public void run() {

        final int MESSAGES_COUNT = messages;

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

            Message msg = null;

            String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

            while (!stopSending.get() && counter < MESSAGES_COUNT) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                msg.setIntProperty("count", counter);

                sendMessage(producer, msg);

                listOfMessagesToBeCommited.add(msg);

                Thread.sleep(getTimeout());

                if (counter % commitAfter == 0) {

                    commitSession(session, producer);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (Message m : listOfMessagesToBeCommited) {
                        stringBuilder.append(m.getJMSMessageID());
                    }
                    logger.debug("Adding messages: " + stringBuilder.toString());
                    for (Message m : listOfMessagesToBeCommited) {
                        m = cleanMessage(m);
                        addMessage(listOfSentMessages, m);
                    }

                    logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                            + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
                    listOfMessagesToBeCommited.clear();

                }
            }

            commitSession(session, producer);

            StringBuilder stringBuilder = new StringBuilder();
            for (Message m : listOfMessagesToBeCommited) {
                stringBuilder.append(m.getJMSMessageID());
            }
            logger.debug("Adding messages: " + stringBuilder.toString());
            for (Message m : listOfMessagesToBeCommited) {
                m = cleanMessage(m);
                addMessage(listOfSentMessages, m);
            }
            logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                    + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(duplicatedHeader));
            listOfMessagesToBeCommited.clear();

            producer.close();

            addSendMessages(listOfSentMessages);

        } catch (Exception e) {

            logger.error("Producer got exception and ended:", e);
            exception = e;

        } finally {

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }
}
