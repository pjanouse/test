package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TransactionRolledBackException;

public class Producer11 extends Producer {

    private static final Logger logger = Logger.getLogger(Producer11.class);

    public Producer11(Container container, String destinationNameJndi, int messages) {
        super(container, destinationNameJndi, messages, 10000);
    }

    @Deprecated
    public Producer11(String container, String hostname, int jndiPort, String destinationNameJndi, int messages) {
        super(container, hostname, jndiPort, destinationNameJndi, messages);
    }

    /**
     * Send message to server. Try send message and if succeed than return. If
     * send fails and exception is thrown it tries send again until max retry is
     * reached. Then throws new Exception.
     *
     * @param producer producer
     * @param msg message
     */
    protected void sendMessage(MessageProducer producer, Message msg) throws Exception {
        int numberOfRetries = 0;

        while (running.get() && numberOfRetries < maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                producer.send(msg);
                counter++;
                return;

            } catch (JMSException ex) {
                logger.error("Failed to send message - counter: " + counter, ex);
            }
        }
        if (running.get()) {
            // this is an error - here we should never be because max retrie expired
            throw new Exception("FAILURE - MaxRetry reached for producer for node: " + getHostname()
                    + ". Sent message with property count: " + counter
                    + ", messageId:" + msg.getJMSMessageID());
        } else {
            throw new Exception("Producer was stopped.");
        }
    }

    protected void commitSession(Session session, MessageProducer producer) throws Exception {
        int numberOfRetries = 0;
        int numberOfRollbacks = 0;
        while (true) {
            try {

                // try commit
                session.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackException ex) {
                // if transaction rollback exception -> send messages again and commit
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once, this can't happen
                if (numberOfRollbacks > 0) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(producer, m);
                }

                numberOfRollbacks++;

            } catch (JMSException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0 && ex.getCause().getMessage().contains("Duplicate message detected")) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(producer, m);
                }
                numberOfRetries++;
            }
        }
    }

    /**
     * Returns connection.
     *
     * @param cf
     * @return connection
     * @throws javax.jms.JMSException
     */
    protected Connection getConnection(ConnectionFactory cf) throws JMSException {

        // if there is username and password and security enabled then use it
        if (getUserName() != null && !"".equals(getUserName()) && getPassword() != null) {
            return cf.createConnection(getUserName(), getPassword());

        }
        // else it's guest user or security disabled
        return cf.createConnection();
    }
}
