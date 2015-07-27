package org.jboss.qa.hornetq;


import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;


/**
 * Representation of the domain server/node in EAP domain mode.
 */
public interface DomainNode {

    /**
     * @return Hostname of the domain node.
     */
    String getHostname();

    /**
     * @return JNDI port for the domain node (taking port offset into account).
     */
    int getJNDIPort();

    /**
     * @return HornetQ port for the domain node (taking port offset into account).
     */
    int getHornetqPort();

    /**
     * @return Server node name
     */
    String getName();

    /**
     * @return JmsOperations that are only useful for getting runtime informations about the node.
     */
    JMSOperations getRuntimeJmsOperations();

    /**
     * Start the node.
     *
     * The method blocks until the node is started.
     */
    void start();

    /**
     * Stop the node.
     *
     * The method blocks until the node is stopped.
     */
    void stop();

    /**
     * Kill the node.
     */
    void kill();

    JmxUtils getJmxUtils();

}
