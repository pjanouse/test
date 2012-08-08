package org.jboss.qa.hornetq.test.compatibility;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.util.NotImplementedException;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.List;

/**
 * Simple verifier that checks number of sent and received messages.
 *
 * @author ochaloup@redhat.com
 */
class CounterVerifier implements FinalTestMessageVerifier {
    private static final Logger log = Logger.getLogger(CounterVerifier.class);

    private int counterSentMsgs = 0;
    private int counterReceivedMsgs = 0;

    @Override
    public void addReceivedMessages(List<Message> list) {
        if (list == null) {
            return;
        }
        counterReceivedMsgs += list.size();
        log.info("Updated received msgs counter. Now is: " + counterReceivedMsgs);
    }

    @Override
    public void addSendMessages(List<Message> list) {
        if (list == null) {
            return;
        }
        counterSentMsgs += list.size();
    }

    @Override
    public List<Message> getReceivedMessages() {
        throw new NotImplementedException("This verifier does not support returning list of messages.");
    }

    @Override
    public List<Message> getSentMessages() {
        throw new NotImplementedException("This verifier does not support returning list of messages.");
    }

    @Override
    public boolean verifyMessages() throws JMSException {
        log.info(String.format("Number of messages which were sent: %d, number of received messages: %d.",
                counterSentMsgs, counterReceivedMsgs));
        return counterSentMsgs != 0 && counterReceivedMsgs == counterSentMsgs;
    }

}
