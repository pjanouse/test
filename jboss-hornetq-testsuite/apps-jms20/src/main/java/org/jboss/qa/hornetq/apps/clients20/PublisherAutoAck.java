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

public class PublisherAutoAck extends Producer20 {

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

    /**
     * Starts end messages to server. This should be started as Thread - publisher.start();
     */
    public void run() {

        Context context = null;

        try {
            context = getContext(hostname, port);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            Topic topic = (Topic) context.lookup(getDestinationNameJndi());

            try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                jmsContext.setClientID(clientId);
                JMSProducer publisher = jmsContext.createProducer();
                Message msg = null;

                while (!stopSending.get() && counter < messages) {
                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    // send message in while cycle
                    sendMessage(publisher, topic, msg);
                    msg = cleanMessage(msg);
                    addMessage(listOfSentMessages, msg);

                    Thread.sleep(getTimeout());

                    logger.debug("Publisher for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());
                }
            }

            addSendMessages(listOfSentMessages);

        } catch (Exception e) {
            exception = e;
            logger.error("Publisher got exception and ended:", e);

        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                }
            }
        }
    }

}
