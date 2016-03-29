package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
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
import java.util.Map;

/**
 * Created by eduda on 4.8.2015.
 */
public class ProducerTransAck extends Client {

    private static final Logger logger = Logger.getLogger(ProducerTransAck.class);
    private static int maxRetries = 50;

    JMSProducer producer;

    private List<Map<String, String>> listOfSentMessages = new ArrayList<Map<String, String>>();
    private List<Message> listOfMessagesToBeCommited = new ArrayList<Message>();

    private String hostname;
    private int port;
    private int messages;
    private String queueNameJndi;
    private boolean stop = false;
    private Exception exception = null;

    private int commitAfter = 10;

    private FinalTestMessageVerifier messageVerifier;
    private MessageBuilder messageBuilder = new TextMessageBuilder(10);

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerTransAck(Container container, String queueNameJndi, int messages) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }

    public void run() {

        final int MESSAGES_COUNT = messages;

        Context context = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(queueNameJndi);

            try (JMSContext jmsContext = cf.createContext(JMSContext.SESSION_TRANSACTED)) {
                producer = jmsContext.createProducer();
                Message msg = null;

                while (counter < MESSAGES_COUNT && !stop) {

                    msg = messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    msg.setIntProperty("count", counter);

                    sendMessage(queue, msg);

                    listOfMessagesToBeCommited.add(msg);

                    Thread.sleep(getTimeout());

                    if (counter % commitAfter == 0) {

                        commitJMSContext(jmsContext, queue);
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Message m : listOfMessagesToBeCommited) {
                            stringBuilder.append(m.getJMSMessageID());
                        }
                        logger.debug("Adding messages: " + stringBuilder.toString());
                        for (Message m : listOfMessagesToBeCommited) {
                            m = cleanMessage(m);
                            addMessage(listOfSentMessages, m);
                        }

                        logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                                + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                        listOfMessagesToBeCommited.clear();

                    }
                }

                commitJMSContext(jmsContext, queue);

                StringBuilder stringBuilder = new StringBuilder();
                for (Message m : listOfMessagesToBeCommited) {
                    stringBuilder.append(m.getJMSMessageID());
                }
                logger.debug("Adding messages: " + stringBuilder.toString());
                for (Message m : listOfMessagesToBeCommited) {
                    m = cleanMessage(m);
                    addMessage(listOfSentMessages, m);
                }
                logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                        + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                listOfMessagesToBeCommited.clear();

                if (messageVerifier != null) {
                    messageVerifier.addSendMessages(listOfSentMessages);
                }
            }




        } catch (Exception e) {

            logger.error("Producer got exception and ended:", e);
            exception = e;

        } finally {

            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(Destination destination, Message msg) {
        int numberOfRetries = 0;

        while (numberOfRetries <= maxRetries) {
            try {
                if (numberOfRetries > 0) {
                    logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
                }
                numberOfRetries++;
                producer.send(destination, msg);
                logger.debug("Sent message with property counter: " + counter + ", messageId:" + msg.getJMSMessageID()
                        + " dupId: " + msg.getStringProperty(jmsImplementation.getDuplicatedHeader()));
                counter++;
                //if successful -> finish method
                return;
            } catch (JMSRuntimeException ex) {
                //resend
                logger.error("Failed to send message - counter: " + counter, ex);
            } catch (JMSException e) {
                //do not try to resend
                logger.warn(e);
                return;
            }
        }
        //in case of maxRetries reached
        try {
            throw new Exception("Number of retries (" + numberOfRetries + ") is greater than limit (" + maxRetries + ").");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void commitJMSContext(JMSContext jmsContext, Destination destination) throws Exception {
        int numberOfRetries = 0;
        while (true) {
            try {

                // try commit
                jmsContext.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackRuntimeException ex) {
                // if transaction rollback exception -> send messages again and commit
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once, this can't happen
                if (numberOfRetries > 2) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(destination, m);
                }

                numberOfRetries++;

            } catch (JMSRuntimeException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                ex.printStackTrace();

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0 && ex.getCause().getMessage().contains("Duplicate message detected")) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(destination, m);
                }
                numberOfRetries++;
            }
        }
    }


    public void setMessageVerifier(FinalTestMessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

    public List<Map<String, String>> getListOfSentMessages() {
        logger.debug("listOfSentMessages" + listOfSentMessages.size());
        return listOfSentMessages;
    }

    public void stopSending() {
        this.stop = true;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
    }

    @Override
    public int getCount() {
        return counter;
    }

}
