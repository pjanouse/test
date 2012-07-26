package org.jboss.qa.tools;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import javax.jms.Destination;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Implementation of the <code>JMSOperations</code> for EAP 5
 */
public class HornetQAdminOperationsEAP5 implements JMSOperations {

    // Logger
    private static final Logger logger = Logger.getLogger(HornetQAdminOperationsEAP6.class);

    private Context ctx;
    private String jbossHome;
    private String profile;
    private String hostname;
    private int rmiPort;

    /**
     * Creates connection to the server
     *
     * @param hostname host name of the target server
     * @param port     port with the JNDI service
     * @throws NamingException if something goes wrong
     */
    public synchronized void connect(String hostname, int port) throws NamingException {
        if (ctx == null) {
            Properties properties = new Properties();
            properties.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
            properties.setProperty("java.naming.provider.url", "jnp://" + hostname + ":" + port);
            properties.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");
            ctx = new InitialContext(properties);
        }
    }

    /**
     * Returns MBean server instance from the remote server
     *
     * @return instance of the MBean server
     * @throws NamingException if something goes wrong
     */
    protected MBeanServerConnection getMBeanServer() throws NamingException {
        return (MBeanServerConnection) ctx.lookup("jmx/invoker/RMIAdaptor");
    }

    /**
     * Returns MBean for HornetQ server
     *
     * @return instance of the 'org.hornetq:module=JMS,type=Server'
     * @throws Exception if something goes wrong
     */
    protected ObjectName getHornetQServerMBean() throws Exception {
        return new ObjectName("org.hornetq:module=JMS,type=Server");
    }

