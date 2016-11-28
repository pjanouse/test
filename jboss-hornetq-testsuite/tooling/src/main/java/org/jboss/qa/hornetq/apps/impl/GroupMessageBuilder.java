package org.jboss.qa.hornetq.apps.impl;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.UUID;

/**
 * Message builder which sets group messadge id to each message it creates.
 */
public class GroupMessageBuilder implements MessageBuilder {

    private static final Logger log = Logger.getLogger(GroupMessageBuilder.class);

    String groupMessageId = "DefaultGroupMessageId";

    boolean addDuplicatedHeader = true;

    private long size = 0;

    public GroupMessageBuilder(String groupMessageId) {
        this.groupMessageId = groupMessageId;
    }

    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        TextMessage m = messageCreator.createTextMessage("message");
        m.setStringProperty("JMSXGroupID", groupMessageId);

        if (size > 0)   {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < size/2; i++) {
                builder.append("a");
            }
            m.setText(builder.toString());
        }

        if (isAddDuplicatedHeader()) {
            m.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()));
        }

        log.info("Creating message with JMSXGroupID: " + m.getStringProperty("JMSXGroupID"));

        return m;
    }

    @Override
    public void setAddDuplicatedHeader(boolean duplHeader) {
        addDuplicatedHeader = duplHeader;
    }

    @Override
    public boolean isAddDuplicatedHeader() {
        return addDuplicatedHeader;
    }

    public long getSize() {
        return size;
    }

    /**
     * Set size of messages in bytes
     * @param size
     */
    public void setSize(long size) {
        this.size = size;
    }
}
