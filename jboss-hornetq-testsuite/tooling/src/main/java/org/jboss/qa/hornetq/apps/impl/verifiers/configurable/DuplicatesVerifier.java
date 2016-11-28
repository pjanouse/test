package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Verifies there are no duplicates
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class DuplicatesVerifier implements Verifiable {

    private static final Logger logger = Logger.getLogger(DuplicatesVerifier.class);

    private JMSImplementation jmsImplementation;
    private boolean isOk = false;

    private List<Map<String, String>> sendMessages;
    private List<Map<String, String>> receivedMessages;
    private List<Map<String, String>> problemMessages;

    public DuplicatesVerifier(JMSImplementation jmsImplementation, List<Map<String, String>> sendMessages, List<Map<String, String>> receivedMessages) {
        this.jmsImplementation = jmsImplementation;
        this.sendMessages = sendMessages;
        this.receivedMessages = receivedMessages;
    }

    @Override
    public String getTitle() {
        return "Duplicate messages verifier";
    }


    @Override
    public boolean isOk() {
        return isOk;
    }

    @Override
    public boolean verify() {
        problemMessages = new ArrayList<Map<String, String>>();

        HashSet<String> set = new HashSet<String>();
        for (Map<String, String> receivedMessage : receivedMessages) {
            if (!set.add(receivedMessage.get(jmsImplementation.getDuplicatedHeader()))) {
                problemMessages.add(receivedMessage);
                logger.info("Detected duplicate" + receivedMessage.get(jmsImplementation.getDuplicatedHeader()));
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