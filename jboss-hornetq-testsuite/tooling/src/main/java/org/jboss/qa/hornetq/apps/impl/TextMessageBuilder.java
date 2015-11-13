package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.Map;
import java.util.UUID;

/**
 * Creates new byte JMS text messages with required size
 *
 * @author pslavice@redhat.com
 */
public class TextMessageBuilder implements MessageBuilder {

    // Counter for messages
    private int counter;

    // Required size
    private int size;

    private boolean addDuplicatedHeader = true;

    private Map<String,String> jndiProperties = null;

    /**
     * @return if header for message duplication will be added
     */
    public boolean isAddDuplicatedHeader() {
        return addDuplicatedHeader;
    }

    /**
     * if header for message duplication will be added
     *
     * @param addDuplicatedHeader
     */
    public void setAddDuplicatedHeader(boolean addDuplicatedHeader) {
        this.addDuplicatedHeader = addDuplicatedHeader;
    }

    public TextMessageBuilder() {
        this.size = 0;
    }

    public TextMessageBuilder(int size) {
        this.size = size;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    /**
     * @see {@link MessageBuilder#createMessage(MessageCreator, JMSImplementation)}
     * @param messageCreator
     * @param jmsImplementation
     */
    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        TextMessage message = messageCreator.createTextMessage();
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
        //        message.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()));
        if (isAddDuplicatedHeader()) {
            message.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()));
        }
        if (this.size > 0) {
            message.setText(new String(new char[this.size]));
//            message.setStringProperty("text", new String(new char[this.size]));
        }
        if (jndiProperties != null && jndiProperties.size() > 0)    {
            message = (TextMessage) MessageUtils.setPropertiesToMessage(jndiProperties, message);
        }
        return message;
    }

    public void setJndiProperties(Map<String, String> jndiProperties) {
        this.jndiProperties = jndiProperties;
    }
}