    /**
     * Returns path to the HornetQ configuration file
     *
     * @return path to the configuration file
     */
    protected String getHornetQConfigurationFile() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.jbossHome).append(File.separator).append("server").append(File.separator);
        sb.append(this.profile).append(File.separator).append("deploy").append(File.separator);
        sb.append("hornetq").append(File.separator).append("hornetq-configuration.xml");
        return sb.toString();
    }

    /**
     * Creates JMS destination on the server
     *
     * @param isQueue         is target destination queue?
     * @param destinationName name of the destination
     * @param jndiName        JNDI name of the destination
     * @param durable         determines if created destination is durable
     */
    protected void createJmsDestination(boolean isQueue, String destinationName, String jndiName, boolean durable) {
        try {
            connect(hostname, rmiPort);
            logger.info("deployDestination " + destinationName);
            try {
                logger.info("Checking for :" + destinationName);
                Destination destination = (Destination) ctx.lookup(destinationName);
                if (destination != null) {
                    removeJmsDestination(isQueue, destinationName);
                }
            } catch (Exception e) {
                logger.info("Destination " + destinationName + " not found");
            }
            MBeanServerConnection server = getMBeanServer();
            ObjectName serverPeer = getHornetQServerMBean();
            String operation = (isQueue) ? "createQueue" : "createTopic";
            server.invoke(serverPeer, operation,
                    new Object[]{destinationName, jndiName, null, durable},
                    new String[]{String.class.getName(), String.class.getName(),
                            String.class.getName(), boolean.class.getName()});
        } catch (Exception e) {
            logger.info("Invoking MBean", e);
        }
    }

    /**
     * Removes JMS destination from the server
     *
     * @param isQueue         is target destination queue?
     * @param destinationName name of the destination
     */
    protected void removeJmsDestination(boolean isQueue, String destinationName) {
        try {
            connect(hostname, rmiPort);
            logger.info("undeployDestination " + destinationName);
            MBeanServerConnection server = getMBeanServer();
            ObjectName serverPeer = getHornetQServerMBean();
            String operation = (isQueue) ? "destroyQueue" : "destroyTopic";
            server.invoke(serverPeer, operation, new Object[]{destinationName}, new String[]{String.class.getName()});
            logger.info("Destination " + destinationName + " has been destroyed");
        } catch (Exception e) {
            logger.info("Destination " + destinationName + " does not exist");
        }
    }

    /**
     * Return name of the called method from the stack trace
     *
     * @return method name
     */
    private String getMethodName() {
        Throwable t = new Throwable();
        StackTraceElement[] elements = t.getStackTrace();
        return elements[1].getMethodName();
    }

    @Override
    public void addAddressSettings(String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addBackup(boolean isBackup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addBackup(String serverName, boolean isBackup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addClustered(boolean clustered) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addClustered(String serverName, boolean clustered) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addJournalType(String journalType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addJournalType(String serverName, String journalType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addLoggerCategory(String category, String level) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addMessagingSubsystem(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addPersistenceEnabled(boolean persistenceEnabled) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addQueueJNDIName(String queueName, String jndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addRemoteSocketBinding(String name, String host, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addRoleToSecuritySettings(String address, String role) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSecurityEnabled(boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSecurityEnabled(String serverName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSharedStore(boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSharedStore(String serverName, boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void cleanupQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void cleanupTopic(String topicName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public synchronized void close() {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ex) {
                logger.error("Problem with closing context: " + ex);
            }
            ctx = null;
        }

    }

    @Override
    public void createBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, String staticConnector) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createInVmAcceptor(String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createInVmConnector(String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createQueue(String queueName, String jndiName) {
        createQueue(queueName, jndiName, true);
    }

    @Override
    public void createQueue(String queueName, String jndiName, boolean durable) {
        createQueue("defalut", queueName, jndiName, durable);
    }

    @Override
    public void createQueue(String serverName, String queueName, String jndiName, boolean durable) {
        createJmsDestination(true, queueName, jndiName, durable);
    }

    @Override
    public void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress, int multicastPort) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createTopic(String topicName, String jndiName) {
        createTopic("HornetQ.main.config", topicName, jndiName);
    }

    @Override
    public void createTopic(String serverName, String topicName, String jndiName) {
        createJmsDestination(false, topicName, jndiName, true);
    }

    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride, boolean noTxSeparatePool) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void disableSecurity() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void disableSecurity(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public long getCountOfMessagesOnQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethodName());
        return 0;
    }

    @Override
    public void reload() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void reloadServer() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeAddressSettings(String address) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeBridge(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeClusteringGroup(String clusterGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeDiscoveryGroup(String dggroup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public long removeMessagesFromQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethodName());
        return -1;
    }

    @Override
    public void removeQueue(String queueName) {
        removeJmsDestination(true, queueName);
    }

    @Override
    public void removeRemoteAcceptor(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteConnector(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteSocketBinding(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeSocketBinding(String socketBindingName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeTopic(String topicName) {
        removeJmsDestination(false, topicName);
    }

    @Override
    public void setAllowFailback(boolean allowFailback) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setAllowFailback(String serverName, boolean allowFailback) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBackup(boolean isBackup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBackup(String serverName, boolean isBackup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBindingsDirectory(String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBindingsDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClusterUserPassword(String password) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClusterUserPassword(String serverName, String password) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClustered(boolean clustered) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClustered(String serverName, boolean clustered) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setConnectionTtlOverride(String serverName, long valueInMillis) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setFailoverOnShutdown(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setFailoverOnShutdown(boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setFailoverOnShutdownOnPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setIdCacheSize(long numberOfIds) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setIdCacheSize(String serverName, long numberOfIds) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setInetAddress(String interfaceName, String ipAddress) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJmxDomainName(String jmxDomainName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalDirectory(String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalFileSize(long sizeInBytes) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalFileSize(String serverName, long sizeInBytes) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalType(String journalType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalType(String serverName, String journalType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setLargeMessagesDirectory(String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setLargeMessagesDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setLoggingLevelForConsole(String level) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setLoopBackAddressType(String interfaceName, String ipAddress) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPagingDirectory(String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPagingDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled("HornetQ.main.config", persistenceEnabled);
    }

    @Override
    public void setPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        logger.info("Profile is" + profile + ", persistenceEnabled is: " + persistenceEnabled);
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            String currentValue = XMLManipulation.getNodeContent("//configuration/clustered", doc);
            if (currentValue != null) {
                XMLManipulation.setNodeContent("//configuration/clustered", Boolean.toString(persistenceEnabled), doc);
            } else {
                XMLManipulation.addNode("//configuration", "clustered", Boolean.toString(persistenceEnabled), doc);
            }
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setReconnectAttemptsForClusterConnection(String clusterGroupName, int attempts) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRedistributionDelay(long delay) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//redistribution-delay", Long.toString(delay), doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setSecurityEnabled(boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setSharedStore(boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setSharedStore(String serverName, boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * This method activates preferFactoryRef property in ActivationSpec.java in ejb3-interceptors-aop.xml. This is specific for EAP 5.
     *
     * @param active if true then this attribute is activated. It's defaulted to true.
     */
    @Override
    public void setFactoryRef(boolean active) {

        StringBuilder configurationFile = new StringBuilder();
        configurationFile.append(this.jbossHome);
        configurationFile.append(File.separator);
        configurationFile.append("server");
        configurationFile.append(File.separator);
        configurationFile.append(this.profile);
        configurationFile.append(File.separator);
        configurationFile.append("deploy");
        configurationFile.append(File.separator);
        configurationFile.append("ejb3-interceptors-aop.xml");

        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile.toString());

            String currentValue = XMLManipulation.getNodeContent("//annotation/*[@expr='!class(@org.jboss.ejb3.annotation.DefaultActivationSpecs)']", doc);

//            logger.info("Content of annotation DefaultActivationSpecs is : " + currentValue);

            String contentToSet = "@org.jboss.ejb3.annotation.DefaultActivationSpecs(@javax.ejb.ActivationConfigProperty(" +
                    "propertyName=\"preferFactoryRef\", propertyValue=\"" + active + "\"))";

            if (currentValue != null) {
                XMLManipulation.setNodeContent("//annotation/*[@expr='!class(@org.jboss.ejb3.annotation.DefaultActivationSpecs)']", contentToSet, doc);
            } else {
                HashMap<String,String> attributes = new HashMap<String, String>();
                attributes.put("expr", "!class(@org.jboss.ejb3.annotation.DefaultActivationSpecs)");
                XMLManipulation.addNode("//domain[@name='Message Driven Bean']", "annotation", contentToSet, doc, attributes);
            }

            XMLManipulation.saveDOMModel(doc, configurationFile.toString());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * @return the jbossHome
     */
    public String getJbossHome() {
        return jbossHome;
    }

    /**
     * @param jbossHome the jbossHome to set
     */
    public void setJbossHome(String jbossHome) {
        this.jbossHome = jbossHome;
    }

    /**
     * @return the profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile the profile to set
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the rmiPort
     */
    public int getRmiPort() {
        return rmiPort;
    }

    /**
     * @param rmiPort the rmiPort to set
     */
    public void setRmiPort(int rmiPort) {
        this.rmiPort = rmiPort;
    }
}