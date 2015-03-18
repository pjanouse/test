package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.UUID;

/**
 * Created by mnovak on 2/9/15.
 */
public class ColoredMessagesBuilder implements MessageBuilder {

    private static final Logger logger = Logger.getLogger(ColoredMessagesBuilder.class);

    // Counter of messages
    private int counter = 0;

    public int size = 1024 * 30;

    private boolean dupId = true;

    /**
     *
     * @param size size in KB
     */
    public ColoredMessagesBuilder(int size) {
        this.size = size * 512; // every char is encoded into 2 bytes so size * 1024/2 = size * 512
    }

    @Override
    public synchronized Message createMessage(Session session) throws Exception {

        TextMessage message = session.createTextMessage();
        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);

        if (isAddDuplicatedHeader()) {
            message.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()));
        }
        if (this.size > 0) {
            message.setText(new String(new char[this.size]));
        }

        if (counter % 2 == 0) {
            message.setStringProperty("color", "RED");
        } else {
            message.setStringProperty("color", "GREEN");
        }

        logger.info("Sending message with counter: " + this.counter + ", messageId: " + message.getJMSMessageID() +
                "_HQ_DUPL_ID: " + message.getStringProperty("_HQ_DUPL_ID") + " and color: " + message.getStringProperty("color"));

        return message;
    }

    @Override
    public void setAddDuplicatedHeader(boolean duplHeader) {
            this.dupId = duplHeader;
    }

    @Override
    public boolean isAddDuplicatedHeader() {
        return dupId;
    }
}