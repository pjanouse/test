package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.*;
import java.util.UUID;


/**
 * Creates new byte JMS messages with mixed size.
 *
 * @author mnovak@redhat.com
 */
public class MixMessageBuilder implements MessageBuilder {

    // Counter for messages
    private int counter = 0;

    private int modulo = 10;

    String content = null;

    private byte[] data = null;

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


    public MixMessageBuilder() {
        this.size = 0;
    }

    public MixMessageBuilder(int size) {
        this.size = size;
        content = new String(new char[size]);
        data = new byte[this.size];
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

        Message message = null;

        if (counter % modulo == 0) { //send large byte messge
            message = messageCreator.createBytesMessage();
            if (this.size > 0) {
                ((BytesMessage) message).writeBytes(data);
            }

        } else if (counter % modulo == 1) { // send lage text message
            message = messageCreator.createTextMessage();
            if (this.size > 0) {
                ((TextMessage) message).setText(content);
            }

        } else if (counter % modulo == 2) { // send lage object message
            message = messageCreator.createObjectMessage();
            if (this.size > 0) {
                ((ObjectMessage) message).setObject(content);
            }

        } else { // send normal message
            message = messageCreator.createTextMessage();
            ((TextMessage) message).setText("normal message:" + message.getJMSMessageID());

        }
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
        //        message.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()));
        if (isAddDuplicatedHeader())    {
            message.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()));
        }
        return message;
    }
}
