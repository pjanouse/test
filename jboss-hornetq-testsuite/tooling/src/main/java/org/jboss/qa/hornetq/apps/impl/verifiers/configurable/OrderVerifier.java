package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies messages are in correct order
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class OrderVerifier implements Verifiable {

    private static final Logger logger = Logger.getLogger(OrderVerifier.class);

    private JMSImplementation jmsImplementation;
    private boolean isOk = false;

    private List<Map<String, String>> sendMessages;
    private List<Map<String, String>> receivedMessages;
    private List<Map<String, String>> problemMessages;

    public OrderVerifier(JMSImplementation jmsImplementation, List<Map<String, String>> sendMessages, List<Map<String, String>> receivedMessages) {
        this.jmsImplementation = jmsImplementation;
        this.sendMessages = sendMessages;
        this.receivedMessages = receivedMessages;
    }

    @Override
    public String getTitle() {
        return "Message ordering verifier";
    }


    @Override
    public boolean isOk() {
        return isOk;
    }

    @Override
    public boolean verify() {
        problemMessages = new ArrayList<Map<String, String>>();

        for (int index = 0; index < sendMessages.size(); index++) {
            Map<String, String> sentMessage = sendMessages.get(index);
            if (index < receivedMessages.size()) {
                Map<String, String> receivedMessage = receivedMessages.get(index);

                if (!sentMessage.get(jmsImplementation.getDuplicatedHeader()).equals(
                        receivedMessage.get(jmsImplementation.getDuplicatedHeader()))) {
                    logger.info("Message received out of order - " + jmsImplementation.getDuplicatedHeader() +
                            ": " + receivedMessage.get(jmsImplementation.getDuplicatedHeader()));
                    problemMessages.add(receivedMessage);
                }
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