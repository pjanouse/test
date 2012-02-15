package org.jboss.qa.hornetq.apps.clients;

import javax.jms.ConnectionFactory;
import javax.jms.Topic;

/**
 * Abstract class for high load JMS clients with semaphores
 */
public class HighLoadClientWithSemaphores extends Thread {

    // Message property used for counter
    public static final String ATTR_MSG_COUNTER = "messageIndex";

    // JMS connection factory
    protected ConnectionFactory cf;

    // Target topic
    protected Topic topic;

    // Is there request to stop this process
    protected volatile boolean requestForStop = false;

    // Process has been stopped
    protected volatile boolean stopped = false;

    /**
     * Constructor
     *
     * @param name               thread name
     * @param topic              target topic
     * @param cf                 connection factory
     */
    public HighLoadClientWithSemaphores(String name, Topic topic, ConnectionFactory cf) {
        super(name);
        this.cf = cf;
        this.topic = topic;
    }

    /**
     * Requests to stop sending process asap
     */
    public void sendStopRequest() {
        this.stopped = false;
        this.requestForStop = true;
    }

    /**
     * Is process stopped?
     *
     * @return true if process is stopped, false otherwise
     */
    public boolean isStopped() {
        return stopped;
    }
}
