package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.JMSException;
import java.util.*;

/**
 * This class observers jms org.jboss.qa.hornetq.apps.clients and store their sent and received messages.
 * <p/>
 *
 * @author mnovak@redhat.com
 */
public class MdbMessageVerifier implements FinalTestMessageVerifier {

    private static final Logger logger = Logger.getLogger(MdbMessageVerifier.class);

    private List<Map<String,String>> sentMessages = new ArrayList<Map<String,String>>();

    private List<Map<String,String>> receivedMessages = new ArrayList<Map<String,String>>();

    /**
     * Returns true if all messages are ok = there are equal number of sent and received messages.
     *
     * @return true if there is equal number of sent and received messages
     * @throws Exception
     */
    @Override
    public boolean verifyMessages() throws JMSException {

        boolean isOk = true;

        if (sentMessages.size() != receivedMessages.size()) {
            logger.error("There is different number of sent and received messages");
            isOk = false;
        }

        // set of lost messages -- (sendMessages - receivedMessages) = lostMessages
        if (getLostMessages().size() != 0) {
            logger.error("Lost message detected - there are not corresponding messages for: " + getLostMessages());
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
     * @return the sentMessages
     */
    @Override
    public List<Map<String,String>> getSentMessages() {
        return sentMessages;
    }

    /**
     * @param sentMessages the sentMessages to set
     */
    public void setSentMessages(List<Map<String,String>> sentMessages) {
        this.sentMessages = sentMessages;
    }

    /**
     * @return the receivedMessages
     */
    @Override
    public List<Map<String,String>> getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * @param receivedMessages the receivedMessages to set
     */
    public void setReceivedMessages(List<Map<String,String>> receivedMessages) {
        this.receivedMessages = receivedMessages;
    }

    /**
     * Add received messages to verify.
     *
     * @param list
     */
    public synchronized void addReceivedMessages(List<Map<String,String>> list) {

        receivedMessages.addAll(list);

    }

    /**
     * Add send messages to verify.
     *
     * @param list
     */
    public synchronized void addSendMessages(List<Map<String,String>> list) {

        sentMessages.addAll(list);

    }

    /**
     * Returns list of lost messages.
     *
     * @return list of lost messages or empty list if there are no lost messages
     */
    private List<String> getLostMessages() throws JMSException {

        Set<String> helpSet = new HashSet<String>();
        for (Map<String,String> receivedMessageInMap : receivedMessages)    {
            helpSet.add(receivedMessageInMap.get("inMessageId"));
        }

        List<String> listOfLostMessages = new ArrayList<String>();

        for (Map<String,String> mapOfSentMessageProperties: sentMessages) {
            if (helpSet.add(mapOfSentMessageProperties.get("messageId"))) {
                listOfLostMessages.add(mapOfSentMessageProperties.get("messageId"));
            }
        }

        return listOfLostMessages;
    }

    /**
     * Returns list of duplicated messages.
     *
     * @return list of duplicated messages or empty list if there are no
     *         duplicated messages
     */
    private List<String> getDuplicatedMessages() throws JMSException {

        List<String> listOfDuplicatedMessages = new ArrayList<String>();

        HashSet<String> set = new HashSet<String>();
        for (Map<String, String> receivedMessage : receivedMessages) {
            if (!set.add(receivedMessage.get("inMessageId"))) {
                listOfDuplicatedMessages.add(receivedMessage.get("messageId"));
            }
        }
        return listOfDuplicatedMessages;

    }
}
