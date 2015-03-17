package org.jboss.qa.hornetq.apps.ejb;

import javax.annotation.Resource;
import javax.jms.Queue;

/**
 * Created by mnovak on 1/21/14.
 */
public interface SenderEJB {

    /**
     * Sends message to queue.
     *
     * @param inMessageId string to be set to "inMessageId" property
     * @param dupId set dup id
     */
    public void sendMessage(String inMessageId, String dupId);
}
