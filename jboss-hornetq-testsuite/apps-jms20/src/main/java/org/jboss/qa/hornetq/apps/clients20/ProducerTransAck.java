package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProducerTransAck extends Producer20 {

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

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(destinationNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED)) {
                JMSProducer producer = jmsContext.createProducer();
                Message msg = null;

                while (!stopSending.get() && counter < MESSAGES_COUNT) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", counter);

                    sendMessage(producer, queue, msg);

                    listOfMessagesToBeCommited.add(msg);

                    Thread.sleep(getTimeout());

                    if (counter % commitAfter == 0) {

                        commitJMSContext(jmsContext, queue, producer);
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
                                + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                        listOfMessagesToBeCommited.clear();

                    }
                }

                commitJMSContext(jmsContext, queue, producer);

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
                        + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                listOfMessagesToBeCommited.clear();

                addSendMessages(listOfSentMessages);
            }




        } catch (Exception e) {

            logger.error("Producer got exception and ended:", e);
            exception = e;

        } finally {

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
