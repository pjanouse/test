package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

/**
 * Creates new byte JMS messages with required size
 *
 * @author pslavice@redhat.com
 */
public class FaultInjectionClient {

    // Logger
    private static final Logger log = Logger.getLogger(FaultInjectionClient.class);

    private String connectionFactoryName = "jms/RemoteConnectionFactory";
    private String hostname;
    private int port;

    private MessageBuilder messageBuilder;
    private MessageVerifier messageVerifier;

    private int messages;
    private int sentMessages;
    private int ackMode;
    private boolean transactionSession;
    private int receivedMessages;
    private int receiveTimeout = 1000;
    private boolean rollbackOnly;

    private Exception exceptionDuringSend;
    private Exception exceptionDuringReceive;

    /**
     * Constructor
     *
     * @param hostname           target host
     * @param port               target port
     * @param messages           count of messages to be send
     * @param ackMode            acknowledge mode
     * @param transactionSession is session transacted
     */
    public FaultInjectionClient(String hostname, int port, int messages, int ackMode, boolean transactionSession) {
        this(hostname, port, messages, ackMode, transactionSession, new ByteMessageBuilder());
    }

    /**
     * Constructor
     *
     * @param hostname           target host
     * @param port               target port
     * @param messages           count of messages to be send
     * @param ackMode            acknowledge mode
     * @param transactionSession is session transacted
     * @param messageBuilder     messages builder used for building messages
     */
    public FaultInjectionClient(String hostname, int port, int messages, int ackMode, boolean transactionSession, MessageBuilder messageBuilder) {
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.ackMode = ackMode;
        this.transactionSession = transactionSession;
        this.messageBuilder = messageBuilder;
    }

    /**
     * Returns context
     *
     * @return context
     * @throws NamingException if something is wrong
     */
    private Context getContext() throws NamingException {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", this.hostname, this.port));
        return new InitialContext(env);
    }

    /**
     * Sends messages to server
     *
     * @param queueJNDIName JNDI name of target queue
     */
    public void sendMessages(String queueJNDIName) {
        Context context = null;
        Connection connection = null;
        Session session = null;
        try {
            context = getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(this.connectionFactoryName);
            Queue queue = (Queue) context.lookup(queueJNDIName);
            connection = cf.createConnection();
            if (this.transactionSession) {
                session = connection.createSession(true, 0);
            } else {
                session = connection.createSession(false, this.ackMode);
            }
            MessageProducer producer = session.createProducer(queue);
            for (int i = 0; i < this.messages; i++) {
                Message message = this.messageBuilder.createMessage(session);
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Sending '%s'. message with id '%s'", i, message.getJMSMessageID()));
                }
                producer.send(message);
                if (this.ackMode == Session.CLIENT_ACKNOWLEDGE) {
                    message.acknowledge();
                }
                if (!this.transactionSession) {
                    this.sentMessages++;
                }
            }
            if (this.transactionSession) {
                if (!this.rollbackOnly) {
                    session.commit();
                    this.sentMessages = this.messages;
                } else {
                    session.rollback();
                    this.sentMessages = 0;
                }
            }
            producer.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            this.exceptionDuringSend = e;
            if (session != null && this.transactionSession) {
                try {
                    session.rollback();
                } catch (Exception ex) {
                    // Ignore it
                }
            }
        } finally {
            cleanupResources(context, connection, session);
        }
    }

    /**
     * Receives messages from queue
     *
     * @param queueJNDIName JNDI name of the queue
     */
    public void receiveMessages(String queueJNDIName) {
        Context context = null;
        Connection connection = null;
        Session session = null;
        try {
            context = getContext();
            ConnectionFactory cf = (ConnectionFactory) context.lookup(this.connectionFactoryName);
            Queue queue = (Queue) context.lookup(queueJNDIName);
            connection = cf.createConnection();
            if (this.transactionSession) {
                session = connection.createSession(true, 0);
            } else {
                session = connection.createSession(false, this.ackMode);
            }
            connection.start();

            Message message;
            MessageConsumer consumer = session.createConsumer(queue);
            int counter = 0;
            while ((message = consumer.receive(this.receiveTimeout)) != null) {
                if (this.messageVerifier != null) {
                    this.messageVerifier.verifyMessage(message);
                }
                if (this.ackMode == Session.CLIENT_ACKNOWLEDGE) {
                    message.acknowledge();
                }
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Received '%s'. message with id '%s'", counter++, message.getJMSMessageID()));
                }
                this.receivedMessages++;
            }
            if (this.transactionSession) {
                if (!this.rollbackOnly) {
                    session.commit();
                } else {
                    session.rollback();
                    this.receivedMessages = 0;
                }
            }
            consumer.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            this.exceptionDuringReceive = e;
            this.receivedMessages = 0;
            if (session != null && this.transactionSession) {
                try {
                    session.rollback();
                } catch (Exception ex) {
                    // Ignore it
                }
            }
        } finally {
            cleanupResources(context, connection, session);
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
     * @param connection connection to JMS server
     * @param session    JMS session
     */
    private void cleanupResources(Context context, Connection connection, Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                // Ignore it
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                // Ignore it
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (NamingException e) {
                // Ignore it
            }
        }
    }

    public String getConnectionFactoryName() {
        return connectionFactoryName;
    }

    public void setConnectionFactoryName(String connectionFactoryName) {
        this.connectionFactoryName = connectionFactoryName;
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

    public MessageVerifier getMessageVerifier() {
        return messageVerifier;
    }

    public void setMessageVerifier(MessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
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

    public boolean isTransactionSession() {
        return transactionSession;
    }

    public void setTransactionSession(boolean transactionSession) {
        this.transactionSession = transactionSession;
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
}
