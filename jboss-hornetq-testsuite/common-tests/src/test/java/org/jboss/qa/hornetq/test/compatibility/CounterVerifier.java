package org.jboss.qa.hornetq.test.compatibility;

import org.jboss.logging.Logger;

import javax.jms.Message;
import java.util.List;

/**
 * Simple verifier that checks number of sent and received messages.
 *
 * @author ochaloup@redhat.com
 */
class CounterVerifier  {
    private static final Logger log = Logger.getLogger(CounterVerifier.class);

    private int counterSentMsgs = 0;
    private int counterReceivedMsgs = 0;

    public void addReceivedMessages(List<Message> list) {
        if (list == null) {
            return;
        }
        counterReceivedMsgs += list.size();
        log.info("Updated received msgs counter. Now is: " + counterReceivedMsgs);
    }


    public void addSendMessages(List<Message> list) {
        if (list == null) {
            return;
        }
        counterSentMsgs += list.size();
    }


    public List<Message> getReceivedMessages() {
        throw new UnsupportedOperationException("This verifier does not support returning list of messages.");
    }


    public List<Message> getSentMessages() {
        throw new UnsupportedOperationException("This verifier does not support returning list of messages.");
    }


    public boolean verifyMessages() {
        log.info(String.format("Number of messages which were sent: %d, number of received messages: %d.",
                counterSentMsgs, counterReceivedMsgs));
        return counterSentMsgs != 0 && counterReceivedMsgs == counterSentMsgs;
    }

}
