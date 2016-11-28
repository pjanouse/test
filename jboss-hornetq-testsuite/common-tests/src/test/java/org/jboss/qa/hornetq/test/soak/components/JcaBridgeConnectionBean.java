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

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.test.soak.modules.BridgeSoakModule;
import org.jboss.qa.hornetq.test.soak.modules.RemoteJcaSoakModule;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@MessageDriven(name = "jca-bridge-connection-bean", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:/" + RemoteJcaSoakModule.JCA_OUT_QUEUE_JNDI)
})
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class JcaBridgeConnectionBean implements MessageListener {

    private final static Logger LOG = Logger.getLogger(JcaBridgeConnectionBean.class);

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/" + BridgeSoakModule.BRIDGE_IN_QUEUE_JNDI)
    private Queue targetQueue;


    @Override
    public void onMessage(final Message msg) {
        Connection connection = null;
        Session session = null;

        try {
            connection = this.connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(this.targetQueue);
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
