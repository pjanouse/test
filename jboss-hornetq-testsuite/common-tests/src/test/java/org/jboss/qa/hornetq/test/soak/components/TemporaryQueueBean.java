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
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.test.soak.modules.TemporaryQueueSoakModule;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@MessageDriven(name = "temporary-queue-bean", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination",
                              propertyValue = "java:/" + TemporaryQueueSoakModule.TEMP_IN_QUEUE_JNDI)
})
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class TemporaryQueueBean implements MessageListener {

    private static final Logger LOG = Logger.getLogger(TemporaryQueueBean.class);

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory connectionFactory;


    @Override
    public void onMessage(final Message msg) {
        Connection connection = null;
        Session session = null;

        try {
            connection = this.connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            Destination target = msg.getJMSReplyTo();
            if (target == null) {
                LOG.error(String.format("Message is missing reply-to queue address (%s, %d)",
                        msg.getJMSMessageID(), msg.getIntProperty("counter")));
                return;
            }

            MessageProducer producer = session.createProducer(target);
            producer.send(msg);
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
