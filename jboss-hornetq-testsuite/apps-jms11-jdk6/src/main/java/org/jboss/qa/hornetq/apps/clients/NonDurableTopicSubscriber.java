package org.jboss.qa.hornetq.apps.clients;


import org.jboss.qa.hornetq.Container;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;


abstract public class NonDurableTopicSubscriber extends AbstractTopicSubscriber {

    protected NonDurableTopicSubscriber(Container container,
                                        String destinationJndiName, long receiveTimeout, int maxRetries) {

        super(container, destinationJndiName, receiveTimeout, maxRetries);
    }

    @Override
    protected MessageConsumer createConsumer(Session session) throws JMSException {
        return session.createConsumer(destination);
    }

}
