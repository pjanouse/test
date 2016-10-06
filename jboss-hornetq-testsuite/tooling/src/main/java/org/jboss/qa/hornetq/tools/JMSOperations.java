/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.tools;

import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.shrinkwrap.api.Archive;

import java.util.*;


/**
 * @author mnovak@redhat.com
 */
public interface JMSOperations {

    /**
     * Add path prefix for all the subsequent operations.
     *
     * This is useful for EAP 6 domains, where you can set the domain/profile prefix for all the operations.
     *
     * @param key path key
     * @param value path value
     */
    void addAddressPrefix(String key, String value);

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
    void addAddressSettings(String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes);

    /**
     * Adds settings for slow consumers to existing address settings for given mask.
     *
     * @param address       address specification
     * @param threshold     the minimum rate of message consumption allowed (in messages-per-second, -1 is disabled)
     * @param policy        what should happen with slow consumers
     * @param checkPeriod   how often to check for slow consumers (in minutes, default is 5)
     */
    void setSlowConsumerPolicy(String address, int threshold, SlowConsumerPolicy policy, int checkPeriod);

    /**
     * Adds settings for slow consumers to existing address settings for given mask.
     *
     * @param serverName    hornetq server name
     * @param address       address specification
     * @param threshold     the minimum rate of message consumption allowed (in messages-per-second, -1 is disabled)
     * @param policy        what should happen with slow consumers
     * @param checkPeriod   how often to check for slow consumers (in minutes, default is 5)
     */
    void setSlowConsumerPolicy(String serverName, String address, int threshold, SlowConsumerPolicy policy, int checkPeriod);

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

    int getNumberOfPreparedTransaction();

    int getNumberOfPreparedTransaction(String serverName);

    String listPreparedTransaction();

    String listPreparedTransaction(String serverName);

    String listPreparedTransactionAsJson();

    String listPreparedTransactionAsJson(String serverName);

    /**
     * Removes protocol from JGroups stack
     *
     * @param nameOfStack  name of stack udp,tcp
     * @param protocolName protocol name PING,MERGE
     */
    public void removeProtocolFromJGroupsStack(String nameOfStack, String protocolName);

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

    void addSocketBinding(String socketBindingName, int port);

    /**
     * Create security realm
     *
     * @param name  name of the new realm
     */
    void createSecurityRealm(String name);

    /**
     * Add server identity into the security realm
     *
     * @param realmName         name of the security realm
     * @param keyStorePath      path to a key store
     * @param keyStorePass      password to a key store
     */
    void addServerIdentity(String realmName, String keyStorePath, String keyStorePass);


