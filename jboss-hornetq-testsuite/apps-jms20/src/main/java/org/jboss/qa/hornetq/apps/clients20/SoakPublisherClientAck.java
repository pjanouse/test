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

/**
 * Publisher with client acknowledge session. Able to fail over.
 * <p/>
 * This class extends thread class and should be started as a thread using start().
 *
 * @author mnovak@redhat.com
 */
public class SoakPublisherClientAck extends Producer20 {

    private static final Logger logger = Logger.getLogger(SoakPublisherClientAck.class);

    private String clientId;

    /**
     * @param container         EAP container
     * @param topicNameJndi     jndi name of topic to publish messages
     * @param messages          number of messages to send
     * @param clientId          ID of client
     */
    public SoakPublisherClientAck(Container container, String topicNameJndi, int messages, String clientId) {
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

            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                Topic topic = (Topic) context.lookup(getDestinationNameJndi());

                jmsContext.setClientID(clientId);

                JMSProducer publisher = jmsContext.createProducer();

                Message msg = null;

                while (!stopSending.get() && counter < messages) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    // send message in while cycle
                    sendMessage(publisher, topic, msg);
                    msg = cleanMessage(msg);
                    addMessage(listOfSentMessages, msg);

                    logger.info("Publisher with clientId: " + clientId + " for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

                }
            }


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


