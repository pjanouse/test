package org.jboss.qa.hornetq.tools;

import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SlowConsumerPolicy;
import org.kohsuke.MetaInfServices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mnovak on 3/19/15.
 */
@MetaInfServices
public class ActiveMQAdminOperationsEAP7 implements JMSOperations {
    private int port;

    @Override
    public void addAddressPrefix(String key, String value) {

    }

    @Override
    public void addAddressSettings(String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {

    }

    @Override
    public void setSlowConsumerPolicy(String address, int threshold, SlowConsumerPolicy policy, int checkPeriod) {

    }

    @Override
    public void setSlowConsumerPolicy(String serverName, String address, int threshold, SlowConsumerPolicy policy, int checkPeriod) {

    }

    @Override
    public void addBackup(boolean isBackup) {

    }

    @Override
    public void addBackup(String serverName, boolean isBackup) {

    }

    @Override
    public int getNumberOfPreparedTransaction() {
        return 0;
    }

    @Override
    public int getNumberOfPreparedTransaction(String serverName) {
        return 0;
    }

    @Override
    public String listPreparedTransaction() {
        return null;
    }

    @Override
    public String listPreparedTransaction(String serverName) {
        return null;
    }

    @Override
    public void removeProtocolFromJGroupsStack(String nameOfStack, String protocolName) {

    }

    @Override
    public void addClustered(boolean clustered) {

    }

    @Override
    public void addClustered(String serverName, boolean clustered) {

    }

    @Override
    public void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName) {

    }

    @Override
    public void addJournalType(String journalType) {

    }

    @Override
    public void addJournalType(String serverName, String journalType) {

    }

    @Override
    public void addLoggerCategory(String category, String level) {

    }

    @Override
    public void addMessagingSubsystem(String serverName) {

    }

    @Override
    public void addPersistenceEnabled(boolean persistenceEnabled) {

    }

    @Override
    public void addPersistenceEnabled(String serverName, boolean persistenceEnabled) {

    }

    @Override
    public void addQueueJNDIName(String queueName, String jndiName) {

    }

    @Override
    public void addRemoteSocketBinding(String name, String host, int port) {

    }

    @Override
    public void addRoleToSecuritySettings(String address, String role) {

    }

    @Override
    public void addSecurityEnabled(boolean value) {

    }

    @Override
    public void addSecurityEnabled(String serverName, boolean value) {

    }

    @Override
    public void addSharedStore(boolean sharedStore) {

    }

    @Override
    public void addSharedStore(String serverName, boolean sharedStore) {

    }

