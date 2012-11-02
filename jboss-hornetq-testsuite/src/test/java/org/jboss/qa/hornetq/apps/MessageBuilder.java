package org.jboss.qa.hornetq.apps;

import javax.jms.Message;
import javax.jms.Session;

/**
 * Creates new JMS messages with required properties
 *
 * @author pslavice@redhat.com
 */
public interface MessageBuilder {

    static final String MESSAGE_COUNTER_PROPERTY = "counter";

    Message createMessage(Session session) throws Exception;

    void setAddDuplicatedHeader(boolean duplHeader);

    boolean isAddDuplicatedHeader();
}
