package org.jboss.qa.hornetq.apps.clients;

import java.security.Security;
import java.util.Observable;
import java.util.Properties;
import java.util.logging.Level;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.jboss.sasl.JBossSaslProvider;

/**
 * Simple sender with client acknowledge session. Able to fail over.
 *
 * @author mnovak
 */
public class ProducerClientAckHA extends Thread {

    static {
        Security.addProvider(new JBossSaslProvider());
    }
    private static final Logger logger = Logger.getLogger(ProducerClientAckHA.class);
    private int numberOfRetries = 0;
    private int maxRetries = 30;
    String hostname = "localhost";
    int jndiPort = 4447;
    String queueNameJndi = "queue/testQueue1";
    int numberOfMessages = 1000;
    long waitAfterMessage = 0;

    public ProducerClientAckHA(String queueName) {

        this.queueNameJndi = queueName;

    }

    public ProducerClientAckHA(String hostname, int jndiPort, String queueName) {

        this.hostname = hostname;

        this.jndiPort = jndiPort;

        this.queueNameJndi = queueName;

    }

    public ProducerClientAckHA(String hostname, int jndiPort, String queueName, int numberOfMessages, long waitAfterMessage) {

        this.hostname = hostname;

        this.jndiPort = jndiPort;

        this.queueNameJndi = queueName;

        this.numberOfMessages = numberOfMessages;

        this.waitAfterMessage = waitAfterMessage;

    }

    public void run() {

        Context context = null;

        Connection con = null;

        Session session = null;

        try {

            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + jndiPort);
            context = new InitialContext(env);

            ConnectionFactory cf = (ConnectionFactory) context.lookup("RemoteConnectionFactory");

            Queue queue = (Queue) context.lookup(queueNameJndi);

            con = cf.createConnection();

            session = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(queue);

            int i = 1;

            Message msg = null;

            while (i <= numberOfMessages) {

                msg = createMessage(session, i);
                // send message in while cycle
                sendMessage(producer, msg);

                logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + i + ", messageId:" + msg.getJMSMessageID());

                i++;

            }

            producer.close();

        } catch (Exception e) {

            logger.error("Producer got exception:", e);

        } finally {

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                }
            }
        }
    }

    private Message createMessage(Session session, int counter) throws Exception {

        // TODO - here provide some Abstraction - how to create message
        TextMessage message = session.createTextMessage("This is text message " + counter);

        message.setIntProperty("count", counter);

        return message;
    }

    /**
     * Send message to server. Try send message and if succeed than return. If
     * send fails and exception is thrown it tries send again until max retry is
     * reached. Then throws new Exception.
     *
     * @param producer
     * @param msg
     */
    private void sendMessage(MessageProducer producer, Message msg) throws Exception {

        while (numberOfRetries < maxRetries) {
            try {

                producer.send(msg);
                
                // notify all observers about sent message

                numberOfRetries = 0;

                return;

            } catch (JMSException ex) {

                try {
                    logger.info("SEND RETRY - Producer for node: " + hostname
                            + ". Sent message with property count: " + msg.getIntProperty("count")
                            + ", messageId:" + msg.getJMSMessageID());
                } catch (JMSException e) {} // ignore 

                numberOfRetries++;

            }
        }

        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for producer for node: " + hostname
                + ". Sent message with property count: " + msg.getIntProperty("count")
                + ", messageId:" + msg.getJMSMessageID());

    }
}

