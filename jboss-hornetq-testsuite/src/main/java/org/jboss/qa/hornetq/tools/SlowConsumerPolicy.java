package org.jboss.qa.hornetq.tools;


/**
 * Policy for handling slow consumers.
 *
 * Copies allowed states from HornetQ (see documentation for slow-consumer-policy attribute in address-settings.
 */
public enum SlowConsumerPolicy {
    KILL,   // kill the consumer
    NOTIFY  // send the JMX notification
}
