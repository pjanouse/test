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

public class ProducerClientAck extends Producer20 {

    private static final Logger logger = Logger.getLogger(ProducerClientAck.class);

    /**
     * Creates a producer to queue with client knowledge.
     * @param container container to which to connect
     * @param queueNameJndi jndi name of the queue
     * @param messages number of messages
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

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            logger.info("Producer for node: " + hostname + ". Do lookup for queue: " + destinationNameJndi);
            Queue queue = (Queue) context.lookup(destinationNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
                JMSProducer producer = jmsContext.createProducer();

                Message msg = null;

                String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

                while (!stopSending.get() && counter < messages) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", counter);

                    // send message in while cycle
                    sendMessage(producer, queue, msg);
                    msg = cleanMessage(msg);
                    addMessage(listOfSentMessages, msg);

                    logger.info("Producer for node: " + hostname + "and queue: " + destinationNameJndi + ". Sent message with property counter: "
                            + counter + ", messageId:" + msg.getJMSMessageID()
                            + ((msg.getStringProperty(duplicatedHeader) != null) ? ", " + duplicatedHeader + "=" + msg.getStringProperty(duplicatedHeader) :""));

                    Thread.sleep(getTimeout());
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
                } catch (NamingException e) {
                }
            }
        }
    }

}
