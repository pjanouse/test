package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

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
     * @see {@link org.jboss.qa.hornetq.apps.MessageBuilder#createMessage(javax.jms.Session)}
     */
    @Override
    public Message createMessage(Session session) throws Exception {
        TextMessage message = session.createTextMessage();
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
        if (this.size > 0) {
            message.setText(new String(new char[this.size]));
        }
        return message;
    }
    
}
