package org.jboss.qa.hornetq.test.soak.clients;


import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.test.soak.modules.DurableSubscriptionsSoakModule;
import org.jboss.qa.tools.ContainerInfo;
import org.jboss.qa.tools.jms.ClientUtils;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class DurableSubscriptionClient extends Client {

    private static final Logger LOG = Logger.getLogger(DurableSubscriptionClient.class);

    private static final int MESSAGES_READ_BEFORE_RECONNECT = 50;

    private final String subscriptionName = UUID.randomUUID().toString();

    private final String clientName = "durable-subscription-client-" + UUID.randomUUID().toString();

    private final ContainerInfo container;

    private final int port;

    private final List<String> receivedMessages = new LinkedList<String>();


    public DurableSubscriptionClient(final ContainerInfo container) {
        this(container, 4447);
    }


    public DurableSubscriptionClient(final ContainerInfo container, final int jndiPort) {
        super(container.getName());
        this.container = container;
        this.port = jndiPort;
    }


    @Override
    public void run() {
        Context ctx = null;
        Connection conn = null;
        Session session = null;

        try {
            ctx = this.getContext(this.container.getIpAddress(), this.port);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryJndiName());
            conn = cf.createConnection();
            conn.setClientID(this.clientName);
            conn.start();

            session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            Topic topic = (Topic) ctx.lookup(DurableSubscriptionsSoakModule.DURABLE_MESSAGES_TOPIC_JNDI);

            boolean needReconnect;
            do {
                needReconnect = this.readMessages(session, topic);
                LOG.info("needReconnect = " + needReconnect);
            } while (needReconnect);
        } catch (JMSException ex) {
            LOG.error("Error while running durable subscription consumer", ex);
        } catch (NamingException ex) {
            LOG.error("Error while running durable subscription consumer", ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the session", ex);
                }
            }

            if (conn != null) {
                try {
                    conn.stop();
                    conn.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the connection", ex);
                }
            }

            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.error("Error while closing the JNDI context", ex);
                }
            }
        }
    }


    public List<String> getReceivedMessages() {
        return this.receivedMessages;
    }


    /**
     *
     * @param session
     * @param topic
     *
     * @return Returns true if consumer read all messages before needing to reconnect.
     *
     * @throws JMSException
     */
    private boolean readMessages(final Session session, final Topic topic) throws JMSException {
        int reconnectionCounter = 0;

        LOG.info("readMessages called");
        TopicSubscriber consumer = null;
        try {
            consumer = session.createDurableSubscriber(topic, this.subscriptionName);
            Message msg;
            while (reconnectionCounter < MESSAGES_READ_BEFORE_RECONNECT
                    && (msg = ClientUtils.receiveMessage(consumer, this.counter)) != null) {

                this.counter++;
                reconnectionCounter++;

                LOG.debug("Receiver for topic " + topic.getTopicName()
                        + " received message with counter" + this.counter
                        + " and id " + msg.getJMSMessageID());
                msg.acknowledge();
                this.receivedMessages.add(msg.getStringProperty("_HQ_DUPL_ID"));
            }

            LOG.info("reconnectionCounter = " + reconnectionCounter);
            return reconnectionCounter == MESSAGES_READ_BEFORE_RECONNECT;
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

}