package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.Message;
import javax.jms.Session;
import java.util.UUID;

/**
 * Message builder which sets group messadge id to each message it creates.
 */
public class GroupMessageBuilder implements MessageBuilder {

    private static final Logger log = Logger.getLogger(GroupMessageBuilder.class);

    String groupMessageId = "DefaultGroupMessageId";

    boolean addDuplicatedHeader = false;

    public GroupMessageBuilder(String groupMessageId) {
        this.groupMessageId = groupMessageId;
    }

    @Override
    public Message createMessage(Session session) throws Exception {
        Message m = session.createTextMessage("message");
        m.setStringProperty("JMSXGroupID", groupMessageId);

        if (isAddDuplicatedHeader()) {
            m.setStringProperty("_HQ_DUPL_ID", String.valueOf(UUID.randomUUID()));
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
