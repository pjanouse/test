package org.jboss.qa.hornetq.apps.clients;


import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;


abstract public class NonDurableTopicSubscriber extends AbstractTopicSubscriber {

    protected NonDurableTopicSubscriber(String containerType, String hostname, int port,
            String destinationJndiName, long receiveTimeout, int maxRetries) {

        super(containerType, hostname, port, destinationJndiName, receiveTimeout, maxRetries);
    }

    @Override
    protected MessageConsumer createConsumer(Session session) throws JMSException {
        return session.createConsumer(destination);
    }

}
