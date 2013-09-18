package org.jboss.qa.hornetq.test.soak.components;


import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@Stateless
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class MessagesToTopicBean {

    private static final Logger LOG = Logger.getLogger(MessagesToTopicBean.class);

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/" + EjbSoakModule.EJB_OUT_QUEUE_JNDI)
    private Queue outQueue;

    @Resource(mappedName = "java:/" + EjbSoakModule.EJB_OUT_TOPIC_JNDI)
    private Topic outTopic;

    public void resendMessage(final Message msg) {
        Connection connection = null;
        Session session = null;

        try {
            connection = this.connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageProducer queueProducer = session.createProducer(this.outQueue);
            MessageProducer topicProducer = session.createProducer(this.outTopic);

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
