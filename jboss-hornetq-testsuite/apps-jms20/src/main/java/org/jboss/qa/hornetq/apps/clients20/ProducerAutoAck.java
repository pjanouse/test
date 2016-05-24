package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.NamingException;

public class ProducerAutoAck extends Producer20 {

    private static final Logger logger = Logger.getLogger(ProducerAutoAck.class);

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerAutoAck(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    /**
     * Starts end messages to server. This should be started as Thread -
     * producer.start();
     */
    public void run() {

        Context context = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(destinationNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                JMSProducer producer = jmsContext.createProducer();
                Message msg;

                while (!stopSending.get() && counter < messages) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    // send message in while cycle
                    sendMessage(producer, queue, msg);
                    msg = cleanMessage(msg);
                    addMessage(listOfSentMessages, msg);

                    Thread.sleep(getTimeout());

                    logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

                }

                addSendMessages(listOfSentMessages);
            }



        } catch (Exception e) {
            exception = e;
            logger.error("Producer got exception and ended:", e);

        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

}
