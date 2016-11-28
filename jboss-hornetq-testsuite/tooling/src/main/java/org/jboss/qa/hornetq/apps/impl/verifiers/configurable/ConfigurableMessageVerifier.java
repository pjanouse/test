package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import javax.jms.JMSException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by mstyk on 6/28/16.
 */
public class ConfigurableMessageVerifier implements FinalTestMessageVerifier {
    private static final Logger logger = Logger.getLogger(ConfigurableMessageVerifier.class);

    private List<Map<String, String>> sentMessages = new ArrayList<Map<String, String>>();

    private List<Map<String, String>> receivedMessages = new ArrayList<Map<String, String>>();

    protected JMSImplementation jmsImplementation;

    private List<Class<? extends Verifiable>> verifiableClasses;

    public ConfigurableMessageVerifier(JMSImplementation jmsImplementation, Class<? extends Verifiable>... verifiables) {
        this.jmsImplementation = jmsImplementation;
        this.verifiableClasses = Arrays.asList(verifiables);
    }

    /**
     * Returns true if all messages are ok = there are equal number of sent and received messages.
     *
     * @return true if there is equal number of sent and received messages
     * @throws Exception
     */
    @Override
    public boolean verifyMessages() throws JMSException {

        List<Verifiable> components = getVerifiableInstances();
        boolean isOk = true;

        logger.info("###############################################################");

        for (Verifiable verifier : components) {
            logger.info(verifier.getTitle() + " is verifying messages...");
            if (verifier.verify() == false) {
                isOk = false;
                List<String> problemIds = verifier.getProblemMessagesIds();
                if (problemIds != null) {
                    logger.error(verifier.getTitle() + " detected problem with messages " + problemIds);
                }
            }
        }

        logger.info("###############################################################");

        return isOk;
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

    private List<Verifiable> getVerifiableInstances() {
        List<Verifiable> instances = new ArrayList<Verifiable>(verifiableClasses.size());

        for (Class<? extends Verifiable> verifiableClass : verifiableClasses) {
            try {
                Constructor<?> cons = verifiableClass.getConstructor(JMSImplementation.class, List.class, List.class);
                instances.add(Verifiable.class.cast(cons.newInstance(jmsImplementation, sentMessages, receivedMessages)));
            } catch (Exception e) {
                logger.error("Cannot instantiate class " + verifiableClass.getSimpleName(), e);
            }
        }
        return instances;
    }

}
