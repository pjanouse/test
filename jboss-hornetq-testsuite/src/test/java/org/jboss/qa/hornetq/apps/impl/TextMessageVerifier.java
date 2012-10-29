/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.*;

/**
 * This class observers jms clients and store their sent and received messages.
 * <p/>
 *
 * @author mnovak@redhat.com
 */
public class TextMessageVerifier implements FinalTestMessageVerifier {

    private static final Logger logger = Logger.getLogger(TextMessageVerifier.class);

    private List<Message> sentMessages = new ArrayList<Message>();

    private List<Message> receivedMessages = new ArrayList<Message>();

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
     * @return the sentMessages
     */
    @Override
    public List<Message> getSentMessages() {
        return sentMessages;
    }

    /**
     * @param sentMessages the sentMessages to set
     */
    public void setSentMessages(ArrayList<Message> sentMessages) {
        this.sentMessages = sentMessages;
    }

    /**
     * @return the receivedMessages
     */
    @Override
    public List<Message> getReceivedMessages() {
        return receivedMessages;
    }

    /**
     * @param receivedMessages the receivedMessages to set
     */
    public void setReceivedMessages(ArrayList<Message> receivedMessages) {
        this.receivedMessages = receivedMessages;
    }

    /**
     * Add received messages to verify.
     *
     * @param list
     */
    public synchronized void addReceivedMessages(List<Message> list) {

        receivedMessages.addAll(list);

    }

    /**
     * Add send messages to verify.
     *
     * @param list
     */
    public synchronized void addSendMessages(List<Message> list) {

        sentMessages.addAll(list);

    }

    /**
     * Returns list of lost messages.
     *
     * @return list of lost messages or empty list if there are no lost messages
     */
    private List<Message> getLostMessages() throws JMSException {

        Map<String, Message> sentMessageIds = new HashMap<String, Message>();

        for (Message message : sentMessages) {

            sentMessageIds.put(message.getStringProperty("_HQ_DUPL_ID"), message);
        }

        for (Message message : receivedMessages) {
            sentMessageIds.remove(message.getStringProperty("_HQ_DUPL_ID"));
        }

        List<Message> listOfLostMessages = new ArrayList<Message>();

        for (Message message : sentMessageIds.values()) {
            listOfLostMessages.add(message);
        }

        return listOfLostMessages;
    }

    /**
     * Returns list of duplicated messages.
     *
     * @return list of duplicated messages or empty list if there are no
     *         duplicated messages
     */
    private List<Message> getDuplicatedMessages() throws JMSException {

        List<Message> listOfDuplicatedMessages = new ArrayList<Message>();

        HashSet<String> set = new HashSet<String>();
        for (int i = 0; i < receivedMessages.size(); i++) {
            if (!set.add(receivedMessages.get(i).getJMSMessageID())) {
                listOfDuplicatedMessages.add(receivedMessages.get(i));
            }
        }
        return listOfDuplicatedMessages;

    }
}
