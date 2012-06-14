package org.jboss.qa.tools;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Basic administration operations for JMS subsystem
 * <p/>
 * TODO parametrize location of journal directory
 *
 * @author jpai
 * @author mnovak@redhat.com
 * @author pslavice@redhat.com
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
     */
    public JMSAdminOperations(final String hostName) {
        this(hostName, 9999);
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
            this.modelControllerClient = ModelControllerClient.Factory.create(inetAddress, port);
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
        createQueue(queueName, jndiName, true);
    }

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName JNDI queue name
     * @param durable is queue durable
     */
    public void createQueue(String queueName, String jndiName, boolean durable) {
        createQueue("default", queueName, jndiName, durable);
    }

    /**
     * Creates queue
     *
     * @param serverName name of the hornetq server
     * @param queueName queue name
     * @param jndiName JNDI queue name
     * @param durable is queue durable
     */
    public void createQueue(String serverName, String queueName, String jndiName, boolean durable) {
        createJmsDestination(serverName, DESTINATION_TYPE_QUEUE, queueName, jndiName, durable);
    }

    /**
     * Creates topic
     *
     * @param topicName queue name
     * @param jndiName JNDI queue name
     */
    public void createTopic(String topicName, String jndiName) {
        createTopic("default", topicName, jndiName);
    }

    /**
     * Creates topic
     *
     * @param serverName
     * @param topicName queue name
     * @param jndiName JNDI queue name
     */
    public void createTopic(String serverName, String topicName, String jndiName) {
        createJmsDestination(serverName, DESTINATION_TYPE_TOPIC, topicName, jndiName, true);
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
    public void cleanupQueue(String queueName) {
        try {
            removeMessagesFromQueue(queueName);
            removeQueue(queueName);
        } catch (Exception e) {
            // Ignore any exceptions
        }
    }

    /**
     * Cleanups topic
     *
     * @param topicName topic name
     */
    public void cleanupTopic(String topicName) {
        try {
            removeTopic(topicName);
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
     * Sets password for cluster user.
     *
     * @param password password
     */
    public void setClusterUserPassword(String password) {
        setClusterUserPassword("default", password);
    }

    /**
     * Sets password for cluster user.
     *
     * @param password password
     * @param serverName name of the hornetq server
     */
    public void setClusterUserPassword(String serverName, String password) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("cluster-password");
        model.get("value").set(password);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Disables security on HornetQ
     */
    public void disableSecurity() {
        disableSecurity("default");
    }

    /**
     * Disables security on HornetQ
     *
     * @param serverName
     */
    public void disableSecurity(String serverName) {
        final ModelNode disableSecurity = new ModelNode();
        disableSecurity.get(ClientConstants.OP).set("write-attribute");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        disableSecurity.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        disableSecurity.get("name").set("security-enabled");
        disableSecurity.get("value").set(Boolean.FALSE);
        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets security on HornetQ
     *
     * @param value
     */
    public void setSecurityEnabled(boolean value) {
        final ModelNode disableSecurity = new ModelNode();
        disableSecurity.get(ClientConstants.OP).set("write-attribute");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        disableSecurity.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        disableSecurity.get("name").set("security-enabled");
        disableSecurity.get("value").set(value);
        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds security attribute on HornetQ
     *
     * @param value set to false to disable security for hornetq
     */
    public void addSecurityEnabled(boolean value) {
        addSecurityEnabled("default", value);
    }

    /**
     * Adds security attribute on HornetQ
     *
     * @param serverName set name of the hornetq server <<<<<<< HEAD
     * @param value set to false to disable security for hornetq =======
     */
    public void addSecurityEnabled(String serverName, boolean value) {
        final ModelNode disableSecurity = new ModelNode();
        disableSecurity.get(ClientConstants.OP).set("add");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        disableSecurity.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        disableSecurity.get("security-enabled").set(value);

        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets permission privileges to a given role.
     *
     * @param address address of the queue like '#' (for all queues)
     * @param role role of the user like 'guest'
     * @param permission possible values
     * {consume,create-durable-queue,create-non-durable-queue,delete-durable-queue,,delete-non-durable-queue,manage,send}
     * @param value true for enable permission
     */
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("security-setting", address);
        model.get(ClientConstants.OP_ADDR).add("role", role);
        model.get("name").set(permission);
        model.get("value").set(value);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets connector on pooled connection factory
     *
     * @param connectionFactoryName name of the pooled connection factory like
     * "hornetq-ra"
     * @param connectorName name of the connector like "remote-connector"
     */
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("connector");
        ModelNode modelnew = new ModelNode();
        modelnew.get(connectorName).clear();
        model.get("value").set(modelnew);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets connector on pooled connection factory transaction=xa,
     * entries={{java:jmsXA3}}, connector={["netty"]}, ha=true)
     *
     * @param connectionFactoryName name of the pooled connection factory like
     * "hornetq-ra"
     * @param connectorName name of the connector like "remote-connector"
     */
    public void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("transaction").set("xa");

        model.get("entries").add(jndiName);

        model.get("name").set("connector");
        ModelNode modelnew = new ModelNode();
        modelnew.get(connectorName).clear();
        model.get("value").set(modelnew);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets connector on pooled connection factory
     *
     * @param connectionFactoryName name of the pooled connection factory like
     * "hornetq-ra"
     * @param connectorNames
     */
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("connector");
        ModelNode modelnew = new ModelNode();
        for (String connectorName : connectorNames) {
            modelnew.get(connectorName).clear();
        }
        model.get("value").set(modelnew);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds role to security settings.
     *
     * @param address address of the queue like '#' (for all queues)
     * @param role role of the user like 'guest'
     */
    public void addRoleToSecuritySettings(String address, String role) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("security-setting", address);
        model.get(ClientConstants.OP_ADDR).add("role", role);

        try {
            this.applyUpdate(model);
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
    private void createJmsDestination(String serverName, String destinationType, String destinationName, String jndiName, boolean durable) {
        String externalSuffix = (jndiName.startsWith("/")) ? "" : "/";
        ModelNode createJmsQueueOperation = new ModelNode();
        createJmsQueueOperation.get(ClientConstants.OP).set(ClientConstants.ADD);
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
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
     * @return instance of ModelNode
     * @throws IOException if something goes wrong
     * @throws JMSAdminOperationException if something goes wrong
     * @see {@link ModelNode}
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
        setPersistenceEnabled("default", persistenceEnabled);
    }

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    public void setPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        final ModelNode removeJmsQueue = new ModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("write-attribute");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        removeJmsQueue.get("name").set("persistence-enabled");
        removeJmsQueue.get("value").set(persistenceEnabled);
        try {
            this.applyUpdate(removeJmsQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets id-cache-size attribute in servers configuration.
     *
     * @param numberOfIds - number of ids to remember
     */
    public void setIdCacheSize(long numberOfIds) {
        setIdCacheSize("default", numberOfIds);
    }

    /**
     * Sets id-cache-size attribute in servers configuration.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param numberOfIds - number of ids to remember
     */
    public void setIdCacheSize(String serverName, long numberOfIds) {
        final ModelNode removeJmsQueue = new ModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("write-attribute");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        removeJmsQueue.get("name").set("id-cache-size");
        removeJmsQueue.get("value").set(numberOfIds);
        try {
            this.applyUpdate(removeJmsQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds persistence-enabled attribute in servers configuration.
     *
     * @param persistenceEnabled - true for persist messages
     */
    public void addPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled("default", persistenceEnabled);
    }

    /**
     * Adds persistence-enabled attribute in servers configuration.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    public void addPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("persistence-enabled").set(persistenceEnabled);

        try {
            this.applyUpdate(model);
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
        setClustered("default", clustered);
    }

    /**
     * Sets clustered attribute.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered set true to allow server to create cluster
     */
    public void setClustered(String serverName, boolean clustered) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("clustered");
        model.get("value").set(clustered);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds clustered attribute.
     *
     * @param clustered set true to allow server to create cluster
     */
    public void addClustered(boolean clustered) {
        setClustered("default", clustered);
    }

    /**
     * Adds clustered attribute.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered set true to allow server to create cluster
     */
    public void addClustered(String serverName, boolean clustered) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("clustered").set(clustered);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set this to true if this server shares journal with other server (with
     * live of backup)
     *
     * @param sharedStore share journal
     */
    public void setSharedStore(boolean sharedStore) {
        setSharedStore("default", sharedStore);
    }

    /**
     * Set this to true if this server shares journal with other server (with
     * live of backup)
     *
     * @param sharedStore share journal
     * @param serverName hornetq server name
     */
    public void setSharedStore(String serverName, boolean sharedStore) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("shared-store");
        model.get("value").set(sharedStore);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore share journal
     */
    public void addSharedStore(boolean sharedStore) {
        addSharedStore("default", sharedStore);
    }

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore shared journal
     * @param serverName hornetq server name
     */
    public void addSharedStore(String serverName, boolean sharedStore) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("shared-store").set(sharedStore);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allow jms clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     */
    public void setAllowFailback(boolean allowFailback) {
        setAllowFailback("default", allowFailback);
    }

    /**
     * Allow jms clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     * @param serverName name of the hornetq server
     */
    public void setAllowFailback(String serverName, boolean allowFailback) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("allow-failback");
        model.get("value").set(allowFailback);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Can be "NIO" or "AIO"
     *
     * @param journalType
     */
    public void setJournalType(String journalType) {
        setJournalType("default", journalType);
    }

    /**
     * Can be "NIO" or "AIO"
     *
     * @param serverName set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    public void setJournalType(String serverName, String journalType) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("journal-type");
        model.get("value").set(journalType);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds journal-type attribute.
     *
     * @param journalType
     */
    public void addJournalType(String journalType) {
        addJournalType("default", journalType);
    }

    /**
     * Adds journal-type attribute.
     *
     * @param serverName set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    public void addJournalType(String serverName, String journalType) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("journal-type").set(journalType);

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
        setJournalDirectory("default", path);
    }

    /**
     * The directory to store the journal files in.
     *
     * @param serverName set name of hornetq server
     * @param path set absolute path
     */
    public void setJournalDirectory(String serverName, String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "journal-directory");
        model.get("path").set(path + File.separator + "journal");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The directory to store paged messages in.
     *
     * @param path set absolute path
     */
    public void setPagingDirectory(String path) {
        setPagingDirectory("default", path);
    }

    /**
     * The directory to store paged messages in.
     *
     * @param serverName set name of the server
     * @param path set absolute path
     */
    public void setPagingDirectory(String serverName, String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "paging-directory");
        model.get("path").set(path + File.separator + "paging");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The directory in which to store large messages.
     *
     * @param path set absolute path
     */
    public void setLargeMessagesDirectory(String path) {
        setLargeMessagesDirectory("default", path);
    }

    /**
     * The directory in which to store large messages.
     *
     * @param serverName set name of hornetq server
     * @param path set absolute path
     */
    public void setLargeMessagesDirectory(String serverName, String path) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "large-messages-directory");
        model.get("path").set(path + File.separator + "large-messages");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param path set absolute path
     */
    public void setBindingsDirectory(String path) {
        setBindingsDirectory("default", path);
    }

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param serverName set name of hornetq server
     * @param path set absolute path
     */
    public void setBindingsDirectory(String serverName, String path) {


        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "bindings-directory");
        model.get("path").set(path + File.separator + "bindings");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * XA datasource.
     *
     * @param jndi_name
     * @param poolName
     * @param enabled
     * @param useJavaContext
     * @param useCCM
     * @param driverName
     * @param transactionIsolation
     */
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext,
            boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass,
            boolean isSameRmOverride, boolean noTxSeparatePool) {

        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        model.get(ClientConstants.OP_ADDR).add("xa-data-source", poolName);
        model.get("jndi-name").set(jndi_name);
        model.get("use-java-context").set(useJavaContext);
        model.get("use-ccm").set(useCCM);
        model.get("driver-name").set(driverName);

        model.get("transaction-isolation").set(transactionIsolation);
        model.get("xa-datasource-class").set(xaDatasourceClass);
        model.get("no-tx-separate-pool").set(noTxSeparatePool);
        model.get("same-rm-override").set(isSameRmOverride);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add XA datasource property.
     *
     * @param poolName
     * @param propertyName
     * @param value
     */
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {

        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        model.get(ClientConstants.OP_ADDR).add("xa-data-source", poolName);
        model.get(ClientConstants.OP_ADDR).add("xa-datasource-properties", propertyName);
        model.get("value").set(value);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Add driver.
     */
    public void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass) {

        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        model.get(ClientConstants.OP_ADDR).add("jdbc-driver", driverName);
        model.get("driver-name").set(driverName);
        model.get("driver-module-name").set(moduleName);
        model.get("driver-class-name").set(driverClass);
        model.get("driver-xa-datasource-class-name").set(xaDatasourceClass);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param name a unique name for the broadcast group - mandatory.
     * @param localBindAddress local bind address that the datagram socket is
     * bound to. The default value is the wildcard IP address chosen by the
     * kernel
     * @param localBindPort local port to which the datagram socket is bound to.
     * @param groupAddress multicast address to which the data will be broadcast
     * - mandatory.
     * @param groupPort UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod period in milliseconds between consecutive
     * broadcasts.
     * @param connectorName A pair connector.
     * @param backupConnectorName optional backup connector that will be
     * broadcasted.
     */
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort,
            String groupAddress, int groupPort, long broadCastPeriod,
            String connectorName, String backupConnectorName) {
        setBroadCastGroup("default", name, localBindAddress, localBindPort, groupAddress, groupPort, broadCastPeriod, connectorName, backupConnectorName);
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param serverName set name of hornetq server
     * @param name a unique name for the broadcast group - mandatory.
     * @param localBindAddress local bind address that the datagram socket is
     * bound to. The default value is the wildcard IP address chosen by the
     * kernel
     * @param localBindPort local port to which the datagram socket is bound to.
     * @param groupAddress multicast address to which the data will be broadcast
     * - mandatory.
     * @param groupPort UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod period in milliseconds between consecutive
     * broadcasts.
     * @param connectorName A pair connector.
     * @param backupConnectorName optional backup connector that will be
     * broadcasted.
     */
    public void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort,
            String groupAddress, int groupPort, long broadCastPeriod,
            String connectorName, String backupConnectorName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
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
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param name a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use
     * for broadcasting connectors
     * @param broadCastPeriod period in milliseconds between consecutive
     * broadcasts.
     * @param connectorName A pair connector.
     * @param backupConnectorName optional backup connector that will be
     * broadcasted.
     */
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod,
            String connectorName, String backupConnectorName) {
        setBroadCastGroup("default", name, messagingGroupSocketBindingName, broadCastPeriod, connectorName, backupConnectorName);
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param serverName set name of hornetq server
     * @param name a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use
     * for broadcasting connectors
     * @param broadCastPeriod period in milliseconds between consecutive
     * broadcasts.
     * @param connectorName A pair connector.
     * @param backupConnectorName optional backup connector that will be
     * broadcasted.
     */
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod,
            String connectorName, String backupConnectorName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", name);

        if (!isEmpty(messagingGroupSocketBindingName)) {
            model.get("socket-binding").set(messagingGroupSocketBindingName);
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
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param name A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this
     * local address.
     * @param groupAddress Multicast IP address of the group to listen on -
     * mandatory.
     * @param groupPort UDP port of the multicast group - mandatory
     * @param refreshTimeout Period the discovery group waits after receiving
     * the last broadcast from a particular server before removing that servers
     * connector pair entry from its list.
     */
    public void setDiscoveryGroup(String name, String localBindAddress,
            String groupAddress, int groupPort, long refreshTimeout) {
        setDiscoveryGroup("default", name, localBindAddress, groupAddress, groupPort, refreshTimeout);
    }

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param serverName Set name of hornetq server
     * @param name A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this
     * local address.
     * @param groupAddress Multicast IP address of the group to listen on -
     * mandatory.
     * @param groupPort UDP port of the multicast group - mandatory
     * @param refreshTimeout Period the discovery group waits after receiving
     * the last broadcast from a particular server before removing that servers
     * connector pair entry from its list.
     */
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress,
            String groupAddress, int groupPort, long refreshTimeout) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
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
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param name A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use
     * for accepting connectors from other servers
     * @param refreshTimeout Period the discovery group waits after receiving
     * the last broadcast from a particular server before removing that servers
     * connector pair entry from its list.
     */
    public void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        setDiscoveryGroup("default", name, messagingGroupSocketBindingName, refreshTimeout);
    }

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param serverName Set name of hornetq server
     * @param name A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use
     * for accepting connectors from other servers
     * @param refreshTimeout Period the discovery group waits after receiving
     * the last broadcast from a particular server before removing that servers
     * connector pair entry from its list.
     */
    public void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("discovery-group", name);

        if (!isEmpty(messagingGroupSocketBindingName)) {
            model.get("socket-binding").set(messagingGroupSocketBindingName);
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
     * Sets cluster configuration.
     *
     * @param name Name of the cluster group - like "failover-cluster"
     * @param address Name of address this cluster connection applies to.
     * @param discoveryGroupRef Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there
     * are no matching consumers on target?
     * @param maxHops Maximum number of hops cluster topology is propagated.
     * Default is 1.
     * @param retryInterval Period (in ms) between successive retries.
     * @param useDuplicateDetection Should duplicate detection headers be
     * inserted in forwarded messages?
     * @param connectorName Name of connector to use for live connection.
     */
    public void setClusterConnections(String name, String address,
            String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops,
            long retryInterval, boolean useDuplicateDetection, String connectorName) {
        setClusterConnections("default", name, address, discoveryGroupRef, forwardWhenNoConsumers, maxHops, retryInterval, useDuplicateDetection, connectorName);
    }

    /**
     * Sets cluster configuration.
     *
     * @param serverName Set name of hornetq server.
     * @param name Name of the cluster group - like "failover-cluster"
     * @param address Name of address this cluster connection applies to.
     * @param discoveryGroupRef Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there
     * are no matching consumers on target?
     * @param maxHops Maximum number of hops cluster topology is propagated.
     * Default is 1.
     * @param retryInterval Period (in ms) between successive retries.
     * @param useDuplicateDetection Should duplicate detection headers be
     * inserted in forwarded messages?
     * @param connectorName Name of connector to use for live connection.
     */
    public void setClusterConnections(String serverName, String name, String address,
            String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops,
            long retryInterval, boolean useDuplicateDetection, String connectorName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", name);

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
     * Sets size of the journal file.
     *
     * @param sizeInBytes size of the journal file in bytes
     */
    public void setJournalFileSize(long sizeInBytes) {
        setJournalFileSize("default", sizeInBytes);
    }

    /**
     * Sets size of the journal file.
     *
     * @param serverName name of the hornetq server
     * @param sizeInBytes size of the journal file in bytes
     */
    public void setJournalFileSize(String serverName, long sizeInBytes) {

        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("journal-file-size");
        model.get("value").set(sizeInBytes);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * How long (in ms) to wait after the last consumer is closed on a queue
     * before redistributing messages.
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
     * Sets ha attribute.
     *
     * @param connectionFactoryName
     * @param value true if connection factory supports ha.
     */
    public void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
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
     * @param value default false, should be true for fail-over scenarios
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
     * Whether or not messages are acknowledged synchronously.
     *
     * @param connectionFactoryName
     * @param value default false, should be true for fail-over scenarios
     */
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
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
     * The time (in ms) to retry a connection after failing.
     *
     * @param connectionFactoryName
     * @param value
     */
    public void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
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
     * Multiplier to apply to successive retry intervals.
     *
     * @param connectionFactoryName
     * @param value 1.0 by default
     */
    public void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval-multiplier");
        model.get("value").set(value);

        applyUpdateWithRetry(model, 50);

    }

    /**
     * How many times should client retry connection when connection is lost.
     * This should be -1 if failover is required.
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
     * How many times should client retry connection when connection is lost.
     * This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value value
     */
    public void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

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
     * Sets backup attribute.
     *
     * @param isBackup
     */
    public void setBackup(boolean isBackup) {
        setBackup("default", isBackup);
    }

    /**
     * Sets backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    public void setBackup(String serverName, boolean isBackup) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("name").set("backup");
        model.get("value").set(isBackup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds backup attribute.
     *
     * @param isBackup
     */
    public void addBackup(boolean isBackup) {
        setBackup("default", isBackup);
    }

    /**
     * Adds backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    public void addBackup(String serverName, boolean isBackup) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get("backup").set(isBackup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes address settings
     *
     * @param address address specification
     */
    public void removeAddressSettings(String address) {
        try {
            ModelNode setAddressAttributes = new ModelNode();
            setAddressAttributes.get(ClientConstants.OP).set("remove");
            setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
            setAddressAttributes.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
            setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
            try {
                this.applyUpdate(setAddressAttributes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Adds address settings
     *
     * @param address address specification
     * @param addressFullPolicy address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes The max bytes size
     * @param redeliveryDelay Defines how long to wait before attempting
     * redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last
     * consumer is closed on a queue before redistributing any messages
     * @param pageSizeBytes The paging size
     */
    public void addAddressSettings(String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay,
            long redistributionDelay, long pageSizeBytes) {
        ModelNode setAddressAttributes = new ModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds loop back-address type of the given interface of the given name.
     * <p/>
     * Removes inet-address type as a side effect.
     * <p/>
     * Like: <loopback-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or
     * "management"
     * @param ipAddress - ipAddress of the interface
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
     * Adds inet-address type of the given interface name.
     * <p/>
     * Removes inet-address type as a side effect.
     * <p/>
     * Like: <inet-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or
     * "management"
     * @param ipAddress - ipAddress of the interface
     */
    public void setInetAddress(String interfaceName, String ipAddress) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("interface", interfaceName);
        model.get("name").set("inet-address");
        model.get("value").set(ipAddress);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes broadcast group.
     *
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", nameOfTheBroadcastGroup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes discovery group
     *
     * @param dggroup name of the discovery group
     */
    public void removeDiscoveryGroup(String dggroup) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("discovery-group", dggroup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Removes clustering group.
     *
     * @param clusterGroupName name of the discovery group
     */
    public void removeClusteringGroup(String clusterGroupName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method is hack! Somehow calling update model throw exception when it
     * should not. For this reason try it more times until success.
     *
     * @param model model
     * @param retry how many times to retry
     */
    private void applyUpdateWithRetry(ModelNode model, int retry) {
        for (int i = 0; i < retry; i++) {
            try {
                this.applyUpdate(model);
                return;
            } catch (Exception e) {
                if (i >= retry - 1) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Sets logging level for console log - standard output.
     *
     * @param level like "ALL",
     * "CONFIG","DEBUG","ERROR","FATAL","FINE","FINER","FINEST","INFO","OFF","TRACE","WARN","WARNING"
     */
    public void setLoggingLevelForConsole(String level) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("change-log-level");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("console-handler", "CONSOLE");
        model.get("level").set(level);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes defined bridge, method just logs exception it does not throws
     * exception
     *
     * @param name Name of the bridge
     */
    public void removeBridge(String name) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("bridge", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * Creates new bridge
     *
     * @param name bridge name
     * @param queueName source queue
     * @param forwardingAddress target address
     * @param reconnectAttempts reconnect attempts for bridge
     * @param staticConnector static connector
     */
    public void createBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts,
            String staticConnector) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("bridge", name);
        model.get("queue-name").set(queueName);
        if (forwardingAddress != null) {
            model.get("forwarding-address").set(forwardingAddress);
        }
        model.get("use-duplicate-detection").set(true);
        model.get("reconnect-attempts").set(reconnectAttempts);
        model.get("static-connectors").add(staticConnector);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * Remove remote connector
     *
     * @param name name of the remote connector
     */
    public void removeRemoteConnector(String name) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("remote-connector", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }
    
    /**
     * Creates remote connector
     *
     * @param name name of the remote connector
     * @param socketBinding
     * @param params source queue
     */
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {
        createRemoteConnector("default", name, socketBinding, params);
    }

    /**
     * Creates remote connector
     *
     * @param serverName set name of hornetq server
     * @param name name of the remote connector
     * @param socketBinding
     * @param params params
     */
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-connector", name);
        model.get("socket-binding").set(socketBinding);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("param").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates socket binding.
     *
     * @param socketBindingName
     * @param port
     */
    public void createSocketBinding(String socketBindingName, int port) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("port").set(port);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates socket binding.
     *
     * @param socketBindingName
     * @param defaultInterface
     * @param multicastAddress
     * @param multicastPort
     */
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress,
            int multicastPort) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("interface").set(defaultInterface);
        model.get("multicast-address").set(multicastAddress);
        model.get("multicast-port").set(multicastPort);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName
     */
    public void removeSocketBinding(String socketBindingName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);

        //socket-binding-group=standard-sockets/socket-binding=messaging-group:write-attribute(name=multicast-address,value=235.1.1.3)

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param multicastAddress
     * @param multicastPort
     */
    public void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("name").set("multicast-address");
        model.get("value").set(multicastAddress);
        model.get("name").set("multicast-port");
        model.get("value").set(multicastPort);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName
     * @param multicastAddress
     */
    public void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-atrribute");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("name").set("multicast-address");
        model.get("value").set(multicastAddress);
        System.out.println(model.toString());
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates in-vm connector
     *
     * @param name name of the remote connetor
     * @param serverId set server id
     * @param params params for connector
     */
    public void createInVmConnector(String name, int serverId, Map<String, String> params) {
        createInVmConnector("default", name, serverId, params);
    }

    /**
     * Creates in-vm connector
     *
     * @param serverName set name of hornetq server
     * @param name name of the remote connector
     * @param serverId set server id
     * @param params params for connector
     */
    public void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("in-vm-connector", name);
        model.get("server-id").set(serverId);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("param").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates remote acceptor
     *
     * @param name name of the remote acceptor
     * @param socketBinding
     * @param params source queue
     */
    public void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params) {
        createRemoteAcceptor("default", name, socketBinding, params);
    }

    /**
     * Creates remote acceptor
     *
     * @param serverName set name of hornetq server
     * @param name name of the remote acceptor
     * @param socketBinding
     * @param params params
     */
    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-acceptor", name);
        model.get("socket-binding").set(socketBinding);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("param").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
         /**
     * Remove remote acceptor
     *
     * @param name name of the remote acceptor
     */
    public void removeRemoteAcceptor(String name) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", "default");
        model.get(ClientConstants.OP_ADDR).add("remote-acceptor", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * Creates in-vm acceptor
     *
     * @param name name of the connector
     * @param serverId set server id
     * @param params params for connector
     */
    public void createInVmAcceptor(String name, int serverId, Map<String, String> params) {
        createInVmAcceptor("default", name, serverId, params);
    }

    /**
     * Creates in-vm acceptor
     *
     * @param serverName set name of hornetq server
     * @param name name of the connector
     * @param serverId set server id
     * @param params params for connector
     */
    public void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);
        model.get(ClientConstants.OP_ADDR).add("in-vm-acceptor", name);
        model.get("server-id").set(serverId);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("param").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove outbound socket binding
     *
     * @param name remote socket binding name
     */
    public void removeRemoteSocketBinding(String name) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("remote-destination-outbound-socket-binding", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }

    }

    /**
     * Adds outbound socket binding
     *
     * @param name remote socket binding name
     * @param host
     * @param port
     */
    public void addRemoteSocketBinding(String name, String host, int port) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("remote-destination-outbound-socket-binding", name);
        model.get("port").set(port);
        model.get("host").set(host);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds new logging category.
     *
     * @param category like "org.hornetq"
     * @param level like DEBUG, WARN, FINE,...
     */
    public void addLoggerCategory(String category, String level) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("logger", category);
        model.get("category").add(category);
        model.get("level").set(level);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reloads server instance
     */
    public void reloadServer() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("reload");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method checks whether object is null or empty string - "".
     *
     * @param attribute object
     * @return true if null or empty
     */
    private boolean isEmpty(Object attribute) {
        boolean empty = false;

        if (attribute == null) {
            return true;
        }

        if ((attribute instanceof String) && ("".equals(attribute))) {
            empty = true;
        }

        if (attribute instanceof Integer && (Integer) attribute == Integer.MIN_VALUE) {
            empty = true;
        }
        return empty;
    }

    /**
     * Adds new messaging subsystem/new hornetq server to configuration
     *
     * @param serverName name of the new hornetq server
     */
    public void addMessagingSubsystem(String serverName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "messaging");
        model.get(ClientConstants.OP_ADDR).add("hornetq-server", serverName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     */
    public void reload() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("reload");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
