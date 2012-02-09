package org.jboss.qa.hornetq.apps;

import javax.jms.Message;
import javax.jms.Session;

/**
 * Creates new JMS messages with required properties
 *
 * @author pslavice@redhat.com
 */
public interface MessageBuilder {

    Message createMessage(Session session) throws Exception;

}
