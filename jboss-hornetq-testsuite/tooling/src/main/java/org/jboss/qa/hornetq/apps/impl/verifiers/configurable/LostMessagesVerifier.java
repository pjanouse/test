package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import java.util.*;

/**
 * Verifies there are no lost messages
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class LostMessagesVerifier implements Verifiable {
    private static final Logger logger = Logger.getLogger(LostMessagesVerifier.class);

    private JMSImplementation jmsImplementation;
    private boolean isOk = false;

    private List<Map<String, String>> sendMessages;
    private List<Map<String, String>> receivedMessages;
    private List<Map<String, String>> problemMessages;

    public LostMessagesVerifier(JMSImplementation jmsImplementation, List<Map<String, String>> sendMessages, List<Map<String, String>> receivedMessages) {
        this.jmsImplementation = jmsImplementation;
        this.sendMessages = sendMessages;
        this.receivedMessages = receivedMessages;
    }

    @Override
    public String getTitle() {
        return "Lost messages verifier";
    }


    @Override
    public boolean isOk() {
        return isOk;
    }

    @Override
    public boolean verify() {
        problemMessages = new ArrayList<Map<String, String>>();

        Set<String> helpSet = new HashSet<String>();
        for (Map<String, String> receivedMessageInMap : receivedMessages) {
            helpSet.add(receivedMessageInMap.get(jmsImplementation.getDuplicatedHeader()));
        }

        for (Map<String, String> mapOfSentMessageProperties : sendMessages) {
            if (helpSet.add(mapOfSentMessageProperties.get(jmsImplementation.getDuplicatedHeader()))) {
                problemMessages.add(mapOfSentMessageProperties);
                logger.info("Detected lost message" + mapOfSentMessageProperties.get(jmsImplementation.getDuplicatedHeader()));
            }
        }
        isOk = problemMessages.isEmpty();
        return isOk;
    }

    @Override
    public List<Map<String, String>> getProblemMessages() {
        return problemMessages;
    }

    @Override
    public List<String> getProblemMessagesIds() {
        if (problemMessages == null) return null;

        List<String> ids = new ArrayList<String>(problemMessages.size());
        for (Map<String, String> message : problemMessages) {
            ids.add(message.get(jmsImplementation.getDuplicatedHeader()));
        }
        return ids;
    }
}