    /**
     * Add server identity into the security realm
     *
     * @param realmName         name of the security realm
     * @param keyStoreProvider  name of keystore provider
     * @param keyStorePass      password to a key store
     */
    void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass);

    /**
     * Add server identity into the security realm
     *
     * @param realmName         name of the security realm
     * @param keyStoreProvider  name of keystore provider
     * @param keyStorePass      password to a key store
     */
    void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider,String keyStorePath, String keyStorePass);

    /**
     * Add authentication into the security realm
     *
     * @param realmName         name of the security realm
     * @param keyStoreProvider     name of keystore provider
     * @param keyStorePass      password to a trust store
     */
    void addAuthenticationWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass);
    /**
     * Add authentication into the security realm
     *
     * @param realmName         name of the security realm
     * @param trustStorePath    path to a trust store
     * @param keyStorePass      password to a trust store
     */
    void addAuthentication(String realmName, String trustStorePath, String keyStorePass);

    /**
     * Add https listener into undertow subsystem
     *
     * @param serverName        name of the undertow server
     * @param name              name of the https listener
     * @param securityRealm     name of the security realm
     * @param socketBinding     name of the socket binding
     * @param verifyClient      politic for verification of client. Possible values: NOT_REQUESTED
     *                          TODO: add all possible values
     */
    void addHttpsListener(String serverName, String name, String securityRealm, String socketBinding, String verifyClient);

    /**
     * Add https listener into undertow subsystem
     *
     * @param name              name of the https listener
     * @param securityRealm     name of the security realm
     * @param socketBinding     name of the socket binding
     * @param verifyClient      politic for verification of client. Possible values: NOT_REQUESTED
     *                          TODO: add all possible values
     */
    void addHttpsListener(String name, String securityRealm, String socketBinding, String verifyClient);

    void removeHttpsListener(String name);


    /**
     * Add XA datasource property.
     *
     * @param poolName
     * @param propertyName
     * @param value
     */
    void addXADatasourceProperty(String poolName, String propertyName, String value);

    void addTopicJNDIName(String queueName, String jndiName);

    void removeQueueJNDIName(String queueName, String jndiName);

    void removeTpicJNDIName(String topicName, String jndiName);

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
    void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, String... staticConnector);

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

    void createHttpConnector(String name, String socketBinding, Map<String, String> params);

    void createHttpConnector(String name, String socketBinding, Map<String, String> params, String endpoint);

    void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params);

    void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params, String endpoint);

    void createConnector(String name, Map<String, String> params);

    void createConnector(String name, String socketBinding, String factoryClass, Map<String, String> params);

    void createAcceptor(String name, Map<String, String> params);

    /**
     * Create acceptor
     *
     * @param name name of acceptor
     * @param socketBinding name of socket binding
     * @param factoryClass factory Java class
     * @param params
     */
    void createAcceptor(String name, String socketBinding, String factoryClass, Map<String, String> params);

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
     * Creates outbound socket binding.
     *
     * @param socketBindingName
     * @param port
     */
    void createOutBoundSocketBinding(String socketBindingName,String host, int port);

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

    void setDefaultResourceAdapter(String resourceAdapterName);

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
     * XA datasource.
     *
     * @param jndi_name
     * @param poolName
     * @param useJavaContext
     * @param useCCM
     * @param driverName
     * @param transactionIsolation
     * @param xaDatasourceProperties
     */
    void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride, boolean noTxSeparatePool,Map<String,String> xaDatasourceProperties);
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

    String getJournalLargeMessageDirectoryPath();

    /**
     *
     */
    void reload();

    /**
     * Removes address settings
     *
     * @param address address specification
     */
    void removeAddressSettings(String address);

    void seRootLoggingLevel(String level);

    /**
     * Disables actually defined FILE-TRACE logging handler
     */
    void disableTraceLoggingToFile();

    /**
     * Disables actually defined logging handler
     *
     * @param handlerName name of handler
     *                    to disable trace logs use FILE-TRACE
     *
     */
    void disableLoggingHandler(String handlerName);

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
     * @param serverName name of the server
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    void removeBroadcastGroup(String serverName, String nameOfTheBroadcastGroup);

    /**
     * Removes broadcast group.
     *
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    void removeBroadcastGroup(String nameOfTheBroadcastGroup);

    /**
     * Removes clustering group.
     *
     * @param serverName name of the server
     * @param clusterGroupName name of the discovery group
     */
    void removeClusteringGroup(String serverName, String clusterGroupName);

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
     * Removes discovery group
     *
     * @param serverName name of the server
     * @param dggroup name of the discovery group
     */
    void removeDiscoveryGroup(String serverName, String dggroup);

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
     * Creates http acceptor.
     *
     * @param name name of the acceptor
     * @param httpListener if null then "default" is used.
     * @param params parameters for the acceptor
     */
    void createHttpAcceptor(String name, String httpListener, Map<String, String> params);

    /**
     * Creates http acceptor.
     *
     * @param serverName name of the server
     * @param name name of the acceptor
     * @param httpListener if null then "default" is used.
     * @param params parameters for the acceptor
     *
     */
    void createHttpAcceptor(String serverName, String name, String httpListener, Map<String, String> params);

    /**
     * Remove connector
     *
     * @param name name of the connector
     */
    void removeConnector(String name);

    /**
     * Remove acceptor
     *
     * @param name name of the acceptor
     */
    void removeAcceptor(String name);

    /**
     * Remove remote acceptor
     *
     * @param name name of the remote acceptor
     */
    void removeRemoteAcceptor(String serverName, String name);

    /**
     * Remove remote acceptor
     *
     * @param name name of the remote acceptor
     */
    void removeRemoteAcceptor(String name);

    void removeHttpConnector(String name);

    void removeHttpAcceptor(String serverName, String name);

    void removeHttpAcceptor(String name);

    void removeHttpConnector(String serverName, String name);

    /**
     * Remove remote connector
     *
     * @param name name of the remote connector
     */
    void removeRemoteConnector(String serverName, String name);

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

    void createSocketBinding(String socketBindingName, int port, String defaultInterface, String multicastAddress,
                             int multicastPort);

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName
     */
    void removeSocketBinding(String socketBindingName);

    /**
     * Removes topic
     *
     * @param topicName topic name
     */
    void removeTopic(String topicName);

    /**
     * Allow jms org.jboss.qa.hornetq.apps.clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     */
    void setAllowFailback(boolean allowFailback);

    /**
     * Allow jms org.jboss.qa.hornetq.apps.clients to reconnect from backup to live when live comes alive.
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
     * The directory in which to store the persisted bindings.
     *
     * @param path set only path segment. The relative-to remains unchanged.
     */
    void setBindingsDirectoryPath(String path);

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param serverName set name of messaging server
     * @param path       set only path segment. The relative-to remains unchanged.
     */
    void setBindingsDirectoryPath(String serverName, String path);

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
     * @param serverName name of the server
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    void setBlockOnAckForConnectionFactory(String serverName, String connectionFactoryName, boolean value);

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
     * @param name            a unique name for the broadcast group - mandatory
     * @param jgroupsStack    jgroups protocol stack
     * @param jgroupsChannel  the name that jgroups channels connect to for broadcasting
     * @param broadcastPeriod period in miliseconds between consecutive broadcasts
     * @param connectorName   a pair connector
     */
    void setBroadCastGroup(String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod, String connectorName);

    /**
     * @param serverName      name of the server
     * @param name            a unique name for the broadcast group - mandatory
     * @param jgroupsStack    jgroups protocol stack
     * @param jgroupsChannel  the name that jgroups channels connect to for broadcasting
     * @param broadcastPeriod period in miliseconds between consecutive broadcasts
     * @param connectorName   a pair connector
     */
    void setBroadCastGroup(String serverName, String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod, String connectorName);

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

    void setDiscoveryGroup(String name, String groupAddress, int groupPort, long refreshTimeout);

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
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param name           A unique name for the discovery group - mandatory.
     * @param refreshTimeout Period the discovery group waits after receiving
     *                       the last broadcast from a particular server before removing that servers
     *                       connector pair entry from its list.
     * @param jgroupsStack   jgroups protocol stack
     * @param jgroupsChannel the name that jgroups channels connect to for broadcasting
     */
    void setDiscoveryGroup(String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel);

    /**
     * Discovery group defines how connector information is received from a
     * multicast address.
     *
     * @param serverName     Name of the server
     * @param name           A unique name for the discovery group - mandatory.
     * @param refreshTimeout Period the discovery group waits after receiving
     *                       the last broadcast from a particular server before removing that servers
     *                       connector pair entry from its list.
     * @param jgroupsStack   jgroups protocol stack
     * @param jgroupsChannel the name that jgroups channels connect to for broadcasting
     */
    void setDiscoveryGroup(String serverName, String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel);

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
     * Sets failover-on-server-shutdown.
     *
     * @param value true if connection factory supports ha.
     */
    void setFailoverOnShutdown(boolean value, String serverName);

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
     * @param serverName name of the server
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    void setHaForConnectionFactory(String serverName, String connectionFactoryName, boolean value);

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
     * Enable access to hornetq management mbeans.
     *
     * @param enable Should we enable JMX.
     */
    void setJmxManagementEnabled(boolean enable);

    /**
     * Enable access to hornetq management mbeans.
     *
     * @param serverName HornetQ server name.
     * @param enable Should we enable JMX.
     */
    void setJmxManagementEnabled(String serverName, boolean enable);

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
     * The directory to store the journal files in.
     *
     * @param path set only path segment. The relative-to remains unchanged.
     */
    void setJournalDirectoryPath(String path);

    /**
     * The directory to store the journal files in.
     *
     * @param serverName set name of messaging server
     * @param path       set only path segment. The relative-to remains unchanged.
     */
    void setJournalDirectoryPath(String serverName, String path);

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
     * The number of journal files that can be reused. ActiveMQ will create
     * as many files as needed however when reclaiming files it will shrink
     * back to the value (-1 means no limit).
     *  @param serverName  set name of hornetq server
     * @param numFiles number of journal files
     */
    void setJournalPoolFiles(String serverName, int numFiles);

    /**
     * The number of journal files that can be reused. ActiveMQ will create
     * as many files as needed however when reclaiming files it will shrink
     * back to the value (-1 means no limit).
     *
     * @param numFiles number of journal files
     */
    void setJournalPoolFiles(int numFiles);

    /**
     * The minimal number of journal data files before we can start compacting.
     * @param serverName
     * @param numFiles
     */
    void setJournalCompactMinFiles(String serverName, int numFiles);

    /**
     * The minimal number of journal data files before we can start compacting.
     * @param numFiles
     */
    void setJournalCompactMinFiles(int numFiles);

    /**
     * The threshold to start compacting. When less than this percentage is considered live data, we start compacting.
     * Note also that compacting won't kick in until you have at least journal-compact-min-files data files on the journal
     *
     * @param percentage
     */
    void setJournalCompactPercentage(int percentage);

    /**
     * The threshold to start compacting. When less than this percentage is considered live data, we start compacting.
     * Note also that compacting won't kick in until you have at least journal-compact-min-files data files on the journal
     *
     * @param serverName
     * @param percentage
     */
    void setJournalCompactPercentage(String serverName, int percentage);

    /**
     * Can be "NIO" or "AIO"
     *
     * @param serverName  set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    void setJournalType(String serverName, String journalType);

    /**
     * Operation for importing messaging journal
     *
     * @param path path to file containing data to be imported
     */
    public void importJournal(String path);

    /**
     * Operation for exporting messaging journal
     *
     * @return name of exported file
     */
    public String exportJournal();

    /**
     * The directory in which to store large messages.
     *
     * @param path set absolute path
     */
    void setLargeMessagesDirectory(String path);

    /**
     * The directory in which to store large messages.
     *
     * @param path set only path segment. The relative-to remains unchanged.
     */
    void setLargeMessagesDirectoryPath(String path);

    /**
     * The directory in which to store large messages.
     *
     * @param serverName set name of messaging server
     * @param path       set only path segment. The relative-to remains unchanged.
     */
    void setLargeMessagesDirectoryPath(String serverName, String path);

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
     * The directory to store paged messages in.
     *
     * @param path set only path segment. The relative-to remains unchanged.
     */
    void setPagingDirectoryPath(String path);

    /**
     * The directory to store paged messages in.
     *
     * @param serverName set name of the server
     * @param path       set only path segment. The relative-to remains unchanged.
     */
    void setPagingDirectoryPath(String serverName, String path);

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
     * Sets permission privileges to a given role.
     *
     * @param serverName server name
     * @param address    address of the queue like '#' (for all queues)
     * @param role       role of the user like 'guest'
     * @param permission possible values
     *                   {consume,create-durable-queue,create-non-durable-queue,delete-durable-queue,,delete-non-durable-queue,manage,send}
     * @param value      true for enable permission
     */
    void setPermissionToRoleToSecuritySettings(String serverName, String address, String role, String permission, boolean value);

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param persistenceEnabled - true for persist messages
     */
    void setPersistenceEnabled(boolean persistenceEnabled);

    void addDivert(String divertName, String divertAddress, String forwardingAddress, boolean isExclusive,
                   String filter, String routingName, String transformerClassName);

    void addDivert(String serverName, String divertName, String divertAddress, String forwardingAddress, boolean isExclusive,
                   String filter, String routingName, String transformerClassName);

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param serverName         sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    void setPersistenceEnabled(String serverName, boolean persistenceEnabled);

    void setClusterConnections(String name, String address, String discoveryGroupRef,
                               Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy, int maxHops,
                               long retryInterval, boolean useDuplicateDetection, String connectorName);

    void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef,
                               Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy, int maxHops,
                               long retryInterval, boolean useDuplicateDetection, String connectorName);

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
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param callTimeout   value
     */
    void setCallTimeoutForConnectionFactory(String connectionFactoryName, long callTimeout);

    /**
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value   value
     */
    void setConnectionTTLForConnectionFactory(String connectionFactoryName, long value);


    /**
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value   value
     */
    void setClientFailureCheckPeriodForConnectionFactory(String connectionFactoryName, long value);

    /**
     * How many times should client retry connection when connection is lost.
     * This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value);

    /**
     * The number of times to set up an MDB endpoint
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    void setSetupAttemptsForPooledConnectionFactory(String connectionFactoryName, int value);


    /**
     * The number of attempts to connect initially with this factory
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    void setInitialConnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value);

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
     * @param serverName name of the server
     * @param connectionFactoryName
     * @param value
     */
    void setRetryIntervalForConnectionFactory(String serverName, String connectionFactoryName, long value);

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
     * @param serverName name of the server
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    void setRetryIntervalMultiplierForConnectionFactory(String serverName, String connectionFactoryName, double value);

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
     * Sets security on HornetQ
     *
     * @param value
     * @param serverName
     */
    void setSecurityEnabled(String serverName, boolean value);

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

    void setStaticClusterConnections(String serverName, String name, String address, Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy,
                                     int maxHops, long retryInterval, boolean useDuplicateDetection,
                                     String connectorName, String... remoteConnectors);

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

    void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha, String username, String password);

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param port              port of the socket binding
     */
    void setMulticastPortOnSocketBinding(String socketBindingName, int port);

    /**
     * Set compression.
     *
     * @param connectionFactoryName name of the connection factory
     * @param value                 true to enable large message compression
     */
    void setCompressionOnConnectionFactory(String connectionFactoryName, boolean value);

    /**
     *
     * @param serverName
     * @return check whether server (HornetQ/Artemis) is active or not
     */
    boolean isActive(String serverName);

    /**
     * Set old(true) or new failover model(false)
     *
     * @param keepOldFailover          false to activate it
     * @param nodeStateRefreshInterval after which time will be node's timestamp updated in database
     */
    void setKeepOldFailoverModel(boolean keepOldFailover, long nodeStateRefreshInterval);

    /**
     * Whether to retyr connection to database
     *
     * @param retryOnConnectionFailure true for retry
     * @param retryInterval            interval in miliseconds
     * @param maxRetry                 how many times to retry before giving up
     */
    void setRetryForDb(boolean retryOnConnectionFailure, long retryInterval, int maxRetry);

    /**
     * Sets TUNNEL protocol for jgroups
     *
     * @param gossipRouterHostname ip address of gosship router
     * @param gossipRouterPort     port of gosship router
     */
    void setTunnelForJGroups(String gossipRouterHostname, int gossipRouterPort);

    /**
     * Set database.
     *
     * @param databaseHostname hostname
     * @param databasePort     port
     */
    void setDatabase(String databaseHostname, int databasePort);

    void removeAddressSettings(String serverName, String address);

    void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay,
                            long redistributionDelay, long pageSizeBytes);

    void addMessageGrouping(String serverName, String name, String type, String address, long timeout,
                            long groupTimeout, long reaperPeriod);


    void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay,
                            long redistributionDelay, long pageSizeBytes, boolean lastValueQueue);

    void addExternalContext(String binding, String className, String module, String bindingType, Map<String, String> environmentProperies);

    /**
     * Sets transaction node identifier.
     *
     * @param i
     */
    void setNodeIdentifier(int i);

    void setAuthenticationForNullUsers(boolean b);

    void addDatasourceProperty(String lodhDb, String propertyName, String value);

    void setMaxSavedReplicatedJournals(int numberOfReplicatedJournals);

    void setMaxSavedReplicatedJournals(String serverName, int numberOfReplicatedJournals);

    void setBackupGroupName(String nameOfBackupGroup);

    void setBackupGroupName(String nameOfBackupGroup, String serverName);

    void setCheckForLiveServer(boolean b);

    void setCheckForLiveServer(boolean b, String serverName);

    void addRoleToSecuritySettings(String backupServerName, String s, String guest);

    void addSecuritySetting(String serverName, String s);

    void removeSecuritySettings(String serverName, String addressMask);

    void setConnectorOnConnectionFactory(String serverName, String nameConnectionFactory, String proxyConnectorName);

    void setConnectorOnConnectionFactory(String nameConnectionFactory, String proxyConnectorName);

    void setMinPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size);

    void setMinLargeMessageSizeOnPooledConnectionFactory(String connectionFactoryName, long size);

    void setMinLargeMessageSizeOnConnectionFactory(String connectionFactoryName, long size);

    void setMaxPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size);

    void removeJGroupsStack(String stackName);

    void addJGroupsStack(String stackName, LinkedHashMap<String, Properties> protocols, Properties transportParameters);

    void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha,
                          String discoveryGroupName);

    void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha,
                          String discoveryGroupName);

    void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress, int reconnectAttempts,
                          String... staticConnector);

    void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceQueue, Map<String, String> sourceContext,
                         String targetConnectionFactory, String targetDestination, Map<String, String> targetContext, String qualityOfService,
                         long failureRetryInterval, int maxRetries, long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader);

    void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceQueue, Map<String, String> sourceContext,
                         String targetConnectionFactory, String targetDestination, Map<String, String> targetContext, String qualityOfService,
                         long failureRetryInterval, int maxRetries, long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader, String sourceUser,
                         String sourcePassword, String targetUser, String targetPassword);

    void setFactoryType(String serverName, String connectionFactoryName, String factoryType);

    void setFactoryType(String connectionFactoryName, String factoryType);


    void addTransportToJGroupsStack(String stackName, String transport, String gosshipRouterAddress, int gosshipRouterPort, boolean enableBundling);

    void createConnectionFactory(String connectionFactoryName, String jndiName, String connectorName);

    void createConnectionFactory(String serverName, String connectionFactoryName, String jndiName, String connectorName);

    void removeConnectionFactory(String connectionFactoryName);

    void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay,
                                   long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue);

    void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes,
            int redeliveryDelay, long redistributionDelay, long pageSizeBytes,
            String expireQueue, String deadLetterQueue, int maxDeliveryAttempts);

    void addMessageGrouping(String name, String type, String address, long timeout);

    void addMessageGrouping(String serverName, String name, String type, String address, long timeout);

    void setXADatasourceAtribute(String poolName, String attributeName, String value);

    void addExtension(String extensionName);

    void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha);

    void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha, String username, String password);

    void setPooledConnectionFactoryToDiscovery(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha,
                                               int reconnectAttempts, String connectorClassName);

    void setPooledConnectionFactoryWithStaticConnectors(String hostname, int port, boolean ha,
                                                        int reconnectAttempts, String connectorClassName);

    void setLoadbalancingPolicyOnPooledConnectionFactory(String connectionFactoryName, String loadbalancingClassName);

    void setPooledConnectionFactoryToDiscovery(String pooledConnectionFactoryName, String discoveryGroupName);

    void setJndiNameForPooledConnectionFactory(String pooledConnectionFactoryName, String jndiName);

    void setJournalMinCompactFiles(int i);

    void setDeliveryGroupActive(String deliveryGroup, boolean isDeliveryGroupActive);

    void startJMSBridge(String jmsBridgeName);

    /**
     * Set whether environment property replacement is avaible or not.
     *
     * @param propertyName "annotation-property-replacement", "ear-subdeployments-isolated",
     *                     "jboss-descriptor-property-replacement",  "spec-descriptor-property-replacement"
     * @param isEnabled   whether to enable it or not
     */
    void setPropertyReplacement(String propertyName, boolean isEnabled);

    void addSubsystem(String subsystemName);

    void addSecurityProvider(String providerName, String providerType, Map<String, String> attributes);

    int getNumberOfNodesInCluster();

    /**
     * Returns count of durable Subscriptions on Server for giver client
     * @param clientId
     * @return
     */
    int getNumberOfDurableSubscriptionsOnTopicForClient(String clientId);

    int getNumberOfDurableSubscriptionsOnTopic(String topicName);

    int getNumberOfNonDurableSubscriptionsOnTopic(String topicName);

    int getNumberOfTempQueues();

    String getPagingDirectoryPath();


    boolean areThereUnfinishedArjunaTransactions();

    boolean closeClientsByDestinationAddress(String address);

    boolean closeClientsByUserName(String username);

    List<String> getJNDIEntriesForQueue(String destinationCoreName);

    List<String> getJNDIEntriesForTopic(String destinationCoreName);

    void setDiscoveryGroupOnConnectionFactory(String connectionFactoryName, String discoveryGroupName);

    int getNumberOfActiveClientConnections();

    int getNumberOfConsumersOnTopic(String clientId, String subscriptionName);

    void removeMessageFromQueue(String queueName, String jmsMessageID);

    void forceFailover();

    void setTransactionTimeout(long hornetqTransactionTimeout);

    public String getSocketBindingAtributes(String socketBindingName);

    void rewriteLoginModule(String securityDomain, String authentication, String loginModule, HashMap<String, String> moduleOptions);

    int countConnections();

    void rewriteLoginModule(String loginModule, HashMap<String, String> moduleOptions);

    void overrideInVMSecurity(boolean b);

    void removePooledConnectionFactory(String pooledConnectionFactoryName);

    int getNumberOfConsumersOnQueue(String queue);


    /**
     *
     * @param isAdminOnlyMode
     */
    void reload(boolean isAdminOnlyMode);

    void removeHAPolicy(String serverName);

    void addHAPolicySharedStoreMaster(long failbackDelay, boolean failoverOnServerShutdown);

    void addHAPolicySharedStoreMaster(String serverName, long failbackDelay, boolean failoverOnServerShutdown);

    void addHAPolicySharedStoreSlave(boolean allowFailback, long failbackDelay, boolean failoverOnServerShutdown,
                                     boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
                                     List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName);

    void addHAPolicySharedStoreSlave(String serverName, boolean allowFailback, long failbackDelay, boolean failoverOnServerShutdown,
                                     boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
                                     List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName);

    void addHAPolicyReplicationMaster(boolean checkForLiveServer, String clusterName, String groupName);

    void addHAPolicyReplicationMaster(String serverName, boolean checkForLiveServer, String clusterName, String groupName);

    void addHAPolicyReplicationSlave(boolean allowFailback, String clusterName, long failbackDelay,
                                     String groupName, int maxSavedReplicatedJournalSize, boolean restartBackup,
                                     boolean scaleDown, String scaleDownClusterName, List<String> scaleDownConnectors,
                                     String scaleDownDiscoveryGroup, String scaleDownGroupName);

    void addHAPolicyReplicationSlave(String serverName, boolean allowFailback, String clusterName, long failbackDelay,
                                     String groupName, int maxSavedReplicatedJournalSize, boolean restartBackup,
                                     boolean scaleDown, String scaleDownClusterName, List<String> scaleDownConnectors,
                                     String scaleDownDiscoveryGroup, String scaleDownGroupName);

    void addHAPolicyColocatedSharedStore();

    void addHAPolicyColocatedSharedStore(String serverName, int backupPortOffest, int backupRequestRetries,
                                         int backupRequestRetryInterval, int maxBackups, boolean requestBackup, boolean failoverOnServerShutdown);

    void addHAPolicyCollocatedReplicated();

    void addHAPolicyCollocatedReplicated(String serverName, int backupPortOffest, int backupRequestRetries,
                                         int backupRequestRetryInterval, int maxBackups, boolean requestBackup, String... excludedConnectors);

    void createNewResourceAdapter(String name, String cfName, String user, String password, List<String> destinationNames, String hostUrl);
    void deploy(Archive archive) throws Exception;

    void stopDeliveryToMdb(String deploymentName);

    void startDeliveryToMdb(String deploymentName);

    void stopJMSBridge(String jmsBridgeName);

    void setClusterConnectionCallTimeout(String clusterGroupName, long callTimout);

    void setClusterConnectionCallTimeout(String serverName, String clusterGroupName, long callTimout);

    void setClusterConnectionCheckPeriod(String clusterGroupName, long checkPeriod);

    void setClusterConnectionTTL(String clusterGroupName, long ttl);

    void setJournalMinFiles(String serverName, int i);

    void setJournalMinFiles(int i);

    void setConfirmationWindowsSizeOnClusterConnection(String clusterGroupName, int confirmationWindowsSizeInBytes);

    void addIncomingInterceptor(String serverName, String moduleName, String className);

    void addIncomingInterceptor(String moduleName, String className);

    void addOutgoingInterceptor(String serverName, String moduleName, String className);

    void addOutgoingInterceptor(String moduleName, String className);

    void setAutoCreateJMSQueue(boolean autoCreateJmsQueue);

    void setAutoDeleteJMSQueue(boolean autoDeleteJmsQueue);

    long getCountOfMessagesOnRuntimeQueue(String coreQueueName);

    Set<String> getRuntimeSFClusterQueueNames();

    long getMessagesAdded(String coreQueueName);

    long getMessagesAdded(String coreQueueName, boolean isTopic);

    List<Map<String, String>> listMessages(String coreQueueName);

    boolean isDeliveryActive(Archive mdb, String mdbName);

    void addDeliveryGroup(String deliveryGroup, boolean isDeliveryGroupActive);

    /**
     * Creates reverse proxy handler in defaulot server of undertow subsystem
     * @param name name of this handler
     */
    void createUndertowReverseProxyHandler(String name);

    /**
     * @param handlerName name of reverse proxy handler @see createUndertowReverseProxyHandler
     * @param host name of host which will be used in udertow subsystem. can be enything, it is not a hostname
     * @param outboundSocketBinding socket binding to remote host
     * @param scheme scheme used for this proxy, preffered for our purposes is http
     * @param intanceId id
     * @param path path which will be proxied, for hornetq/artemis it is "/"
     */
    void addHostToUndertowReverseProxyHandler(String handlerName, String host, String outboundSocketBinding, String scheme, String intanceId, String path);

    void addFilterToUndertowServerHost(String filterRef);

    void addLocationToUndertowServerHost(String location, String handler);

    void removeLocationFromUndertowServerHost(String filterRef);

    /**
     * Set modcluster connector - ajp or default (stands for http)
     * @param connectorName
     */
    void setModClusterConnector(String connectorName);

    /**
     * Set modcluster advertise security key. Have to be set the same for all nodes expecting to conenct using modcluster
     * @param key
     */
    void setModClusterAdvertiseKey(String key);

    /**
     * Creates modlcuster filter in undertow subsystem. This fileter needs to be set using addFilterToUndertowServerHost(String filterRef);
     * @param filterName
     * @param managementSocketBinding
     * @param advertiseSocketBinding
     * @param advertiseKey
     */
    void addModClusterFilterToUndertow(String filterName, String managementSocketBinding, String advertiseSocketBinding, String advertiseKey);

    /**
     * Set node id of undertow. Neccessary to run on localhost with more nodes and modcluster
     * @param id
     */
    void setUndertowInstanceId(String id);

    void setRebalanceConnectionsOnPooledConnectionFactory(String pooledConnectionFactoryName, boolean rebalanceConnections);

}
