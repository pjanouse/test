package org.jboss.qa.hornetq.apps.clients;


import org.jboss.qa.hornetq.Container;

import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;


public class NonDurableTopicSubscriberAutoAck extends NonDurableTopicSubscriber {

    public NonDurableTopicSubscriberAutoAck(Container container, String topicJndiName,
                                            long receiveTimeout, int maxRetries) {

        super(container, topicJndiName, receiveTimeout, maxRetries);
    }

    public NonDurableTopicSubscriberAutoAck(Container container, String topicJndiName) {

        super(container, topicJndiName, TimeUnit.SECONDS.toMillis(30), 5);
    }

    @Override
    protected Session createSession(Connection connection) throws JMSException {
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }
}
