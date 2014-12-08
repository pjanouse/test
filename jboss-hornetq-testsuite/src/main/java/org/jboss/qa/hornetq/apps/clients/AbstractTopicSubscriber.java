package org.jboss.qa.hornetq.apps.clients;


abstract class AbstractTopicSubscriber extends AbstractMessageConsumer {

    protected AbstractTopicSubscriber(String containerType, String hostname, int port,
            String topicJndiName, long receiveTimeout, int maxRetries) {

        super(containerType, hostname, port, topicJndiName, receiveTimeout, maxRetries);
    }

    public String getTopicJndiName() {
        return getDestinationJndiName();
    }

}
