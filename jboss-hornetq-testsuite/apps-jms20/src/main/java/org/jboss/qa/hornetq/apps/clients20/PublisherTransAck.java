package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;

public class PublisherTransAck extends Producer20 {

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

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Topic topic = (Topic) context.lookup(destinationNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED)) {
                jmsContext.setClientID(clientId);
                JMSProducer publisher = jmsContext.createProducer();

                Message msg = null;

                String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

                while (!stopSending.get() && counter < messages) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", counter);

                    sendMessage(publisher, topic, msg);

                    listOfMessagesToBeCommited.add(msg);

                    Thread.sleep(getTimeout());

                    if (counter % commitAfter == 0) {

                        commitJMSContext(jmsContext, topic, publisher);
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

                commitJMSContext(jmsContext, topic, publisher);

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


                addSendMessages(listOfSentMessages);
            }



        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

        } finally {
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
