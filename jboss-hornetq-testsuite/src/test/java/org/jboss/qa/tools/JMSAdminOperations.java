//TODO parametrize location of journal directory
package org.jboss.qa.tools;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.jboss.as.arquillian.container.Authentication.getCallbackHandler;
import org.jboss.dmr.ModelType;

/**
 * Basic administration operations for JMS subsystem
 * <p/>
 * User: jpai User: pslavice@redhat.com
 */
public final class JMSAdminOperations {

    // Logger
    private static final Logger logger = Logger.getLogger(JMSAdminOperations.class);
    // Definition for the queue
    private static final String DESTINATION_TYPE_QUEUE = "jms-queue";
    // Definition for the topics
    private static final String DESTINATION_TYPE_TOPIC = "jms-topic";
    // Instance of Model controller client
    private final ModelControllerClient modelControllerClient;

    /**
     * Default constructor
     */
    public JMSAdminOperations() {
        this("localhost", 9999);
    }

    /**
     * Constructor
     *
     * @param hostName host with the administration
     * @param port port where is administration available
     */
    public JMSAdminOperations(final String hostName, final int port) {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostName);
            this.modelControllerClient = ModelControllerClient.Factory.create(inetAddress, port, getCallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot create model controller client for host: " + hostName + " and port " + port, e);
        }
    }

    /**
     * Closes connection
     */
    public void close() {
        try {
            modelControllerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName JNDI queue name
     */
    public void createQueue(String queueName, String jndiName) {
        createJmsDestination(DESTINATION_TYPE_QUEUE, queueName, jndiName, true);
    }

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName JNDI queue name
     * @param durable is queue durable
     */
    public void createQueue(String queueName, String jndiName, boolean durable) {
        createJmsDestination(DESTINATION_TYPE_QUEUE, queueName, jndiName, durable);
    }

    /**
     * Creates topic
     *
     * @param topicName queue name
     * @param jndiName JNDI queue name
     */
    public void createTopic(String topicName, String jndiName) {
        createJmsDestination(DESTINATION_TYPE_TOPIC, topicName, jndiName, true);
    }

    /**
     * Removes queue
     *
     * @param queueName queue name
     */
    public void removeQueue(String queueName) {
        removeJmsDestination(DESTINATION_TYPE_QUEUE, queueName);
    }

    /**
     * Removes topic
     *
     * @param topicName queue name
     */
    public void removeTopic(String topicName) {
        removeJmsDestination(DESTINATION_TYPE_TOPIC, topicName);
    }

    /**
     * Adds JNDI name for queue
     *
     * @param queueName queue name
     * @param jndiName new JNDI name for the queue
     */
    public void addQueueJNDIName(String queueName, String jndiName) {
        addDestinationJNDIName(DESTINATION_TYPE_QUEUE, queueName, jndiName);
    }

    /**
     * Cleanups queue
     *
     * @param queueName queue name
     */
    public void cleanUpQueue(String queueName) {
        try {
            removeMessagesFromQueue(queueName);
            removeQueue(queueName);
        } catch (Exception e) {
            // Ignore any exceptions
        }
    }

    /**
     * Returns count of messages on queue
     *
     * @param queueName queue name
     * @return count of messages on queue
     */
    public long getCountOfMessagesOnQueue(String queueName) {
        final ModelNode countMessages = new ModelNode();
        countMessages.get(ClientConstants.OP).set("count-messages");
        countMessages.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        countMessages.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        countMessages.get(ClientConstants.OP_ADDR).add(DESTINATION_TYPE_QUEUE, queueName);
        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(countMessages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (modelNode != null) ? modelNode.get(ClientConstants.RESULT).asLong(0) : 0;
    }

    /**
     * Remove messages from queue
     *
     * @param queueName queue name
     * @return count of removed messages from queue
     */
    public long removeMessagesFromQueue(String queueName) {
        final ModelNode removeMessagesFromQueue = new ModelNode();
        removeMessagesFromQueue.get(ClientConstants.OP).set("remove-messages");
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add(DESTINATION_TYPE_QUEUE, queueName);
        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(removeMessagesFromQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (modelNode != null) ? modelNode.get(ClientConstants.RESULT).asLong(0) : 0;
    }

    /**
     * Disables security on HornetQ
     */
    public void disableSecurity() {
        final ModelNode disableSecurity = new ModelNode();
        disableSecurity.get(ClientConstants.OP).set("write-attribute");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        disableSecurity.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        disableSecurity.get("name").set("security-enabled");
        disableSecurity.get("value").set(Boolean.FALSE);
        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add JNDI name
     *
     * @param destinationType type of destination (queue, topic)
     * @param destinationName destination name
     * @param jndiName JNDI name
     */
    private void addDestinationJNDIName(String destinationType, String destinationName, String jndiName) {
        final ModelNode addJmsJNDIName = new ModelNode();
        addJmsJNDIName.get(ClientConstants.OP).set("add-jndi");
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add(destinationType, destinationName);
        addJmsJNDIName.get("jndi-binding").set(jndiName);
        try {
            this.applyUpdate(addJmsJNDIName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates JMS destination on server
     *
     * @param destinationType type of destination (queue, topic)
     * @param destinationName destination name
     * @param jndiName JNDI name for destination
     * @param durable Is durable destination
     */
    private void createJmsDestination(String destinationType, String destinationName, String jndiName, boolean durable) {
        String externalSuffix = (jndiName.startsWith("/")) ? "" : "/";
        ModelNode createJmsQueueOperation = new ModelNode();
        createJmsQueueOperation.get(ClientConstants.OP).set(ClientConstants.ADD);
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add(destinationType, destinationName);
        createJmsQueueOperation.get("entries").add(jndiName);
        createJmsQueueOperation.get("entries").add("java:jboss/exported" + externalSuffix + jndiName);
        createJmsQueueOperation.get("durable").set(durable);
        try {
            this.applyUpdate(createJmsQueueOperation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes JMS destination from server
     *
     * @param destinationType type of destination (queue, topic)
     * @param destinationName destination name
     */
    private void removeJmsDestination(String destinationType, String destinationName) {
        final ModelNode removeJmsQueue = new ModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("remove");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add(destinationType, destinationName);
        try {
            this.applyUpdate(removeJmsQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Applies update to server
     *
     * @param update instance of the update
     * @see {@link ModelNode}
     * @return instance of ModelNode
     * @throws IOException if something goes wrong
     * @throws JMSAdminOperationException if something goes wrong
     */
    private ModelNode applyUpdate(final ModelNode update) throws IOException, JMSAdminOperationException {
        ModelNode result = this.modelControllerClient.execute(update);
        if (result.hasDefined(ClientConstants.OUTCOME)
                && ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            logger.info(String.format("Operation successful for update = '%s'", update.toString()));
        } else if (result.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
            final String failureDesc = result.get(ClientConstants.FAILURE_DESCRIPTION).toString();
            throw new JMSAdminOperationException(failureDesc);
        } else {
            throw new JMSAdminOperationException(String.format("Operation not successful; outcome = '%s'",
                    result.get(ClientConstants.OUTCOME)));
        }
        return result;
    }

    /** 
     * Sets persistence-enabled attribute in servers configuration.
     * 
     * @param persistenceEnabled - true for persist messages
     */
    public void setPersistenceEnabled(boolean persistenceEnabled) {
        final ModelNode removeJmsQueue = new ModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("write-attribute");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        removeJmsQueue.get("name").set("persistence-enabled");
        removeJmsQueue.get("value").set(Boolean.TRUE);

        try {
            this.applyUpdate(removeJmsQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets clustered attribute.
     * 
     * @param clustered set true to allow server to create cluster
     */
    public void setClustered(boolean clustered) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("clustered");
        model.get("value").set(Boolean.TRUE);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Set this to true if this server shares journal with other server (with live of backup)
     * 
     * @param sharedStore share journal 
     */
    public void setSharedStore(boolean sharedStore) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("shared-store");
        model.get("value").set(sharedStore);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 
     * Allow jms clients to reconnect from backup to live when live comes alive.
     * 
     * @param allowFailback 
     */
    public void setAllowFailback(boolean allowFailback) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("allow-failback");
        model.get("value").set(allowFailback);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Can be "NIO" or "AIO"
     *
     * @param "NIO" or "AIO"
     */
    public void setJournalType(String journalType) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("journal-type");
        model.get("value").set(journalType);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The directory to store the journal files in.
     * 
     * @param path set absolute path
     */
    public void setJournalDirectory(String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("path", "journal-directory");
        model.get("path").set(path);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 
     * The directory to store paged messages in. 
     * 
     * @param path set absolute path
     */
    public void setPagingDirectory(String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("path", "paging-directory");
        model.get("path").set(path);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 
     * The directory in which to store large messages.
     * 
     * @param path set absolute path
     */
    public void setLargeMessagesDirectory(String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("path", "large-messages-directory");
        model.get("path").set(path);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 
     * The directory in which to store the persisted bindings. 
     * 
     * @param path set absolute path
     */
    public void setBindingsDirectory(String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("path", "bindings-directory");
        model.get("path").set(path);
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors over the network. 
     * A connector defines a way in which a client (or other server) can make connections to the server.
     * 
     * @param name a unique name for the broadcast group - mandatory. 
     * @param localBindAddress local bind address that the datagram socket is bound to. The default value is the wildcard IP address chosen by the kernel 
     * @param localBindPort local port to which the datagram socket is bound to. 
     * @param groupAddress multicast address to which the data will be broadcast - mandatory. 
     * @param groupPort UDP port number used for broadcasting - mandatory. 
     * @param broadCastPeriod period in milliseconds between consecutive broadcasts. 
     * @param connectorName A pair connector. 
     * @param backupConnectorName optional backup connector that will be broadcasted.
     */
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort,
            String groupAddress, int groupPort, long broadCastPeriod,
            String connectorName, String backupConnectorName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", name);

        if (!isEmpty(localBindAddress)) {
            model.get("local-bind-address").set(localBindAddress);
        }

        if (!isEmpty(localBindPort)) {
            model.get("local-bind-port").set(localBindPort);
        }

        if (!isEmpty(groupAddress)) {
            model.get("group-address").set(groupAddress);
        }

        if (!isEmpty(groupPort)) {
            model.get("group-port").set(groupPort);
        }

        if (!isEmpty(broadCastPeriod)) {
            model.get("broadcast-period").set(broadCastPeriod);
        }

        model.get("connectors").add(connectorName);

        if (!isEmpty(backupConnectorName)) {
            model.get("connectors").add(backupConnectorName);
        }
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Discovery group defines how connector information is received from a multicast address. 
     * 
     * @param name A unique name for the discovery group - mandatory. 
     * @param localBindAddress The discovery group will be bound only to this local address. 
     * @param groupAddress Multicast IP address of the group to listen on - mandatory. 
     * @param groupPort UDP port of the multicast group - mandatory 
     * @param refreshTimeout  Period the discovery group waits after receiving the last broadcast from a particular server before removing that servers connector pair entry from its list. 
     */
    public void setDiscoveryGroup(String name, String localBindAddress, 
            String groupAddress, int groupPort, long refreshTimeout) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("discovery-group", name);

        if (!isEmpty(localBindAddress)) {
            model.get("local-bind-address").set(localBindAddress);
        }

        if (!isEmpty(groupAddress)) {
            model.get("group-address").set(groupAddress);
        }

        if (!isEmpty(groupPort)) {
            model.get("group-port").set(groupPort);
        }
        
        if (!isEmpty(refreshTimeout)) {
            model.get("refresh-timeout").set(refreshTimeout);
        }
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    
    /**
     * 
     * Sets cluster configuration.
     * 
     * @param address Name of address this cluster connection applies to. 
     * @param discoveryGroupRef Name of discovery group used by this bridge. 
     * @param forwardWhenNoConsumers Should messages be load balanced if there are no matching consumers on target? 
     * @param maxHops Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval Period (in ms) between successive retries. 
     * @param useDuplicateDetection Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName Name of connector to use for live connection. 
     */
    public void setClusterConnections(String address, 
            String discoveryGroupRef, boolean  forwardWhenNoConsumers, int maxHops,
                    long retryInterval, boolean useDuplicateDetection, String connectorName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", "failover-cluster");

        model.get("cluster-connection-address").set(address);
        model.get("discovery-group-name").set(discoveryGroupRef);
        model.get("forward-when-no-consumers").set(forwardWhenNoConsumers);
        model.get("max-hops").set(maxHops);
        model.get("retry-interval").set(retryInterval);
        model.get("use-duplicate-detection").set(useDuplicateDetection);
        model.get("connector-ref").set(connectorName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * How long (in ms) to wait after the last consumer is closed on a queue before redistributing messages. 
     * 
     * @param delay in milliseconds
     */
    public void setRedistributionDelay(long delay) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("address-setting", "#");
        model.get("name").set("redistribution-delay");
        model.get("value").set(delay);
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     *  
     * Add new jndi name for connection factory.
     * 
     * @param connectionFactoryName
     * @param newConnectionFactoryJndiName 
     */
    public void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add-jndi");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("jndi-binding").set(newConnectionFactoryJndiName);
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Sets ha attribute.
     * 
     * @param connectionFactoryName
     * @param value true if connection factory supports ha.
     * 
     */
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("ha");
        model.get("value").set(value);
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Whether or not messages are acknowledged synchronously. 
     * 
     * @param connectionFactoryName
     * @param value default false, should be true for failover scenarios
     */
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("block-on-acknowledge");
        model.get("value").set(value);
        
        applyUpdateWithRetry(model, 50);
        
    }
    
    /**
     * The time (in ms) to retry a connection after failing. 
     * 
     * @param connectionFactoryName
     * @param value 
     */
    public void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval");
        model.get("value").set(value);
        
        applyUpdateWithRetry(model, 50);
    }
    
    /**
     * Multiplier to apply to successive retry intervals. 
     * 
     * @param connectionFactoryName
     * @param value 1.0 by default
     */
    public void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval-multiplier");
        model.get("value").set(value);
        
        applyUpdateWithRetry(model, 50);
        
    }
    
    /**
     * How many times should client retry connection when connection is lost. This should be -1 
     * if failover is required.
     * 
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value value
     */
    public void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value) {
        
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        
        model.get("name").set("reconnect-attempts");
        model.get("value").set(value);
        applyUpdateWithRetry(model, 50);
        
    }
    
    /**
     * The JMX domain used to registered HornetQ MBeans in the MBeanServer. ?
     * 
     * @param jmxDomainName 
     */
    public void setJmxDomainName(String jmxDomainName) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("jmx-domain");
        model.get("value").set(jmxDomainName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Is this server bakcup?
     * 
     * @param isBackup 
     */
    public void setBackup(boolean isBackup) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get("name").set("backup");
        model.get("value").set(isBackup);
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /** 
     * 
     * Adds loopback-address type of the given interface of the given name.
     * 
     * Removes inet-address type as a side effect.
     * 
     * Like: <loopback-address value="127.0.0.2" \>
     * 
     * @param interfaceName - name of the interface like "public" or "managemant"
     * @param address - ipAddress of the interface     
     * 
     */
    public void setLoopBackAddressType(String interfaceName, String ipAddress) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("interface", interfaceName);
        model.get("name").set("loopback-address");
        model.get("value").set(ipAddress);
                
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        model = new ModelNode();
        model.get(ClientConstants.OP).set("undefine-attribute");
        model.get(ClientConstants.OP_ADDR).add("interface", interfaceName);
        model.get("name").set("inet-address");
        
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
    }
        
    
    /** 
     * This method is hack! Somehow calling update model throw exception when it should not.
     * For this reason try it more times until success.
     * 
     * @param model model
     * @param retry how many times to retry 
     */
    public void applyUpdateWithRetry(ModelNode model, int retry)   {
            
        for (int i = 0; i < retry; i++) {
            try {
                this.applyUpdate(model);
                return;
            } catch (Exception e) {
                if (i >= retry - 1)  {
                    throw new RuntimeException(e);
                } 
            }
        }
    }
    
    /** 
     * This method checks whether object is null or empty string - "".
     * 
     * @param attribute object
     * @return true if null or empty
     */
    public boolean isEmpty(Object attribute) {
        
        boolean empty = false;

        if (attribute == null || "".equals(attribute)) {

            empty = true;

        }

        return empty;

    }


    /**
     * Exception
     */
    private class JMSAdminOperationException extends Exception {

        public JMSAdminOperationException(final String msg) {
            super(msg);
        }
    }
}
