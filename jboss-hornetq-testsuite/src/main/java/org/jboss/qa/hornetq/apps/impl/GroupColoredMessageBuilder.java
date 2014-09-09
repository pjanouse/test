package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.Message;
import javax.jms.Session;
import java.util.UUID;

/**
 * Created by okalman on 9/3/14.
 */
public class GroupColoredMessageBuilder implements MessageBuilder {
    private static final Logger log = Logger.getLogger(GroupColoredMessageBuilder.class);

    private String groupMessageId = "DefaultGroupMessageId";
    private String color = "RED";

    private boolean addDuplicatedHeader = true;

    public GroupColoredMessageBuilder(String groupMessageId, String color) {
        this.groupMessageId = groupMessageId;
        this.color= color;
    }

    @Override
    public Message createMessage(Session session) throws Exception {
        Message m = session.createTextMessage("message");
        m.setStringProperty("JMSXGroupID", groupMessageId);
        m.setStringProperty("color", color);

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
