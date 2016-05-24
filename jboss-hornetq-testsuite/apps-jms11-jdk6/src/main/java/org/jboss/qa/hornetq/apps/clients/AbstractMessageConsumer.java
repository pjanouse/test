package org.jboss.qa.hornetq.apps.clients;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.Context;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;


/**
 * Base class for topic consumers, both durable and non-durable
 */
abstract class AbstractMessageConsumer extends Receiver {

    private static final Logger LOG = Logger.getLogger(AbstractMessageConsumer.class);

    protected FinalTestMessageVerifier verifier;
    protected List<Map<String, String>> listOfReceivedMessages = new ArrayList<Map<String, String>>();
    protected int count = 0;
    protected Exception exception;

    protected Context context;
    protected Connection connection;
    protected Session session;
    protected Destination destination;

    protected MessageConsumer consumer;

    protected AbstractMessageConsumer(Container container,
                                      String destinationJndiName, long receiveTimeout, int maxRetries) {

        super(container, destinationJndiName, receiveTimeout, maxRetries);
    }

    @Override
    public void run() {
        try {
            context = getContext(hostname, port);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            connection = createConnection(cf);

            destination = (Destination) context.lookup(destinationNameJndi);

            session = createSession(connection);
            consumer = createConsumer(session);

            Message msg;
            while ((msg = receiveMessage(consumer)) != null) {
                Thread.sleep(getTimeout());
                addMessage(listOfReceivedMessages, msg);
                count++;

                LOG.debug(receiveLogEntry(msg));
            }

            LOG.info(receivingFinishedLogEntry());

            if (verifier != null) {
                verifier.addReceivedMessages(listOfReceivedMessages);
            }
        } catch (JMSException ex) {
            LOG.error("JMS exception was thrown during receiving messages", ex);
            exception = ex;
        } catch (Exception ex) {
            LOG.error("Exception was thrown during receiving messages", ex);
            exception = ex;
        } finally {
            JMSTools.cleanupResources(context, connection, session);
        }
    }

    protected Connection createConnection(ConnectionFactory cf) throws JMSException {
        Connection c = cf.createConnection();
        c.start();
        return c;
    }

    abstract protected Session createSession(Connection connection) throws JMSException;

    abstract protected MessageConsumer createConsumer(Session session) throws JMSException;

    private Message receiveMessage(MessageConsumer consumer) throws Exception {
        Message msg;
        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                msg = consumer.receive(receiveTimeout);
                postReceive(msg);
                return msg;
            } catch (JMSException ex) {
                numberOfRetries++;
                LOG.error(retryLogEntry(), ex);
            }
        }

        throw new Exception(receiveFailureLogEntry());
    }

    protected void postReceive(Message receivedMsg) throws Exception {
    }

    protected String receiveLogEntry(Message msg) {
        String msgId;
        try {
            msgId = msg.getJMSMessageID();
        } catch (JMSException ex) {
            LOG.error("Cannot read message id from received message");
            msgId = "(unknown)";
        }

        return "Consumer for node " + hostname + " and destination " + destinationNameJndi
                + " received message - count " + count + ", messageId " + msgId;
    }

    protected String receivingFinishedLogEntry() {
        return "Consumer for node " + hostname + " and destination " + destinationNameJndi
                + " received NULL - number of received messages is " + count;
    }

    protected String retryLogEntry() {
        return "RETRY receive for host " + hostname + " and destination " + destinationNameJndi
                + ", trying to receive message with counter " + (count + 1);
    }

    protected String receiveFailureLogEntry() {
        return "FAILURE max retry reached for receiver for host " + hostname + " and destination"
                + destinationNameJndi;
    }
}
