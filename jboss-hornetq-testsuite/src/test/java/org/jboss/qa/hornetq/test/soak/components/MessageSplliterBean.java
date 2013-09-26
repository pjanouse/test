package org.jboss.qa.hornetq.test.soak.components;


import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.test.soak.modules.BridgeSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.DurableSubscriptionsSoakModule;


/**
 * Bean that takes messages from bridge module output and copies them to output queue and topic.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@MessageDriven(name = "message-splitter-bean", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination",
                              propertyValue = "java:/" + BridgeSoakModule.BRIDGE_OUT_QUEUE_JNDI)
})
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class MessageSplliterBean implements MessageListener {

    private static final Logger LOG = Logger.getLogger(MessageSplliterBean.class);

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/" + DurableSubscriptionsSoakModule.DURABLE_MESSAGES_QUEUE_JNDI)
    private Queue queue;

    @Resource(mappedName = "java:/" + DurableSubscriptionsSoakModule.DURABLE_MESSAGES_TOPIC_JNDI)
    private Topic topic;


    @Override
    public void onMessage(Message msg) {
        Connection connection = null;
        Session session = null;

        try {
            connection = this.connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer queueProducer = session.createProducer(this.queue);
            MessageProducer topicProducer = session.createProducer(this.topic);

            queueProducer.send(msg);
            topicProducer.send(msg);
        } catch (JMSException ex) {
            LOG.error("Error while sending message", ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing JMS session", ex);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing JMS connection", ex);
                }
            }
        }
    }

}
