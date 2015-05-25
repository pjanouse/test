package org.jboss.qa.hornetq.apps.clients;


import org.jboss.qa.hornetq.Container;

abstract class AbstractTopicSubscriber extends AbstractMessageConsumer {

    @Deprecated
    protected AbstractTopicSubscriber(String containerType, String hostname, int port,
            String topicJndiName, long receiveTimeout, int maxRetries) {

        super(containerType, hostname, port, topicJndiName, receiveTimeout, maxRetries);
    }

    protected AbstractTopicSubscriber(Container container,
                                      String topicJndiName, long receiveTimeout, int maxRetries) {

        super(container,topicJndiName, receiveTimeout, maxRetries);
    }

    public String getTopicJndiName() {
        return getDestinationJndiName();
    }

}
