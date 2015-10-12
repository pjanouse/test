package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;

/**
 * Created by mnovak on 5/26/14.
 */
public class MixMessageGroupMessageBuilder extends ClientMixMessageBuilder {

    private String groupMessageId = "groupMessageId1";

    public MixMessageGroupMessageBuilder() {
        super();
    }

    public MixMessageGroupMessageBuilder(int sizeNormal, int sizeLarge, String groupMessageId) {
        super(sizeNormal, sizeLarge);
        this.groupMessageId = groupMessageId;
    }


    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {

        Message m = super.createMessage(messageCreator, jmsImplementation);

        m.setStringProperty("JMSXGroupID", groupMessageId);

        return m;

    }

    public String getGroupMessageId() {
        return groupMessageId;
    }

    public void setGroupMessageId(String groupMessageId) {
        this.groupMessageId = groupMessageId;
    }
}
