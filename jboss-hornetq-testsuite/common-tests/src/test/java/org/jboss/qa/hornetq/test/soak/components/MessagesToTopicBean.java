package org.jboss.qa.hornetq.test.soak.components;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;


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

    private static AtomicInteger counter = new AtomicInteger();

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

            if (counter.incrementAndGet() % 100 == 0) {
                LOG.info("MessagesToTopicBean - processed " + counter.get() + " messages, messageId: " + msg.getJMSMessageID());
            }

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
