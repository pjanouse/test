package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.*;
import java.io.Serializable;

/**
 * Created by eduda on 3.8.2015.
 */
public class MessageCreator20 implements MessageCreator {

    protected JMSContext context;

    public MessageCreator20(JMSContext context) {
        this.context = context;
    }

    @Override
    public BytesMessage createBytesMessage() {
        return context.createBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() {
        return context.createMapMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() {
        return context.createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        return context.createObjectMessage(object);
    }

    @Override
    public StreamMessage createStreamMessage() {
        return context.createStreamMessage();
    }

    @Override
    public TextMessage createTextMessage() {
        return context.createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) {
        return context.createTextMessage(text);
    }
}
