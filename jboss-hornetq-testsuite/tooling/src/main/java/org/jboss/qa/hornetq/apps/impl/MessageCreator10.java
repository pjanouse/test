package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.*;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Created by eduda on 3.8.2015.
 */
public class MessageCreator10 implements MessageCreator {

    private static final Logger LOG = Logger.getLogger(MessageCreator10.class.getName());

    Session session;

    public MessageCreator10(Session session) {
        this.session = session;
    }

    @Override
    public BytesMessage createBytesMessage() {
        try {
            return session.createBytesMessage();
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public MapMessage createMapMessage() {
        try {
            return session.createMapMessage();
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public ObjectMessage createObjectMessage() {
        try {
            return session.createObjectMessage();
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        try {
            return session.createObjectMessage(object);
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public StreamMessage createStreamMessage() {
        try {
            return session.createStreamMessage();
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public TextMessage createTextMessage() {
        try {
            return session.createTextMessage();
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public TextMessage createTextMessage(String text) {
        try {
            return session.createTextMessage(text);
        } catch (JMSException e) {
            LOG.severe(e.toString());
            throw new RuntimeException(e);
        }
    }
}
