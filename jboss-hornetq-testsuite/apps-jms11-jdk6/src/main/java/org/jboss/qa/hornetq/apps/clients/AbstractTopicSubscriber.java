package org.jboss.qa.hornetq.apps.clients;


import org.jboss.qa.hornetq.Container;

abstract class AbstractTopicSubscriber extends AbstractMessageConsumer {

    protected AbstractTopicSubscriber(Container container,
                                      String topicJndiName, long receiveTimeout, int maxRetries) {

        super(container,topicJndiName, receiveTimeout, maxRetries);
    }

}
