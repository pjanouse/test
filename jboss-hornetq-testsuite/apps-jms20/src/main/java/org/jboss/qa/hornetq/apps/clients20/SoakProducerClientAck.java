package org.jboss.qa.hornetq.apps.clients20;

import org.jboss.logging.Logger;
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
public class SoakProducerClientAck extends Producer20 {

    private static final Logger logger = Logger.getLogger(SoakProducerClientAck.class);

    /**
     * @param container         EAP container
     * @param queueNameJndi     set jndi name of the queue to send messages
     * @param messages          number of messages to send
     */
    public SoakProducerClientAck(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    /**
     * Starts end messages to server. This should be started as Thread - producer.start();
     */
    public void run() {

        Context context = null;

        try {

            ConnectionFactory cf;

            context = getContext(hostname, port);
            cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            
            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {

                logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + destinationNameJndi);

                Queue queue = (Queue) context.lookup(destinationNameJndi);

                JMSProducer producer = jmsContext.createProducer();

                Message msg;

                while (!stopSending.get() && getCounter() < messages) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", getCounter());

                    // send message in while cycle
                    sendMessage(producer, queue, msg);
                    msg = cleanMessage(msg);
                    addMessage(listOfSentMessages, msg);

                    Thread.sleep(getTimeout());

                    logger.debug("Producer for node: " + hostname + "and queue: " + destinationNameJndi + ". Sent message with property my counter: " + getCounter()
                            + ", message-counter: " + msg.getStringProperty("counter") + ", messageId:" + msg.getJMSMessageID());
                }

            }


        } catch (Exception e) {
            exception = e;
            logger.error("Producer got exception and ended:", e);

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

