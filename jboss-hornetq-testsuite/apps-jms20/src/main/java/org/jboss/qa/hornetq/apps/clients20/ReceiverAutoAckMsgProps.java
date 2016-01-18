package org.jboss.qa.hornetq.apps.clients20;

import org.jboss.qa.hornetq.Container;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Receiver which collect defined properties from messages.
 */
public class ReceiverAutoAckMsgProps extends ReceiverAutoAck {

    protected List<String> msgProperties;

    public ReceiverAutoAckMsgProps(Container container, String queueJndiName, List<String> msgProps) {
        super(container, queueJndiName);
        this.msgProperties = msgProps;
    }

    public ReceiverAutoAckMsgProps(Container container, String queueJndiName, List<String> msgProps, long receiveTimeOut, int maxRetries) {
        super(container, queueJndiName, receiveTimeOut, maxRetries);
        this.msgProperties = msgProps;
    }

    @Override
    protected void addMessage(List<Map<String, String>> listOfReceivedMessages, Message message) throws JMSException {
        Map<String, String> mapOfPropertiesOfTheMessage = new HashMap<String,String>();

        for (String prop : msgProperties) {
            if (message.getStringProperty(prop) != null) {
                mapOfPropertiesOfTheMessage.put(prop, message.getStringProperty(prop));
            }
        }
        listOfReceivedMessages.add(mapOfPropertiesOfTheMessage);
    }
}
