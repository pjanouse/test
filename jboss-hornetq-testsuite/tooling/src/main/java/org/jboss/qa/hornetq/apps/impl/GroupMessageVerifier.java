/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.JMSException;
import java.util.*;

/**
 * This class observers jms org.jboss.qa.hornetq.apps.clients and store their sent and received messages. This class takes all received and send messages
 * and trow away send messages which do not have the same JMSXGroupID as received messages. So we can find lost and dups messages.
 * <p/>
 * It's expected that there will be used another way to verify that all messages with JMSXGroupID were received by consumers.
 * <p/>
 * There should be one GroupMessageVerifier per consumer.
 * <p/>
 *
 * @author mnovak@redhat.com
 */
public class GroupMessageVerifier implements FinalTestMessageVerifier {

    private static final Logger logger = Logger.getLogger(GroupMessageVerifier.class);

    private List<Map<String, String>> sentMessages = new LinkedList<Map<String, String>>();

    private List<Map<String, String>> receivedMessages = new LinkedList<Map<String, String>>();

    /**
     * Returns true if all messages are ok = there are equal number of sent and received messages.
     *
     * @return true if there is equal number of sent and received messages
     * @throws Exception
     */
    @Override
    public boolean verifyMessages() throws JMSException {

        logger.info("###############################################################");
        logger.info("GroupMessageVerifier verifies messages:");

        getRidOfMissingGroupIds();

        logger.info("Number of send messages: " + sentMessages.size());
        logger.info("Number of received messages: " + receivedMessages.size());

        boolean isOk = true;

        if (sentMessages.size() != receivedMessages.size()) {
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

        logger.info("##################### Check ordering - start ##########################################");
        // check that ordering
        checkOrdering();
        logger.info("##################### Check ordering - finish ##########################################");

        return isOk;
    }

    private void checkOrdering() {
        for (int index = 0; index < sentMessages.size(); index++) {
            Map<String, String> sentMessage = sentMessages.get(index);
            if (index < receivedMessages.size()) {
                Map<String, String> receivedMessage = receivedMessages.get(index);

                if (!sentMessage.get("_HQ_DUPL_ID").equals(receivedMessage.get("_HQ_DUPL_ID"))) {
                    logger.info("Message received out of order - _HQ_DUPL_ID: " + receivedMessage.get("_HQ_DUPL_ID"));
                }
            }
        }
    }

    /**
     * This removed messages from producers lists which do not have groupid in received messages.
     */
    private void getRidOfMissingGroupIds() {


        Set<String> helpSet = new HashSet<String>();

        // get all message group IDs
        for (Map<String, String> mapOfReceivedMessageProperties : receivedMessages) {
            helpSet.add(mapOfReceivedMessageProperties.get("JMSXGroupID"));
        }

        // Clone sent messages
        List<Map<String, String>> cloneOfsentMessages = (List<Map<String, String>>) ((LinkedList<Map<String, String>>) sentMessages).clone();

        // remove "bad" messages from sent messages
        for (Map<String, String> mapOfSentMessageProperties : cloneOfsentMessages) {
            if (!helpSet.contains(mapOfSentMessageProperties.get("JMSXGroupID"))) {
                sentMessages.remove(mapOfSentMessageProperties);
            }
        }

        StringBuilder s = new StringBuilder("This message verifiers contains messages for JMSXGroupIDs: ");
        for (String id : helpSet) {
            s.append(id + ", ");
        }
        logger.info(s.toString());
    }

    /**
     * @return the sentMessages
     */
    @Override
    public List<Map<String, String>> getSentMessages() {
        return sentMessages;
    }

    /**
     * @param sentMessages the sentMessages to set
     */
    public void setSentMessages(List<Map<String, String>> sentMessages) {
        this.sentMessages = sentMessages;
    }

    /**
     * @return the receivedMessages
     */
    @Override
    public List<Map<String, String>> getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * @param receivedMessages the receivedMessages to set
     */
    public void setReceivedMessages(List<Map<String, String>> receivedMessages) {
        this.receivedMessages = receivedMessages;
    }

    /**
     * Add received messages to verify.
     *
     * @param list
     */
    public synchronized void addReceivedMessages(List<Map<String, String>> list) {

        receivedMessages.addAll(list);

    }

    /**
     * Add send messages to verify.
     *
     * @param list
     */
    public synchronized void addSendMessages(List<Map<String, String>> list) {

        sentMessages.addAll(list);

    }

    /**
     * Returns list of lost messages.
     *
     * @return list of lost messages or empty list if there are no lost messages
     */
    private List<String> getLostMessages() throws JMSException {

        Set<String> helpSet = new HashSet<String>();
        for (Map<String, String> receivedMessageInMap : receivedMessages) {
            helpSet.add(receivedMessageInMap.get("_HQ_DUPL_ID"));
        }

        List<String> listOfLostMessages = new ArrayList<String>();


        for (Map<String, String> mapOfSentMessageProperties : sentMessages) {
            if (helpSet.add(mapOfSentMessageProperties.get("_HQ_DUPL_ID"))) {
                listOfLostMessages.add(mapOfSentMessageProperties.get("messageId"));
            }
        }

        return listOfLostMessages;
    }

    /**
     * Returns list of duplicated messages.
     *
     * @return list of duplicated messages or empty list if there are no
     * duplicated messages
     */
    private List<String> getDuplicatedMessages() throws JMSException {

        List<String> listOfDuplicatedMessages = new ArrayList<String>();

        HashSet<String> set = new HashSet<String>();
        for (Map<String, String> receivedMessage : receivedMessages) {
            if (!set.add(receivedMessage.get("_HQ_DUPL_ID"))) {
                listOfDuplicatedMessages.add(receivedMessage.get("messageId"));
            }
        }
        return listOfDuplicatedMessages;

    }
}
