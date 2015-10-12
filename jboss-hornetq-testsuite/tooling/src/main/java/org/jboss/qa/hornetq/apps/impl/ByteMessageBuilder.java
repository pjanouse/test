package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.BytesMessage;
import javax.jms.Message;
import java.util.UUID;

/**
 * Creates new byte JMS messages with required size
 *
 * @author pslavice@redhat.com
 */
public class ByteMessageBuilder implements MessageBuilder {

    // Counter for messages
    private int counter;

    // Required size
    private int size;

    private boolean addDuplicatedHeader = true;

    /**
     *
     * @return if header for message duplication will be added
     */
    public boolean isAddDuplicatedHeader() {
        return addDuplicatedHeader;
    }

    /**
     *
     * if header for message duplication will be added
     *
     * @param addDuplicatedHeader
     */
    public void setAddDuplicatedHeader(boolean addDuplicatedHeader) {
        this.addDuplicatedHeader = addDuplicatedHeader;
    }


    public ByteMessageBuilder() {
        this.size = 0;
    }

    public ByteMessageBuilder(int size) {
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
        BytesMessage message = messageCreator.createBytesMessage();
        message.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()) + counter);
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
        if (isAddDuplicatedHeader())    {
            message.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()));
        }

        if (this.size > 0) {
            byte[] data = new byte[this.size];
            message.writeBytes(data);
        }
        return message;
    }
}

