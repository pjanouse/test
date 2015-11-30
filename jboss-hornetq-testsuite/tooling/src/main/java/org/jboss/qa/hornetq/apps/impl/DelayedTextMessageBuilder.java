package org.jboss.qa.hornetq.apps.impl;


import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;
import org.jboss.qa.hornetq.tools.RandomStringGenerator;

import javax.jms.Message;
import java.util.UUID;


/**
 * Builder for text messages with defined scheduled delivery delay.
 *
 * Delivery delay means the receiver will only read the messages after the specific
 * delay after they were sent to the destination.
 */
public class DelayedTextMessageBuilder implements MessageBuilder {

    private final int textLength;
    private final long messageDelay;

    private int counter = 0;
    private boolean duplicateHeader = false;

    public DelayedTextMessageBuilder(int textLength) {
        this(textLength, 10000);
    }

    public DelayedTextMessageBuilder(int textLength, long messageDelay) {
        this.textLength = textLength;
        this.messageDelay = messageDelay;
    }

    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        Message msg = messageCreator.createTextMessage(RandomStringGenerator.generateString(textLength));
        msg.setLongProperty(jmsImplementation.getScheduledDeliveryTimeHeader(), System.currentTimeMillis() + messageDelay);
        msg.setIntProperty(MESSAGE_COUNTER_PROPERTY, counter++);
        if (isAddDuplicatedHeader())    {
            String uuid = String.valueOf(UUID.randomUUID());
            msg.setStringProperty(jmsImplementation.getDuplicatedHeader(), uuid);
        }
        return msg;
    }

    @Override
    public void setAddDuplicatedHeader(boolean duplHeader) {
        this.duplicateHeader = duplHeader;
    }

    @Override
    public boolean isAddDuplicatedHeader() {
        return duplicateHeader;
    }
}
