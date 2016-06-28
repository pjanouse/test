package org.jboss.qa.hornetq.apps.impl.verifiers.configurable;

import org.jboss.qa.hornetq.apps.JMSImplementation;

/**
 * Provides ways to instantiate ConfigurableMessageVerifier
 *
 * Created by mstyk on 6/28/16.
 */
public class MessageVerifierFactory {

    public static ConfigurableMessageVerifier getBasicVerifier(JMSImplementation jmsImplementation) {
        return new ConfigurableMessageVerifier(jmsImplementation, SendReceiveCountVerifier.class, LostMessagesVerifier.class, DuplicatesVerifier.class);
    }

    public static ConfigurableMessageVerifier getMdbVerifier(JMSImplementation jmsImplementation) {
        return new ConfigurableMessageVerifier(jmsImplementation, SendReceiveCountVerifier.class, MdbLostMessagesVerifier.class, MdbDuplicatesVerifier.class);
    }

    public static ConfigurableMessageVerifier getOrderingVerifier(JMSImplementation jmsImplementation) {
        return new ConfigurableMessageVerifier(jmsImplementation, SendReceiveCountVerifier.class, LostMessagesVerifier.class, DuplicatesVerifier.class,
                OrderVerifier.class);
    }
}
