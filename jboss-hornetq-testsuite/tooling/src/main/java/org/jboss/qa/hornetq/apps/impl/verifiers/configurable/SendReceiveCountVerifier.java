package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import java.util.List;
import java.util.Map;

/**
 * Verifies all send messages have been received
 * <p>
 * Created by mstyk on 6/28/16.
 */
public class SendReceiveCountVerifier implements Verifiable {

    private static final Logger logger = Logger.getLogger(SendReceiveCountVerifier.class);

    private JMSImplementation jmsImplementation;
    private boolean isOk = false;

    private List<Map<String, String>> sendMessages;
    private List<Map<String, String>> receivedMessages;

    public SendReceiveCountVerifier(JMSImplementation jmsImplementation, List<Map<String, String>> sendMessages, List<Map<String, String>> receivedMessages) {
        this.jmsImplementation = jmsImplementation;
        this.sendMessages = sendMessages;
        this.receivedMessages = receivedMessages;
    }

    @Override
    public String getTitle() {
        return "Correct send and receive count verifier";
    }


    @Override
    public boolean isOk() {
        return isOk;
    }

    @Override
    public boolean verify() {

        isOk = sendMessages.size() == receivedMessages.size();
        if (!isOk) {
            logger.error("There is different number of sent and received messages. Number of sent messages: "
                    + sendMessages.size() + ". Number of received messages: " + receivedMessages.size());
        }
        return isOk;
    }

    @Override
    public List<Map<String, String>> getProblemMessages() {
        return null;
    }

    @Override
    public List<String> getProblemMessagesIds() {
        return null;
    }
}