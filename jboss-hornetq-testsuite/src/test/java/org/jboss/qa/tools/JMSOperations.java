/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.tools;

import java.util.List;
import java.util.Map;

/**
 * @author mnovak@redhat.com
 */
public interface JMSOperations {

    /**
     * Adds address settings
     *
     * @param address             address specification
     * @param addressFullPolicy   address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes        The max bytes size
     * @param redeliveryDelay     Defines how long to wait before attempting
     *                            redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last
     *                            consumer is closed on a queue before redistributing any messages
     * @param pageSizeBytes       The paging size
     */
    void addAddressSettings(String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes);

    /**
     * Adds backup attribute.
     *
     * @param isBackup
     */
    void addBackup(boolean isBackup);

    /**
     * Adds backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    void addBackup(String serverName, boolean isBackup);

    /**
     * Adds clustered attribute.
     *
     * @param clustered set true to allow server to create cluster
     */
    void addClustered(boolean clustered);

    /**
     * Adds clustered attribute.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered  set true to allow server to create cluster
     */
    void addClustered(String serverName, boolean clustered);

    /**
     * Add new jndi name for connection factory.
     *
     * @param connectionFactoryName
     * @param newConnectionFactoryJndiName
     */
    void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName);

    /**
     * Adds journal-type attribute.
     *
     * @param journalType
     */
    void addJournalType(String journalType);

    /**
     * Adds journal-type attribute.
     *
     * @param serverName  set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    void addJournalType(String serverName, String journalType);

    /**
     * Adds new logging category.
     *
     * @param category like "org.hornetq"
     * @param level    like DEBUG, WARN, FINE,...
     */
    void addLoggerCategory(String category, String level);

    /**
     * Adds new messaging subsystem/new hornetq server to configuration
     *
     * @param serverName name of the new hornetq server
     */
    void addMessagingSubsystem(String serverName);

    /**
     * Adds persistence-enabled attribute in servers configuration.
     *
     * @param persistenceEnabled - true for persist messages
     */
    void addPersistenceEnabled(boolean persistenceEnabled);

    /**
     * Adds persistence-enabled attribute in servers configuration.
     *
     * @param serverName         sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    void addPersistenceEnabled(String serverName, boolean persistenceEnabled);

    /**
     * Adds JNDI name for queue
     *
     * @param queueName queue name
     * @param jndiName  new JNDI name for the queue
     */
    void addQueueJNDIName(String queueName, String jndiName);

    /**
     * Adds outbound socket binding
     *
     * @param name remote socket binding name
     * @param host
     * @param port
     */
    void addRemoteSocketBinding(String name, String host, int port);

    /**
     * Adds role to security settings.
     *
     * @param address address of the queue like '#' (for all queues)
     * @param role    role of the user like 'guest'
     */
    void addRoleToSecuritySettings(String address, String role);

    /**
     * Adds security attribute on HornetQ
     *
     * @param value set to false to disable security for hornetq
     */
    void addSecurityEnabled(boolean value);

    /**
     * Adds security attribute on HornetQ
     *
     * @param serverName set name of the hornetq server <<<<<<< HEAD
     * @param value      set to false to disable security for hornetq =======
     */
    void addSecurityEnabled(String serverName, boolean value);

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore share journal
     */
    void addSharedStore(boolean sharedStore);

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore shared journal
     * @param serverName  hornetq server name
     */
    void addSharedStore(String serverName, boolean sharedStore);

    /**
     * Add multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param multicastAddress
     * @param multicastPort
     */
    void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort);

    /**
     * Add XA datasource property.
     *
     * @param poolName
     * @param propertyName
     * @param value
     */
    void addXADatasourceProperty(String poolName, String propertyName, String value);

    /**
     * Cleanups queue
     *
     * @param queueName queue name
     */
    void cleanupQueue(String queueName);

    /**
     * Cleanups topic
     *
     * @param topicName topic name
     */
    void cleanupTopic(String topicName);

    /**
     * Closes connection
     */
    void close();

    /**
     * Creates new bridge
     *
     * @param name              bridge name
     * @param queueName         source queue
     * @param forwardingAddress target address
     * @param reconnectAttempts reconnect attempts for bridge
     * @param staticConnector   static connector
     */
    void createBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, String staticConnector);

    /**
     * Creates in-vm acceptor
     *
     * @param name     name of the connector
     * @param serverId set server id
     * @param params   params for connector
     */
    void createInVmAcceptor(String name, int serverId, Map<String, String> params);

    /**
     * Creates in-vm acceptor
     *
     * @param serverName set name of hornetq server
     * @param name       name of the connector
     * @param serverId   set server id
     * @param params     params for connector
     */
    void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params);

    /**
     * Creates in-vm connector
     *
     * @param name     name of the remote connetor
     * @param serverId set server id
     * @param params   params for connector
     */
    void createInVmConnector(String name, int serverId, Map<String, String> params);

    /**
     * Creates in-vm connector
     *
     * @param serverName set name of hornetq server
     * @param name       name of the remote connector
     * @param serverId   set server id
     * @param params     params for connector
     */
    void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params);

    /**
     * Add driver.
     */
    void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass);

    /**
     * Sets connector on pooled connection factory transaction=xa,
     * entries={{java:jmsXA3}}, connector={["netty"]}, ha=true)
     *
     * @param connectionFactoryName name of the pooled connection factory like
     *                              "hornetq-ra"
     * @param connectorName         name of the connector like "remote-connector"
     */
    void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName);

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName  JNDI queue name
     */
    void createQueue(String queueName, String jndiName);

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName  JNDI queue name
     * @param durable   is queue durable
     */
    void createQueue(String queueName, String jndiName, boolean durable);

    /**
     * Creates queue
     *
     * @param serverName name of the hornetq server
     * @param queueName  queue name
     * @param jndiName   JNDI queue name
     * @param durable    is queue durable
     */
    void createQueue(String serverName, String queueName, String jndiName, boolean durable);

    /**
     * Creates remote acceptor
     *
     * @param name          name of the remote acceptor
     * @param socketBinding
     * @param params        source queue
     */
    void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params);

    /**
     * Creates remote acceptor
     *
     * @param serverName    set name of hornetq server
     * @param name          name of the remote acceptor
     * @param socketBinding
     * @param params        params
     */
    void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params);

    /**
     * Creates remote connector
     *
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        source queue
     */
    void createRemoteConnector(String name, String socketBinding, Map<String, String> params);

    /**
     * Creates remote connector
     *
     * @param serverName    set name of hornetq server
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        params
     */
    void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params);

    /**
     * Creates socket binding.
     *
     * @param socketBindingName
     * @param port
     */
    void createSocketBinding(String socketBindingName, int port);

    /**
     * Creates socket binding.
     *
     * @param socketBindingName
     * @param defaultInterface
     * @param multicastAddress
     * @param multicastPort
     */
    void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress, int multicastPort);

    /**
     * Creates topic
     *
     * @param topicName queue name
     * @param jndiName  JNDI queue name
     */
    void createTopic(String topicName, String jndiName);

    /**
     * Creates topic
     *
     * @param serverName
     * @param topicName  queue name
     * @param jndiName   JNDI queue name
     */
    void createTopic(String serverName, String topicName, String jndiName);

    /**
     * XA datasource.
     *
     * @param jndi_name
     * @param poolName
     * @param useJavaContext
     * @param useCCM
     * @param driverName
     * @param transactionIsolation
     */
    void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride, boolean noTxSeparatePool);

    /**
     * Disables security on HornetQ
     */
    void disableSecurity();

    /**
     * Disables security on HornetQ
     *
     * @param serverName
     */
    void disableSecurity(String serverName);

    /**
     * Returns count of messages on queue
     *
     * @param queueName queue name
     * @return count of messages on queue
     */
    long getCountOfMessagesOnQueue(String queueName);

    /**
     *
     */
    void reload();

    /**
     * Reloads server instance
     */
    void reloadServer();

    /**
     * Removes address settings
     *
     * @param address address specification
     */
    void removeAddressSettings(String address);

    /**
     * Removes defined bridge, method just logs exception it does not throws
     * exception
     *
     * @param name Name of the bridge
     */
    void removeBridge(String name);

    /**
     * Removes broadcast group.
     *
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    void removeBroadcastGroup(String nameOfTheBroadcastGroup);

    /**
     * Removes clustering group.
     *
     * @param clusterGroupName name of the discovery group
     */
    void removeClusteringGroup(String clusterGroupName);

    /**
     * Removes discovery group
     *
     * @param dggroup name of the discovery group
     */
    void removeDiscoveryGroup(String dggroup);

    /**
     * Remove messages from queue
     *
     * @param queueName queue name
     * @return count of removed messages from queue
     */
    long removeMessagesFromQueue(String queueName);

    /**
     * Removes queue
     *
     * @param queueName queue name
     */
    void removeQueue(String queueName);

    /**
     * Remove remote acceptor
     *
     * @param name name of the remote acceptor
     */
    void removeRemoteAcceptor(String name);

    /**
     * Remove remote connector
     *
     * @param name name of the remote connector
     */
    void removeRemoteConnector(String name);

    /**
     * Remove outbound socket binding
     *
     * @param name remote socket binding name
     */
    void removeRemoteSocketBinding(String name);

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName
     */
    void removeSocketBinding(String socketBindingName);

    /**
     * Removes topic
     *
     * @param topicName queue name
     */
    void removeTopic(String topicName);

    /**
     * Allow jms clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     */
    void setAllowFailback(boolean allowFailback);

    /**
     * Allow jms clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     * @param serverName    name of the hornetq server
     */
    void setAllowFailback(String serverName, boolean allowFailback);

    /**
     * Sets backup attribute.
     *
     * @param isBackup
     */
    void setBackup(boolean isBackup);

    /**
     * Sets backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    void setBackup(String serverName, boolean isBackup);

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param path set absolute path
     */
    void setBindingsDirectory(String path);

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    void setBindingsDirectory(String serverName, String path);

    /**
     * Whether or not messages are acknowledged synchronously.
     *
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * Whether or not messages are acknowledged synchronously.
     *
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param name                a unique name for the broadcast group - mandatory.
     * @param localBindAddress    local bind address that the datagram socket is
     *                            bound to. The default value is the wildcard IP address chosen by the
     *                            kernel
     * @param localBindPort       local port to which the datagram socket is bound to.
     * @param groupAddress        multicast address to which the data will be broadcast
     *                            - mandatory.
     * @param groupPort           UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod     period in milliseconds between consecutive
     *                            broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be
     *                            broadcasted.
     */
    void setBroadCastGroup(String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName);

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param serverName          set name of hornetq server
     * @param name                a unique name for the broadcast group - mandatory.
     * @param localBindAddress    local bind address that the datagram socket is
     *                            bound to. The default value is the wildcard IP address chosen by the
     *                            kernel
     * @param localBindPort       local port to which the datagram socket is bound to.
     * @param groupAddress        multicast address to which the data will be broadcast
     *                            - mandatory.
     * @param groupPort           UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod     period in milliseconds between consecutive
     *                            broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be
     *                            broadcasted.
     */
    void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName);

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param name                a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName
     *                            name of the socket binding to use
     *                            for broadcasting connectors
     * @param broadCastPeriod     period in milliseconds between consecutive
     *                            broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be
     *                            broadcasted.
     */
    void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName);

    /**
     * A broadcast group is the means by which a server broadcasts connectors
     * over the network. A connector defines a way in which a client (or other
     * server) can make connections to the server.
     *
     * @param serverName          set name of hornetq server
     * @param name                a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName
     *                            name of the socket binding to use
     *                            for broadcasting connectors
     * @param broadCastPeriod     period in milliseconds between consecutive
     *                            broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be
     *                            broadcasted.
     */
    void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName);

    /**
     * Sets cluster configuration.
     *
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param discoveryGroupRef      Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there
     *                               are no matching consumers on target?
     * @param maxHops                Maximum number of hops cluster topology is propagated.
     *                               Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be
     *                               inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName);

    /**
     * Sets cluster configuration.
     *
     * @param serverName             Set name of hornetq server.
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param discoveryGroupRef      Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there
     *                               are no matching consumers on target?
     * @param maxHops                Maximum number of hops cluster topology is propagated.
     *                               Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be
     *                               inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName);

    /**
     * Sets password for cluster user.
     *
     * @param password password
     */
    void setClusterUserPassword(String password);

    /**
     * Sets password for cluster user.
     *
     * @param password   password
     * @param serverName name of the hornetq server
     */
    void setClusterUserPassword(String serverName, String password);

    /**
     * Sets clustered attribute.
     *
     * @param clustered set true to allow server to create cluster
     */
    void setClustered(boolean clustered);

    /**
     * Sets clustered attribute.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered  set true to allow server to create cluster
     */
    void setClustered(String serverName, boolean clustered);

    /**
     * Sets connection ttl value.
     *
     * @param serverName    name of the server
     * @param valueInMillis ttl
     */
    void setConnectionTtlOverride(String serverName, long valueInMillis);

    /**
     * Sets connector on pooled connection factory
     *
     * @param connectionFactoryName name of the pooled connection factory like
     *                              "hornetq-ra"
     * @param connectorName         name of the connector like "remote-connector"
     */
    void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName);

    /**
     * Sets connector on pooled connection factory
     *
     * @param connectionFactoryName name of the pooled connection factory like
     *                              "hornetq-ra"
     * @param connectorNames
     */
    void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames);

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param name             A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this
     *                         local address.
     * @param groupAddress     Multicast IP address of the group to listen on -
     *                         mandatory.
     * @param groupPort        UDP port of the multicast group - mandatory
     * @param refreshTimeout   Period the discovery group waits after receiving
     *                         the last broadcast from a particular server before removing that servers
     *                         connector pair entry from its list.
     */
    void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout);

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param serverName       Set name of hornetq server
     * @param name             A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this
     *                         local address.
     * @param groupAddress     Multicast IP address of the group to listen on -
     *                         mandatory.
     * @param groupPort        UDP port of the multicast group - mandatory
     * @param refreshTimeout   Period the discovery group waits after receiving
     *                         the last broadcast from a particular server before removing that servers
     *                         connector pair entry from its list.
     */
    void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout);

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param name           A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName
     *                       name of the socket binding to use
     *                       for accepting connectors from other servers
     * @param refreshTimeout Period the discovery group waits after receiving
     *                       the last broadcast from a particular server before removing that servers
     *                       connector pair entry from its list.
     */
    void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout);

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param serverName     Set name of hornetq server
     * @param name           A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName
     *                       name of the socket binding to use
     *                       for accepting connectors from other servers
     * @param refreshTimeout Period the discovery group waits after receiving
     *                       the last broadcast from a particular server before removing that servers
     *                       connector pair entry from its list.
     */
    void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout);

    /**
     * Sets ha attribute.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    void setFailoverOnShutdown(String connectionFactoryName, boolean value);

    /**
     * Sets failover-on-server-shutdown.
     *
     * @param value true if connection factory supports ha.
     */
    void setFailoverOnShutdown(boolean value);

    /**
     * Sets failover-on-server-shutdown.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    void setFailoverOnShutdownOnPooledConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * Sets ha attribute.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    void setHaForConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * Sets ha attribute.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * Sets id-cache-size attribute in servers configuration.
     *
     * @param numberOfIds - number of ids to remember
     */
    void setIdCacheSize(long numberOfIds);

    /**
     * Sets id-cache-size attribute in servers configuration.
     *
     * @param serverName  sets name of the hornetq server to be changed
     * @param numberOfIds - number of ids to remember
     */
    void setIdCacheSize(String serverName, long numberOfIds);

    /**
     * Adds inet-address type of the given interface name.
     * <p/>
     * Removes inet-address type as a side effect.
     * <p/>
     * Like: <inet-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or
     *                      "management"
     * @param ipAddress     - ipAddress of the interface
     */
    void setInetAddress(String interfaceName, String ipAddress);

    /**
     * The JMX domain used to registered HornetQ MBeans in the MBeanServer. ?
     *
     * @param jmxDomainName
     */
    void setJmxDomainName(String jmxDomainName);

    /**
     * The directory to store the journal files in.
     *
     * @param path set absolute path
     */
    void setJournalDirectory(String path);

    /**
     * The directory to store the journal files in.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    void setJournalDirectory(String serverName, String path);

    /**
     * Sets size of the journal file.
     *
     * @param sizeInBytes size of the journal file in bytes
     */
    void setJournalFileSize(long sizeInBytes);

    /**
     * Sets size of the journal file.
     *
     * @param serverName  name of the hornetq server
     * @param sizeInBytes size of the journal file in bytes
     */
    void setJournalFileSize(String serverName, long sizeInBytes);

    /**
     * Can be "NIO" or "AIO"
     *
     * @param journalType
     */
    void setJournalType(String journalType);

    /**
     * Can be "NIO" or "AIO"
     *
     * @param serverName  set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    void setJournalType(String serverName, String journalType);

    /**
     * The directory in which to store large messages.
     *
     * @param path set absolute path
     */
    void setLargeMessagesDirectory(String path);

    /**
     * The directory in which to store large messages.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    void setLargeMessagesDirectory(String serverName, String path);

    /**
     * Sets logging level for console log - standard output.
     *
     * @param level like "ALL",
     *              "CONFIG","DEBUG","ERROR","FATAL","FINE","FINER","FINEST","INFO","OFF","TRACE","WARN","WARNING"
     */
    void setLoggingLevelForConsole(String level);

    /**
     * Adds loop back-address type of the given interface of the given name.
     * <p/>
     * Removes inet-address type as a side effect.
     * <p/>
     * Like: <loopback-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or
     *                      "management"
     * @param ipAddress     - ipAddress of the interface
     */
    void setLoopBackAddressType(String interfaceName, String ipAddress);

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName
     * @param multicastAddress
     */
    void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress);

    /**
     * The directory to store paged messages in.
     *
     * @param path set absolute path
     */
    void setPagingDirectory(String path);

    /**
     * The directory to store paged messages in.
     *
     * @param serverName set name of the server
     * @param path       set absolute path
     */
    void setPagingDirectory(String serverName, String path);

    /**
     * Sets permission privileges to a given role.
     *
     * @param address    address of the queue like '#' (for all queues)
     * @param role       role of the user like 'guest'
     * @param permission possible values
     *                   {consume,create-durable-queue,create-non-durable-queue,delete-durable-queue,,delete-non-durable-queue,manage,send}
     * @param value      true for enable permission
     */
    void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value);

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param persistenceEnabled - true for persist messages
     */
    void setPersistenceEnabled(boolean persistenceEnabled);

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param serverName         sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    void setPersistenceEnabled(String serverName, boolean persistenceEnabled);

    /**
     * Sets reconnect attempts on cluster connection.
     *
     * @param clusterGroupName name
     * @param attempts         number of retries (-1 for indenfitely)
     */
    void setReconnectAttemptsForClusterConnection(String clusterGroupName, int attempts);

    /**
     * How many times should client retry connection when connection is lost.
     * This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value);

    /**
     * How many times should client retry connection when connection is lost.
     * This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value);

    /**
     * How long (in ms) to wait after the last consumer is closed on a queue
     * before redistributing messages.
     *
     * @param delay in milliseconds
     */
    void setRedistributionDelay(long delay);

    /**
     * The time (in ms) to retry a connection after failing.
     *
     * @param connectionFactoryName
     * @param value
     */
    void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value);

    /**
     * The time (in ms) to retry a connection after failing.
     *
     * @param connectionFactoryName
     * @param value
     */
    void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value);

    /**
     * Multiplier to apply to successive retry intervals.
     *
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value);

    /**
     * Multiplier to apply to successive retry intervals.
     *
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value);

    /**
     * Sets security on HornetQ
     *
     * @param value
     */
    void setSecurityEnabled(boolean value);

    /**
     * Set this to true if this server shares journal with other server (with
     * live of backup)
     *
     * @param sharedStore share journal
     */
    void setSharedStore(boolean sharedStore);

    /**
     * Set this to true if this server shares journal with other server (with
     * live of backup)
     *
     * @param sharedStore share journal
     * @param serverName  hornetq server name
     */
    void setSharedStore(String serverName, boolean sharedStore);

    /**
     * Sets cluster configuration.
     *
     * @param serverName             Set name of hornetq server.
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param forwardWhenNoConsumers Should messages be load balanced if there are no matching consumers on target?
     * @param maxHops                Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors);

    /**
     * This method activates preferFactoryRef property in ActivationSpec.java in ejb3-interceptors-aop.xml. This is specific for EAP 5.
     *
     * @param active if true then this attribute is activated. It's defaulted to true.
     */
    void setFactoryRef(boolean active);

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in ra.xml.
     *
     * @param connectorClassName
     * @param connectionParameters
     * @param ha
     */
    void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha);

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param port port of the socket binding
     */
    void setMulticastPortOnSocketBinding(String socketBindingName, int port);

    /**
     * Set compression.
     *
     * @param connectionFactoryName name of the connection factory
     * @param value true to enable large message compression
     */
    void setCompressionOnConnectionFactory(String connectionFactoryName, boolean value);

    /**
     * Set old(true) or new failover model(false)
     *
     * @param keepOldFailover false to activate it
     * @param nodeStateRefreshInterval after which time will be node's timestamp updated in database
     */
    void setKeepOldFailoverModel(boolean keepOldFailover, long nodeStateRefreshInterval);

    /**
     * Whether to retyr connection to database
     *
     * @param retryOnConnectionFailure true for retry
     * @param retryInterval interval in miliseconds
     * @param maxRetry how many times to retry before giving up
     */
    void setRetryForDb(boolean retryOnConnectionFailure, long retryInterval, int maxRetry);

    /**
     * Sets TUNNEL protocol for jgroups
     * @param gossipRouterHostname ip address of gosship router
     * @param gossipRouterPort  port of gosship router
     */
    void setTunnelForJGroups(String gossipRouterHostname, int gossipRouterPort);

    /**
     * Set database.
     *
     * @param databaseHostname hostname
     * @param databasePort  port
     */
    void setDatabase(String databaseHostname, int databasePort);

    void removeAddressSettings(String serverName, String address);

    void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay,
                            long redistributionDelay, long pageSizeBytes);

    /**
     *
     * Sets transaction node identifier.
     *
     * @param i
     */
    void setNodeIdentifier(int i);

    void setAuthenticationForNullUsers(boolean b);

    void addDatasourceProperty(String lodhDb, String propertyName, String value);
}
