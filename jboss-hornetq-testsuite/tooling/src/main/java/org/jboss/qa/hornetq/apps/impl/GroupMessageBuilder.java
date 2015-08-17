package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;
import java.util.UUID;

/**
 * Message builder which sets group messadge id to each message it creates.
 */
public class GroupMessageBuilder implements MessageBuilder {

    private static final Logger log = Logger.getLogger(GroupMessageBuilder.class);

    String groupMessageId = "DefaultGroupMessageId";

    boolean addDuplicatedHeader = true;

    public GroupMessageBuilder(String groupMessageId) {
        this.groupMessageId = groupMessageId;
    }

    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        Message m = messageCreator.createTextMessage("message");
        m.setStringProperty("JMSXGroupID", groupMessageId);

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
}
