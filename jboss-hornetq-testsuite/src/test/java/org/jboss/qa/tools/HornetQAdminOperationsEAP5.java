package org.jboss.qa.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import javax.jms.Destination;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

/**
 *
 */
public class HornetQAdminOperationsEAP5 implements JMSOperations {

    // Logger
    private static final Logger logger = Logger.getLogger(HornetQAdminOperationsEAP6.class);
    private Context ctx;
    private String jbossHome;
    private String profile;
    private String hostname;
    private int rmiPort;

    public void HornetQAdminOperationsEAP5() {
    }

    public void connect(String hostname, int port) throws NamingException {
        if (ctx == null) {
            Properties properties = new Properties();
            properties.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
            properties.setProperty("java.naming.provider.url", "jnp://" + hostname + ":1099");
            properties.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");

            ctx = new InitialContext(properties);
        }
    }

    /**
     * Deploys Queue/Topics on the server
     *
     */
    public void createJmsDestination(String serverName, boolean isQueue, String destinationName, String jndiName, boolean durable) {

        try {

            connect(hostname, rmiPort);

            logger.info("deployDestination " + destinationName);

            try {
                logger.info("Checking for :" + destinationName);
                Destination dest = (Destination) ctx.lookup(destinationName);
                if (dest != null) {
                    removeJmsDestination(isQueue, destinationName);
                }
            } catch (Exception e) {
                logger.info("Destination " + destinationName + " not found");
            }

            MBeanServerConnection server = (MBeanServerConnection) ctx.lookup("jmx/invoker/RMIAdaptor");
            ObjectName serverPeer = new ObjectName("org.hornetq:module=JMS,type=Server");
            String operation = (isQueue) ? "createQueue" : "createTopic";
            server.invoke(serverPeer, operation, new Object[]{destinationName, jndiName, null, durable}, new String[]{String.class.getName(), String.class.getName(), String.class.getName(), boolean.class.getName()});

            try {
                Thread.sleep(200);
            } catch (Exception e) {
            }
        } catch (Exception e) {
            logger.info("Invoking mbean", e);
        }
    }

    /**
     * Remove Queue/Topics on the server
     *
     * @param isQueue
     * @param name
     * @throws Exception
     */
    protected void removeJmsDestination(boolean isQueue, String name) {
        try {

            connect(hostname, rmiPort);

            logger.info("undeployDestination " + name);
            MBeanServerConnection server = (MBeanServerConnection) ctx.lookup("jmx/invoker/RMIAdaptor");
            ObjectName serverPeer = new ObjectName("org.hornetq:module=JMS,type=Server");
            String operation = (isQueue) ? "destroyQueue" : "destroyTopic";
            server.invoke(serverPeer, operation, new Object[]{name}, new String[]{String.class.getName()});
            logger.info("Destination " + name + " has been destroyed");
            Thread.sleep(100);
        } catch (Exception e) {
            logger.info("Destination " + name + " does not exist");
        }
    }

    private String getMethoName() {
        Throwable t = new Throwable();
        StackTraceElement[] elements = t.getStackTrace();
        return elements[1].getMethodName();
    }

