package org.jboss.qa.hornetq.apps.clients;


import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;


public class NonDurableTopicSubscriberAutoAck extends NonDurableTopicSubscriber {

    public NonDurableTopicSubscriberAutoAck(String hostname, int port, String topicJndiName) {
        super(EAP6_CONTAINER, hostname, port, topicJndiName, TimeUnit.SECONDS.toMillis(30), 5);
    }

    public NonDurableTopicSubscriberAutoAck(String containerType, String hostname, int port,
            String topicJndiName) {

        super(containerType, hostname, port, topicJndiName, TimeUnit.SECONDS.toMillis(30), 5);
    }

    public NonDurableTopicSubscriberAutoAck(String hostname, int port, String topicJndiName,
            long receiveTimeout, int maxRetries) {

        super(EAP6_CONTAINER, hostname, port, topicJndiName, receiveTimeout, maxRetries);
    }

    public NonDurableTopicSubscriberAutoAck(String containerType, String hostname, int port,
            String topicJndiName, long receiveTimeout, int maxRetries) {

        super(containerType, hostname, port, topicJndiName, receiveTimeout, maxRetries);
    }

    @Override
    protected Session createSession(Connection connection) throws JMSException {
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }
}
