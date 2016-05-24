package org.jboss.qa.hornetq.apps.clients20;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.MessageCreator20;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of the simple JMS client who is able to send and receive messages to or from server
 *
 * @author pslavice@redhat.com
 */
public class SimpleJMSClient extends Client {

    // Logger
    private static final Logger log = Logger.getLogger(SimpleJMSClient.class);

    private MessageBuilder messageBuilder;

    private int messages;
    private int sentMessages;
    private int ackMode;
    private int receivedMessages;
    private int receiveTimeout = 10000;
    private boolean rollbackOnly;

    private Exception exceptionDuringSend;
    private Exception exceptionDuringReceive;

    private List<Message> listOfSentMesages = new LinkedList<Message>();
    private List<Message> listOfReceivedMessages = new LinkedList<Message>();

    /**
     * Constructor
     *
     * @param container         container
     * @param messages           count of messages to be send
     * @param ackMode            acknowledge mode
     * @param messageBuilder     messages builder used for building messages
     */
    public SimpleJMSClient(Container container, int messages, int ackMode, MessageBuilder messageBuilder) {
        super(container, null, 0, 10);
        this.messages = messages;
        this.ackMode = ackMode;
        this.messageBuilder = messageBuilder;
    }

    /**
     * Constructor
     *
     * @param messages           count of messages to be send
     * @param ackMode            acknowledge mode
     */
    public SimpleJMSClient(Container container, int messages, int ackMode) {
        this(container, messages, ackMode, new ByteMessageBuilder());
    }

    /**
     * Sends messages to server
     *
     * @param queueJNDIName JNDI name of target queue
     */
    public void sendMessages(String queueJNDIName) {
        Context context = null;

        if (this.messageBuilder == null) {
            this.messageBuilder = new ByteMessageBuilder();
        }

        try {
            context = getContext(hostname, port);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            Queue queue = (Queue) context.lookup(queueJNDIName);

            try (JMSContext jmsContext = cf.createContext(this.ackMode)) {
                JMSProducer producer = jmsContext.createProducer();
                for (int i = 0; i < this.messages; i++) {
                    Message message = this.messageBuilder.createMessage(new MessageCreator20(jmsContext), jmsImplementation);
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Sending '%s'. message with id '%s'", i, message.getJMSMessageID()));
                    }
                    producer.send(queue, message);
                    listOfSentMesages.add(message);
                    if (this.ackMode == Session.CLIENT_ACKNOWLEDGE) {
                        message.acknowledge();
                    }
                    if (!(this.ackMode == JMSContext.SESSION_TRANSACTED)) {
                        this.sentMessages++;
                    }
                }
                if (this.ackMode == JMSContext.SESSION_TRANSACTED) {
                    if (!this.rollbackOnly) {
                        jmsContext.commit();
                        this.sentMessages = this.messages;
                    } else {
                        jmsContext.rollback();
                        this.sentMessages = 0;
                    }
                }
                log.info(String.format("Sent '%s' messages", this.sentMessages));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            this.exceptionDuringSend = e;
        } finally {
            cleanupResources(context, null);
        }
    }

    /**
     * Receives messages from queue
     *
     * @param queueJNDIName JNDI name of the queue
     */
    public void receiveMessages(String queueJNDIName) {
        Context context = null;
        JMSContext jmsContext = null;
        try {
            context = getContext(hostname, port);
            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());
            Queue queue = (Queue) context.lookup(queueJNDIName);

            jmsContext = cf.createContext(this.ackMode);
            jmsContext.start();

            Message message;
            JMSConsumer consumer = jmsContext.createConsumer(queue);
            int counter = 0;
            while ((message = consumer.receive(this.receiveTimeout)) != null) {
                if (this.ackMode == Session.CLIENT_ACKNOWLEDGE) {
                    message.acknowledge();
                }
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Received '%s'. message with id '%s'", counter++, message.getJMSMessageID()));
                }
                this.receivedMessages++;
                listOfReceivedMessages.add(message);
            }
            if (this.ackMode == JMSContext.SESSION_TRANSACTED) {
                if (!this.rollbackOnly) {
                    jmsContext.commit();
                } else {
                    jmsContext.rollback();
                    this.receivedMessages = 0;
                }
            }
            consumer.close();
            log.info(String.format("Received '%s' messages", this.receivedMessages));

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            this.exceptionDuringReceive = e;
            this.receivedMessages = 0;
            if (jmsContext != null && this.ackMode == JMSContext.SESSION_TRANSACTED) {
                try {
                    jmsContext.rollback();
                } catch (Exception ex) {
                    // Ignore it
                }
            }
        } finally {
            cleanupResources(context, jmsContext);
        }
    }

    /**
     * Sends and receives messages
     */
    public void sendAndReceiveMessages(String queue) {
        this.sendMessages(queue);
        this.receiveMessages(queue);
    }

    /**
     * Cleanups resources
     *
     * @param context    initial context
     */
    private void cleanupResources(Context context, JMSContext jmsContext) {
        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
                // Ignore it
            }
        }
        if (jmsContext != null) {
            jmsContext.close();
        }
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    public int getMessages() {
        return messages;
    }

    public void setMessages(int messages) {
        this.messages = messages;
    }

    public int getSentMessages() {
        return sentMessages;
    }

    public void setSentMessages(int sentMessages) {
        this.sentMessages = sentMessages;
    }

    public int getAckMode() {
        return ackMode;
    }

    public void setAckMode(int ackMode) {
        this.ackMode = ackMode;
    }

    public int getReceivedMessages() {
        return receivedMessages;
    }

    public void setReceivedMessages(int receivedMessages) {
        this.receivedMessages = receivedMessages;
    }

    public int getReceiveTimeout() {
        return receiveTimeout;
    }

    public void setReceiveTimeout(int receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    public Exception getExceptionDuringSend() {
        return exceptionDuringSend;
    }

    public void setExceptionDuringSend(Exception exceptionDuringSend) {
        this.exceptionDuringSend = exceptionDuringSend;
    }

    public Exception getExceptionDuringReceive() {
        return exceptionDuringReceive;
    }

    public void setExceptionDuringReceive(Exception exceptionDuringReceive) {
        this.exceptionDuringReceive = exceptionDuringReceive;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    public void setRollbackOnly(boolean rollbackOnly) {
        this.rollbackOnly = rollbackOnly;
    }

    public List<Message> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    public void setListOfReceivedMessages(List<Message> listOfReceivedMessages) {
        this.listOfReceivedMessages = listOfReceivedMessages;
    }

    public List<Message> getListOfSentMesages() {
        return listOfSentMesages;
    }

    public void setListOfSentMesages(List<Message> listOfSentMesages) {
        this.listOfSentMesages = listOfSentMesages;
    }
}