    @Override
    public void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort) {

    }

    @Override
    public void addSocketBinding(String socketBindingName, int port) {

    }

    @Override
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {

    }

    @Override
    public void addTopicJNDIName(String queueName, String jndiName) {

    }

    @Override
    public void removeQueueJNDIName(String queueName, String jndiName) {

    }

    @Override
    public void removeTpicJNDIName(String topicName, String jndiName) {

    }

    @Override
    public void cleanupQueue(String queueName) {

    }

    @Override
    public void cleanupTopic(String topicName) {

    }

    @Override
    public void close() {

    }

    @Override
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, String... staticConnector) {

    }

    @Override
    public void createInVmAcceptor(String name, int serverId, Map<String, String> params) {

    }

    @Override
    public void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params) {

    }

    @Override
    public void createInVmConnector(String name, int serverId, Map<String, String> params) {

    }

    @Override
    public void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params) {

    }

    @Override
    public void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass) {

    }

    @Override
    public void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {

    }

    @Override
    public void createQueue(String queueName, String jndiName) {

    }

    @Override
    public void createQueue(String queueName, String jndiName, boolean durable) {

    }

    @Override
    public void createQueue(String serverName, String queueName, String jndiName, boolean durable) {

    }

    @Override
    public void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params) {

    }

    @Override
    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params) {

    }

    @Override
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {

    }

    @Override
    public void createConnector(String name, Map<String, String> params) {

    }

    @Override
    public void createAcceptor(String name, Map<String, String> params) {

    }

    @Override
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {

    }

    @Override
    public void createSocketBinding(String socketBindingName, int port) {

    }

    @Override
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress, int multicastPort) {

    }

    @Override
    public void createTopic(String topicName, String jndiName) {

    }

    @Override
    public void createTopic(String serverName, String topicName, String jndiName) {

    }

    @Override
    public void setDefaultResourceAdapter(String resourceAdapterName) {

    }

    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride, boolean noTxSeparatePool) {

    }

    @Override
    public void disableSecurity() {

    }

    @Override
    public void disableSecurity(String serverName) {

    }

    @Override
    public long getCountOfMessagesOnQueue(String queueName) {
        return 0;
    }

    @Override
    public void reload() {

    }

    @Override
    public void reloadServer() {

    }

    @Override
    public void removeAddressSettings(String address) {

    }

    @Override
    public void seRootLoggingLevel(String level) {

    }

    @Override
    public void removeBridge(String name) {

    }

    @Override
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {

    }

    @Override
    public void removeClusteringGroup(String clusterGroupName) {

    }

    @Override
    public void removeDiscoveryGroup(String dggroup) {

    }

    @Override
    public long removeMessagesFromQueue(String queueName) {
        return 0;
    }

    @Override
    public void removeQueue(String queueName) {

    }

    @Override
    public void removeRemoteAcceptor(String name) {

    }

    @Override
    public void removeRemoteConnector(String name) {

    }

    @Override
    public void removeRemoteSocketBinding(String name) {

    }

    @Override
    public void createSocketBinding(String socketBindingName, int port, String defaultInterface, String multicastAddress, int multicastPort) {

    }

    @Override
    public void removeSocketBinding(String socketBindingName) {

    }

    @Override
    public void removeTopic(String topicName) {

    }

    @Override
    public void setAllowFailback(boolean allowFailback) {

    }

    @Override
    public void setAllowFailback(String serverName, boolean allowFailback) {

    }

    @Override
    public void setBackup(boolean isBackup) {

    }

    @Override
    public void setBackup(String serverName, boolean isBackup) {

    }

    @Override
    public void setBindingsDirectory(String path) {

    }

    @Override
    public void setBindingsDirectory(String serverName, String path) {

    }

    @Override
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {

    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {

    }

    @Override
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {

    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {

    }

    @Override
    public void setBroadCastGroup(String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod, String connectorName) {

    }

    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {

    }

    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {

    }

    @Override
    public void setClusterUserPassword(String password) {

    }

    @Override
    public void setClusterUserPassword(String serverName, String password) {

    }

    @Override
    public void setClustered(boolean clustered) {

    }

    @Override
    public void setClustered(String serverName, boolean clustered) {

    }

    @Override
    public void setConnectionTtlOverride(String serverName, long valueInMillis) {

    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName) {

    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {

    }

    @Override
    public void setDiscoveryGroup(String name, String groupAddress, int groupPort, long refreshTimeout) {

    }

    @Override
    public void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {

    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {

    }

    @Override
    public void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout) {

    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout) {

    }

    @Override
    public void setDiscoveryGroup(String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel) {

    }

    @Override
    public void setFailoverOnShutdown(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setFailoverOnShutdown(boolean value) {

    }

    @Override
    public void setFailoverOnShutdownOnPooledConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setFailoverOnShutdown(boolean value, String serverName) {

    }

    @Override
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setIdCacheSize(long numberOfIds) {

    }

    @Override
    public void setIdCacheSize(String serverName, long numberOfIds) {

    }

    @Override
    public void setInetAddress(String interfaceName, String ipAddress) {

    }

    @Override
    public void setJmxDomainName(String jmxDomainName) {

    }

    @Override
    public void setJmxManagementEnabled(boolean enable) {

    }

    @Override
    public void setJmxManagementEnabled(String serverName, boolean enable) {

    }

    @Override
    public void setJournalDirectory(String path) {

    }

    @Override
    public void setJournalDirectory(String serverName, String path) {

    }

    @Override
    public void setJournalFileSize(long sizeInBytes) {

    }

    @Override
    public void setJournalFileSize(String serverName, long sizeInBytes) {

    }

    @Override
    public void setJournalType(String journalType) {

    }

    @Override
    public void setJournalType(String serverName, String journalType) {

    }

    @Override
    public void setLargeMessagesDirectory(String path) {

    }

    @Override
    public void setLargeMessagesDirectory(String serverName, String path) {

    }

    @Override
    public void setLoggingLevelForConsole(String level) {

    }

    @Override
    public void setLoopBackAddressType(String interfaceName, String ipAddress) {

    }

    @Override
    public void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress) {

    }

    @Override
    public void setPagingDirectory(String path) {

    }

    @Override
    public void setPagingDirectory(String serverName, String path) {

    }

    @Override
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {

    }

    @Override
    public void setPermissionToRoleToSecuritySettings(String serverName, String address, String role, String permission, boolean value) {

    }

    @Override
    public void setPersistenceEnabled(boolean persistenceEnabled) {

    }

    @Override
    public void addDivert(String divertName, String divertAddress, String forwardingAddress, boolean isExclusive, String filter, String routingName, String transformerClassName) {

    }

    @Override
    public void addDivert(String serverName, String divertName, String divertAddress, String forwardingAddress, boolean isExclusive, String filter, String routingName, String transformerClassName) {

    }

    @Override
    public void setPersistenceEnabled(String serverName, boolean persistenceEnabled) {

    }

    @Override
    public void setReconnectAttemptsForClusterConnection(String clusterGroupName, int attempts) {

    }

    @Override
    public void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value) {

    }

    @Override
    public void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {

    }

    @Override
    public void setRedistributionDelay(long delay) {

    }

    @Override
    public void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value) {

    }

    @Override
    public void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value) {

    }

    @Override
    public void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value) {

    }

    @Override
    public void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value) {

    }

    @Override
    public void setSecurityEnabled(boolean value) {

    }

    @Override
    public void setSecurityEnabled(String serverName, boolean value) {

    }

    @Override
    public void setSharedStore(boolean sharedStore) {

    }

    @Override
    public void setSharedStore(String serverName, boolean sharedStore) {

    }

    @Override
    public void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors) {

    }

    @Override
    public void setFactoryRef(boolean active) {

    }

    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha) {

    }

    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha, String username, String password) {

    }

    @Override
    public void setMulticastPortOnSocketBinding(String socketBindingName, int port) {

    }

    @Override
    public void setCompressionOnConnectionFactory(String connectionFactoryName, boolean value) {

    }

    @Override
    public void setKeepOldFailoverModel(boolean keepOldFailover, long nodeStateRefreshInterval) {

    }

    @Override
    public void setRetryForDb(boolean retryOnConnectionFailure, long retryInterval, int maxRetry) {

    }

    @Override
    public void setTunnelForJGroups(String gossipRouterHostname, int gossipRouterPort) {

    }

    @Override
    public void setDatabase(String databaseHostname, int databasePort) {

    }

    @Override
    public void removeAddressSettings(String serverName, String address) {

    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {

    }

    @Override
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout, long groupTimeout, long reaperPeriod) {

    }

    @Override
    public void addExternalContext(String binding, String className, String module, String bindingType, Map<String, String> environmentProperies) {

    }

    @Override
    public void setNodeIdentifier(int i) {

    }

    @Override
    public void setAuthenticationForNullUsers(boolean b) {

    }

    @Override
    public void addDatasourceProperty(String lodhDb, String propertyName, String value) {

    }

    @Override
    public void setMaxSavedReplicatedJournals(int numberOfReplicatedJournals) {

    }

    @Override
    public void setMaxSavedReplicatedJournals(String serverName, int numberOfReplicatedJournals) {

    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup) {

    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup, String serverName) {

    }

    @Override
    public void setCheckForLiveServer(boolean b) {

    }

    @Override
    public void setCheckForLiveServer(boolean b, String serverName) {

    }

    @Override
    public void addRoleToSecuritySettings(String backupServerName, String s, String guest) {

    }

    @Override
    public void addSecuritySetting(String serverName, String s) {

    }

    @Override
    public void removeSecuritySettings(String serverName, String addressMask) {

    }

    @Override
    public void setConnectorOnConnectionFactory(String nameConnectionFactory, String proxyConnectorName) {

    }

    @Override
    public void setMinPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size) {

    }

    @Override
    public void setMaxPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size) {

    }

    @Override
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha, String discoveryGroupName) {

    }

    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha, String discoveryGroupName) {

    }

    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress, int reconnectAttempts, String... staticConnector) {

    }

    @Override
    public void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceQueue, Map<String, String> sourceContext, String targetConnectionFactory, String targetDestination, Map<String, String> targetContext, String qualityOfService, long failureRetryInterval, int maxRetries, long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader) {

    }

    @Override
    public void setFactoryType(String serverName, String connectionFactoryName, String factoryType) {

    }

    @Override
    public void setFactoryType(String connectionFactoryName, String factoryType) {

    }

    @Override
    public void addTransportToJGroupsStack(String stackName, String transport, String gosshipRouterAddress, int gosshipRouterPort, boolean enableBundling) {

    }

    @Override
    public void createConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {

    }

    @Override
    public void removeConnectionFactory(String connectionFactoryName) {

    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue) {

    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue, int maxDeliveryAttempts) {

    }

    @Override
    public void addMessageGrouping(String name, String type, String address, long timeout) {

    }

    @Override
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout) {

    }

    @Override
    public void setXADatasourceAtribute(String poolName, String attributeName, String value) {

    }

    @Override
    public void addExtension(String extensionName) {

    }

    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha) {

    }

    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha, String username, String password) {

    }

    @Override
    public void setPooledConnectionFactoryToDiscovery(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha, int reconnectAttempts, String connectorClassName) {

    }

    @Override
    public void setPooledConnectionFactoryWithStaticConnectors(String hostname, int port, boolean ha, int reconnectAttempts, String connectorClassName) {

    }

    @Override
    public void setPooledConnectionFactoryToDiscovery(String pooledConnectionFactoryName, String discoveryGroupName) {

    }

    @Override
    public void setJndiNameForPooledConnectionFactory(String pooledConnectionFactoryName, String jndiName) {

    }

    @Override
    public void setPropertyReplacement(String propertyName, boolean isEnabled) {

    }

    @Override
    public void addSubsystem(String subsystemName) {

    }

    @Override
    public void addSecurityProvider(String providerName, String providerType, Map<String, String> attributes) {

    }

    @Override
    public int getNumberOfNodesInCluster() {
        return 0;
    }

    @Override
    public int getNumberOfDurableSubscriptionsOnTopic(String clientId) {
        return 0;
    }

    @Override
    public int getNumberOfTempQueues() {
        return 0;
    }

    @Override
    public String getPagingDirectoryPath() {
        return null;
    }

    @Override
    public boolean areThereUnfinishedArjunaTransactions() {
        return false;
    }

    @Override
    public boolean closeClientsByDestinationAddress(String address) {
        return false;
    }

    @Override
    public boolean closeClientsByUserName(String username) {
        return false;
    }

    @Override
    public List<String> getJNDIEntriesForQueue(String destinationCoreName) {
        return null;
    }

    @Override
    public List<String> getJNDIEntriesForTopic(String destinationCoreName) {
        return null;
    }

    @Override
    public void setDiscoveryGroupOnConnectionFactory(String connectionFactoryName, String discoveryGroupName) {

    }

    @Override
    public int getNumberOfActiveClientConnections() {
        return 0;
    }

    @Override
    public void removeMessageFromQueue(String queueName, String jmsMessageID) {

    }

    @Override
    public void forceFailover() {

    }

    @Override
    public void setTransactionTimeout(long hornetqTransactionTimeout) {

    }

    @Override
    public String getSocketBindingAtributes(String socketBindingName) {
        return null;
    }

    @Override
    public void rewriteLoginModule(String securityDomain, String authentication, String loginModule, HashMap<String, String> moduleOptions) {

    }

    @Override
    public void rewriteLoginModule(String loginModule, HashMap<String, String> moduleOptions) {

    }

    @Override
    public void overrideInVMSecurity(boolean b) {

    }

    @Override
    public void removePooledConnectionFactory(String pooledConnectionFactoryName) {

    }

    @Override
    public int getNumberOfConsumersOnQueue(String queue) {
        return 0;
    }

    public void setHostname(String hostname) {

    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void connect() {

    }
}
