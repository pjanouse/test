package org.jboss.qa.hornetq.apps.impl;


import java.util.UUID;
import javax.jms.Message;
import javax.jms.Session;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.tools.RandomStringGenerator;


/**
 * Builder for text messages with defined scheduled delivery delay.
 *
 * Delivery delay means the receiver will only read the messages after the specific
 * delay after they were sent to the destination.
 */
public class DelayedTextMessageBuilder implements MessageBuilder {

    private static final String SCHEDULED_DELIVER_HEADER =
            org.hornetq.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME.toString();
    private static final String DUPLICATE_ID_HEADER =
            org.hornetq.api.core.Message.HDR_DUPLICATE_DETECTION_ID.toString();

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
    public synchronized Message createMessage(Session session) throws Exception {
        Message msg = session.createTextMessage(RandomStringGenerator.generateString(textLength));
        msg.setLongProperty(SCHEDULED_DELIVER_HEADER, System.currentTimeMillis() + messageDelay);
        msg.setIntProperty(MESSAGE_COUNTER_PROPERTY, counter++);
        if (isAddDuplicatedHeader())    {
            msg.setStringProperty(DUPLICATE_ID_HEADER, String.valueOf(UUID.randomUUID()));
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
