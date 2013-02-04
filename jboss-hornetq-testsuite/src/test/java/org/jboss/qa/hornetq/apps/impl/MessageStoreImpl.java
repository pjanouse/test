// TODO finish this class
package org.jboss.qa.hornetq.apps.impl;

import javax.jms.Message;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: mnovak
 * Date: 2/2/13
 * Time: 3:58 PM
 *
 * This class is used to store messages into file by JMS clients.
 *
 * Saves each message on separate line in a form of:
 * 1. When HQ_DUP_HEADER is set:
 * MessageID, HQ_DUP_HEADER
 * 2. When it's not set:
 * MessageID, NO_HEADER
 *
 */
public class MessageStoreImpl {

    /**
     * Save messages from list to a file
     * @param messages list of messages
     * @param toFile file
     */
    public void save(List<Message> messages, File toFile) {

    }

    /**
     * Save messages from list to a file
     * @param messages messages
     * @param toFile file
     */
    public void save(Map<String, String> messages, File toFile) {

    }

    /**
     * Load messages from file
     * @param fromFile
     * @return
     */
    public Map<String, String> load(File fromFile, int numberOfMessages)  {
        return null;
    }

}
