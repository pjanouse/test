package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.*;

/**
 * Implementation of the <code>JMSOperations</code> for EAP 5
 */
public class HornetQAdminOperationsEAP5 implements JMSOperations {

    // Logger
    private static final Logger logger = Logger.getLogger(HornetQAdminOperationsEAP5.class);

    private Context ctx;
    private String jbossHome;
    private String profile;
    private String hostname;
    private int rmiPort;

    /**
     * Creates connection to the server
     *
     * @param hostname host name of the target server
     * @param port port with the JNDI service
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
    // protected ObjectName getHornetQServerMBean() throws Exception {
    // return new ObjectName("org.hornetq:module=JMS,type=Server");
    // }
    /**
     * Returns MBean for the Queue
     *
     * @param queueName name of the queue
     * @return name of the Queue MBean
     * @throws Exception if something goes wrong
     */
    protected ObjectName getHornetQQueueMBean(String queueName) throws Exception {
        return new ObjectName(String.format(
                "org.hornetq:address=\"jms.queue.%s\",module=Core,name=\"jms.queue.%s\",type=Queue", queueName, queueName));
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
     * Returns path to the HornetQ configuration file
     *
     * @return path to the configuration file
     */
    protected String getHornetQJmsConfigurationFile() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.jbossHome).append(File.separator).append("server").append(File.separator);
        sb.append(this.profile).append(File.separator).append("deploy").append(File.separator);
        sb.append("hornetq").append(File.separator).append("hornetq-jms.xml");
        return sb.toString();
    }

    /**
     * Returns path to the HornetQ jms-ds.xml configuration file
     *
     * @return path to the configuration file
     */
    protected String getJmsDsConfigurationFile() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.jbossHome).append(File.separator).append("server").append(File.separator);
        sb.append(this.profile).append(File.separator).append("deploy").append(File.separator);
        sb.append("hornetq").append(File.separator).append("jms-ds.xml");
        return sb.toString();
    }

    /**
     * Retrun path to ra.xml. Configuration file of resource adapter.
     *
     * @return path to ra.xml
     */
    protected String getRAConfigurationFile() {

        StringBuilder sb = new StringBuilder();
        sb.append(this.jbossHome).append(File.separator).append("server").append(File.separator);
        sb.append(this.profile).append(File.separator).append("deploy").append(File.separator);
        sb.append("jms-ra.rar").append(File.separator).append("META-INF").append(File.separator).append("ra.xml");
        return sb.toString();
    }

    /**
     * Creates JMS destination on the server
     *
     * @param isQueue is target destination queue?
     * @param destinationName name of the destination
     * @param jndiName JNDI name of the destination
     * @param durable determines if created destination is durable
     */
    protected void createJmsDestination(boolean isQueue, String destinationName, String jndiName, boolean durable) {

        // try to remove it first
        removeJmsDestination(isQueue, destinationName);

        String configurationFile = getHornetQJmsConfigurationFile();

        logger.info("Deploy destination: " + jndiName);

        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);

            Element e;
            if (isQueue) {
                e = doc.createElement("queue");
            } else {
                e = doc.createElement("topic");
            }

            e.setAttribute("name", destinationName);
            Element entry = doc.createElement("entry");
            entry.setAttribute("name", jndiName);
            e.appendChild(entry);

            if (isQueue) {
                Element eDurable = doc.createElement("durable");
                eDurable.setTextContent(String.valueOf(durable));
                e.appendChild(eDurable);
            }

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node node = (Node) xpathInstance.evaluate("//configuration", doc, XPathConstants.NODE);
            node.appendChild(e);

            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Removes JMS destination from the server
     *
     * @param isQueue is target destination queue?
     * @param destinationName name of the destination
     */
    protected void removeJmsDestination(boolean isQueue, String destinationName) {
        String configurationFile = getHornetQJmsConfigurationFile();
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Removing JMS Destination '%s', is queue? '%s'", destinationName, isQueue));
            }
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//configuration/*[@name='" + destinationName + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
        }
        // try {
        // connect(hostname, rmiPort);
        // logger.info("undeployDestination " + destinationName);
        // MBeanServerConnection server = getMBeanServer();
        // ObjectName serverPeer = getHornetQServerMBean();
        // String operation = (isQueue) ? "destroyQueue" : "destroyTopic";
        // server.invoke(serverPeer, operation, new Object[]{destinationName}, new String[]{String.class.getName()});
        // logger.info("Destination " + destinationName + " has been destroyed");
        // } catch (Exception e) {
        // logger.info("Destination " + destinationName + " does not exist");
        // }
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
    public void addAddressPrefix(String key, String value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAddressSettings(String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay,
            long redistributionDelay, long pageSizeBytes) {

        removeAddressSettings(address);

        String configurationFile = getHornetQConfigurationFile();
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("match", address);
            XMLManipulation.addNode("//address-settings", "address-setting", "", doc, attributes);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "address-full-policy", addressFullPolicy,
                    doc);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "max-size-bytes",
                    String.valueOf(maxSizeBytes), doc);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "redelivery-delay",
                    String.valueOf(redeliveryDelay), doc);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "redistribution-delay",
                    String.valueOf(redistributionDelay), doc);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "page-size-bytes",
                    String.valueOf(pageSizeBytes), doc);
            XMLManipulation.addNode("//address-setting[@match='" + address + "']", "max-delivery-attempts",
                    String.valueOf(200), doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setSlowConsumerPolicy(String serverName, String address, int threshold, SlowConsumerPolicy policy,
            int checkPeriod) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setSlowConsumerPolicy(String address, int threshold, SlowConsumerPolicy policy, int checkPeriod) {
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
    public int getNumberOfPreparedTransaction() {

        logger.info("This operation is not supported: " + getMethodName());

        return -1;
    }

    @Override
    public int getNumberOfPreparedTransaction(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());

        return 0;
    }

    @Override
    public String listPreparedTransaction() {

        logger.info("This operation is not supported: " + getMethodName());

        return null;
    }

    @Override
    public String listPreparedTransaction(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());

        return null;
    }

    /**
     * Removes protocol from JGroups stack
     *
     * @param nameOfStack name of stack udp,tcp
     * @param protocolName protocol name PING,MERGE
     */
    @Override
    public void removeProtocolFromJGroupsStack(String nameOfStack, String protocolName) {
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
    public void addSocketBinding(String socketBindingName, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSecurityRealm(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addServerIdentity(String realmName, String keyStorePath, String keyStorePass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePath, String keyStorePass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAuthenticationWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAuthentication(String realmName, String trustStorePath, String keyStorePass) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHttpsListener(String serverName, String name, String securityRealm, String socketBinding, String verifyClient) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHttpsListener(String name, String securityRealm, String socketBinding, String verifyClient) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addTopicJNDIName(String queueName, String jndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeQueueJNDIName(String queueName, String jndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeTpicJNDIName(String topicName, String jndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void cleanupQueue(String queueName) {
        try {
            connect(hostname, rmiPort);
            MBeanServerConnection server = getMBeanServer();
            ObjectName serverPeer = getHornetQQueueMBean(queueName);
            String operation = "removeMessages";
            server.invoke(serverPeer, operation, new Object[]{""}, new String[]{String.class.getName()});
        } catch (Exception e) {
            logger.info("Destination " + queueName + " does not exist");
        }
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
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts,
            String... staticConnector) {
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
        createQueue("default", queueName, jndiName, durable);
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

    /**
     * @param name name of the connector f.e.: "netty-remote"
     * @param socketBinding ingnored
     * @param params map of params
     */
    @Override
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {

        String configurationFile = getHornetQConfigurationFile();
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", name);
            XMLManipulation.addNode("//connectors", "connector", "", doc, attributes);
            XMLManipulation.addNode("//connector[@name='" + name + "']", "factory-class",
                    "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory", doc);
            attributes = new Hashtable<String, String>();
            attributes.put("key", "host");
            attributes.put("value", params.get("host"));
            XMLManipulation.addNode("//connector[@name='" + name + "']", "param", "", doc, attributes);
            attributes = new Hashtable<String, String>();
            attributes.put("key", "port");
            attributes.put("value", params.get("port"));
            XMLManipulation.addNode("//connector[@name='" + name + "']", "param", "", doc, attributes);
            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void createHttpConnector(String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createHttpConnector(String name, String socketBinding, Map<String, String> params, String endpoint) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params, String endpoint) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * @param name name of the connector f.e.: "netty-remote"
     * @param params map of params
     */
    @Override
    public void createConnector(String name, Map<String, String> params) {

        String configurationFile = getHornetQConfigurationFile();
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", name);
            XMLManipulation.addNode("//connectors", "connector", "", doc, attributes);

            XMLManipulation.addNode("//connector[@name='" + name + "']", "factory-class",
                    "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory", doc);

            for (String key : params.keySet()) {
                attributes = new Hashtable<String, String>();
                attributes.put("key", key);
                attributes.put("value", params.get(key));
                XMLManipulation.addNode("//connector[@name='" + name + "']", "param", "", doc, attributes);
            }

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void createConnector(String name, String socketBinding, String factoryClass, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * @param name name of the connector f.e.: "netty-remote"
     * @param params map of params
     */
    @Override
    public void createAcceptor(String name, Map<String, String> params) {

        String configurationFile = getHornetQConfigurationFile();
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", name);
            XMLManipulation.addNode("//acceptors", "acceptor", "", doc, attributes);

            XMLManipulation.addNode("//acceptor[@name='" + name + "']", "factory-class",
                    "org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory", doc);

            for (String key : params.keySet()) {
                attributes = new Hashtable<String, String>();
                attributes.put("key", key);
                attributes.put("value", params.get(key));
                XMLManipulation.addNode("//acceptor[@name='" + name + "']", "param", "", doc, attributes);
            }

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void createAcceptor(String name, String socketBinding, String factoryClass, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createOutBoundSocketBinding(String socketBindingName, String host, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress,
            int multicastPort) {
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
    public void setDefaultResourceAdapter(String resourceAdapterName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM,
            String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride,
            boolean noTxSeparatePool) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM,
            String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride,
            boolean noTxSeparatePool, Map<String, String> xaDatasourceProperties) {
        throw new UnsupportedOperationException("This operation is not supported: " + getMethodName());
    }

    @Override
    public void disableSecurity() {
        String configurationFile = getHornetQConfigurationFile();
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            // if exists just set
            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node node = (Node) xpathInstance.evaluate("//security-enabled", doc, XPathConstants.NODE);
            if (node != null) {
                XMLManipulation.setNodeContent("//security-enabled", "false", doc);
            } else { // else add it
                XMLManipulation.addNode("//configuration", "security-enabled", "false", doc);
            }
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void disableSecurity(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * @see {@link JMSOperations#getCountOfMessagesOnQueue(String)}
     */
    @Override
    public long getCountOfMessagesOnQueue(String queueName) {
        long result = 0;
        try {
            connect(hostname, rmiPort);
            MBeanServerConnection server = getMBeanServer();
            ObjectName queue = getHornetQQueueMBean(queueName);
            result = (Long) server.getAttribute(queue, "MessageCount");
        } catch (Exception e) {
            logger.info("Invoking MBean", e);
        }
        return result;
    }

    @Override
    public String getJournalLargeMessageDirectoryPath() {
        logger.info("This operation is not supported: " + getMethodName());
        return null;
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
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//address-setting[@match='" + address + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void seRootLoggingLevel(String level) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeBridge(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeBroadcastGroup(String serverName, String nameOfTheBroadcastGroup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//broadcast-group[@name='" + nameOfTheBroadcastGroup + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeClusteringGroup(String serverName, String clusterGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeClusteringGroup(String clusterGroupName) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//cluster-connection[@name='" + clusterGroupName + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeDiscoveryGroup(String dggroup) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//discovery-group[@name='" + dggroup + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeDiscoveryGroup(String serverName, String dggroup) {
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
    public void createHttpAcceptor(String name, String httpListener, Map<String, String> params) {

    }

    @Override
    public void createHttpAcceptor(String serverName, String name, String httpListener, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeConnector(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeAcceptor(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteAcceptor(String serverName, String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteAcceptor(String name) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//acceptor[@name='" + name + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeHttpConnector(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeHttpAcceptor(String serverName, String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeHttpAcceptor(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeHttpConnector(String serverName, String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteConnector(String serverName, String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeRemoteConnector(String name) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.removeNode("//connector[@name='" + name + "']", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void removeRemoteSocketBinding(String name) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createSocketBinding(String socketBindingName, int port, String defaultInterface, String multicastAddress,
            int multicastPort) {
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
        String configurationFile = getHornetQConfigurationFile();
        try {
            logger.info("Set bindings directory to " + path + " in " + configurationFile);
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//bindings-directory", new File(path).getAbsolutePath() + File.separator
                    + "bindings", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setBindingsDirectory(String serverName, String path) {
        setBindingsDirectory(path);
    }

    @Override
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBlockOnAckForConnectionFactory(String serverName, String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String broadcastGroupName, String localBindAddress, int localBindPort, String groupAddress,
            int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", broadcastGroupName);
            XMLManipulation.addNode("//broadcast-groups", "broadcast-group", "", doc, attributes);
            // TODO There is issue https://issues.jboss.org/browse/JBPAPP-11148 - until resolved keep next 2 lines commented
            // XMLManipulation.addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "local-bind-address",
            // localBindAddress, doc);
            // XMLManipulation.addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "local-bind-port",
            // String.valueOf(localBindPort), doc);
            XMLManipulation
                    .addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "group-address", groupAddress, doc);
            XMLManipulation.addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "group-port",
                    String.valueOf(groupPort), doc);
            XMLManipulation.addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "broadcast-period",
                    String.valueOf(broadCastPeriod), doc);
            if (backupConnectorName != null && !"".equals(backupConnectorName)) {
                logger.info("setBroadCastGroup - backupConnectorName is not null but it's unsupported for now. (TODO)");
            }
            XMLManipulation.addNode("//broadcast-group[@name='" + broadcastGroupName + "']", "connector-ref", connectorName,
                    doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setBroadCastGroup(String serverName, String broadcastGroupName, String localBindAddress, int localBindPort,
            String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {
        // ignore serverName for EAP 5
        setBroadCastGroup(broadcastGroupName, localBindAddress, localBindPort, groupAddress, groupPort, broadCastPeriod,
                connectorName, backupConnectorName);

    }

    @Override
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod,
            String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod,
            String connectorName, String backupConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod,
            String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBroadCastGroup(String serverName, String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers,
            int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        // ignore connectorName for EAP5
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", name);
            XMLManipulation.addNode("//cluster-connections", "cluster-connection", "", doc, attributes);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "address", address, doc);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "connector-ref", connectorName, doc);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "retry-interval",
                    String.valueOf(retryInterval), doc);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "use-duplicate-detection",
                    String.valueOf(useDuplicateDetection), doc);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "forward-when-no-consumers",
                    String.valueOf(forwardWhenNoConsumers), doc);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "max-hops", String.valueOf(maxHops), doc);
            attributes = new HashMap<String, String>();
            attributes.put("discovery-group-name", discoveryGroupRef);
            XMLManipulation.addNode("//cluster-connection[@name='" + name + "']", "discovery-group-ref", "", doc, attributes);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef,
            boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        // ignore serverName
        setClusterConnections(name, address, discoveryGroupRef, forwardWhenNoConsumers, maxHops, retryInterval,
                useDuplicateDetection, connectorName);
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
        String configurationFile = getHornetQConfigurationFile();
        try {
            logger.info("Set clustered to " + clustered + " in " + configurationFile);
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//clustered", String.valueOf(clustered), doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
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

    }

    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String name, String groupAddress, int groupPort, long refreshTimeout) {

        setDiscoveryGroup(name, null, groupAddress, groupPort, refreshTimeout);
    }

    @Override
    public void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("name", name);
            XMLManipulation.addNode("//discovery-groups", "discovery-group", "", doc, attributes);
            if (localBindAddress != null & !"".equals(localBindAddress)) {
                XMLManipulation.addNode("//discovery-group[@name='" + name + "']", "local-bind-address", localBindAddress, doc);
            }
            XMLManipulation.addNode("//discovery-group[@name='" + name + "']", "group-address", groupAddress, doc);
            XMLManipulation.addNode("//discovery-group[@name='" + name + "']", "group-port", String.valueOf(groupPort), doc);
            XMLManipulation.addNode("//discovery-group[@name='" + name + "']", "refresh-timeout",
                    String.valueOf(refreshTimeout), doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort,
            long refreshTimeout) {
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
    public void setDiscoveryGroup(String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setDiscoveryGroup(String serverName, String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel) {
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

    /**
     * Sets failover-on-server-shutdown.
     *
     * @param value true if connection factory supports ha.
     */
    @Override
    public void setFailoverOnShutdown(boolean value, String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setHaForConnectionFactory(String serverName, String connectionFactoryName, boolean value) {
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
    public void setJmxManagementEnabled(boolean enable) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJmxManagementEnabled(String serverName, boolean enable) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJournalDirectory(String path) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            logger.info("Set journal directory to " + path + " in " + configurationFile);
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//journal-directory",
                    new File(path).getAbsolutePath() + File.separator + "journal", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setJournalDirectory(String serverName, String path) {
        setJournalDirectory(path);
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
    public String exportJournal() {
        throw new UnsupportedOperationException("export journal not supported for eap5 operations");
    }

    @Override
    public void importJournal(String path) {
        throw new UnsupportedOperationException("import journal not supported for eap5 operations");
    }

    @Override
    public void setLargeMessagesDirectory(String path) {
        String configurationFile = getHornetQConfigurationFile();
        try {
            logger.info("Set large-messages-directory directory to " + path + " in " + configurationFile);
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//large-messages-directory", new File(path).getAbsolutePath() + File.separator
                    + "large-messages", doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setLargeMessagesDirectory(String serverName, String path) {
        setLargeMessagesDirectory(path);
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
        String configurationFile = getHornetQConfigurationFile();
        try {
            logger.info("Set paging-directory directory to " + path + " in " + configurationFile);
            Document doc = XMLManipulation.getDOMModel(configurationFile);
            XMLManipulation.setNodeContent("//paging-directory", new File(path).getAbsolutePath() + File.separator + "paging",
                    doc);
            XMLManipulation.saveDOMModel(doc, configurationFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setPagingDirectory(String serverName, String path) {
        setPagingDirectory(path);
    }

    @Override
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {
        String configurationFile = getHornetQConfigurationFile();

        try {
            Document doc = XMLManipulation.getDOMModel(configurationFile);

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node securityRootNode = (Node) xpathInstance.evaluate("//security-setting", doc, XPathConstants.NODE);

            securityRootNode.appendChild(createSecurityPermission(doc, role, permission));

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * @param doc document
     * @param role role
     * @param permission permission
     * @return config property
     */
    private Node createSecurityPermission(Document doc, String role, String permission) {

        Element e = doc.createElement("permission");

        e.setAttribute("roles", role);
        e.setAttribute("type", permission);

        return e;
    }

    /**
     * Sets permission privileges to a given role.
     *
     * @param serverName server name
     * @param address address of the queue like '#' (for all queues)
     * @param role role of the user like 'guest'
     * @param permission possible values
     * {consume,create-durable-queue,create-non-durable-queue,delete-durable-queue,,delete-non-durable-queue,manage,send}
     * @param value true for enable permission
     */
    @Override
    public void setPermissionToRoleToSecuritySettings(String serverName, String address, String role, String permission,
            boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled("HornetQ.main.config", persistenceEnabled);
    }

    @Override
    public void addDivert(String divertName, String divertAddress, String forwardingAddress, boolean isExclusive,
            String filter, String routingName, String transformerClassName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addDivert(String serverName, String divertName, String divertAddress, String forwardingAddress,
            boolean isExclusive, String filter, String routingName, String transformerClassName) {
        logger.info("This operation is not supported: " + getMethodName());
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
    public void setRetryIntervalForConnectionFactory(String serverName, String connectionFactoryName, long value) {
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
    public void setRetryIntervalMultiplierForConnectionFactory(String serverName, String connectionFactoryName, double value) {
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

    /**
     * Sets security on HornetQ
     *
     * @param value value
     * @param serverName server name
     */
    @Override
    public void setSecurityEnabled(String serverName, boolean value) {
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
    public void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers,
            int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * This method activates preferFactoryRef property in ActivationSpec.java in
     * ejb3-interceptors-aop.xml. This is specific for EAP 5.
     *
     * @param active if true then this attribute is activated. It's defaulted to
     * true.
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

            String currentValue = XMLManipulation.getNodeContent(
                    "//annotation/*[@expr='!class(@org.jboss.ejb3.annotation.DefaultActivationSpecs)']", doc);

            // logger.info("Content of annotation DefaultActivationSpecs is : " + currentValue);
            String contentToSet = "@org.jboss.ejb3.annotation.DefaultActivationSpecs(@javax.ejb.ActivationConfigProperty("
                    + "propertyName=\"preferFactoryRef\", propertyValue=\"" + active + "\"))";

            if (currentValue != null) {
                XMLManipulation.setNodeContent(
                        "//annotation/*[@expr='!class(@org.jboss.ejb3.annotation.DefaultActivationSpecs)']", contentToSet, doc);
            } else {
                HashMap<String, String> attributes = new HashMap<String, String>();
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

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in ra.xml.
     *
     * @param connectorClassName
     * org.hornetq.core.remoting.impl.invm.InVMConnectorFactory,org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
     * @param connectionParameters host->port
     * @param ha if ha
     */
    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha) {
        setRA(connectorClassName, connectionParameters, ha, null, null);
    }

    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha, String username,
            String password) {

        String configurationFile = getRAConfigurationFile();

        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            // child[contains(string(),'Likes')]
            XMLManipulation.removeNode("//config-property/*[contains(string(),'ConnectorClassName')]/..", doc);
            XMLManipulation.removeNode("//config-property/*[contains(string(),'ConnectionParameters')]/..", doc);
            XMLManipulation.removeNode("//config-property/*[contains(string(),'HA')]/..", doc);

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node rootNode = (Node) xpathInstance.evaluate("//resourceadapter", doc, XPathConstants.NODE);
            Node insertBeforeNode = (Node) xpathInstance.evaluate("//outbound-resourceadapter", doc, XPathConstants.NODE);

            rootNode.insertBefore(
                    createConfigProperty(doc, "desc", "ConnectorClassName", "java.lang.String",
                            "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"), insertBeforeNode);

            StringBuilder st = new StringBuilder();
            for (String key : connectionParameters.keySet()) {
                st.append("host=").append(key).append(";port=").append(connectionParameters.get(key)).append(",");
            }
            // remove last comma ","
            st.deleteCharAt(st.length() - 1);
            logger.info("Setting ConnectionParameters in ra.xml to: " + st);
            rootNode.insertBefore(createConfigProperty(doc, "desc", "ConnectionParameters", "java.lang.String", st.toString()),
                    insertBeforeNode);
            rootNode.insertBefore(createConfigProperty(doc, "desc", "HA", "java.lang.Boolean", String.valueOf(ha)),
                    insertBeforeNode);
            if (username != null && password != null) {
                rootNode.insertBefore(createConfigProperty(doc, "desc", "UserName", "java.lang.String", username),
                        insertBeforeNode);
                rootNode.insertBefore(createConfigProperty(doc, "desc", "Password", "java.lang.String", password),
                        insertBeforeNode);
            }

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in jms-ds.xml.
     *
     * @param discoveryMulticastAddress discovery multicast address
     * @param discoveryMulticastPort multicast port
     * @param ha if ha
     * @param reconnectAttempts
     */
    @Override
    public void setPooledConnectionFactoryToDiscovery(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha,
            int reconnectAttempts, String connectorClassName) {

        String configurationFile = getJmsDsConfigurationFile();

        // CHANGE THIS:
        // <tx-connection-factory>
        // <jndi-name>JmsXA</jndi-name>
        // <xa-transaction/>
        // <rar-name>jms-ra.rar</rar-name>
        // <connection-definition>org.hornetq.ra.HornetQRAConnectionFactory</connection-definition>
        // <config-property name="SessionDefaultType" type="java.lang.String">javax.jms.Topic</config-property>
        // <config-property name="JmsProviderAdapterJNDI" type="java.lang.String">java:/DefaultJMSProvider</config-property>
        // <max-pool-size>20</max-pool-size>
        // <security-domain-and-application>JmsXARealm</security-domain-and-application>
        // </tx-connection-factory>
        // TO THIS:
        // <tx-connection-factory>
        // <jndi-name>JmsXA</jndi-name>
        // <xa-transaction/>
        // <rar-name>jms-ra.rar</rar-name>
        // <connection-definition>org.hornetq.ra.HornetQRAConnectionFactory</connection-definition>
        // <config-property name="SessionDefaultType" type="java.lang.String">javax.jms.Topic</config-property>
        // <config-property name="JmsProviderAdapterJNDI" type="java.lang.String">java:/DefaultJMSProvider</config-property>
        // <config-property name="ConnectorClassName"
        // type="java.lang.String">org.hornetq.core.remoting.impl.netty.NettyConnectorFactory</config-property>
        // <config-property name="DiscoveryAddress" type="java.lang.String">234.11.13.16</config-property>
        // <config-property name="DiscoveryPort" type="java.lang.Integer">9878</config-property>
        // <config-property name="HA" type="java.lang.Boolean">true</config-property>
        // <config-property name="reconnectAttempts" type="java.lang.Integer">-1</config-property>
        // <max-pool-size>20</max-pool-size>
        // <security-domain-and-application>JmsXARealm</security-domain-and-application>
        // </tx-connection-factory>
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node rootNode = (Node) xpathInstance.evaluate("//tx-connection-factory", doc, XPathConstants.NODE);

            Node securityDomainAndApplicationAttribute = (Node) xpathInstance.evaluate("//security-domain-and-application",
                    doc, XPathConstants.NODE);

            rootNode.removeChild(securityDomainAndApplicationAttribute);

            Node discoveryAddressConfigProperty = createConfigPropertyForJmsDs(doc, "DiscoveryAddress", "java.lang.String",
                    discoveryMulticastAddress);
            rootNode.appendChild(discoveryAddressConfigProperty);

            Node discoveryPortConfigProperty = createConfigPropertyForJmsDs(doc, "DiscoveryPort", "java.lang.Integer",
                    String.valueOf(discoveryMulticastPort));
            rootNode.appendChild(discoveryPortConfigProperty);

            Node haConfigProperty = createConfigPropertyForJmsDs(doc, "HA", "java.lang.Boolean", String.valueOf(ha));
            rootNode.appendChild(haConfigProperty);

            Node reconnectAttemptsConfigProperty = createConfigPropertyForJmsDs(doc, "reconnectAttempts", "java.lang.Integer",
                    String.valueOf(reconnectAttempts));
            rootNode.appendChild(reconnectAttemptsConfigProperty);

            Node connectorClassNameConfigProperty = createConfigPropertyForJmsDs(doc, "ConnectorClassName",
                    "java.lang.Integer", connectorClassName);
            rootNode.appendChild(connectorClassNameConfigProperty);

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in jms-ds.xml.
     *
     * @param connectorClassName like
     * org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
     * @param ha if ha
     * @param reconnectAttempts reconnect attempts in case of jms server failure
     * @param hostname host of hq server
     * @param port port of hq server
     */
    @Override
    public void setPooledConnectionFactoryWithStaticConnectors(String hostname, int port, boolean ha, int reconnectAttempts,
            String connectorClassName) {

        String configurationFile = getJmsDsConfigurationFile();

        // CHANGE THIS:
        // <tx-connection-factory>
        // <jndi-name>JmsXA</jndi-name>
        // <xa-transaction/>
        // <rar-name>jms-ra.rar</rar-name>
        // <connection-definition>org.hornetq.ra.HornetQRAConnectionFactory</connection-definition>
        // <config-property name="SessionDefaultType" type="java.lang.String">javax.jms.Topic</config-property>
        // <config-property name="JmsProviderAdapterJNDI" type="java.lang.String">java:/DefaultJMSProvider</config-property>
        // <max-pool-size>20</max-pool-size>
        // <security-domain-and-application>JmsXARealm</security-domain-and-application>
        // </tx-connection-factory>
        // TO THIS:
        // <tx-connection-factory>
        // <jndi-name>JmsXA</jndi-name>
        // <xa-transaction/>
        // <rar-name>jms-ra.rar</rar-name>
        // <connection-definition>org.hornetq.ra.HornetQRAConnectionFactory</connection-definition>
        // <config-property name="SessionDefaultType" type="java.lang.String">javax.jms.Topic</config-property>
        // <config-property name="JmsProviderAdapterJNDI" type="java.lang.String">java:/DefaultJMSProvider</config-property>
        // <config-property name="ConnectorClassName"
        // type="java.lang.String">org.hornetq.core.remoting.impl.netty.NettyConnectorFactory</config-property>
        // <config-property name="ConnectionParameters" type="java.lang.String">host=messaging-12;port=5445</config-property>
        // <max-pool-size>20</max-pool-size>
        // <security-domain-and-application>JmsXARealm</security-domain-and-application>
        // </tx-connection-factory>
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node rootNode = (Node) xpathInstance.evaluate("//tx-connection-factory", doc, XPathConstants.NODE);

            Node securityDomainAndApplicationAttribute = (Node) xpathInstance.evaluate("//security-domain-and-application",
                    doc, XPathConstants.NODE);

            rootNode.removeChild(securityDomainAndApplicationAttribute);

            Node haConfigProperty = createConfigPropertyForJmsDs(doc, "HA", "java.lang.Boolean", String.valueOf(ha));
            rootNode.appendChild(haConfigProperty);

            Node reconnectAttemptsConfigProperty = createConfigPropertyForJmsDs(doc, "reconnectAttempts", "java.lang.Integer",
                    String.valueOf(reconnectAttempts));
            rootNode.appendChild(reconnectAttemptsConfigProperty);

            Node connectorClassNameConfigProperty = createConfigPropertyForJmsDs(doc, "ConnectorClassName",
                    "java.lang.Integer", connectorClassName);
            rootNode.appendChild(connectorClassNameConfigProperty);

            String connectionParameters = "host=" + hostname + ";port=" + port;
            Node connectionParametersConfigProperty = createConfigPropertyForJmsDs(doc, "ConnectionParameters",
                    "java.lang.String", connectionParameters);
            rootNode.appendChild(connectionParametersConfigProperty);

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void setPooledConnectionFactoryToDiscovery(String pooledConnectionFactoryName, String discoveryGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setJndiNameForPooledConnectionFactory(String pooledConnectionFactoryName, String jndiName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void startJMSBridge(String jmsBridgeName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPropertyReplacement(String propertyName, boolean isEnabled) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSubsystem(String subsystemName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSecurityProvider(String providerName, String providerType, Map<String, String> attributes) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public int getNumberOfNodesInCluster() {
        logger.info("This operation is not supported: " + getMethodName());
        return -1;
    }

    private Node createConfigPropertyForJmsDs(Document doc, String nameOfProperty, String typeOfProperty, String value) {

        Element e = doc.createElement("config-property");

        e.setAttribute("name", nameOfProperty);
        e.setAttribute("type", typeOfProperty);
        e.setTextContent(value);

        return e;
    }

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in ra.xml.
     *
     * @param discoveryMulticastAddress discovery multicast address
     * @param discoveryMulticastPort multicast port
     * @param ha if ha
     */
    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha) {
        setRA(discoveryMulticastAddress, discoveryMulticastPort, ha, null, null);
    }

    /**
     * Related only to EAP 5.
     * <p/>
     * Sets basic attributes in ra.xml.
     *
     * @param discoveryMulticastAddress discovery multicast address
     * @param discoveryMulticastPort multicast port
     * @param ha if ha
     */
    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha, String username, String password) {

        String configurationFile = getRAConfigurationFile();

        // REMOTE THIS
        // <config-property>
        // <description>
        // The transport type. Multiple connectors can be configured by using a comma separated list,
        // i.e.
        // org.hornetq.core.remoting.impl.invm.InVMConnectorFactory,org.hornetq.core.remoting.impl.invm.InVMConnectorFactory.
        // </description>
        // <config-property-name>ConnectorClassName</config-property-name>
        // <config-property-type>java.lang.String</config-property-type>
        // <config-property-value>org.hornetq.core.remoting.impl.invm.InVMConnectorFactory</config-property-value>
        // </config-property>
        // <config-property>
        // <description>The transport configuration. These values must be in the form of key=val;key=val;,
        // if multiple connectors are used then each set must be separated by a comma i.e.
        // host=host1;port=5445,host=host2;port=5446.
        // Each set of params maps to the connector classname specified.
        // </description>
        // <config-property-name>ConnectionParameters</config-property-name>
        // <config-property-type>java.lang.String</config-property-type>
        // <config-property-value>server-id=0</config-property-value>
        // </config-property>
        // ADD THIS
        // <config-property>
        // <description>The discovery group address</description>
        // <config-property-name>DiscoveryAddress</config-property-name>
        // <config-property-type>java.lang.String</config-property-type>
        // <config-property-value></config-property-value>
        // </config-property>
        // <config-property>
        // <description>The discovery group port</description>
        // <config-property-name>DiscoveryPort</config-property-name>
        // <config-property-type>java.lang.Integer</config-property-type>
        // <config-property-value></config-property-value>
        // </config-property>
        try {

            Document doc = XMLManipulation.getDOMModel(configurationFile);

            // child[contains(string(),'Likes')]
            XMLManipulation.removeNode("//config-property/*[contains(string(),'ConnectorClassName')]/..", doc);
            XMLManipulation.removeNode("//config-property/*[contains(string(),'ConnectionParameters')]/..", doc);
            XMLManipulation.removeNode("//config-property/*[contains(string(),'HA')]/..", doc);

            XPath xpathInstance = XPathFactory.newInstance().newXPath();
            Node rootNode = (Node) xpathInstance.evaluate("//resourceadapter", doc, XPathConstants.NODE);
            Node insertBeforeNode = (Node) xpathInstance.evaluate("//outbound-resourceadapter", doc, XPathConstants.NODE);

            rootNode.insertBefore(
                    createConfigProperty(doc, "The discovery group address", "DiscoveryAddress", "java.lang.String",
                            discoveryMulticastAddress), insertBeforeNode);

            rootNode.insertBefore(
                    createConfigProperty(doc, "The discovery group port", "DiscoveryPort", "java.lang.Integer",
                            String.valueOf(discoveryMulticastPort)), insertBeforeNode);
            rootNode.insertBefore(createConfigProperty(doc, "desc", "HA", "java.lang.Boolean", String.valueOf(ha)),
                    insertBeforeNode);

            if (username != null && password != null) {
                rootNode.insertBefore(createConfigProperty(doc, "desc", "UserName", "java.lang.String", username),
                        insertBeforeNode);
                rootNode.insertBefore(createConfigProperty(doc, "desc", "Password", "java.lang.String", password),
                        insertBeforeNode);
            }

            XMLManipulation.saveDOMModel(doc, configurationFile);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Set multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param port port of the socket binding
     */
    @Override
    public void setMulticastPortOnSocketBinding(String socketBindingName, int port) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Set compression.
     *
     * @param connectionFactoryName name of the connection factory
     * @param value true to enable large message compression
     */
    @Override
    public void setCompressionOnConnectionFactory(String connectionFactoryName, boolean value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public boolean isActive(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
        return false;
    }

    /**
     * Set old(true) or new failover model(false)
     *
     * @param keepOldFailover false to activate it
     * @param nodeStateRefreshInterval after which time will be node's timestamp
     * updated in database
     */
    @Override
    public void setKeepOldFailoverModel(boolean keepOldFailover, long nodeStateRefreshInterval) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Whether to retyr connection to database
     *
     * @param retryOnConnectionFailure true for retry
     * @param retryInterval interval in miliseconds
     * @param maxRetry how many times to retry before giving up
     */
    @Override
    public void setRetryForDb(boolean retryOnConnectionFailure, long retryInterval, int maxRetry) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Sets TUNNEL protocol for jgroups
     *
     * @param gossipRouterHostname ip address of gosship router
     * @param gossipRouterPort port of gosship router
     */
    @Override
    public void setTunnelForJGroups(String gossipRouterHostname, int gossipRouterPort) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Set database.
     *
     * @param databaseHostname hostname
     * @param databasePort port
     */
    @Override
    public void setDatabase(String databaseHostname, int databasePort) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeAddressSettings(String serverName, String address) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes,
            int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout,
            long groupTimeout, long reaperPeriod) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addExternalContext(String binding, String className, String module, String bindingType,
            Map<String, String> environmentProperies) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Sets transaction node identifier.
     *
     * @param i node identifier
     */
    @Override
    public void setNodeIdentifier(int i) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setAuthenticationForNullUsers(boolean b) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addDatasourceProperty(String lodhDb, String propertyName, String value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setMaxSavedReplicatedJournals(int numberOfReplicatedJournals) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setMaxSavedReplicatedJournals(String serverName, int numberOfReplicatedJournals) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup, String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setCheckForLiveServer(boolean b) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setCheckForLiveServer(boolean b, String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addRoleToSecuritySettings(String backupServerName, String s, String guest) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addSecuritySetting(String serverName, String s) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeSecuritySettings(String serverName, String addressMask) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setConnectorOnConnectionFactory(String serverName, String nameConnectionFactory, String proxyConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * @param doc document
     * @param description description
     * @param configPropertyName name
     * @param configPropertyType type
     * @param configPropertyValues value
     * @return config property
     */
    private Node createConfigProperty(Document doc, String description, String configPropertyName, String configPropertyType,
            String configPropertyValues) {

        Element e = doc.createElement("config-property");

        Element eDescription = doc.createElement("description");
        eDescription.setTextContent(description);

        Element eConfigProperty = doc.createElement("config-property-name");
        eConfigProperty.setTextContent(configPropertyName);

        Element eConfigPropertyType = doc.createElement("config-property-type");
        eConfigPropertyType.setTextContent(configPropertyType);

        Element eConfigPropertyValue = doc.createElement("config-property-value");
        eConfigPropertyValue.setTextContent(configPropertyValues);

        e.appendChild(eDescription);
        e.appendChild(eConfigProperty);
        e.appendChild(eConfigPropertyType);
        e.appendChild(eConfigPropertyValue);

        return e;
    }

    @Override
    public void setConnectorOnConnectionFactory(String nameConnectionFactory, String proxyConnectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setMinPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setMaxPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha,
            String discoveryGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress,
            int reconnectAttempts, boolean ha, String discoveryGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress,
            int reconnectAttempts, String... staticConnector) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceQueue,
            Map<String, String> sourceContext, String targetConnectionFactory, String targetDestination,
            Map<String, String> targetContext, String qualityOfService, long failureRetryInterval, int maxRetries,
            long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setFactoryType(String serverName, String connectionFactoryName, String factoryType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setFactoryType(String connectionFactoryName, String factoryType) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addTransportToJGroupsStack(String stackName, String transport, String gosshipRouterAddress,
            int gosshipRouterPort, boolean enableBundling) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());

    }

    @Override
    public void createConnectionFactory(String serverName, String connectionFactoryName, String jndiName, String connectorName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeConnectionFactory(String connectionFactoryName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes,
            int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes,
            int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue,
            int maxDeliveryAttempts) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addMessageGrouping(String name, String type, String address, long timeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setXADatasourceAtribute(String poolName, String attributeName, String value) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addExtension(String extensionName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public int getNumberOfDurableSubscriptionsOnTopic(String clientId) {
        logger.info("This operation is not supported: " + getMethodName());
        return -1;
    }

    @Override
    public int getNumberOfTempQueues() {
        logger.info("This operation is not supported: " + getMethodName());
        return -1;
    }

    public String getPagingDirectoryPath() {
        logger.info("This operation is not supported: " + getMethodName());
        return null;
    }

    @Override
    public boolean areThereUnfinishedArjunaTransactions() {
        logger.info("This operation is not supported: " + getMethodName());
        return false;
    }

    @Override
    public boolean closeClientsByDestinationAddress(String address) {
        logger.info("This operation is not supported: " + getMethodName());
        return false;
    }

    @Override
    public boolean closeClientsByUserName(String username) {
        logger.info("This operation is not supported: " + getMethodName());
        return false;
    }

    @Override
    public List<String> getJNDIEntriesForQueue(String destinationCoreName) {
        logger.info("This operation is not supported: " + getMethodName());
        return null;
    }

    @Override
    public List<String> getJNDIEntriesForTopic(String destinationCoreName) {
        logger.info("This operation is not supported: " + getMethodName());
        return null;
    }

    @Override
    public void setDiscoveryGroupOnConnectionFactory(String connectionFactoryName, String discoveryGroupName) {
        logger.info("This operation is not supported: " + getMethodName());

    }

    @Override
    public int getNumberOfActiveClientConnections() {

        logger.info("This operation is not supported: " + getMethodName());

        return 0;
    }

    @Override
    public void removeMessageFromQueue(String queueName, String jmsMessageID) {
        logger.info("This operation is not supported: " + getMethodName());

    }

    @Override
    public void forceFailover() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setTransactionTimeout(long hornetqTransactionTimeout) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public String getSocketBindingAtributes(String socketBindingName) {
        logger.info("This operation is not supported: " + getMethodName());
        return null;
    }

    @Override
    public void rewriteLoginModule(String securityDomain, String authentication, String loginModule,
            HashMap<String, String> moduleOptions) {
        logger.info("This operation is not supported: " + getMethodName());

    }

    @Override
    public int countConnections() {
        logger.info("This operation is not supported: " + getMethodName());
        throw new RuntimeException("This operation is not supported: " + getMethodName());
    }

    @Override
    public void rewriteLoginModule(String loginModule, HashMap<String, String> moduleOptions) {
        logger.info("This operation is not supported: " + getMethodName());

    }

    @Override
    public void overrideInVMSecurity(boolean b) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removePooledConnectionFactory(String pooledConnectionFactoryName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public int getNumberOfConsumersOnQueue(String queue) {
        logger.info("This operation is not supported: " + getMethodName());
        return 0;
    }

    @Override
    public void reload(boolean isAdminOnlyMode) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void removeHAPolicy(String serverName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicySharedStoreMaster(long failbackDelay, boolean failoverOnServerShutdown) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicySharedStoreMaster(String serverName, long failbackDelay, boolean failoverOnServerShutdown) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicySharedStoreSlave(boolean allowFailback, long failbackDelay, boolean failoverOnServerShutdown,
            boolean restartBackup, boolean scaleDown, String scaleDownClusterName, List<String> scaleDownConnectors,
            String scaleDownDiscoveryGroup, String scaleDownGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicySharedStoreSlave(String serverName, boolean allowFailback, long failbackDelay,
            boolean failoverOnServerShutdown, boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
            List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyReplicationMaster(boolean checkForLiveServer, String clusterName, String groupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyReplicationMaster(String serverName, boolean checkForLiveServer, String clusterName, String groupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyReplicationSlave(boolean allowFailback, String clusterName, long failbackDelay, String groupName,
            int maxSavedReplicatedJournalSize, boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
            List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyReplicationSlave(String serverName, boolean allowFailback, String clusterName, long failbackDelay,
            String groupName, int maxSavedReplicatedJournalSize, boolean restartBackup, boolean scaleDown,
            String scaleDownClusterName, List<String> scaleDownConnectors, String scaleDownDiscoveryGroup,
            String scaleDownGroupName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyColocatedSharedStore() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyColocatedSharedStore(String serverName, int backupPortOffest, int backupRequestRetries, int backupRequestRetryInterval, int maxBackups, boolean requestBackup, boolean failoverOnServerShutdown) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyCollocatedReplicated() {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void addHAPolicyCollocatedReplicated(String serverName, int backupPortOffest, int backupRequestRetries, int backupRequestRetryInterval, int maxBackups, boolean requestBackup, String... excludedConnectors) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void deploy(Archive archive) throws Exception {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void stopDeliveryToMdb(String deploymentName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void startDeliveryToMdb(String deploymentName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void stopJMSBridge(String jmsBridgeName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createNewResourceAdapter(String name, String cfName, String user, String password, List<String> destinationNames, String hostUrl) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    public static void main(String[] args) {

        HornetQAdminOperationsEAP5 eap5AdmOps = new HornetQAdminOperationsEAP5();
        eap5AdmOps.setHostname("127.0.0.1");
        eap5AdmOps.setProfile("all");
        eap5AdmOps.setRmiPort(1099);
        eap5AdmOps.setJbossHome("/home/mnovak/tmp/JBPAPP-10876/jboss-eap-5.2/jboss-as");

        eap5AdmOps.removeRemoteAcceptor("netty");
        eap5AdmOps.removeRemoteConnector("netty");

        Map<String, String> connectionParameters = new HashMap<String, String>();
        connectionParameters.put("localhost", "5445");
        // eap5AdmOps.setRA("org.hornetq.core.remoting.impl.netty.NettyConnectorFactory", connectionParameters, false);
        eap5AdmOps.setPooledConnectionFactoryWithStaticConnectors("localhost", 5445, false, -1,
                "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory");

        // String pathToCertificates = "/home/mnovak/tmp/tools_for_patches/tmp";
        //
        // Map<String, String> props = new HashMap<String, String>();
        // props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        // props.put(TransportConstants.HOST_PROP_NAME, "127.0.0.1");
        // props.put(TransportConstants.PORT_PROP_NAME, "5446");
        //
        // props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME,
        // pathToCertificates.concat(File.separator).concat("server.truststore"));
        // props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "123456");
        // props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME,
        // pathToCertificates.concat(File.separator).concat("server.keystore"));
        // props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "123456");
        // props.put("need-client-auth", "true");
        // eap5AdmOps.createAcceptor("netty2", props);
        //
        //
        // Map<String, String> props2 = new HashMap<String, String>();
        // props2.put(TransportConstants.HOST_PROP_NAME, "127.0.0.1");
        // props2.put(TransportConstants.PORT_PROP_NAME, "5446");
        // props2.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        // props2.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME,
        // pathToCertificates.concat(File.separator).concat("client.truststore"));
        // props2.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "123456");
        // props2.put(TransportConstants.KEYSTORE_PATH_PROP_NAME,
        // pathToCertificates.concat(File.separator).concat("client.keystore"));
        // props2.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "123456");
        //
        // eap5AdmOps.createConnector("netty2", props2);
        eap5AdmOps.close();
    }
}
