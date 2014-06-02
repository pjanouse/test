package org.jboss.qa.hornetq.apps.impl;

import javax.jms.Message;
import javax.jms.Session;

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


    public Message createMessage(Session session) throws Exception {

        Message m = super.createMessage(session);

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