    @Override
    public void addAddressSettings(String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {

        logger.info("This operation is not supported: " + getMethoName());

    }

    @Override
    public void addBackup(boolean isBackup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addBackup(String serverName, boolean isBackup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addClustered(boolean clustered) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addClustered(String serverName, boolean clustered) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addJournalType(String journalType) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addJournalType(String serverName, String journalType) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addLoggerCategory(String category, String level) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addMessagingSubsystem(String serverName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addPersistenceEnabled(boolean persistenceEnabled) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addQueueJNDIName(String queueName, String jndiName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addRemoteSocketBinding(String name, String host, int port) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addRoleToSecuritySettings(String address, String role) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addSecurityEnabled(boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addSecurityEnabled(String serverName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addSharedStore(boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addSharedStore(String serverName, boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void cleanupQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void cleanupTopic(String topicName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void close() {
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
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createInVmAcceptor(String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createInVmConnector(String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        logger.info("This operation is not supported: " + getMethoName());
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
        createJmsDestination(serverName, true, queueName, jndiName, durable);
    }

    @Override
    public void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, int port) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress, int multicastPort) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void createTopic(String topicName, String jndiName) {
        createTopic("HornetQ.main.config", topicName, jndiName);
    }

    @Override
    public void createTopic(String serverName, String topicName, String jndiName) {
        createJmsDestination(serverName, false, topicName, jndiName, true);
    }

    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM, String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride, boolean noTxSeparatePool) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void disableSecurity() {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void disableSecurity(String serverName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public long getCountOfMessagesOnQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethoName());
        return 0;
    }

    @Override
    public void reload() {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void reloadServer() {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeAddressSettings(String address) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeBridge(String name) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeClusteringGroup(String clusterGroupName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeDiscoveryGroup(String dggroup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public long removeMessagesFromQueue(String queueName) {
        logger.info("This operation is not supported: " + getMethoName());
        return -1;
    }

    @Override
    public void removeQueue(String queueName) {
        removeJmsDestination(true, queueName);
    }

    @Override
    public void removeRemoteAcceptor(String name) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeRemoteConnector(String name) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeRemoteSocketBinding(String name) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeSocketBinding(String socketBindingName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void removeTopic(String topicName) {
        removeJmsDestination(false, topicName);
    }

    @Override
    public void setAllowFailback(boolean allowFailback) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setAllowFailback(String serverName, boolean allowFailback) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBackup(boolean isBackup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBackup(String serverName, boolean isBackup) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBindingsDirectory(String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBindingsDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod, String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClusterUserPassword(String password) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClusterUserPassword(String serverName, String password) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClustered(boolean clustered) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setClustered(String serverName, boolean clustered) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setConnectionTtlOverride(String serverName, long valueInMillis) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setFailoverOnShutdown(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setFailoverOnShutdown(boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setFailoverOnShutdownOnPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setIdCacheSize(long numberOfIds) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setIdCacheSize(String serverName, long numberOfIds) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setInetAddress(String interfaceName, String ipAddress) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJmxDomainName(String jmxDomainName) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalDirectory(String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalFileSize(long sizeInBytes) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalFileSize(String serverName, long sizeInBytes) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalType(String journalType) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setJournalType(String serverName, String journalType) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setLargeMessagesDirectory(String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setLargeMessagesDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setLoggingLevelForConsole(String level) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setLoopBackAddressType(String interfaceName, String ipAddress) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setPagingDirectory(String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setPagingDirectory(String serverName, String path) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled("HornetQ.main.config", persistenceEnabled);
    }

    @Override
    public void setPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        logger.info("Profile is" + profile + ", persistenceEnabled is: " + persistenceEnabled);
        SAXBuilder builder = new SAXBuilder();
        File xmlFile = new File(jbossHome + File.separator + "server" + File.separator + profile + File.separator
                + "deploy" + File.separator + "hornetq" + File.separator + "hornetq-configuration.xml");
        try {

            Document document = (Document) builder.build(xmlFile);
            Element rootNode = document.getRootElement();
            Element clustered = rootNode.getChild("clustered");

            if (clustered != null) {
                rootNode.setAttribute("clustered", String.valueOf(persistenceEnabled));
            } else {
                clustered = new Element("clustered").setText(String.valueOf(persistenceEnabled));
                rootNode.addContent(clustered);
            }

            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.output(document, new FileWriter(xmlFile));

        } catch (IOException io) {
            System.out.println(io.getMessage());
        } catch (JDOMException jdomex) {
            System.out.println(jdomex.getMessage());
        }
    }

    @Override
    public void setReconnectAttemptsForClusterConnection(String clusterGroupName, int attempts) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setRedistributionDelay(long delay) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setSecurityEnabled(boolean value) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setSharedStore(boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setSharedStore(String serverName, boolean sharedStore) {
        logger.info("This operation is not supported: " + getMethoName());
    }

    @Override
    public void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors) {
        logger.info("This operation is not supported: " + getMethoName());
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