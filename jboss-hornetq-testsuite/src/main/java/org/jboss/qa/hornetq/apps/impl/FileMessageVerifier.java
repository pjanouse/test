package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;

import java.io.File;
import java.util.*;

/**
 * Load messages from file and compare them.
 */
public class FileMessageVerifier {

    private static final Logger logger = Logger.getLogger(FileMessageVerifier.class);

    private File sentMessagesFile;
    private File receivedMessagesFile;

//    private Map<String,String> sentMessages = new HashMap<String, String>();
//    private Map<String,String> receivedMessages = new HashMap<String, String>();

    /**
     * Returns true if all messages are ok = there are equal number of sent and received messages.
     *
     * @return true if there is equal number of sent and received messages
     * @throws Exception
     */
    public boolean verifyMessages() throws Exception {

        boolean isOk = true;

        MessageStoreImpl store = new MessageStoreImpl();

        if (store.load(receivedMessagesFile).size() != store.load(sentMessagesFile).size()) {
            logger.error("There is different number of sent and received messages");
            isOk = false;
        }

        // set of lost messages -- (sendMessages - receivedMessages) = lostMessages
        if (getLostMessages().size() != 0) {
            logger.error("Lost message detected: " + getLostMessages());
            isOk = false;
        }

        // set of duplicated messages --  (receivedMessages - sendMessages) = lostMessages
        if (getDuplicatedMessages().size() != 0) {
            logger.error("Duplicated message detected: " + getDuplicatedMessages());
            isOk = false;
        }

        return isOk;
    }

    /**
     * Returns list of lost messages.
     *
     * @return list of lost messages or empty list if there are no lost messages
     */
    private List<String> getLostMessages() throws Exception {
        MessageStoreImpl messageStore = new MessageStoreImpl();

        // get sent messageIds
        Set<String> sentMessageIds = messageStore.load(sentMessagesFile).keySet();

        // get received messageIds
        Set<String> receivedMessageIds = messageStore.load(receivedMessagesFile).keySet();

        // put all received messages ids to set
        Set<String> helpSet = new HashSet<String>();
        for (String receivedMessageId : receivedMessageIds) {
            helpSet.add(receivedMessageId);
        }
        // put all sent messageIds to set > when true for add() on set then lost
        List<String> lostMessages = new ArrayList<String>();
        for (String sentMessageId : sentMessageIds) {
            if (helpSet.add(sentMessageId)) { // if it can be added return true then id is new and it means this message was not received -> lost
                lostMessages.add(sentMessageId);
            }
        }
        return lostMessages;
    }

    /**
     * Returns list of duplicated messages.
     *
     * @return list of duplicated messages or empty list if there are no
     *         duplicated messages
     */
    private List<String> getDuplicatedMessages() throws Exception {
        MessageStoreImpl messageStore = new MessageStoreImpl();
        Map<String,String> mapOfReceivedMessages = messageStore.load(receivedMessagesFile);
        Set<String> receivedMessages = mapOfReceivedMessages.keySet();

        List<String> listOfDuplicatedMessages = new ArrayList<String>();

        HashSet<String> set = new HashSet<String>();
        for (String messageId : receivedMessages) {
            if (!set.add(messageId)) {
                listOfDuplicatedMessages.add(messageId);
            }
        }
        return listOfDuplicatedMessages;

    }

    public File getSentMessagesFile() {
        return sentMessagesFile;
    }

    public void setSentMessagesFile(File sentMessagesFile) {
        this.sentMessagesFile = sentMessagesFile;
    }

    public File getReceivedMessagesFile() {
        return receivedMessagesFile;
    }

    public void setReceivedMessagesFile(File receivedMessagesFile) {
        this.receivedMessagesFile = receivedMessagesFile;
    }
}
