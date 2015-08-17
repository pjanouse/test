package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;
import java.util.UUID;

/**
 * Created by okalman on 9/3/14.
 */
public class GroupColoredMessageBuilder implements MessageBuilder {
    private static final Logger log = Logger.getLogger(GroupColoredMessageBuilder.class);

    private String groupMessageId = "DefaultGroupMessageId";
    private String color = "RED";
    private boolean largeMessages=false;
    private boolean addDuplicatedHeader = true;

    public GroupColoredMessageBuilder(String groupMessageId, String color) {
        this.groupMessageId = groupMessageId;
        this.color= color;
    }
    public GroupColoredMessageBuilder(String groupMessageId, String color,boolean largeMessages) {
        this(groupMessageId,color);
        this.largeMessages=largeMessages;
    }

    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        Message m=null;
        if(largeMessages){
           String s= new String(new char[110*1024]);
            m = messageCreator.createTextMessage(s);
        }else{
           m = messageCreator.createTextMessage("message");
        }

        m.setStringProperty("JMSXGroupID", groupMessageId);
        m.setStringProperty("color", color);

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
