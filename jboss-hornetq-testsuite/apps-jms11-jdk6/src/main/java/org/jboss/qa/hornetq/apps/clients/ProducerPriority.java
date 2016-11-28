package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;

import java.util.Random;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Simple sender with auto acknowledge session. Able to fail over. Possibility to set priority of producer
 * <p>
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mstyk
 */
public class ProducerPriority extends Producer11 {

    private static final Logger logger = Logger.getLogger(ProducerPriority.class);
    private Random random = new Random();
    private Integer producerPriority = null;

    /**
     * @param container     container instance
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    public ProducerPriority(Container container, String queueNameJndi, int messages) {
        super(container, queueNameJndi, messages);
    }

    public void setProducerPriority(Integer producerPriority) {
        this.producerPriority = producerPriority;
    }

    /**
     * Starts end messages to server. This should be started as Thread -
     * producer.start();
     */
    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(destinationNameJndi);

            con = getConnection(cf);

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            Message msg;

            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);

                if (producerPriority != null) {
                    producer.setPriority(producerPriority);
                } else {
                    int rand = random.nextInt(10);
                    logger.info("Sendind message with priority " + rand);
                    producer.setPriority(rand);
                }

                // send message in while cycle
                sendMessage(producer, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                Thread.sleep(getTimeout());

                logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            producer.close();

            addSendMessages(listOfSentMessages);

        } catch (Exception e) {
            exception = e;
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
}
