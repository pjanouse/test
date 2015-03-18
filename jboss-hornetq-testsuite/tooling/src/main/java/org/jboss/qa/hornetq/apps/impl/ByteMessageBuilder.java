package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.Session;
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
     * @see {@link MessageBuilder#createMessage(javax.jms.Session)}
     */
    @Override
    public synchronized Message createMessage(Session session) throws Exception {
        BytesMessage message = session.createBytesMessage();
        message.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()) + counter);
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
        if (isAddDuplicatedHeader())    {
            message.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()));
        }

        if (this.size > 0) {
            byte[] data = new byte[this.size];
            message.writeBytes(data);
        }
        return message;
    }
}
