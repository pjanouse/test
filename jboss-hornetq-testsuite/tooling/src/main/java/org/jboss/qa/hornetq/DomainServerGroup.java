package org.jboss.qa.hornetq;

import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.Archive;

/**
 * Object representation of server group in EAP domain mode.
 */
public interface DomainServerGroup {

    /**
     * @return Server group name
     */
    String getName();

    /**
     * Get jms operations object for setting up all nodes in this server group.
     *
     * Operation have path prefix pre-set properly for this particular server group.
     *
     * @return Setting operations object
     */
    JMSOperations getJmsOperations();

    /**
     * Get server node object representation for given name.
     * 
     * @param name Server node name
     * @return Server node representation
     */
    DomainNode node(String name);

    /**
     * Create new server node in this server group.
     *
     * Port offset for the new node will be 0.
     *
     * @param nodeName New server node name
     */
    void createNode(String nodeName);

    /**
     * Create new server node with the given port offset in this server group.
     *
     * @param nodeName New server node name
     * @param portOffset Port offset for the new node
     */
    void createNode(String nodeName, int portOffset);

    /**
     * Delete server node from this server group.
     *
     * @param nodeName Deleted server node name.
     */
    void deleteNode(String nodeName);

    /**
     * Start all server nodes in this server group.
     *
     * The method blocks until all the nodes are started.
     */
    void startAllNodes();

    /**
     * Stop all server nodes in this server group.
     *
     * The method blocks until all the nodes are stopped.
     */
    void stopAllNodes();

    /**
     * Deploy the archive on all the nodes in this server group.
     *
     * @param archive Deployed archive
     */
    void deploy(Archive archive);

    /**
     * Uneploy the archive from all the nodes in this server group.
     *
     * @param archive Undeployed archive
     */
    void undeploy(Archive archive);

    /**
     * Uneploy the archive from all the nodes in this server group.
     *
     * @param archiveName Undeployed archive name
     */
    void undeploy(String archiveName);

}
