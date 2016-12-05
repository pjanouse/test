// TODO - change prefix names of operations - use just set/add/get/is(for boolean)/remove, remove "create" prefix
package org.jboss.qa.hornetq.tools;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.json.JSONArray;
import org.kohsuke.MetaInfServices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jboss.as.controller.client.helpers.ClientConstants.*;

/**
 * Basic administration operations for JMS subsystem
 * <p>
 *
 * @author jpai
 * @author mnovak@redhat.com
 * @author pslavice@redhat.com
 */
@MetaInfServices
public final class ActiveMQAdminOperationsEAP7 implements JMSOperations {

    private static String NAME_OF_MESSAGING_SUBSYSTEM = "messaging-activemq";
    private static String NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER = "server"; // it's "server" in "server=default"
    private static String NAME_OF_MESSAGING_DEFAULT_SERVER = "default";
    private static String NAME_OF_UNDERTOW_SUBSYSTEM = "undertow";
    private static String NAME_OF_UNDERTOW_DEFAULT_SERVER = "default-server";
    private static String NAME_OF_MODCLUSTER_SUBSYSTEM = "modcluster";
    private static String NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER = "server";
    private static String NAME_OF_JGROUPS_SUBSYSTEM = "jgroups";


    // Logger
    private static final Logger logger = Logger.getLogger(ActiveMQAdminOperationsEAP7.class);

    private static final AtomicInteger executorCount = new AtomicInteger();

    // Definition for the queue
    private static final String DESTINATION_TYPE_QUEUE = "jms-queue";
    // Definition for the topics
    private static final String DESTINATION_TYPE_TOPIC = "jms-topic";
    // Instance of Model controller client
    private ModelControllerClient modelControllerClient;

    private final List<PrefixEntry> prefix = new ArrayList<PrefixEntry>();

    private String hostname;

    private int port;

    // set timeout for creating connection
    private int timeout = 30000;

    /**
     * Default constructor
     */
    public ActiveMQAdminOperationsEAP7() {

    }

    /**
     * Constructor
     */
    public void connect() {
        try {

            // final ThreadGroup group = new ThreadGroup("management-client-thread");
            // final ThreadFactory threadFactory = new JBossThreadFactory(group, Boolean.FALSE, null, "%G " +
            // executorCount.incrementAndGet() + "-%t", null, null, AccessController.getContext());
            // ExecutorService executorService = new ThreadPoolExecutor(2, 6, 60, TimeUnit.SECONDS, new
            // LinkedBlockingQueue<Runnable>(), threadFactory);
            //
            // ClientConfigurationImpl clientConfiguration = new ClientConfigurationImpl();
            // this.modelControllerClient = ModelControllerClient.Factory.create(ClientConfigurationImpl.create(hostname, port,
            // null, null, timeout));

            InetAddress inetAddress = InetAddress.getByName(hostname);
            this.modelControllerClient = ModelControllerClient.Factory.create(inetAddress, port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot create model controller client for host: " + hostname + " and port " + port, e);
        }
    }

    /**
     * Closes connection
     */
    @Override
    public void close() {
        try {
            modelControllerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAddressPrefix(String key, String value) {
        // note that the key can be empty if there's no parameter
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Prefix key cannot be empty");
        }

        prefix.add(new PrefixEntry(key, value));
    }

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName  JNDI queue name
     */
    @Override
    public void createQueue(String queueName, String jndiName) {
        createQueue(queueName, jndiName, true);
    }

    /**
     * Creates queue
     *
     * @param queueName queue name
     * @param jndiName  JNDI queue name
     * @param durable   is queue durable
     */
    @Override
    public void createQueue(String queueName, String jndiName, boolean durable) {
        createQueue(NAME_OF_MESSAGING_DEFAULT_SERVER, queueName, jndiName, durable);
    }

    /**
     * Creates queue
     *
     * @param serverName name of the hornetq server
     * @param queueName  queue name
     * @param jndiName   JNDI queue name
     * @param durable    is queue durable
     */
    @Override
    public void createQueue(String serverName, String queueName, String jndiName, boolean durable) {
        createJmsDestination(serverName, DESTINATION_TYPE_QUEUE, queueName, jndiName, durable);
    }

    /**
     * Creates topic
     *
     * @param topicName queue name
     * @param jndiName  JNDI queue name
     */
    @Override
    public void createTopic(String topicName, String jndiName) {
        createTopic(NAME_OF_MESSAGING_DEFAULT_SERVER, topicName, jndiName);
    }

    /**
     * Creates topic
     *
     * @param serverName
     * @param topicName  queue name
     * @param jndiName   JNDI queue name
     */
    @Override
    public void createTopic(String serverName, String topicName, String jndiName) {
        createJmsDestination(serverName, DESTINATION_TYPE_TOPIC, topicName, jndiName, true);
    }

    /**
     * Removes queue
     *
     * @param queueName queue name
     */
    @Override
    public void removeQueue(String queueName) {
        removeJmsDestination(DESTINATION_TYPE_QUEUE, queueName);
    }

    /**
     * Removes topic
     *
     * @param topicName queue name
     */
    @Override
    public void removeTopic(String topicName) {
        removeJmsDestination(DESTINATION_TYPE_TOPIC, topicName);
    }

    /**
     * Adds JNDI name for queue
     *
     * @param queueName queue name
     * @param jndiName  new JNDI name for the queue
     */
    @Override
    public void addQueueJNDIName(String queueName, String jndiName) {
        addDestinationJNDIName(DESTINATION_TYPE_QUEUE, queueName, jndiName);
    }

    /**
     * Adds JNDI name for queue
     *
     * @param queueName queue name
     * @param jndiName  new JNDI name for the queue
     */
    @Override
    public void addTopicJNDIName(String queueName, String jndiName) {
        addDestinationJNDIName(DESTINATION_TYPE_TOPIC, queueName, jndiName);
    }

    /**
     * Adds JNDI name for queue
     *
     * @param queueName queue name
     * @param jndiName  new JNDI name for the queue
     */
    @Override
    public void removeQueueJNDIName(String queueName, String jndiName) {
        removeDestinationJNDIName(DESTINATION_TYPE_QUEUE, queueName, jndiName);
    }

    /**
     * Adds JNDI name for queue
     *
     * @param topicName topic name
     * @param jndiName  new JNDI name for the queue
     */
    @Override
    public void removeTpicJNDIName(String topicName, String jndiName) {
        removeDestinationJNDIName(DESTINATION_TYPE_TOPIC, topicName, jndiName);
    }

    /**
     * Cleanups queue
     *
     * @param queueName queue name
     */
    @Override
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
    @Override
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
    @Override
    public long getCountOfMessagesOnQueue(String queueName) {
        final ModelNode countMessages = createModelNode();
        countMessages.get(ClientConstants.OP).set("count-messages");
        countMessages.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        countMessages.get(ClientConstants.OP_ADDR)
                .add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
    @Override
    public long removeMessagesFromQueue(String queueName) {
        final ModelNode removeMessagesFromQueue = createModelNode();
        removeMessagesFromQueue.get(ClientConstants.OP).set("remove-messages");
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
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
    @Override
    public void setClusterUserPassword(String password) {
        setClusterUserPassword(NAME_OF_MESSAGING_DEFAULT_SERVER, password);
    }

    /**
     * Sets password for cluster user.
     *
     * @param password   password
     * @param serverName name of the hornetq server
     */
    @Override
    public void setClusterUserPassword(String serverName, String password) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("cluster-password");
        model.get("value").set(password);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            // throw new RuntimeException(e);
            logger.error("Set cluster password can't be set. Remove this log when it's fixed.", e);
        }
    }

    /**
     * Disables security on HornetQ
     */
    @Override
    public void disableSecurity() {
        disableSecurity(NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    /**
     * Disables security on HornetQ
     *
     * @param serverName
     */
    @Override
    public void disableSecurity(String serverName) {
        final ModelNode disableSecurity = createModelNode();
        disableSecurity.get(ClientConstants.OP).set("write-attribute");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        disableSecurity.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        disableSecurity.get("name").set("security-enabled");
        disableSecurity.get("value").set(Boolean.FALSE);
        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setSecurityEnabled(String serverName, boolean value) {
        final ModelNode disableSecurity = createModelNode();
        disableSecurity.get(ClientConstants.OP).set("write-attribute");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        disableSecurity.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        disableSecurity.get("name").set("security-enabled");
        disableSecurity.get("value").set(value);
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
    @Override
    public void setSecurityEnabled(boolean value) {
        setSecurityEnabled(NAME_OF_MESSAGING_DEFAULT_SERVER, value);
    }

    /**
     * Adds security attribute on HornetQ
     *
     * @param value set to false to disable security for hornetq
     */
    @Override
    public void addSecurityEnabled(boolean value) {
        addSecurityEnabled(NAME_OF_MESSAGING_DEFAULT_SERVER, value);
    }

    /**
     * Adds security attribute on HornetQ
     *
     * @param serverName set name of the hornetq server <<<<<<< HEAD
     * @param value      set to false to disable security for hornetq =======
     */
    @Override
    public void addSecurityEnabled(String serverName, boolean value) {
        final ModelNode disableSecurity = createModelNode();
        disableSecurity.get(ClientConstants.OP).set("add");
        disableSecurity.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        disableSecurity.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        disableSecurity.get("security-enabled").set(value);

        try {
            this.applyUpdate(disableSecurity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void overrideInVMSecurity(boolean b) {
        final ModelNode modelNode = createModelNode();
        modelNode.get(ClientConstants.OP).set("write-attribute");
        modelNode.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        modelNode.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        modelNode.get("name").set("override-in-vm-security");
        modelNode.get("value").set(b);
        try {
            this.applyUpdate(modelNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removePooledConnectionFactory(String pooledConnectionFactoryName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void rewriteLoginModule(String loginModule, HashMap<String, String> moduleOptions) {
        rewriteLoginModule("other", "classic", loginModule, moduleOptions);
    }

    public void rewriteLoginModule(String securityDomain, String authentication, String loginModule,
                                   HashMap<String, String> moduleOptions) {
        final ModelNode loginModuleRemove = createModelNode();
        loginModuleRemove.get(ClientConstants.OP).set("write-attribute");
        loginModuleRemove.get(ClientConstants.OP_ADDR).add("subsystem", "security");
        loginModuleRemove.get(ClientConstants.OP_ADDR).add("security-domain", securityDomain);
        loginModuleRemove.get(ClientConstants.OP_ADDR).add("authentication", authentication);
        loginModuleRemove.get(ClientConstants.OP_ADDR).add("login-module", loginModule);
        loginModuleRemove.get("name").set("module-options");
        Iterator i = moduleOptions.entrySet().iterator();
        ArrayList<ModelNode> list = new ArrayList<ModelNode>();
        for (Map.Entry<String, String> entry : moduleOptions.entrySet()) {
            list.add(new ModelNode().setExpression(entry.getKey(), entry.getValue()));
        }
        // loginModuleRemove.get("value").set(new ModelNode().setExpression("password-stacking","useFirstPass"));
        loginModuleRemove.get("value").set(list);
        try {
            this.applyUpdate(loginModuleRemove);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds extension.
     *
     * @param extensionName name of extesnion
     */
    @Override
    public void addExtension(String extensionName) {
        final ModelNode addExtension = createModelNode();
        addExtension.get(ClientConstants.OP).set("add");
        addExtension.get(ClientConstants.OP_ADDR).add("extension", extensionName);

        try {
            this.applyUpdate(addExtension);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha, String username,
                      String password) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPooledConnectionFactoryToDiscovery(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha,
                                                      int reconnectAttempts, String connectorClassName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setPooledConnectionFactoryToDiscovery(String pooledConnectionFactoryName, String discoveryGroupName) {

        ModelNode composite = createModelNode();
        composite.get(ClientConstants.OP).set("composite");
        composite.get(ClientConstants.OP_ADDR).setEmptyList();
        composite.get(ClientConstants.OPERATION_HEADERS, ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode undefineConnector = createModelNode();
        undefineConnector.get(ClientConstants.OP).set(ClientConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        undefineConnector.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        undefineConnector.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        undefineConnector.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        undefineConnector.get("name").set("connectors");

        ModelNode setDiscoveryGroup = createModelNode();
        setDiscoveryGroup.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        setDiscoveryGroup.get("name").set("discovery-group");
        setDiscoveryGroup.get("value").set(discoveryGroupName);
        composite.get(ClientConstants.STEPS).add(undefineConnector);
        composite.get(ClientConstants.STEPS).add(setDiscoveryGroup);

        try {
            this.applyUpdate(composite);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJndiNameForPooledConnectionFactory(String pooledConnectionFactoryName, String jndiName) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        model.get("entries").clear();
        model.get("name").set("entries");
        model.get("value").add(jndiName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalMinCompactFiles(int i) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get("name").set("journal-compact-min-files");
        model.get("value").set(i);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopJMSBridge(String jmsBridgeName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("stop");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("jms-bridge", jmsBridgeName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setClusterConnectionCallTimeout(String clusterGroupName, long callTimout) {
        setClusterConnectionCallTimeout(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroupName, callTimout);
    }

    @Override
    public void setClusterConnectionCallTimeout(String serverName, String clusterGroupName, long callTimout) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);
        model.get("name").set("call-timeout");
        model.get("value").set(callTimout);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setClusterConnectionCheckPeriod(String serverName, String clusterGroupName, long checkPeriod) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);
        model.get("name").set("check-period");
        model.get("value").set(checkPeriod);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setClusterConnectionCheckPeriod(String clusterGroupName, long checkPeriod) {
        setClusterConnectionCheckPeriod(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroupName, checkPeriod);
    }

    @Override
    public void setClusterConnectionTTL(String serverName, String clusterGroupName, long ttl) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);
        model.get("name").set("connection-ttl");
        model.get("value").set(ttl);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setClusterConnectionTTL(String clusterGroupName, long ttl) {
        setClusterConnectionTTL(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroupName, ttl);
    }

    @Override
    public void setJournalMinFiles(String serverName, int i) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-min-files");
        model.get("value").set(i);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalMinFiles(int i) {
        setJournalMinFiles(NAME_OF_MESSAGING_DEFAULT_SERVER, i);
    }

    @Override
    public void setConfirmationWindowsSizeOnClusterConnection(String clusterGroupName, int confirmationWindowsSizeInBytes) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);
        model.get("name").set("confirmation-window-size");
        model.get("value").set(confirmationWindowsSizeInBytes);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addIncomingInterceptor(String serverName, String moduleName, String className) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        ModelNode interceptor = new ModelNode();
        interceptor.get("module").set(moduleName);
        interceptor.get("name").set(className);
        model.get("name").set("incoming-interceptors");
        model.get("value").add(interceptor);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addIncomingInterceptor(String moduleName, String className) {
        addIncomingInterceptor("default", moduleName, className);
    }

    @Override
    public void addOutgoingInterceptor(String serverName, String moduleName, String className) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        ModelNode interceptor = new ModelNode();
        interceptor.get("module").set(moduleName);
        interceptor.get("name").set(className);
        model.get("name").set("outgoing-interceptors");
        model.get("value").add(interceptor);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addOutgoingInterceptor(String moduleName, String className) {
        addOutgoingInterceptor("default", moduleName, className);
    }

    @Override
    public void setAutoCreateJMSQueue(boolean autoCreateJmsQueue) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, "default");
        model.get(ClientConstants.OP_ADDR).add("address-setting", "#");
        model.get("name").set("auto-create-jms-queues");
        model.get("value").set(autoCreateJmsQueue);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAutoDeleteJMSQueue(boolean autoDeleteJmsQueue) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, "default");
        model.get(ClientConstants.OP_ADDR).add("address-setting", "#");
        model.get("name").set("auto-delete-jms-queues");
        model.get("value").set(autoDeleteJmsQueue);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getCountOfMessagesOnRuntimeQueue(String coreQueueName) {
        final ModelNode countMessages = createModelNode();
        countMessages.get(ClientConstants.OP).set("count-messages");
        countMessages.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        countMessages.get(ClientConstants.OP_ADDR)
                .add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        countMessages.get(ClientConstants.OP_ADDR).add("runtime-queue", coreQueueName);
        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(countMessages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return (modelNode != null) ? modelNode.get(ClientConstants.RESULT).asLong(0) : 0;
    }

    public Set<String> getRuntimeSFClusterQueueNames() {
        final ModelNode queueNames = createModelNode();
        queueNames.get(ClientConstants.OP).set("read-resource");
        queueNames.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        queueNames.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);

        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(queueNames);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String resource = modelNode.get(ClientConstants.RESULT).asString();
        Pattern r = Pattern.compile("\"(sf\\.\\S*)\"");
        Matcher m = r.matcher(resource);
        Set<String> names = new HashSet<String>();
        while(m.find()){
            names.add(m.group());
            System.out.println(m.group());
        }
        return names;
    }

    @Override
    public long getMessagesAdded(String coreQueueName){
        return getMessagesAdded(coreQueueName,false);
    }

    @Override
    public long getMessagesAdded(String destName, boolean isTopic) {
        // /subsystem=messaging/server=default/jms-queue=InQueue:read-attribute(name=messages-added)
        final ModelNode messagesAdded = createModelNode();
        messagesAdded.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        messagesAdded.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        messagesAdded.get(ClientConstants.OP_ADDR)
                .add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        if(isTopic){
            messagesAdded.get(ClientConstants.OP_ADDR).add("jms-topic", destName);
        }else{
            messagesAdded.get(ClientConstants.OP_ADDR).add("jms-queue", destName);
        }
        messagesAdded.get("name").set("messages-added");
        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(messagesAdded);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return (modelNode != null) ? modelNode.get(ClientConstants.RESULT).asLong(0) : 0;
    }

    @Override
    public List<Map<String, String>> listMessages(String coreQueueName) {
        // /subsystem=messaging/server=default/jms-queue=InQueue:list-messages
        final ModelNode messagesAdded = createModelNode();
        messagesAdded.get(ClientConstants.OP).set("list-messages");
        messagesAdded.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        messagesAdded.get(ClientConstants.OP_ADDR)
                .add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        messagesAdded.get(ClientConstants.OP_ADDR).add("jms-queue", coreQueueName);
        ModelNode modelNode;
        try {
            modelNode = this.applyUpdate(messagesAdded);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        if (modelNode != null) {
            ModelNode res = modelNode.get("result");
            for (ModelNode msg : res.asList()) {
                Map<String, String> message = new HashMap<String, String>();
                for (Property property : msg.asPropertyList()) {
                    message.put(property.getName(), property.getValue().toString());
                }
                result.add(message);
            }
        }

        return result;
    }

    @Override
    public boolean isDeliveryActive(Archive mdb, String mdbName) {
        // /deployment=mdb-1.0-SNAPSHOT.jar/subsystem=ejb3/message-driven-bean=LocalResendingMdbFromQueueToQueue:read-attribute(name=delivery-active)
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("deployment", mdb.getName());
        model.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        model.get(ClientConstants.OP_ADDR).add("message-driven-bean", mdbName);
        model.get("name").set("delivery-active");
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Boolean.valueOf(result.get("result").asString());
    }

    @Override
    public void addDeliveryGroup(String deliveryGroup, boolean isDeliveryGroupActive) {
        // /subsystem=ejb3/mdb-delivery-group=group2:add(active=true)
        ModelNode addDeliveryGroup = createModelNode();
        addDeliveryGroup.get(ClientConstants.OP).set("add");
        addDeliveryGroup.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        addDeliveryGroup.get(ClientConstants.OP_ADDR).add("mdb-delivery-group", deliveryGroup);
        addDeliveryGroup.get("active").set(isDeliveryGroupActive);
        try {
            this.applyUpdate(addDeliveryGroup);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setForwardWhenNoConsumers(String serverName, String clusterGroup, boolean value) {
        ModelNode modelNode = createModelNode();
        modelNode.get(ClientConstants.OP).set("write-attribute");
        modelNode.get(ClientConstants.OP_ADDR).add(SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        modelNode.get(ClientConstants.OP_ADDR).add(SERVER, serverName);
        modelNode.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroup);
        modelNode.get("name").set("message-load-balancing-type");
        if (value) {
            modelNode.get("value").set(Constants.MESSAGE_LOAD_BALANCING_POLICY.STRICT.name());
        } else {
            modelNode.get("value").set(Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND.name());
        }
        try {
            this.applyUpdate(modelNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setForwardWhenNoConsumers(String clusterGroup, boolean value) {
        setForwardWhenNoConsumers(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroup, value);
    }

    @Override
    public void setJournalBindingsTable(String journalBindingsTable) {
        setJournalBindingsTable(NAME_OF_MESSAGING_DEFAULT_SERVER, journalBindingsTable);
    }

    @Override
    public void setJournalBindingsTable(String serverName, String journalBindingsTable) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);

        model.get(ClientConstants.NAME).set("journal-bindings-table");
        model.get(ClientConstants.VALUE).set(journalBindingsTable);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalMessagesTable(String journalMessagesTable) {
        setJournalMessagesTable(NAME_OF_MESSAGING_DEFAULT_SERVER, journalMessagesTable);
    }

    @Override
    public void setJournalMessagesTable(String serverName, String journalMessagesTable) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);

        model.get(ClientConstants.NAME).set("journal-messages-table");
        model.get(ClientConstants.VALUE).set(journalMessagesTable);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalLargeMessagesTable(String journalLargeMessagesTable) {
        setJournalLargeMessagesTable(NAME_OF_MESSAGING_DEFAULT_SERVER, journalLargeMessagesTable);
    }

    @Override
    public void setJournalLargeMessagesTable(String serverName, String journalLargeMessagesTable) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);

        model.get(ClientConstants.NAME).set("journal-large-messages-table");
        model.get(ClientConstants.VALUE).set(journalLargeMessagesTable);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDeliveryGroupActive(String deliveryGroup, boolean isDeliveryGroupActive) {
        // /subsystem=ejb3/mdb-delivery-group=group2:add(active=true)
        ModelNode addDeliveryGroup = createModelNode();
        addDeliveryGroup.get(ClientConstants.OP).set("write-attribute");
        addDeliveryGroup.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        addDeliveryGroup.get(ClientConstants.OP_ADDR).add("mdb-delivery-group", deliveryGroup);
        addDeliveryGroup.get("name").set("active");
        addDeliveryGroup.get("value").set(isDeliveryGroupActive);
        try {
            this.applyUpdate(addDeliveryGroup);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startJMSBridge(String jmsBridgeName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("start");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("jms-bridge", jmsBridgeName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setPooledConnectionFactoryWithStaticConnectors(String hostname, int port, boolean ha, int reconnectAttempts,
                                                               String connectorClassName) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Sets permission privileges to a given role.
     *
     * @param address    address of the queue like '#' (for all queues)
     * @param role       role of the user like 'guest'
     * @param permission possible values
     *                   {consume,create-durable-queue,create-non-durable-queue,delete-durable-queue,,delete-non-durable-queue,manage,send}
     * @param value      true for enable permission
     */
    @Override
    public void setPermissionToRoleToSecuritySettings(String address, String role, String permission, boolean value) {
        setPermissionToRoleToSecuritySettings(NAME_OF_MESSAGING_DEFAULT_SERVER, address, role, permission, value);
    }

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
    @Override
    public void setPermissionToRoleToSecuritySettings(String serverName, String address, String role, String permission,
                                                      boolean value) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     * @param connectorName         name of the connector like "remote-connector"
     */
    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, String connectorName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("connectors");
        ModelNode modelnew = createModelNode();
        modelnew.set(connectorName);
        Collection<ModelNode> connectors = new ArrayList<ModelNode>();
        connectors.add(modelnew);
        model.get("value").set(connectors);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets loadbalancing policy on pooled connection factory
     *
     * @param connectionFactoryName  name of the pooled connection factory like "hornetq-ra"
     * @param loadbalancingClassName fully qualified class name
     */
    @Override
    public void setLoadbalancingPolicyOnPooledConnectionFactory(String connectionFactoryName, String loadbalancingClassName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("connection-load-balancing-policy-class-name");
        model.get("value").set(loadbalancingClassName);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Sets connector on pooled connection factory transaction=xa, entries={{java:jmsXA3}}, connector={["netty"]}, ha=true)
     *
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     * @param connectorName         name of the connector like "remote-connector"
     */
    @Override
    public void createPooledConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("transaction").set("xa");

        model.get("entries").add(jndiName);

        ModelNode modelnew = createModelNode();
        modelnew.set(connectorName);

        Collection<ModelNode> connectors = new ArrayList<ModelNode>();
        connectors.add(modelnew);
        model.get("connectors").set(connectors);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates connection factory.
     *
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     * @param jndiName              jndi name of connection factory
     * @param connectorName         name of the connector like "remote-connector"
     */
    @Override
    public void createConnectionFactory(String connectionFactoryName, String jndiName, String connectorName) {
        createConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, jndiName, connectorName);
    }

    /**
     * Creates connection factory.
     *
     * @param serverName            name of the server
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     * @param jndiName              jndi name of connection factory
     * @param connectorName         name of the connector like "remote-connector"
     */
    @Override
    public void createConnectionFactory(String serverName, String connectionFactoryName, String jndiName, String connectorName) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("entries").add(jndiName);

        // model.get("name").set("connector");
        ModelNode modelnew = createModelNode();
        modelnew.set(connectorName);
        Collection<ModelNode> connectors = new ArrayList<ModelNode>();
        connectors.add(modelnew);
        model.get("connectors").set(connectors);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes connection factory.
     *
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     */
    @Override
    public void removeConnectionFactory(String connectionFactoryName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

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
     * @param connectionFactoryName name of the pooled connection factory like "hornetq-ra"
     * @param connectorNames
     */
    @Override
    public void setConnectorOnPooledConnectionFactory(String connectionFactoryName, List<String> connectorNames) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("connectors");
        List<ModelNode> connectors = new ArrayList<ModelNode>();
        for (String s : connectorNames) {
            ModelNode modelnew = createModelNode();
            modelnew.set(s);
            connectors.add(modelnew);
        }

        model.get("value").set(connectors);

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConnectorOnBroadcastGroup(String serverName, String broadcastGroup, List<String> connectorNames) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", broadcastGroup);

        model.get("name").set("connectors");
        List<ModelNode> connectors = new ArrayList<ModelNode>();
        for (String s : connectorNames) {
            ModelNode modelnew = createModelNode();
            modelnew.set(s);
            connectors.add(modelnew);
        }
        model.get("value").set(connectors);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConnectorOnBroadcastGroup(String broadcastGroup, List<String> connectorNames) {
        setConnectorOnBroadcastGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, broadcastGroup, connectorNames);
    }

    @Override
    public void setConnectorOnClusterGroup(String serverName, String clusterGroup, String connectorName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroup);

        model.get("name").set("connector-name");
        model.get("value").set(connectorName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConnectorOnClusterGroup(String clusterGroup, String connectorName) {
        setConnectorOnClusterGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroup, connectorName);
    }

    @Override
    public void setDiscoveryGroup(String name, String groupAddress, int groupPort, long refreshTimeout) {
        setDiscoveryGroup(name, null, groupAddress, groupPort, refreshTimeout);
    }

    /**
     * Adds role to security settings.
     *
     * @param address address of the queue like '#' (for all queues)
     * @param role    role of the user like 'guest'
     */
    @Override
    public void addRoleToSecuritySettings(String address, String role) {
        addRoleToSecuritySettings(NAME_OF_MESSAGING_DEFAULT_SERVER, address, role);
    }

    public void addRoleToSecuritySettings(String serverName, String address, String role) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("security-setting", address);
        model.get(ClientConstants.OP_ADDR).add("role", role);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addSecuritySetting(String s) {
        addSecuritySetting(NAME_OF_MESSAGING_DEFAULT_SERVER, s);
    }

    @Override
    public void addSecuritySetting(String serverName, String s) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("security-setting", s);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeSecuritySettings(String serverName, String addressMask) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("security-settings", addressMask);

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
     * @param jndiName        JNDI name
     */
    private void addDestinationJNDIName(String destinationType, String destinationName, String jndiName) {
        final ModelNode addJmsJNDIName = createModelNode();
        addJmsJNDIName.get(ClientConstants.OP).set("add-jndi");
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add(destinationType, destinationName);
        addJmsJNDIName.get("jndi-binding").set(jndiName);
        try {
            this.applyUpdate(addJmsJNDIName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add JNDI name
     *
     * @param destinationType type of destination (queue, topic)
     * @param destinationName destination name
     * @param jndiName        JNDI name
     */
    private void removeDestinationJNDIName(String destinationType, String destinationName, String jndiName) {
        final ModelNode addJmsJNDIName = new ModelNode();
        addJmsJNDIName.get(ClientConstants.OP).set("remove-jndi");
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        addJmsJNDIName.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
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
     * @param jndiName        JNDI name for destination
     * @param durable         Is durable destination
     */
    private void createJmsDestination(String serverName, String destinationType, String destinationName, String jndiName,
                                      boolean durable) {

        try {
            removeJmsDestination(destinationType, destinationName);
        } catch (Exception ex) {
            // ignore
        }

        String externalSuffix = (jndiName.startsWith("/")) ? "" : "/";
        ModelNode createJmsQueueOperation = createModelNode();
        createJmsQueueOperation.get(ClientConstants.OP).set(ClientConstants.ADD);
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        createJmsQueueOperation.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove-messages");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add(destinationType, destinationName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final ModelNode removeJmsQueue = createModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("remove");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        removeJmsQueue.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
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
     * @throws IOException                if something goes wrong
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
     * Creates initial model node for the operation.
     * <p>
     * If any prefix was specified with {@link #addAddressPrefix(String, String)}, the initial operation address will be
     * populated with path composed of prefix entries (in the order they were added).
     *
     * @return created model node instance
     */
    private ModelNode createModelNode() {
        ModelNode model = new ModelNode();
        if (!prefix.isEmpty()) {
            for (PrefixEntry p : prefix) {
                if (p.value == null || p.value.isEmpty()) {
                    model.get(ClientConstants.OP_ADDR).add(p.key);
                } else {
                    model.get(ClientConstants.OP_ADDR).add(p.key, p.value);
                }
            }
        }

        return model;
    }

    /**
     * Creates model node which contains list of strings
     *
     * @param list list of strings
     * @return list of model nodes containing string value
     */
    private ModelNode createModelNodeForList(List<String> list) {

        List<ModelNode> connectors = new ArrayList<ModelNode>();
        for (String s : list) {
            ModelNode modelnew = createModelNode();
            modelnew.set(s);
            connectors.add(modelnew);
        }

        return createModelNode().set(connectors);
    }

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param persistenceEnabled - true for persist messages
     */
    @Override
    public void setPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled(NAME_OF_MESSAGING_DEFAULT_SERVER, persistenceEnabled);
    }

    @Override
    public void addDivert(String divertName, String divertAddress, String forwardingAddress, boolean isExclusive,
                          String filter, String routingName, String transformerClassName) {
        addDivert(NAME_OF_MESSAGING_DEFAULT_SERVER, divertName, divertAddress, forwardingAddress, isExclusive, filter,
                routingName, transformerClassName);
    }

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param divertName
     */
    @Override
    public void addDivert(String serverName, String divertName, String divertAddress, String forwardingAddress,
                          boolean isExclusive, String filter, String routingName, String transformerClassName) {
        final ModelNode addDivert = new ModelNode();
        addDivert.get(ClientConstants.OP).set(ClientConstants.ADD);
        addDivert.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        addDivert.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        addDivert.get(ClientConstants.OP_ADDR).add("divert", divertName);
        addDivert.get("divert-address").set(divertAddress);
        addDivert.get("forwarding-address").set(forwardingAddress);
        addDivert.get("exclusive").set(isExclusive);
        if (!isEmpty(filter)) {
            addDivert.get("filter").set(filter);
        }
        if (!isEmpty(routingName)) {
            addDivert.get("routing-name").set(routingName);
        }
        if (!isEmpty(transformerClassName)) {
            addDivert.get("transformer-class-name").set(transformerClassName);
        }

        try {
            this.applyUpdate(addDivert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets persistence-enabled attribute in servers configuration.
     *
     * @param serverName         sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    @Override
    public void setPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        final ModelNode removeJmsQueue = createModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("write-attribute");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        removeJmsQueue.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void setIdCacheSize(long numberOfIds) {
        setIdCacheSize(NAME_OF_MESSAGING_DEFAULT_SERVER, numberOfIds);
    }

    /**
     * Sets id-cache-size attribute in servers configuration.
     *
     * @param serverName  sets name of the hornetq server to be changed
     * @param numberOfIds - number of ids to remember
     */
    @Override
    public void setIdCacheSize(String serverName, long numberOfIds) {
        final ModelNode removeJmsQueue = createModelNode();
        removeJmsQueue.get(ClientConstants.OP).set("write-attribute");
        removeJmsQueue.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        removeJmsQueue.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void addPersistenceEnabled(boolean persistenceEnabled) {
        setPersistenceEnabled(NAME_OF_MESSAGING_DEFAULT_SERVER, persistenceEnabled);
    }

    /**
     * Adds persistence-enabled attribute in servers configuration.
     *
     * @param serverName         sets name of the hornetq server to be changed
     * @param persistenceEnabled - true for persist messages
     */
    @Override
    public void addPersistenceEnabled(String serverName, boolean persistenceEnabled) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("persistence-enabled").set(persistenceEnabled);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets clustered attribute. This is not necessary any more
     *
     * @param clustered set true to allow server to create cluster
     */
    @Override
    public void setClustered(boolean clustered) {
        logger.warn("This operation is not supported for EAP7 container");
    }

    /**
     * Sets clustered attribute. This is not necessary any more
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered  set true to allow server to create cluster
     */
    @Override
    public void setClustered(String serverName, boolean clustered) {
        throw new UnsupportedOperationException("This operation is not supported for EAP7 container");
    }

    /**
     * Adds clustered attribute.
     *
     * @param clustered set true to allow server to create cluster
     */
    @Override
    public void addClustered(boolean clustered) {
        setClustered(NAME_OF_MESSAGING_DEFAULT_SERVER, clustered);
    }

    /**
     * Adds clustered attribute.
     *
     * @param serverName sets name of the hornetq server to be changed
     * @param clustered  set true to allow server to create cluster
     */
    @Override
    public void addClustered(String serverName, boolean clustered) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("clustered").set(clustered);

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
    @Override
    public void setSharedStore(boolean sharedStore) {
        throw new UnsupportedOperationException("This operation is not supported for EAP7 container");
    }

    /**
     * Set this to true if this server shares journal with other server (with live of backup)
     *
     * @param sharedStore share journal
     * @param serverName  hornetq server name
     */
    @Override
    public void setSharedStore(String serverName, boolean sharedStore) {
        throw new UnsupportedOperationException("This operation is not supported for EAP7 container");
    }

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore share journal
     */
    @Override
    public void addSharedStore(boolean sharedStore) {

        throw new UnsupportedOperationException("This operation is not supported for EAP7 container");
    }

    /**
     * Adds attribute for sharing journal.
     *
     * @param sharedStore shared journal
     * @param serverName  hornetq server name
     */
    @Override
    public void addSharedStore(String serverName, boolean sharedStore) {
        throw new UnsupportedOperationException("This operation is not supported for EAP7 container");
    }

    /**
     * Allow jms org.jboss.qa.hornetq.apps.clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     */
    @Override
    public void setAllowFailback(boolean allowFailback) {
        setAllowFailback(NAME_OF_MESSAGING_DEFAULT_SERVER, allowFailback);
    }

    /**
     * Allow jms org.jboss.qa.hornetq.apps.clients to reconnect from backup to live when live comes alive.
     *
     * @param allowFailback
     * @param serverName    name of the hornetq server
     */
    @Override
    public void setAllowFailback(String serverName, boolean allowFailback) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void setJournalType(String journalType) {
        setJournalType(NAME_OF_MESSAGING_DEFAULT_SERVER, journalType);
    }

    /**
     * Can be "NIO" or "AIO"
     *
     * @param serverName  set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    @Override
    public void setJournalType(String serverName, String journalType) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-type");
        model.get("value").set(journalType);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalPoolFiles(String serverName, int numFiles) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-pool-files");
        model.get("value").set(numFiles);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalPoolFiles(int numFiles) {
        setJournalPoolFiles(NAME_OF_MESSAGING_DEFAULT_SERVER, numFiles);
    }

    @Override
    public void setJournalCompactMinFiles(String serverName, int numFiles) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-compact-min-files");
        model.get("value").set(numFiles);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalCompactMinFiles(int numFiles) {
        setJournalCompactMinFiles(NAME_OF_MESSAGING_DEFAULT_SERVER, numFiles);
    }

    @Override
    public void setJournalCompactPercentage(int percentage) {
        setJournalCompactPercentage(NAME_OF_MESSAGING_DEFAULT_SERVER, percentage);
    }

    @Override
    public void setJournalCompactPercentage(String serverName, int percentage) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-compact-percentage");
        model.get("value").set(percentage);
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
    @Override
    public void addJournalType(String journalType) {
        addJournalType(NAME_OF_MESSAGING_DEFAULT_SERVER, journalType);
    }

    /**
     * Adds journal-type attribute.
     *
     * @param serverName  set name of hornetq server
     * @param journalType can be "NIO" or "AIO"
     */
    @Override
    public void addJournalType(String serverName, String journalType) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void setJournalDirectory(String path) {
        setJournalDirectory(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    /**
     * The directory to store the journal files in.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    @Override
    public void setJournalDirectory(String serverName, String path) {

        removePath(serverName, "journal-directory");

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "journal-directory");
        model.get("path").set(path + File.separator + "journal");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalDirectoryPath(String path) {
        setJournalDirectoryPath(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    @Override
    public void setJournalDirectoryPath(String serverName, String path) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "journal-directory");
        model.get("name").set("path");
        model.get("value").set(path);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Export ActiveMQ Artemis journal.
     * <p>
     * server needs to be in admin-only mode
     *
     * @return path to file with exported journal
     */
    @Override
    public String exportJournal() {
        ModelNode exportJournalOp = new ModelNode();
        exportJournalOp.get(OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        exportJournalOp.get(OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        exportJournalOp.get(OP).set("export-journal");
        ModelNode result;
        try {
            result = this.applyUpdate(exportJournalOp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String outcome = result.asString();
        String[] split = outcome.split("\"");
        if (split.length == 9) {
            return split[7];
        }
        return null;
    }

    /**
     * Import ActiveMQ Artemis journal.
     *
     * @param path to exported file.
     */
    @Override
    public void importJournal(String path) {
        ModelNode importJournalOp = new ModelNode();
        importJournalOp.get(OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        importJournalOp.get(OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        importJournalOp.get(OP).set("import-journal");
        importJournalOp.get("file").set(path);
        ModelNode result;
        try {
            result = this.applyUpdate(importJournalOp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int countConnections() {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("list-connection-ids");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);

        System.out.println(model.toString());

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        logger.info("result:" + result);
        return result.get("result").asList().size();
    }

    /**
     * The directory to store paged messages in.
     *
     * @param path set absolute path
     */
    @Override
    public void setPagingDirectory(String path) {
        setPagingDirectory(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    /**
     * The directory to store paged messages in.
     *
     * @param serverName set name of the server
     * @param path       set absolute path
     */
    @Override
    public void setPagingDirectory(String serverName, String path) {

        removePath(serverName, "paging-directory");

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "paging-directory");
        model.get("path").set(path + File.separator + "paging");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setPagingDirectoryPath(String path) {
        setPagingDirectoryPath(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    @Override
    public void setPagingDirectoryPath(String serverName, String path) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "paging-directory");
        model.get("name").set("path");
        model.get("value").set(path);
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
    @Override
    public void setLargeMessagesDirectory(String path) {
        setLargeMessagesDirectory(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    @Override
    public void setLargeMessagesDirectoryPath(String path) {
        setLargeMessagesDirectoryPath(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    @Override
    public void setLargeMessagesDirectoryPath(String serverName, String path) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "large-messages-directory");
        model.get("name").set("path");
        model.get("value").set(path);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The directory in which to store large messages.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    @Override
    public void setLargeMessagesDirectory(String serverName, String path) {

        removePath(serverName, "large-messages-directory");

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "large-messages-directory");
        model.get("path").set(path + File.separator + "largemessages");

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
    @Override
    public void setBindingsDirectory(String path) {
        setBindingsDirectory(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    /**
     * The directory in which to store the persisted bindings.
     *
     * @param serverName set name of hornetq server
     * @param path       set absolute path
     */
    @Override
    public void setBindingsDirectory(String serverName, String path) {

        removePath(serverName, "bindings-directory");

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "bindings-directory");
        model.get("path").set(path + File.separator + "bindings");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setBindingsDirectoryPath(String path) {
        setBindingsDirectoryPath(NAME_OF_MESSAGING_DEFAULT_SERVER, path);
    }

    @Override
    public void setBindingsDirectoryPath(String serverName, String path) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", "bindings-directory");
        model.get("name").set("path");
        model.get("value").set(path);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void removePath(String serverName, String attributeName) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("path", attributeName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Sets default resource adapter in EJB3 subsystem.
     *
     * @param resourceAdapterName name of pooled cf from messaging subsystem
     */
    @Override
    public void setDefaultResourceAdapter(String resourceAdapterName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        model.get("name").set("default-resource-adapter-name");
        model.get("value").set(resourceAdapterName);

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
     * @param useJavaContext
     * @param useCCM
     * @param driverName
     * @param transactionIsolation
     */
    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM,
                                   String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride,
                                   boolean noTxSeparatePool) {
        throw new UnsupportedOperationException(
                "Use method createXADatasource which takes also xaDatasourceProperties as an argument.");
    }

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
    // ModelNode composite = new ModelNode();
    // composite.get(ClientConstants.OP).set("composite");
    // composite.get(ClientConstants.OP_ADDR).setEmptyList();
    // composite.get(ClientConstants.OPERATION_HEADERS, ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(false);
    //
    // ModelNode undefineConnector = new ModelNode();
    // undefineConnector.get(ClientConstants.OP).set(ClientConstants.UNDEFINE_ATTRIBUTE_OPERATION);
    // undefineConnector.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
    // undefineConnector.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
    // NAME_OF_MESSAGING_DEFAULT_SERVER);
    // undefineConnector.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
    // undefineConnector.get("name").set("connectors");
    //
    // ModelNode setDiscoveryGroup = new ModelNode();
    // setDiscoveryGroup.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
    // setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
    // setDiscoveryGroup.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
    // NAME_OF_MESSAGING_DEFAULT_SERVER);
    // setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
    // setDiscoveryGroup.get("name").set("discovery-group");
    // setDiscoveryGroup.get("value").set(discoveryGroupName);
    // composite.get(ClientConstants.STEPS).add(undefineConnector);
    // composite.get(ClientConstants.STEPS).add(setDiscoveryGroup);
    @Override
    public void createXADatasource(String jndi_name, String poolName, boolean useJavaContext, boolean useCCM,
                                   String driverName, String transactionIsolation, String xaDatasourceClass, boolean isSameRmOverride,
                                   boolean noTxSeparatePool, Map<String, String> xaDatasourceProperties) {

        ModelNode composite = new ModelNode();
        composite.get(ClientConstants.OP).set("composite");
        composite.get(ClientConstants.OP_ADDR).setEmptyList();
        composite.get(ClientConstants.OPERATION_HEADERS, ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        final ModelNode modelDatasource = createModelNode();
        modelDatasource.get(ClientConstants.OP).set(ClientConstants.ADD);
        modelDatasource.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        modelDatasource.get(ClientConstants.OP_ADDR).add("xa-data-source", poolName);
        modelDatasource.get("jndi-name").set(jndi_name);
        modelDatasource.get("use-java-context").set(useJavaContext);
        modelDatasource.get("use-ccm").set(useCCM);
        modelDatasource.get("driver-name").set(driverName);
        modelDatasource.get("transaction-isolation").set(transactionIsolation);
        modelDatasource.get("xa-datasource-class").set(xaDatasourceClass);
        modelDatasource.get("no-tx-separate-pool").set(noTxSeparatePool);
        modelDatasource.get("same-rm-override").set(isSameRmOverride);
        modelDatasource.get("enabled").set(true);
        logger.info(modelDatasource.toString());

        List<ModelNode> props = new ArrayList<ModelNode>();

        for (String key : xaDatasourceProperties.keySet()) {
            final ModelNode model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.ADD);
            model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
            model.get(ClientConstants.OP_ADDR).add("xa-data-source", poolName);
            model.get(ClientConstants.OP_ADDR).add("xa-datasource-properties", key);
            model.get("value").set(xaDatasourceProperties.get(key));
            props.add(model);
        }

        composite.get(ClientConstants.STEPS).add(modelDatasource);
        for (ModelNode node : props) {
            composite.get(ClientConstants.STEPS).add(node);
        }
        try {
            this.applyUpdate(composite);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createDataSource(String jndiName, String poolName, String driver, String connectionUrl, String userName, String password) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, "datasources");
        model.get(ClientConstants.OP_ADDR).add("data-source", poolName);

        model.get("jndi-name").set(jndiName);
        model.get("driver-name").set(driver);
        model.get("connection-url").set(connectionUrl);
        model.get("user-name").set(userName);
        model.get("password").set(password);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalDataSource(String serverName, String dataSourceName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);

        model.get("name").set("journal-datasource");
        model.get("value").set(dataSourceName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalDataSource(String dataSourceName) {
        setJournalDataSource(NAME_OF_MESSAGING_DEFAULT_SERVER, dataSourceName);
    }

    @Override
    public void setJournalSqlProviderFactoryClass(String serverName, String className, String module) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);

        model.get("name").set("journal-sql-provider-factory-class");
        model.get("value").get("name").set(className);
        model.get("value").get("module").set(module);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJournalSqlProviderFactoryClass(String className, String module) {
        setJournalSqlProviderFactoryClass(NAME_OF_MESSAGING_DEFAULT_SERVER, className, module);
    }

    /**
     * Add XA datasource property.
     *
     * @param poolName
     * @param propertyName
     * @param value
     */
    @Override
    public void addXADatasourceProperty(String poolName, String propertyName, String value) {

        final ModelNode model = createModelNode();
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
     * Add XA datasource property.
     *
     * @param poolName      pool name
     * @param attributeName attribute name
     * @param value         value
     */
    @Override
    public void setXADatasourceAtribute(String poolName, String attributeName, String value) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        model.get(ClientConstants.OP_ADDR).add("xa-data-source", poolName);
        model.get("name").set(attributeName);
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
    @Override
    public void createJDBCDriver(String driverName, String moduleName, String driverClass, String xaDatasourceClass) {

        final ModelNode model = createModelNode();
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
     * A broadcast group is the means by which a server broadcasts connectors over the network. A connector defines a way in
     * which a client (or other server) can make connections to the server.
     *
     * @param name                a unique name for the broadcast group - mandatory.
     * @param localBindAddress    local bind address that the datagram socket is bound to. The default value is the wildcard IP
     *                            address chosen by the kernel
     * @param localBindPort       local port to which the datagram socket is bound to.
     * @param groupAddress        multicast address to which the data will be broadcast - mandatory.
     * @param groupPort           UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod     period in milliseconds between consecutive broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be broadcasted.
     */
    @Override
    public void setBroadCastGroup(String name, String localBindAddress, int localBindPort, String groupAddress, int groupPort,
                                  long broadCastPeriod, String connectorName, String backupConnectorName) {
        setBroadCastGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, localBindAddress, localBindPort, groupAddress, groupPort,
                broadCastPeriod, connectorName, backupConnectorName);
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors over the network. A connector defines a way in
     * which a client (or other server) can make connections to the server.
     *
     * @param serverName          set name of hornetq server
     * @param name                a unique name for the broadcast group - mandatory.
     * @param localBindAddress    local bind address that the datagram socket is bound to. The default value is the wildcard IP
     *                            address chosen by the kernel
     * @param localBindPort       local port to which the datagram socket is bound to.
     * @param groupAddress        multicast address to which the data will be broadcast - mandatory.
     * @param groupPort           UDP port number used for broadcasting - mandatory.
     * @param broadCastPeriod     period in milliseconds between consecutive broadcasts.
     * @param connectorName       A pair connector.
     * @param backupConnectorName optional backup connector that will be broadcasted.
     */
    @Override
    public void setBroadCastGroup(String serverName, String name, String localBindAddress, int localBindPort,
                                  String groupAddress, int groupPort, long broadCastPeriod, String connectorName, String backupConnectorName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * A broadcast group is the means by which a server broadcasts connectors over the network. A connector defines a way in
     * which a client (or other server) can make connections to the server.
     *
     * @param name                            a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use for broadcasting connectors
     * @param broadCastPeriod                 period in milliseconds between consecutive broadcasts.
     * @param connectorName                   A pair connector.
     * @param backupConnectorName             optional backup connector that will be broadcasted.
     */
    @Override
    public void setBroadCastGroup(String name, String messagingGroupSocketBindingName, long broadCastPeriod,
                                  String connectorName, String backupConnectorName) {
        setBroadCastGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, messagingGroupSocketBindingName, broadCastPeriod,
                connectorName, backupConnectorName);
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors over the network. A connector defines a way in
     * which a client (or other server) can make connections to the server.
     *
     * @param serverName                      set name of hornetq server
     * @param name                            a unique name for the broadcast group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use for broadcasting connectors
     * @param broadCastPeriod                 period in milliseconds between consecutive broadcasts.
     * @param connectorName                   A pair connector.
     * @param backupConnectorName             optional backup connector that will be broadcasted.
     */
    @Override
    public void setBroadCastGroup(String serverName, String name, String messagingGroupSocketBindingName, long broadCastPeriod,
                                  String connectorName, String backupConnectorName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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

        logger.info(model);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A broadcast group is the means by which a server broadcasts connectors over the network. A connector defines a way in
     * which a client (or other server) can make connections to the server.
     *
     * @param name            a unique name for the broadcast group - mandatory
     * @param jgroupsStack    jgroups protocol stack
     * @param jgroupsChannel  the name that jgroups channels connect to for broadcasting
     * @param broadcastPeriod period in miliseconds between consecutive broadcasts
     * @param connectorName   a pair connector
     */
    @Override
    public void setBroadCastGroup(String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod,
                                  String connectorName) {

        setBroadCastGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, jgroupsStack, jgroupsChannel, broadcastPeriod, connectorName);
    }

    /**
     * @param serverName      name of the server
     * @param name            a unique name for the broadcast group - mandatory
     * @param jgroupsStack    jgroups protocol stack
     * @param jgroupsChannel  the name that jgroups channels connect to for broadcasting
     * @param broadcastPeriod period in miliseconds between consecutive broadcasts
     * @param connectorName   a pair connector
     */
    public void setBroadCastGroup(String serverName, String name, String jgroupsStack, String jgroupsChannel, long broadcastPeriod, String connectorName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", name);

        if (!isEmpty(jgroupsStack)) {
            model.get("jgroups-stack").set(jgroupsStack);
        }

        if (!isEmpty(jgroupsChannel)) {
            model.get("jgroups-channel").set(jgroupsChannel);
        }

        if (!isEmpty(broadcastPeriod)) {
            model.get("broadcast-period").set(broadcastPeriod);
        }

        model.get("connectors").add(connectorName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Discovery group defines how connector information is received from a multicast address.
     *
     * @param name             A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this local address.
     * @param groupAddress     Multicast IP address of the group to listen on - mandatory.
     * @param groupPort        UDP port of the multicast group - mandatory
     * @param refreshTimeout   Period the discovery group waits after receiving the last broadcast from a particular server before
     *                         removing that servers connector pair entry from its list.
     */
    @Override
    public void setDiscoveryGroup(String name, String localBindAddress, String groupAddress, int groupPort, long refreshTimeout) {
        setDiscoveryGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, localBindAddress, groupAddress, groupPort, refreshTimeout);
    }

    /**
     * Discovery group defines how connector information is received from a multicast address.
     *
     * @param serverName       Set name of hornetq server
     * @param name             A unique name for the discovery group - mandatory.
     * @param localBindAddress The discovery group will be bound only to this local address.
     * @param groupAddress     Multicast IP address of the group to listen on - mandatory.
     * @param groupPort        UDP port of the multicast group - mandatory
     * @param refreshTimeout   Period the discovery group waits after receiving the last broadcast from a particular server before
     *                         removing that servers connector pair entry from its list.
     */
    @Override
    public void setDiscoveryGroup(String serverName, String name, String localBindAddress, String groupAddress, int groupPort,
                                  long refreshTimeout) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * Discovery group defines how connector information is received from a multicast address.
     *
     * @param name                            A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use for accepting connectors from other servers
     * @param refreshTimeout                  Period the discovery group waits after receiving the last broadcast from a particular server before
     *                                        removing that servers connector pair entry from its list.
     */
    @Override
    public void setDiscoveryGroup(String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        setDiscoveryGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, messagingGroupSocketBindingName, refreshTimeout);
    }

    /**
     * Discovery group defines how connector information is received from a multicast address.
     *
     * @param serverName                      Set name of hornetq server
     * @param name                            A unique name for the discovery group - mandatory.
     * @param messagingGroupSocketBindingName name of the socket binding to use for accepting connectors from other servers
     * @param refreshTimeout                  Period the discovery group waits after receiving the last broadcast from a particular server before
     *                                        removing that servers connector pair entry from its list.
     */
    @Override
    public void setDiscoveryGroup(String serverName, String name, String messagingGroupSocketBindingName, long refreshTimeout) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * Discovery group defines how connector information is received from a multicast address.
     *
     * @param name           A unique name for the discovery group - mandatory.
     * @param refreshTimeout Period the discovery group waits after receiving the last broadcast from a particular server before
     *                       removing that servers connector pair entry from its list.
     * @param jgroupsStack   jgroups protocol stack
     * @param jgroupsChannel the name that jgroups channels connect to for broadcasting
     */
    @Override
    public void setDiscoveryGroup(String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel) {
        setDiscoveryGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, name, refreshTimeout, jgroupsStack, jgroupsChannel);
    }

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
    public void setDiscoveryGroup(String serverName, String name, long refreshTimeout, String jgroupsStack, String jgroupsChannel) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("discovery-group", name);

        if (!isEmpty(jgroupsStack)) {
            model.get("jgroups-stack").set(jgroupsStack);
        }

        if (!isEmpty(jgroupsChannel)) {
            model.get("jgroups-channel").set(jgroupsChannel);
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
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param discoveryGroupRef      Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there are no matching consumers on target?
     * @param maxHops                Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef, boolean forwardWhenNoConsumers,
                                      int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        setClusterConnections(NAME_OF_MESSAGING_DEFAULT_SERVER, name, address, discoveryGroupRef, forwardWhenNoConsumers,
                maxHops, retryInterval, useDuplicateDetection, connectorName);
    }

    @Override
    public int getNumberOfPreparedTransaction() {
        return getNumberOfPreparedTransaction(NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    /**
     * Get number of prepared transactions
     */
    @Override
    public int getNumberOfPreparedTransaction(String serverName) {
        // /subsystem=messaging/hornetq-server=default:list-prepared-transactions
        int i = -1;
        try {
            ModelNode model = new ModelNode();
            model.get(ClientConstants.OP).set("list-prepared-transactions");
            model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
            model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);

            ModelNode result;
            try {
                result = this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            i = result.get("result").asList().size();
            logger.info(result.toString());

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return i;
    }

    @Override
    public String listPreparedTransaction() {
        return listPreparedTransaction(NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    @Override
    public String listPreparedTransaction(String serverName) {
        // /subsystem=messaging/hornetq-server=default:list-prepared-transactions
        int i = -1;
        ModelNode result = null;
        try {
            ModelNode model = new ModelNode();
            model.get(ClientConstants.OP).set("list-prepared-transactions");
            model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
            model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);

            try {
                result = this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return result.toString();
    }

    @Override
    public String listPreparedTransactionAsJson() {
        return listPreparedTransactionAsJson(NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    @Override
    public String listPreparedTransactionAsJson(String serverName) {
        // /subsystem=messaging/hornetq-server=default:list-prepared-transactions
        int i = -1;
        ModelNode result = null;
        try {
            ModelNode model = new ModelNode();
            model.get(ClientConstants.OP).set("list-prepared-transaction-details-as-json");
            model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
            model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);

            try {
                result = this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return result.toString();
    }

    /**
     * Removes protocol from JGroups stack
     *
     * @param nameOfStack  name of stack udp,tcp
     * @param protocolName protocol name PING,MERGE
     */
    public void removeProtocolFromJGroupsStack(String nameOfStack, String protocolName) {

        try {
            ModelNode model = createModelNode();
            model.get(ClientConstants.OP).set("remove");
            model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
            model.get(ClientConstants.OP_ADDR).add("stack", nameOfStack);
            model.get(ClientConstants.OP_ADDR).add("protocol", protocolName);
            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Sets cluster configuration.
     *
     * @param serverName             Set name of hornetq server.
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param discoveryGroupRef      Name of discovery group used by this bridge.
     * @param forwardWhenNoConsumers Should messages be load balanced if there are no matching consumers on target?
     * @param maxHops                Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef,
                                      boolean forwardWhenNoConsumers, int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName) {
        Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy = null;
        if (forwardWhenNoConsumers) {
            messageLoadBalancingPolicy = Constants.MESSAGE_LOAD_BALANCING_POLICY.STRICT;
        } else {
            messageLoadBalancingPolicy = Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND;
        }

        setClusterConnections(serverName, name, address, discoveryGroupRef, messageLoadBalancingPolicy, maxHops, retryInterval,
                useDuplicateDetection, connectorName);
    }

    /**
     * Sets cluster configuration.
     *
     * @param name                       Name of the cluster group - like "failover-cluster"
     * @param address                    Name of address this cluster connection applies to.
     * @param discoveryGroupRef          Name of discovery group used by this bridge.
     * @param messageLoadBalancingPolicy Should messages be load balanced if there are no matching consumers on target?
     * @param maxHops                    Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval              Period (in ms) between successive retries.
     * @param useDuplicateDetection      Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName              Name of connector to use for live connection.
     */
    @Override
    public void setClusterConnections(String name, String address, String discoveryGroupRef,
                                      Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy, int maxHops,
                                      long retryInterval, boolean useDuplicateDetection, String connectorName) {
        setClusterConnections(NAME_OF_MESSAGING_DEFAULT_SERVER, name, address, discoveryGroupRef, messageLoadBalancingPolicy, maxHops, retryInterval,
                useDuplicateDetection, connectorName);
    }

    /**
     * Sets cluster configuration.
     *
     * @param serverName                 Set name of hornetq server.
     * @param name                       Name of the cluster group - like "failover-cluster"
     * @param address                    Name of address this cluster connection applies to.
     * @param discoveryGroupRef          Name of discovery group used by this bridge.
     * @param messageLoadBalancingPolicy Should messages be load balanced if there are no matching consumers on target?
     * @param maxHops                    Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval              Period (in ms) between successive retries.
     * @param useDuplicateDetection      Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName              Name of connector to use for live connection.
     */
    @Override
    public void setClusterConnections(String serverName, String name, String address, String discoveryGroupRef,
                                      Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy, int maxHops,
                                      long retryInterval, boolean useDuplicateDetection, String connectorName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", name);

        model.get("cluster-connection-address").set(address);
        model.get("discovery-group").set(discoveryGroupRef);
        model.get("message-load-balancing-type").set(messageLoadBalancingPolicy.toString());
        model.get("max-hops").set(maxHops);
        model.get("retry-interval").set(retryInterval);
        model.get("use-duplicate-detection").set(useDuplicateDetection);
        if (connectorName != null && !"".equals(connectorName)) {
            model.get("connector-name").set(connectorName);
        } else {
            model.get("connector-name").set(ModelType.UNDEFINED);
        }
        logger.info(model);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Sets reconnect attempts on cluster connection.
     *
     * @param clusterGroupName name
     * @param attempts         number of retries (-1 for indenfitely)
     */
    @Override
    public void setReconnectAttemptsForClusterConnection(String clusterGroupName, int attempts) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);

        model.get("name").set("reconnect-attempts");
        model.get("value").set(attempts);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets connection ttl value.
     *
     * @param serverName    name of the server
     * @param valueInMillis ttl
     */
    @Override
    public void setConnectionTtlOverride(String serverName, long valueInMillis) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);

        model.get("name").set("connection-ttl-override");
        model.get("value").set(valueInMillis);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets cluster configuration.
     *
     * @param serverName             Set name of hornetq server.
     * @param name                   Name of the cluster group - like "failover-cluster"
     * @param address                Name of address this cluster connection applies to.
     * @param forwardWhenNoConsumers This is actually load balancing policy in EAP 7
     * @param maxHops                Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval          Period (in ms) between successive retries.
     * @param useDuplicateDetection  Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName          Name of connector to use for live connection.
     */
    @Override
    public void setStaticClusterConnections(String serverName, String name, String address, boolean forwardWhenNoConsumers,
                                            int maxHops, long retryInterval, boolean useDuplicateDetection, String connectorName, String... remoteConnectors) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", name);

        model.get("cluster-connection-address").set(address);
        model.get("forward-when-no-consumers").set(forwardWhenNoConsumers);
        model.get("max-hops").set(maxHops);
        model.get("retry-interval").set(retryInterval);
        model.get("use-duplicate-detection").set(useDuplicateDetection);
        model.get("connector-name").set(connectorName);

        for (String remoteConnectorName : remoteConnectors) {
            model.get("static-connectors").add(remoteConnectorName);
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
     * @param serverName                 Set name of hornetq server.
     * @param name                       Name of the cluster group - like "failover-cluster"
     * @param address                    Name of address this cluster connection applies to.
     * @param messageLoadBalancingPolicy This is actually load balancing policy in EAP 7
     * @param maxHops                    Maximum number of hops cluster topology is propagated. Default is 1.
     * @param retryInterval              Period (in ms) between successive retries.
     * @param useDuplicateDetection      Should duplicate detection headers be inserted in forwarded messages?
     * @param connectorName              Name of connector to use for live connection.
     */
    @Override
    public void setStaticClusterConnections(String serverName, String name, String address, Constants.MESSAGE_LOAD_BALANCING_POLICY messageLoadBalancingPolicy,
                                            int maxHops, long retryInterval, boolean useDuplicateDetection,
                                            String connectorName, String... remoteConnectors) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", name);

        model.get("cluster-connection-address").set(address);
        model.get("message-load-balancing-type").set(messageLoadBalancingPolicy.toString());
        model.get("max-hops").set(maxHops);
        model.get("retry-interval").set(retryInterval);
        model.get("use-duplicate-detection").set(useDuplicateDetection);
        model.get("connector-name").set(connectorName);

        for (String remoteConnectorName : remoteConnectors) {
            model.get("static-connectors").add(remoteConnectorName);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method activates preferFactoryRef property in ActivationSpec.java in ejb3-interceptors-aop.xml. This is specific for
     * EAP 5.
     *
     * @param active if true then this attribute is activated. It's defaulted to true.
     */
    @Override
    public void setFactoryRef(boolean active) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Related only to EAP 5.
     * <p>
     * Sets basic attributes in ra.xml.
     *
     * @param connectorClassName
     * @param connectionParameters
     * @param ha
     */
    @Override
    public void setRA(String connectorClassName, Map<String, String> connectionParameters, boolean ha) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void setRA(String discoveryMulticastAddress, int discoveryMulticastPort, boolean ha, String username, String password) {
        logger.info("This operation is not supported: " + getMethodName());
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

    /**
     * Sets size of the journal file.
     *
     * @param sizeInBytes size of the journal file in bytes
     */
    @Override
    public void setJournalFileSize(long sizeInBytes) {
        setJournalFileSize(NAME_OF_MESSAGING_DEFAULT_SERVER, sizeInBytes);
    }

    /**
     * Sets size of the journal file.
     *
     * @param serverName  name of the hornetq server
     * @param sizeInBytes size of the journal file in bytes
     */
    @Override
    public void setJournalFileSize(String serverName, long sizeInBytes) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("journal-file-size");
        model.get("value").set(sizeInBytes);
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
    @Override
    public void setRedistributionDelay(long delay) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
    @Override
    public void addJndiBindingForConnectionFactory(String connectionFactoryName, String newConnectionFactoryJndiName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add-jndi");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
     * @param value                 true if connection factory supports ha.
     */
    @Override
    public void setHaForConnectionFactory(String connectionFactoryName, boolean value) {
        setHaForConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, value);
    }

    /**
     * Sets ha attribute.
     *
     * @param serverName            name of the server
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    public void setHaForConnectionFactory(String serverName, String connectionFactoryName, boolean value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("ha");
        model.get("value").set(value);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setConnectorOnConnectionFactory(String connectionFactoryName, String connectorName) {
        setConnectorOnConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, connectorName);
        ;
    }

    @Override
    public void setConnectorOnConnectionFactory(String serverName, String connectionFactoryName, String connectorName) {
        // java.lang.UnsupportedOperationException: JBAS011664: Runtime handling for connector is not implemented

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("name").set("connectors");
        List<String> connectors = new ArrayList<String>();
        connectors.add(connectorName);
        model.get("value").set(createModelNodeForList(connectors));

        System.out.println(model.toString());

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMinPoolSizeOnPooledConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("min-pool-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMinLargeMessageSizeOnPooledConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("min-large-message-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMinLargeMessageSizeOnConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("min-large-message-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxPoolSizeOnPooledConnectionFactory(String connectionFactoryName, int size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("max-pool-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConsumerWindowSizeOnConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("consumer-window-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setConsumerWindowSizeOnPooledConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("consumer-window-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setProducerWindowSizeOnConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("producer-window-size");
        model.get("value").set(size);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setProducerWindowSizeOnPooledConnectionFactory(String connectionFactoryName, long size) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("producer-window-size");
        model.get("value").set(size);

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
     * @param value                 true if connection factory supports ha.
     */
    @Override
    public void setFailoverOnShutdown(String connectionFactoryName, boolean value) {
        throw new UnsupportedOperationException("Set failover on shutdown is not available in EAP 7.");
    }

    /**
     * Sets ha attribute.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    @Override
    public void setHaForPooledConnectionFactory(String connectionFactoryName, boolean value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
     * Sets failover-on-server-shutdown.
     *
     * @param connectionFactoryName
     * @param value                 true if connection factory supports ha.
     */
    @Override
    public void setFailoverOnShutdownOnPooledConnectionFactory(String connectionFactoryName, boolean value) {
        throw new UnsupportedOperationException("Set failover on shutdown is not available in EAP 7.");
    }

    /**
     * Sets failover-on-server-shutdown.
     *
     * @param value true if connection factory supports ha.
     */
    @Override
    public void setFailoverOnShutdown(boolean value) {
        throw new UnsupportedOperationException("Set failover on shutdown is not available in EAP 7.");
    }

    /**
     * Sets failover-on-server-shutdown.
     *
     * @param value true if connection factory supports ha.
     */
    @Override
    public void setFailoverOnShutdown(boolean value, String serverName) {
        throw new UnsupportedOperationException("Set failover on shutdown is not available in EAP 7.");
    }

    /**
     * Whether or not messages are acknowledged synchronously.
     *
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    @Override
    public void setBlockOnAckForConnectionFactory(String connectionFactoryName, boolean value) {
        setBlockOnAckForConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, value);
    }

    /**
     * Whether or not messages are acknowledged synchronously.
     *
     * @param serverName            name of the server
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    public void setBlockOnAckForConnectionFactory(String serverName, String connectionFactoryName, boolean value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("block-on-acknowledge");
        model.get("value").set(value);

        applyUpdateWithRetry(model, 50);
    }

    /**
     * Whether or not messages are acknowledged synchronously.
     *
     * @param connectionFactoryName
     * @param value                 default false, should be true for fail-over scenarios
     */
    @Override
    public void setBlockOnAckForPooledConnectionFactory(String connectionFactoryName, boolean value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
    @Override
    public void setRetryIntervalForConnectionFactory(String connectionFactoryName, long value) {
        setRetryIntervalForConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, value);
    }

    /**
     * The time (in ms) to retry a connection after failing.
     *
     * @param serverName            name of the server
     * @param connectionFactoryName
     * @param value
     */
    public void setRetryIntervalForConnectionFactory(String serverName, String connectionFactoryName, long value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void setRetryIntervalForPooledConnectionFactory(String connectionFactoryName, long value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval");
        model.get("value").set(value);

        applyUpdateWithRetry(model, 50);
    }

    /**
     * Multiplier to apply to successive retry intervals.
     *
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    @Override
    public void setRetryIntervalMultiplierForConnectionFactory(String connectionFactoryName, double value) {
        setRetryIntervalMultiplierForConnectionFactory(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, value);
    }

    /**
     * Multiplier to apply to successive retry intervals.
     *
     * @param serverName            name of the server
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    public void setRetryIntervalMultiplierForConnectionFactory(String serverName, String connectionFactoryName, double value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval-multiplier");
        model.get("value").set(value);

        applyUpdateWithRetry(model, 50);
    }

    /**
     * Multiplier to apply to successive retry intervals.
     *
     * @param connectionFactoryName
     * @param value                 1.0 by default
     */
    @Override
    public void setRetryIntervalMultiplierForPooledConnectionFactory(String connectionFactoryName, double value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);
        model.get("name").set("retry-interval-multiplier");
        model.get("value").set(value);

        applyUpdateWithRetry(model, 50);

    }

    /**
     * How many times should client retry connection when connection is lost. This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    @Override
    public void setReconnectAttemptsForConnectionFactory(String connectionFactoryName, int value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("name").set("reconnect-attempts");
        model.get("value").set(value);
        applyUpdateWithRetry(model, 50);

    }

    @Override
    public void setCallTimeoutForConnectionFactory(String connectionFactoryName, long callTimeout) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("name").set("call-timeout");
        model.get("value").set(callTimeout);
        applyUpdateWithRetry(model, 50);
    }

    @Override
    public void setConnectionTTLForConnectionFactory(String connectionFactoryName, long callTimeout) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("name").set("connection-ttl");
        model.get("value").set(callTimeout);
        applyUpdateWithRetry(model, 50);
    }

    @Override
    public void setClientFailureCheckPeriodForConnectionFactory(String connectionFactoryName, long callTimeout) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);

        model.get("name").set("client-failure-check-period");
        model.get("value").set(callTimeout);
        applyUpdateWithRetry(model, 50);
    }

    /**
     * The number of times to set up an MDB endpoint
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    @Override
    public void setSetupAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("setup-attempts");
        model.get("value").set(value);
        applyUpdateWithRetry(model, 50);
    }

    /**
     * The number of attempts to connect initially with this factory
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    @Override
    public void setInitialConnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", connectionFactoryName);

        model.get("name").set("initial-connect-attempts");
        model.get("value").set(value);
        applyUpdateWithRetry(model, 50);
    }

    /**
     * How many times should client retry connection when connection is lost. This should be -1 if failover is required.
     *
     * @param connectionFactoryName nameOfConnectionFactory (not jndi name)
     * @param value                 value
     */
    @Override
    public void setReconnectAttemptsForPooledConnectionFactory(String connectionFactoryName, int value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
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
    @Override
    public void setJmxDomainName(String jmxDomainName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get("name").set("jmx-domain");
        model.get("value").set(jmxDomainName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setJmxManagementEnabled(boolean enable) {
        setJmxManagementEnabled(NAME_OF_MESSAGING_DEFAULT_SERVER, enable);
    }

    @Override
    public void setJmxManagementEnabled(String serverName, boolean enable) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("jmx-management-enabled");
        model.get("value").set(enable);
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
    @Override
    public void setBackup(boolean isBackup) {
        setBackup(NAME_OF_MESSAGING_DEFAULT_SERVER, isBackup);
    }

    /**
     * Sets backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    @Override
    public void setBackup(String serverName, boolean isBackup) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
    @Override
    public void addBackup(boolean isBackup) {
        setBackup(NAME_OF_MESSAGING_DEFAULT_SERVER, isBackup);
    }

    /**
     * Adds backup attribute.
     *
     * @param isBackup
     * @param serverName name of the hornetq server
     */
    @Override
    public void addBackup(String serverName, boolean isBackup) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
        removeAddressSettings(NAME_OF_MESSAGING_DEFAULT_SERVER, address);
    }

    /**
     * Removes address settings
     *
     * @param serverName name of the server
     * @param address    address specification
     */
    @Override
    public void removeAddressSettings(String serverName, String address) {
        try {
            ModelNode setAddressAttributes = createModelNode();
            setAddressAttributes.get(ClientConstants.OP).set("remove");
            setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
            setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * @param address             address specification
     * @param addressFullPolicy   address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes        The max bytes size
     * @param redeliveryDelay     Defines how long to wait before attempting redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last consumer is closed on a queue before redistributing any
     *                            messages
     * @param pageSizeBytes       The paging size
     */
    @Override
    public void addAddressSettings(String address, String addressFullPolicy, long maxSizeBytes, int redeliveryDelay,
                                   long redistributionDelay, long pageSizeBytes) {
        addAddressSettings(NAME_OF_MESSAGING_DEFAULT_SERVER, address, addressFullPolicy, maxSizeBytes, redeliveryDelay,
                redistributionDelay, pageSizeBytes);
    }

    /**
     * Adds address settings
     *
     * @param address             address specification
     * @param addressFullPolicy   address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes        The max bytes size
     * @param redeliveryDelay     Defines how long to wait before attempting redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last consumer is closed on a queue before redistributing any
     *                            messages
     * @param pageSizeBytes       The paging size
     */
    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes,
                                   int redeliveryDelay, long redistributionDelay, long pageSizeBytes) {
        ModelNode setAddressAttributes = createModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, containerName);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        setAddressAttributes.get("max-delivery-attempts").set(200);

        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds address settings
     *
     * @param address             address specification
     * @param addressFullPolicy   address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes        The max bytes size
     * @param redeliveryDelay     Defines how long to wait before attempting redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last consumer is closed on a queue before redistributing any
     *                            messages
     * @param pageSizeBytes       The paging size
     * @param lastValueQueue      true for last value queue
     */
    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, long maxSizeBytes,
                                   int redeliveryDelay, long redistributionDelay, long pageSizeBytes, boolean lastValueQueue) {
        ModelNode setAddressAttributes = createModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, containerName);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        setAddressAttributes.get("max-delivery-attempts").set(200);
        setAddressAttributes.get("last-value-queue").set(lastValueQueue);

        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds settings for slow consumers to existing address settings for given mask.
     *
     * @param address     address specification
     * @param threshold   the minimum rate of message consumption allowed (in messages-per-second, -1 is disabled)
     * @param policy      what should happen with slow consumers
     * @param checkPeriod how often to check for slow consumers (in minutes, default is 5)
     */
    @Override
    public void setSlowConsumerPolicy(String address, int threshold, SlowConsumerPolicy policy, int checkPeriod) {
        setSlowConsumerPolicy(NAME_OF_MESSAGING_DEFAULT_SERVER, address, threshold, policy, checkPeriod);
    }

    /**
     * Adds settings for slow consumers to existing address settings for given mask.
     *
     * @param serverName  hornetq server name
     * @param address     address specification
     * @param threshold   the minimum rate of message consumption allowed (in messages-per-second, -1 is disabled)
     * @param policy      what should happen with slow consumers
     * @param checkPeriod how often to check for slow consumers (in minutes, default is 5)
     */
    @Override
    public void setSlowConsumerPolicy(String serverName, String address, int threshold, SlowConsumerPolicy policy,
                                      int checkPeriod) {

        ModelNode addressSettings = createModelNode();
        addressSettings.get(ClientConstants.OP).set("write-attribute");
        addressSettings.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        addressSettings.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        addressSettings.get(ClientConstants.OP_ADDR).add("address-setting", address);

        ModelNode setThreshold = addressSettings.clone();
        setThreshold.get("name").set("slow-consumer-threshold");
        setThreshold.get("value").set(threshold);

        ModelNode setPolicy = addressSettings.clone();
        setPolicy.get("name").set("slow-consumer-policy");
        setPolicy.get("value").set(policy.name());

        ModelNode setCheckPeriod = addressSettings.clone();
        setCheckPeriod.get("name").set("slow-consumer-check-period");
        setCheckPeriod.get("value").set(checkPeriod);

        try {
            this.applyUpdate(setThreshold);
            this.applyUpdate(setPolicy);
            this.applyUpdate(setCheckPeriod);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds address settings
     *
     * @param address             address specification
     * @param addressFullPolicy   address full policy (PAGE, DROP or BLOCK)
     * @param maxSizeBytes        The max bytes size
     * @param redeliveryDelay     Defines how long to wait before attempting redelivery of a cancelled message
     * @param redistributionDelay Defines how long to wait when the last consumer is closed on a queue before redistributing any
     *                            messages
     * @param pageSizeBytes       The paging size
     * @param expireQueue         Expire queue
     * @param deadLetterQueue     dead letter queue jms.queue.DLQ
     */
    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes,
                                   int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue) {
        ModelNode setAddressAttributes = createModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, containerName);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        setAddressAttributes.get("expiry-address").set(expireQueue);
        setAddressAttributes.get("dead-letter-address").set(deadLetterQueue);

        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes,
                                   int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue,
                                   int maxDeliveryAttempts) {

        ModelNode setAddressAttributes = createModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, containerName);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        setAddressAttributes.get("expiry-address").set(expireQueue);
        setAddressAttributes.get("dead-letter-address").set(deadLetterQueue);
        setAddressAttributes.get("max-delivery-attempts").set(maxDeliveryAttempts);

        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAddressSettings(String containerName, String address, String addressFullPolicy, int maxSizeBytes, int redeliveryDelay, long redistributionDelay, long pageSizeBytes, String expireQueue, String deadLetterQueue, int maxDeliveryAttempts, boolean lastValueQueue) {
        ModelNode setAddressAttributes = createModelNode();
        setAddressAttributes.get(ClientConstants.OP).set("add");
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, containerName);
        setAddressAttributes.get(ClientConstants.OP_ADDR).add("address-setting", address);
        setAddressAttributes.get("address-full-policy").set(addressFullPolicy);
        setAddressAttributes.get("max-size-bytes").set(maxSizeBytes);
        setAddressAttributes.get("redelivery-delay").set(redeliveryDelay);
        setAddressAttributes.get("redistribution-delay").set(redistributionDelay);
        setAddressAttributes.get("page-size-bytes").set(pageSizeBytes);
        setAddressAttributes.get("expiry-address").set(expireQueue);
        setAddressAttributes.get("dead-letter-address").set(deadLetterQueue);
        setAddressAttributes.get("max-delivery-attempts").set(maxDeliveryAttempts);
        setAddressAttributes.get("last-value-queue").set(lastValueQueue);

        try {
            this.applyUpdate(setAddressAttributes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds grouping handler
     *
     * @param name    name of grouping handler
     * @param type    type - LOCAL, REMOTE
     * @param address cluster address
     * @param timeout timeout to have decision where the message will be routed
     */
    @Override
    public void addMessageGrouping(String name, String type, String address, long timeout) {
        addMessageGrouping(NAME_OF_MESSAGING_DEFAULT_SERVER, name, type, address, timeout);

    }

    /**
     * Adds grouping handler
     *
     * @param serverName hornetq server name
     * @param name       name of grouping handler
     * @param type       type - LOCAL, REMOTE
     * @param address    cluster address
     * @param timeout    timeout to have decision where the message will be routed
     */
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout) {

        ModelNode modelNode = createModelNode();
        modelNode.get(ClientConstants.OP).set("add");
        modelNode.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        modelNode.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        modelNode.get(ClientConstants.OP_ADDR).add("grouping-handler", name);
        modelNode.get("grouping-handler-address").set(address);
        modelNode.get("type").set(type);
        modelNode.get("timeout").set(timeout);
        // modelNode.get("group-timeout").set(timeout);

        try {
            this.applyUpdate(modelNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Adds grouping handler
     *
     * @param serverName hornetq server name
     * @param name       name of grouping handler
     * @param type       type - LOCAL, REMOTE
     * @param address    cluster address
     * @param timeout    timeout to have decision where the message will be routed
     */
    @Override
    public void addMessageGrouping(String serverName, String name, String type, String address, long timeout,
                                   long groupTimeout, long reaperPeriod) {

        ModelNode modelNode = createModelNode();
        modelNode.get(ClientConstants.OP).set("add");
        modelNode.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        modelNode.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        modelNode.get(ClientConstants.OP_ADDR).add("grouping-handler", name);
        modelNode.get("grouping-handler-address").set(address);
        modelNode.get("type").set(type);
        modelNode.get("timeout").set(timeout);
        modelNode.get("group-timeout").set(groupTimeout);
        modelNode.get("reaper-period").set(reaperPeriod);

        try {
            this.applyUpdate(modelNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Adds external context.
     */
    @Override
    public void addExternalContext(String binding, String className, String module, String bindingType,
                                   Map<String, String> environmentProperies) {
        // [standalone@localhost:9999 /]
        // /subsystem=naming/binding="java:global/client-context":add(module=org.jboss.legacy.naming.spi,
        // class=javax.naming.InitialContext,binding-type=external-context,
        // environment={"java.naming.provider.url" => "jnp://localhost:5599", "java.naming.factory.url.pkgs" =>
        // "org.jnp.interfaces",
        // "java.naming.factory.initial" => "org.jboss.legacy.jnp.factory.WatchfulContextFactory" })

        ModelNode modelNode = new ModelNode();
        modelNode.get(ClientConstants.OP).set("add");
        modelNode.get(ClientConstants.OP_ADDR).add("subsystem", "naming");
        modelNode.get(ClientConstants.OP_ADDR).add("binding", binding);

        modelNode.get("class").set(className);
        modelNode.get("module").set(module);
        modelNode.get("binding-type").set(bindingType);

        if (environmentProperies != null) {
            for (String key : environmentProperies.keySet()) {
                modelNode.get("environment").add(key, environmentProperies.get(key));
            }
        }

        try {
            this.applyUpdate(modelNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Sets transaction node identifier.
     *
     * @param i
     */
    @Override
    public void setNodeIdentifier(int i) {

        ModelNode setNodeIdentifier = createModelNode();
        setNodeIdentifier.get(ClientConstants.OP).set("write-attribute");
        setNodeIdentifier.get(ClientConstants.OP_ADDR).add("subsystem", "transactions");
        setNodeIdentifier.get("name").set("node-identifier");
        setNodeIdentifier.get("value").set(i);
        try {
            this.applyUpdate(setNodeIdentifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAuthenticationForNullUsers(boolean b) {
        logger.warn("This operation is not supoprted - setAuthenticationForNullUsers");
        // TODO IMPLEMENT IT
    }

    @Override
    public void addDatasourceProperty(String poolName, String propertyName, String value) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        model.get(ClientConstants.OP_ADDR).add("data-source", poolName);
        model.get("name").set(propertyName);
        model.get("value").set(value);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxSavedReplicatedJournals(int numberOfReplicatedJournals) {
        setMaxSavedReplicatedJournals(NAME_OF_MESSAGING_DEFAULT_SERVER, numberOfReplicatedJournals);
    }

    @Override
    public void setMaxSavedReplicatedJournals(String serverName, int numberOfReplicatedJournals) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("max-saved-replicated-journal-size");
        model.get("value").set(numberOfReplicatedJournals);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup) {
        setBackupGroupName(nameOfBackupGroup, NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    @Override
    public void setBackupGroupName(String nameOfBackupGroup, String serverName) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("backup-group-name");
        model.get("value").set(nameOfBackupGroup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setCheckForLiveServer(boolean b) {
        setCheckForLiveServer(b, NAME_OF_MESSAGING_DEFAULT_SERVER);
    }

    @Override
    public void setCheckForLiveServer(boolean b, String serverName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("check-for-live-server");
        model.get("value").set(b);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds loop back-address type of the given interface of the given name.
     * <p>
     * Removes inet-address type as a side effect.
     * <p>
     * Like: <loopback-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or "management"
     * @param ipAddress     - ipAddress of the interface
     */
    @Override
    public void setLoopBackAddressType(String interfaceName, String ipAddress) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("interface", interfaceName);
        model.get("name").set("loopback-address");
        model.get("value").set(ipAddress);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        model = createModelNode();
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
     * <p>
     * Removes inet-address type as a side effect.
     * <p>
     * Like: <inet-address value="127.0.0.2" \>
     *
     * @param interfaceName - name of the interface like "public" or "management"
     * @param ipAddress     - ipAddress of the interface
     */
    @Override
    public void setInetAddress(String interfaceName, String ipAddress) {
        ModelNode model = createModelNode();
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
     * @param serverName              name of the server
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    public void removeBroadcastGroup(String serverName, String nameOfTheBroadcastGroup) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("broadcast-group", nameOfTheBroadcastGroup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.warn("Broadcast group " + nameOfTheBroadcastGroup + " could not be removed. Reason: ", e);
        }
    }

    /**
     * Removes broadcast group.
     *
     * @param nameOfTheBroadcastGroup name of the broadcast group
     */
    @Override
    public void removeBroadcastGroup(String nameOfTheBroadcastGroup) {
        removeBroadcastGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, nameOfTheBroadcastGroup);
    }

    /**
     * Removes discovery group
     *
     * @param dggroup name of the discovery group
     */
    @Override
    public void removeDiscoveryGroup(String dggroup) {
        removeDiscoveryGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, dggroup);
    }

    /**
     * Removes discovery group
     *
     * @param serverName name of the server
     * @param dggroup    name of the discovery group
     */
    public void removeDiscoveryGroup(String serverName, String dggroup) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("discovery-group", dggroup);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.warn("Dicovery group " + dggroup + " could not be removed. Reason: ", e);
        }
    }

    /**
     * Removes clustering group.
     *
     * @param serverName       name of the server
     * @param clusterGroupName name of the discovery group
     */
    public void removeClusteringGroup(String serverName, String clusterGroupName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterGroupName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.warn("Cluster connection " + clusterGroupName + " could not be removed. Reason:", e);
        }
    }

    /**
     * Removes clustering group.
     *
     * @param clusterGroupName name of the discovery group
     */
    @Override
    public void removeClusteringGroup(String clusterGroupName) {
        removeClusteringGroup(NAME_OF_MESSAGING_DEFAULT_SERVER, clusterGroupName);
    }

    /**
     * This method is hack! Somehow calling update model throw exception when it should not. For this reason try it more times
     * until success.
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
     * @param level like "ALL", "CONFIG","DEBUG","ERROR","FATAL","FINE","FINER","FINEST","INFO","OFF","TRACE","WARN","WARNING"
     */
    @Override
    public void setLoggingLevelForConsole(String level) {
        // ./console-handler=CONSOLE:add(level=DEBUG,formatter="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n")

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("console-handler", "CONSOLE");
        model.get("level").set(level);
        model.get("formatter").set("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // ./root-logger=ROOT:write-attribute(name=handlers,value=["FILE", "CONSOLE"])

        model = createModelNode();
        model.get(ClientConstants.OP).set("add-handler");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("root-logger", "ROOT");
        model.get("name").set("CONSOLE");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets logging level for root level log
     *
     * @param level like "ALL", "CONFIG","DEBUG","ERROR","FATAL","FINE","FINER","FINEST","INFO","OFF","TRACE","WARN","WARNING"
     */
    @Override
    public void seRootLoggingLevel(String level) {

        // /subsystem=logging/root-logger=ROOT:write-attribute(name=level, value=ERROR)
        ModelNode model = createModelNode();
        model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("root-logger", "ROOT");
        model.get("name").set("level");
        model.get("value").set(level);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Disables actually defined FILE-TRACE logging handler
     */
    @Override
    public void disableTraceLoggingToFile() {
        disableLoggingHandler("FILE-TRACE");
    }

    /**
     * Disables actually defined logging handler
     *
     * @param handlerName name of handler
     *                    to disable trace logs use FILE-TRACE
     *
     */
    @Override
    public void disableLoggingHandler(String handlerName) {

        // /subsystem=logging/root-logger=ROOT:write-attribute(name=level, value=ERROR)
        ModelNode model = createModelNode();
        model = createModelNode();
        model.get(ClientConstants.OP).set("remove-handler");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("root-logger", "ROOT");
        model.get("name").set(handlerName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes defined bridge, method just logs exception it does not throws exception
     *
     * @param name Name of the bridge
     */
    @Override
    public void removeBridge(String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("bridge", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts,
                                 String... staticConnector) {
        createCoreBridge(NAME_OF_MESSAGING_DEFAULT_SERVER, name, queueName, forwardingAddress, reconnectAttempts,
                staticConnector);
    }

    /**
     * Creates new bridge
     *
     * @param name              bridge name
     * @param queueName         source queue
     * @param forwardingAddress target address
     * @param reconnectAttempts reconnect attempts for bridge
     * @param staticConnector   static connector
     */
    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress,
                                 int reconnectAttempts, String... staticConnector) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("bridge", name);
        model.get("queue-name").set(queueName);
        if (forwardingAddress != null) {
            model.get("forwarding-address").set(forwardingAddress);
        }
        model.get("use-duplicate-detection").set(true);
        model.get("failover-on-server-shutdown").set(true);
        model.get("ha").set(true);
        // model.get("failover-on-server-shutdown").set(true);
        model.get("reconnect-attempts").set(reconnectAttempts);
        for (String c : staticConnector) {
            model.get("static-connectors").add(c);
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void stopDeliveryToMdb(String deploymentName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("stop-delivery");
        model.get(ClientConstants.OP_ADDR).add("deployment", deploymentName);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        model.get(ClientConstants.OP_ADDR).add("message-driven-bean", "mdb");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            // throw new RuntimeException(e);
            logger.error("Set property replacement could not be set.", e);
        }
    }

    @Override
    public void startDeliveryToMdb(String deploymentName) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("start-delivery");
        model.get(ClientConstants.OP_ADDR).add("deployment", deploymentName);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "ejb3");
        model.get(ClientConstants.OP_ADDR).add("message-driven-bean", "mdb");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            // throw new RuntimeException(e);
            logger.error("Set property replacement could not be set.", e);
        }
    }

    @Override
    public void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceDestination,
                                Map<String, String> sourceContext, String targetConnectionFactory, String targetDestination,
                                Map<String, String> targetContext, String qualityOfService, long failureRetryInterval, int maxRetries,
                                long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader) {
        createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, sourceContext, targetConnectionFactory, targetDestination, targetContext, qualityOfService, failureRetryInterval,
                maxRetries, maxBatchSize, maxBatchTime, addMessageIDInHeader, null, null, null, null);
    }

    @Override
    public void createJMSBridge(String bridgeName, String sourceConnectionFactory, String sourceDestination, Map<String, String> sourceContext,
                                String targetConnectionFactory, String targetDestination, Map<String, String> targetContext, String qualityOfService,
                                long failureRetryInterval, int maxRetries, long maxBatchSize, long maxBatchTime, boolean addMessageIDInHeader, String sourceUser,
                                String sourcePassword, String targetUser, String targetPassword) {


        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("jms-bridge", bridgeName);
        model.get("source-connection-factory").set(sourceConnectionFactory);
        model.get("ha").set(true);
        model.get("source-destination").set(sourceDestination);
        if (sourceContext != null) {
            for (String key : sourceContext.keySet()) {
                model.get("source-context").add(key, sourceContext.get(key));
            }
        }
        model.get("target-connection-factory").set(targetConnectionFactory);
        model.get("target-destination").set(targetDestination);
        if (targetContext != null) {
            for (String key : targetContext.keySet()) {
                model.get("target-context").add(key, targetContext.get(key));
            }
        }
        model.get("quality-of-service").set(qualityOfService);
        model.get("failure-retry-interval").set(failureRetryInterval);
        model.get("add-messageID-in-header").set(addMessageIDInHeader);
        model.get("max-retries").set(maxRetries);
        model.get("max-batch-size").set(maxBatchSize);
        model.get("max-batch-time").set(maxBatchTime);
        model.get("module").set("org.apache.activemq.artemis");

        if (sourceUser != null) model.get("source-user").set(sourceUser);
        if (sourcePassword != null) model.get("source-password").set(sourcePassword);
        if (targetUser != null) model.get("target-user").set(targetUser);
        if (targetPassword != null) model.get("target-password").set(targetPassword);

        logger.info(model);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }

    }

    @Override
    public void setFactoryType(String serverName, String connectionFactoryName, String factoryType) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("factory-type");
        model.get("value").set(factoryType);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void setFactoryType(String connectionFactoryName, String factoryType) {
        setFactoryType(NAME_OF_MESSAGING_DEFAULT_SERVER, connectionFactoryName, factoryType);
    }

    /**
     * Removes jgroups stack
     *
     * @param stackName "udp", "tcp",...
     */
    @Override
    public void removeJGroupsStack(String stackName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", stackName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void addJGroupsStack(String stackName, LinkedHashMap<String, Properties> protocols, Properties transportParameters) {
        /*
            batch
            /subsystem="jgroups"/stack="tcpping":add()
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="TCPPING")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="MERGE2")
            /subsystem="jgroups"/stack="tcpping":add-protocol(socket-binding="jgroups-tcp-fd",type="FD_SOCK")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="FD")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="VERIFY_SUSPECT")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="BARRIER")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="pbcast.NAKACK")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="UNICAST2")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="pbcast.STABLE")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="pbcast.GMS")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="UFC")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="MFC")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="FRAG2")
            /subsystem="jgroups"/stack="tcpping":add-protocol(type="RSVP")
            /subsystem="jgroups"/stack="tcpping"/transport="TRANSPORT":add(socket-binding="jgroups-tcp",type="TCP")
            run-batch

            /subsystem="jgroups"/stack="tcpping"/protocol="TCPPING"/property="initial_hosts":add(value="192.168.10.1[7600],192.168.10.2[7600]")
            /subsystem="jgroups"/stack="tcpping"/protocol="TCPPING"/property="port_range":add(value="10")
            /subsystem="jgroups"/stack="tcpping"/protocol="TCPPING"/property="timeout":add(value="3000")
            /subsystem="jgroups"/stack="tcpping"/protocol="TCPPING"/property="num_initial_members":add(value="2")
         */
        ModelNode composite = new ModelNode();
        composite.get(ClientConstants.OP).set("composite");
        composite.get(ClientConstants.OP_ADDR).setEmptyList();
        composite.get(ClientConstants.OPERATION_HEADERS, ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode modelAddStack = new ModelNode();
        modelAddStack.get(ClientConstants.OP).set(ClientConstants.ADD);
        modelAddStack.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        modelAddStack.get(ClientConstants.OP_ADDR).add("stack", stackName);
        composite.get(ClientConstants.STEPS).add(modelAddStack);

        for (String protocol : protocols.keySet()) {
            ModelNode addProtocol = new ModelNode();
            addProtocol.get(ClientConstants.OP).set("add-protocol");
            addProtocol.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
            addProtocol.get(ClientConstants.OP_ADDR).add("stack", stackName);
            addProtocol.get("type").set(protocol);
            composite.get(ClientConstants.STEPS).add(addProtocol);
        }

        ModelNode transport = new ModelNode();
        transport.get(ClientConstants.OP).set(ClientConstants.ADD);
        transport.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        transport.get(ClientConstants.OP_ADDR).add("stack", stackName);
        transport.get(ClientConstants.OP_ADDR).add("transport", "TRANSPORT");
        for (String paramName : transportParameters.stringPropertyNames()) {
            transport.get(paramName).set(transportParameters.getProperty(paramName));
        }
        composite.get(ClientConstants.STEPS).add(transport);

        try {
            this.applyUpdate(composite);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (String protocol : protocols.keySet()) {
            Properties protocolProperties = protocols.get(protocol);
            if (protocolProperties != null && protocolProperties.size() > 0) {
                for (String paramName : protocolProperties.stringPropertyNames()) {
                    ModelNode addParam = new ModelNode();
                    addParam.get(ClientConstants.OP).set(ClientConstants.ADD);
                    addParam.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
                    addParam.get(ClientConstants.OP_ADDR).add("stack", stackName);
                    addParam.get(ClientConstants.OP_ADDR).add("protocol", protocol);
                    addParam.get(ClientConstants.OP_ADDR).add("property", paramName);
                    addParam.get("value").set(protocolProperties.getProperty(paramName));
                    try {
                        this.applyUpdate(addParam);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void addTransportToJGroupsStack(String stackName, String transport, String gosshipRouterAddress,
                                           int gosshipRouterPort, boolean enableBundling) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", stackName);
        model.get(ClientConstants.OP_ADDR).add("transport", "UDP");
        model.get("name").set("type");
        model.get("value").set(transport);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }

        // model = new ModelNode();
        // model.get(ClientConstants.OP).set("write-attribute");
        // model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        // model.get(ClientConstants.OP_ADDR).add("stack", stackName);
        // model.get(ClientConstants.OP_ADDR).add("transport", "TRANSPORT");
        // model.get("name").set("socket-binding");
        // model.get("value").set(ModelType.UNDEFINED);
        // try {
        // this.applyUpdate(model);
        // } catch (Exception e) {
        // logger.error(e);
        // }

        model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", stackName);
        model.get(ClientConstants.OP_ADDR).add("transport", "UDP");
        model.get("name").set("shared");
        model.get("value").set(false);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }

        setTunnelForJGroups(gosshipRouterAddress, gosshipRouterPort);

        model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", stackName);
        model.get(ClientConstants.OP_ADDR).add("transport", "UDP");
        model.get(ClientConstants.OP_ADDR).add("property", "enable_bundling");
        model.get("value").set(false);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }

    }

    @Override
    public void createCoreBridge(String name, String queueName, String forwardingAddress, int reconnectAttempts, boolean ha,
                                 String discoveryGroupName) {
        createCoreBridge(NAME_OF_MESSAGING_DEFAULT_SERVER, name, queueName, forwardingAddress, reconnectAttempts, ha,
                discoveryGroupName);
    }

    /**
     * Creates new bridge
     *
     * @param name               bridge name
     * @param queueName          source queue
     * @param forwardingAddress  target address
     * @param reconnectAttempts  reconnect attempts for bridge
     * @param ha                 ha
     * @param discoveryGroupName discovery group name
     */
    @Override
    public void createCoreBridge(String serverName, String name, String queueName, String forwardingAddress,
                                 int reconnectAttempts, boolean ha, String discoveryGroupName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("bridge", name);
        model.get("queue-name").set(queueName);
        if (forwardingAddress != null) {
            model.get("forwarding-address").set(forwardingAddress);
        }
        model.get("use-duplicate-detection").set(true);
        model.get("ha").set(ha);
        model.get("failover-on-server-shutdown").set(true);
        model.get("reconnect-attempts").set(reconnectAttempts);
        model.get("discovery-group").set(discoveryGroupName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void removeHttpConnector(String name) {
        removeHttpConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    @Override
    public void removeHttpAcceptor(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("http-acceptor", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void removeHttpAcceptor(String name) {
        removeHttpAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    @Override
    public void removeHttpConnector(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
            model.get(ClientConstants.OP_ADDR).add("http-connector", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void removeConnector(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connector", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    /**
     * Remove connector
     *
     * @param name name of the connector
     */
    @Override
    public void removeConnector(String name) {
        removeConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    public void removeRemoteConnector(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-connector", name);
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
    @Override
    public void removeRemoteConnector(String name) {
        removeRemoteConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    private void removeAcceptor(String serverName, String name) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("acceptor", name);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void removeAcceptor(String name) {
        removeAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    /**
     * Creates remote connector
     *
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        source queue
     */
    @Override
    public void createRemoteConnector(String name, String socketBinding, Map<String, String> params) {
        createRemoteConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, params);
    }

    /**
     * Creates remote connector
     *
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        source queue
     */
    @Override
    public void createHttpConnector(String name, String socketBinding, Map<String, String> params) {
        createHttpConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, params);
    }

    @Override
    public void createHttpConnector(String name, String socketBinding, Map<String, String> params, String endpoint) {
        createHttpConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, params, endpoint);
    }

    @Override
    public void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params) {
        createHttpConnector(serverName, name, socketBinding, params, "http-acceptor");
    }

    /**
     * Creates remote connector
     *
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        source queue
     * @param endpoint      endpoint name
     */
    @Override
    public void createHttpConnector(String serverName, String name, String socketBinding, Map<String, String> params, String endpoint) {

        removeHttpConnector(serverName, name);

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("http-connector", name);
        model.get("socket-binding").set(socketBinding);
        model.get("endpoint").set(endpoint);

        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createConnector(String serverName, String name, String socketBinding, String factoryClass,
                                 Map<String, String> params) {

        removeConnector(name);

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("connector", name);
        model.get("socket-binding").set(socketBinding);
        if (isEmpty(factoryClass)) {
            factoryClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";
        }
        model.get("factory-class").set(factoryClass);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createConnector(String name, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    @Override
    public void createConnector(String name, String socketBinding, String factoryClass, Map<String, String> params) {
        createConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, factoryClass, params);
    }

    @Override
    public void createAcceptor(String name, Map<String, String> params) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    private void createAcceptor(String serverName, String name, String socketBinding, String factoryClass,
                                Map<String, String> params) {

        removeAcceptor(serverName, name);

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("acceptor", name);
        model.get("socket-binding").set(socketBinding);
        if (isEmpty(factoryClass)) {
            factoryClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory";
        }
        model.get("factory-class").set(factoryClass);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createAcceptor(String name, String socketBinding, String factoryClass, Map<String, String> params) {
        createAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, factoryClass, params);
    }

    /**
     * Creates remote connector
     *
     * @param serverName    set name of hornetq server
     * @param name          name of the remote connector
     * @param socketBinding
     * @param params        params
     */
    @Override
    public void createRemoteConnector(String serverName, String name, String socketBinding, Map<String, String> params) {

        removeRemoteConnector(serverName, name);

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-connector", name);
        model.get("socket-binding").set(socketBinding);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createOutBoundSocketBinding(String socketBindingName, String host, int port) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("remote-destination-outbound-socket-binding", socketBindingName);
        model.get("port").set(port);
        model.get("host").set(host);
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
    @Override
    public void createSocketBinding(String socketBindingName, int port) {
        ModelNode model = createModelNode();
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
    @Override
    public void createSocketBinding(String socketBindingName, String defaultInterface, String multicastAddress,
                                    int multicastPort) {

        try {
            removeSocketBinding(socketBindingName);
        } catch (Exception ex) {
            // ignore
        }
        ModelNode model = createModelNode();
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
     * Creates socket binding.
     *
     * @param socketBindingName
     * @param port
     * @param defaultInterface
     * @param multicastAddress
     * @param multicastPort
     */
    @Override
    public void createSocketBinding(String socketBindingName, int port, String defaultInterface, String multicastAddress,
                                    int multicastPort) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("interface").set(defaultInterface);
        model.get("multicast-address").set(multicastAddress);
        model.get("multicast-port").set(multicastPort);
        model.get("port").set(port);

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
    @Override
    public void removeSocketBinding(String socketBindingName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);

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
    @Override
    public void addSocketBinding(String socketBindingName, String multicastAddress, int multicastPort) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("multicast-address").set(multicastAddress);
        model.get("multicast-port").set(multicastPort);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addSocketBinding(String socketBindingName, int port) {
        ModelNode model = createModelNode();
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

    public void createSecurityRealm(String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", name);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addServerIdentity(String realmName, String keyStorePath, String keyStorePass) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", realmName);
        model.get(ClientConstants.OP_ADDR).add("server-identity", "ssl");
        model.get("keystore-path").set(keyStorePath);
        model.get("keystore-password").set(keyStorePass);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", realmName);
        model.get(ClientConstants.OP_ADDR).add("server-identity", "ssl");
        model.get("keystore-provider").set(keyStoreProvider);
        model.get("keystore-password").set(keyStorePass);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addServerIdentityWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePath, String keyStorePass) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", realmName);
        model.get(ClientConstants.OP_ADDR).add("server-identity", "ssl");
        model.get("keystore-provider").set(keyStoreProvider);
        model.get("keystore-password").set(keyStorePass);
        model.get("keystore-path").set(keyStorePath);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addAuthenticationWithKeyStoreProvider(String realmName, String keyStoreProvider, String keyStorePass) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", realmName);
        model.get(ClientConstants.OP_ADDR).add("authentication", "truststore");
        model.get("keystore-provider").set(keyStoreProvider);
        model.get("keystore-password").set(keyStorePass);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addAuthentication(String realmName, String trustStorePath, String keyStorePass) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("core-service", "management");
        model.get(ClientConstants.OP_ADDR).add("security-realm", realmName);
        model.get(ClientConstants.OP_ADDR).add("authentication", "truststore");
        model.get("keystore-path").set(trustStorePath);
        model.get("keystore-password").set(keyStorePass);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHttpsListener(String serverName, String name, String securityRealm, String socketBinding, String verifyClient) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("https-listener", name);
        model.get("security-realm").set(securityRealm);
        model.get("socket-binding").set(socketBinding);
        model.get("verify-client").set(verifyClient);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHttpsListener(String name, String securityRealm, String socketBinding, String verifyClient) {
        addHttpsListener(NAME_OF_UNDERTOW_DEFAULT_SERVER, name, securityRealm, socketBinding, verifyClient);
    }

    @Override
    public void removeHttpsListener(String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER, NAME_OF_UNDERTOW_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("https-listener", name);

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
    @Override
    public void setMulticastAddressOnSocketBinding(String socketBindingName, String multicastAddress) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
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
     * Set multicast address for socket binding
     *
     * @param socketBindingName name of the socket binding
     * @param port              port of the socket binding
     */
    @Override
    public void setMulticastPortOnSocketBinding(String socketBindingName, int port) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("name").set("multicast-port");
        model.get("value").set(port);
        System.out.println(model.toString());
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets compression on connection factory.
     *
     * @param connectionFactoryName name of the connection factory
     * @param value                 true to enable large message compression
     */
    @Override
    public void setCompressionOnConnectionFactory(String connectionFactoryName, boolean value) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("compress-large-messages");
        model.get("value").set(value);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSocketBindingAtributes(String socketBindingName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("socket-binding-group", "standard-sockets");
        model.get(ClientConstants.OP_ADDR).add("socket-binding", socketBindingName);
        model.get("include-runtime").set("true");
        model.get("recursive").set("true");
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").asString().substring(1, result.get("result").asString().length() - 2);
    }

    @Override
    public boolean isActive(String serverName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("active");
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Boolean.valueOf(result.get("result").asString());
    }

    /**
     * Set old(true) or new failover model(false)
     *
     * @param keepOldFailover          false to activate it
     * @param nodeStateRefreshInterval after which time will be node's timestamp updated in database
     */
    @Override
    public void setKeepOldFailoverModel(boolean keepOldFailover, long nodeStateRefreshInterval) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Whether to retyr connection to database
     *
     * @param retryOnConnectionFailure true for retry
     * @param retryInterval            interval in miliseconds
     * @param maxRetry                 how many times to retry before giving up
     */
    @Override
    public void setRetryForDb(boolean retryOnConnectionFailure, long retryInterval, int maxRetry) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Sets TUNNEL protocol for jgroups
     *
     * @param gossipRouterHostname ip address of gosship router
     * @param gossipRouterPort     port of gosship router
     */
    @Override
    public void setTunnelForJGroups(String gossipRouterHostname, int gossipRouterPort) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", "udp");
        model.get(ClientConstants.OP_ADDR).add("protocol", "TUNNEL");
        model.get("type").set("TUNNEL");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, "jgroups");
        model.get(ClientConstants.OP_ADDR).add("stack", "udp");
        model.get(ClientConstants.OP_ADDR).add("protocol", "TUNNEL");
        model.get(ClientConstants.OP_ADDR).add("property", "gossip_router_hosts");
        model.get("value").set(gossipRouterHostname + "[" + gossipRouterPort + "]");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Set database.
     *
     * @param databaseHostname hostname
     * @param databasePort     port
     */
    @Override
    public void setDatabase(String databaseHostname, int databasePort) {
        logger.info("This operation is not supported: " + getMethodName());
    }

    /**
     * Creates in-vm connector
     *
     * @param name     name of the remote connetor
     * @param serverId set server id
     * @param params   params for connector
     */
    @Override
    public void createInVmConnector(String name, int serverId, Map<String, String> params) {
        createInVmConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name, serverId, params);
    }

    private void removeInVmConnector(String name) {
        removeInVmConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    private void removeInVmConnector(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("in-vm-connector", name);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates in-vm connector
     *
     * @param serverName set name of hornetq server
     * @param name       name of the remote connector
     * @param serverId   set server id
     * @param params     params for connector
     */
    @Override
    public void createInVmConnector(String serverName, String name, int serverId, Map<String, String> params) {

        try {
            removeInVmConnector(serverName, name);
        } catch (Exception ex) {
            logger.warn("Removing old connector failed:", ex);
        }
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("in-vm-connector", name);
        model.get("server-id").set(serverId);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
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
     * @param name          name of the remote acceptor
     * @param socketBinding
     * @param params        source queue
     */
    @Override
    public void createRemoteAcceptor(String name, String socketBinding, Map<String, String> params) {
        createRemoteAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name, socketBinding, params);
    }

    /**
     * Creates remote acceptor
     *
     * @param serverName    set name of hornetq server
     * @param name          name of the remote acceptor
     * @param socketBinding
     * @param params        params
     */
    @Override
    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params) {
        try {
            removeRemoteAcceptor(serverName, name);
        } catch (Exception ex) {
            logger.warn("Removing remote acceptor failed: ", ex);
        }
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-acceptor", name);
        model.get("socket-binding").set(socketBinding);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void createRemoteAcceptor(String serverName, String name, String socketBinding, Map<String, String> params, String protocols) {
        try {
            removeRemoteAcceptor(serverName, name);
        } catch (Exception ex) {
            logger.warn("Removing remote acceptor failed: ", ex);
        }
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("remote-acceptor", name);
        model.get("socket-binding").set(socketBinding);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }
        if (protocols != null) {
            model.get("protocols").set(protocols);
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
     * @param name         name of the remote acceptor
     * @param httpListener
     * @param params       source queue
     */
    @Override
    public void createHttpAcceptor(String name, String httpListener, Map<String, String> params) {
        createHttpAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name, httpListener, params);
    }

    /**
     * Creates remote acceptor
     *
     * @param serverName   set name of hornetq server
     * @param name         name of the remote acceptor
     * @param httpListener
     * @param params       params
     */
    @Override
    public void createHttpAcceptor(String serverName, String name, String httpListener, Map<String, String> params) {
        try {
            removeHttpAcceptor(serverName, name);
        } catch (Exception ex) {
            logger.warn("Removing remote acceptor failed: ", ex);
        }
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("http-acceptor", name);

        if (isEmpty(httpListener)) { // put there default
            model.get("http-listener").set("default");
        } else {
            model.get("http-listener").set(httpListener);
        }

        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
            }
        }

        logger.info(model);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setParamsForHttpAcceptor(String serverName, String acceptorName, Map<String, String> params) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("http-acceptor", acceptorName);

        ModelNode paramsModel = new ModelNode();
        for (String key : params.keySet()) {
            paramsModel.add(key, params.get(key));
        }

        model.get("name").set("params");
        model.get("value").set(paramsModel);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setParamsForHttpAcceptor(String acceptorName, Map<String, String> params) {
        setParamsForHttpAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, acceptorName, params);
    }

    @Override
    public void setParamsForHttpConnector(String serverName, String connectorName, Map<String, String> params) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("http-connector", connectorName);

        ModelNode paramsModel = new ModelNode();
        for (String key : params.keySet()) {
            paramsModel.add(key, params.get(key));
        }

        model.get("name").set("params");
        model.get("value").set(paramsModel);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setParamsForHttpConnector(String connectorName, Map<String, String> params) {
        setParamsForHttpConnector(NAME_OF_MESSAGING_DEFAULT_SERVER, connectorName, params);
    }

    @Override
    public void removeRemoteAcceptor(String name) {
        removeRemoteAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name);
    }

    /**
     * Remove remote acceptor
     *
     * @param name name of the remote acceptor
     */
    public void removeRemoteAcceptor(String serverName, String name) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
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
     * @param name     name of the connector
     * @param serverId set server id
     * @param params   params for connector
     */
    @Override
    public void createInVmAcceptor(String name, int serverId, Map<String, String> params) {
        createInVmAcceptor(NAME_OF_MESSAGING_DEFAULT_SERVER, name, serverId, params);
    }

    /**
     * Creates in-vm acceptor
     *
     * @param serverName set name of hornetq server
     * @param name       name of the connector
     * @param serverId   set server id
     * @param params     params for connector
     */
    @Override
    public void createInVmAcceptor(String serverName, String name, int serverId, Map<String, String> params) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("in-vm-acceptor", name);
        model.get("server-id").set(serverId);
        if (params != null) {
            for (String key : params.keySet()) {
                model.get("params").add(key, params.get(key));
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
    @Override
    public void removeRemoteSocketBinding(String name) {
        ModelNode model = createModelNode();
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
    @Override
    public void addRemoteSocketBinding(String name, String host, int port) {
        ModelNode model = createModelNode();
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
     * @param level    like DEBUG, WARN, FINE,...
     */
    @Override
    public void addLoggerCategory(String category, String level) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "logging");
        model.get(ClientConstants.OP_ADDR).add("logger", category);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            logger.error("Operation remove category was not completed.", e);
        }

        model = createModelNode();
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
     * <p>
     * WORKAROUND FOR https://bugzilla.redhat.com/show_bug.cgi?id=947779 TODO remove this when ^ is fixed
     *
     * @param serverName name of the new hornetq server
     */
    @Override
    public void addMessagingSubsystem(String serverName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // /**
    // * Adds new messaging subsystem/new hornetq server to configuration
    // *
    // * WORKAROUND FOR https://bugzilla.redhat.com/show_bug.cgi?id=947779
    // * TODO remove this when ^ is fixed
    // * @param serverName name of the new hornetq server
    // */
    // @Override
    // public void addMessagingSubsystem(String serverName) {
    //
    // String jbossHome = null;
    // if (HornetQTestCase.CONTAINER1_IP.equalsIgnoreCase(hostname)) {
    // jbossHome = HornetQTestCase.getJbossHome(HornetQTestCase.CONTAINER1_NAME);
    // } else if (HornetQTestCase.CONTAINER2_IP.equalsIgnoreCase(hostname)) {
    // jbossHome = HornetQTestCase.getJbossHome(HornetQTestCase.CONTAINER2_NAME);
    // } else if (HornetQTestCase.CONTAINER3_IP.equalsIgnoreCase(hostname)) {
    // jbossHome = HornetQTestCase.getJbossHome(HornetQTestCase.CONTAINER3_NAME);
    // } else if (HornetQTestCase.CONTAINER4_IP.equalsIgnoreCase(hostname)) {
    // jbossHome = HornetQTestCase.getJbossHome(HornetQTestCase.CONTAINER4_NAME);
    // }
    //
    // StringBuilder sb = new StringBuilder();
    // sb.append(jbossHome).append(File.separator).append("standalone").append(File.separator);
    // sb.append("configuration").append(File.separator).append("standalone-full-ha.xml");
    // String configurationFile = sb.toString();
    //
    // try {
    //
    // Document doc = XMLManipulation.getDOMModel(configurationFile);
    //
    // XPath xpathInstance = XPathFactory.newInstance().newXPath();
    // Node node = (Node) xpathInstance.evaluate("//hornetq-server", doc, XPathConstants.NODE);
    // Node parent = node.getParentNode();
    //
    // Element e = doc.createElement(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER);
    // e.setAttribute("name", serverName);
    //
    // parent.appendChild(e);
    // XMLManipulation.saveDOMModel(doc, configurationFile);
    //
    //
    // } catch (Exception e) {
    // logger.error(e.getMessage(), e);
    // }
    // }

    @Override
    public void reload(boolean isAdminOnlyMode) {
        long timeout = 30000;

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("reload");

        if (isAdminOnlyMode) {
            model.get("admin-only").set("true");
        } else {
            model.get("admin-only").set("false");
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get("name").set("server-state");
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                Thread.sleep(1000);
                if (this.applyUpdate(model).get("result").toString().toLowerCase().contains("running")) {
                    logger.warn("we should be running now: " + this.applyUpdate(model).get("result").toString());
                    break;
                } else {
                    logger.warn("not ready now: " + this.applyUpdate(model).get("result").toString());
                }
            } catch (Exception e) {
                logger.warn("server still booting");
            } finally {
                if (System.currentTimeMillis() - startTime > timeout) {
                    throw new RuntimeException("wait for boot timeouted");
                }
            }
        }
    }

    /**
     * @return relatetive path to large mesage journal directory relative to jboss.data.dir
     */
    @Override
    public String getJournalLargeMessageDirectoryPath() {

        ModelNode model = new ModelNode();

        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("path", "large-messages-directory");
        model.get("name").set("path");

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").asString();
    }

    /**
     *
     */
    @Override
    public void reload() {

        reload(false);
    }

    @Override
    public void enableServerDump(long dumpPeriod) {
        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get("name").set("server-dump-interval");
        model.get("value").set(dumpPeriod);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void deploy(Archive archive) throws Exception {

        ServerDeploymentHelper server = new ServerDeploymentHelper(modelControllerClient);
        final InputStream input = archive.as(ZipExporter.class).exportAsInputStream();
        server.deploy(archive.getName(), input);

    }

    public void undeploy(String archiveName) throws Exception {
        ServerDeploymentHelper server = new ServerDeploymentHelper(modelControllerClient);
        server.undeploy(archiveName);
    }

/**
 * Exception
 */
private class JMSAdminOperationException extends Exception {

    public JMSAdminOperationException(final String msg) {
        super(msg);
    }
}

    /**
     * Set whether environment property replacement is avaible or not.
     *
     * @param propertyName "annotation-property-replacement", "ear-subdeployments-isolated",
     *                     "jboss-descriptor-property-replacement", "spec-descriptor-property-replacement"
     * @param isEnabled    whether to enable it or not
     */
    @Override
    public void setPropertyReplacement(String propertyName, boolean isEnabled) {

        final ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "ee");
        model.get("name").set(propertyName);
        model.get("value").set(isEnabled);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            // throw new RuntimeException(e);
            logger.error("Set property replacement could not be set.", e);
        }

    }

    @Override
    public void addSubsystem(String subsystemName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", subsystemName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addSecurityProvider(String providerName, String providerType, Map<String, String> attributes) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "security-providers");
        model.get(ClientConstants.OP_ADDR).add(providerName, providerType);
        // attributes=[(\"nssLibraryDirectory\"=>\"/usr/lib\"),(\"nssSecmodDirectory\"=>\"${WORKSPACE}/fipsdb\"),(\"nssModule\"=>\"fips\")]
        for (String key : attributes.keySet()) {
            model.get("attributes").add(key, attributes.get(key));
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getNumberOfNodesInCluster() {
        return getNumberOfNodesInCluster("my-cluster");
    }

    /**
     * Return number of nodes which this node sees in cluster.
     *
     * @return number of nodes in cluster
     */
    public int getNumberOfNodesInCluster(String clusterName) {
        // /subsystem=messaging/hornetq-server=default/cluster-connection=my-cluster:get-nodes

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("get-nodes");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("cluster-connection", clusterName);

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result.get("result").asList().size();

    }

    @Override
    public int getNumberOfDurableSubscriptionsOnTopicForClient(String clientId) {
        // /subsystem=messaging/hornetq-server=default/cluster-connection=my-cluster:get-nodes
        int counter = 0;
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<ModelNode> results = result.get("result").get("runtime-queue").asList();
        logger.info(results.toString());
        for (ModelNode node : results) {
            if (node.toString().contains(clientId)) {
                counter++;
            }
        }

        return counter;
    }

    @Override
    public int getNumberOfDurableSubscriptionsOnTopic(String topicName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("jms-topic", topicName);
        model.get("name").set("durable-subscription-count");

        ModelNode result;

        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").asInt();
    }

    @Override
    public int getNumberOfNonDurableSubscriptionsOnTopic(String topicName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("jms-topic", topicName);
        model.get("name").set("non-durable-subscription-count");

        ModelNode result;

        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").asInt();
    }

    @Override
    public int getNumberOfTempQueues() {
        int counter = 0;
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("read-resource");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<ModelNode> results = result.get("result").get("runtime-queue").asList();
        logger.info(results.toString());
        for (ModelNode node : results) {
            if (node.toString().contains("tempqueue")) {
                counter++;
            }
        }

        return counter;

    }

    public String getPagingDirectoryPath() {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("resolve-path");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("path", "paging-directory");
        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").asString();
    }

    @Override
    public boolean areThereUnfinishedArjunaTransactions() {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("probe");
        model.get(ClientConstants.OP_ADDR).add("subsystem", "transactions");
        model.get(ClientConstants.OP_ADDR).add("log-store", "log-store");

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ModelNode readTransactionModel = createModelNode();
        readTransactionModel.get(ClientConstants.OP).set("read-resource");
        readTransactionModel.get(ClientConstants.OP_ADDR).add("subsystem", "transactions");
        readTransactionModel.get(ClientConstants.OP_ADDR).add("log-store", "log-store");

        ModelNode result;
        try {
            result = this.applyUpdate(readTransactionModel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result.get("result").get("transactions").isDefined();

    }

    @Override
    public boolean closeClientsByDestinationAddress(String address) {
        ModelNode closeOperation = createModelNode();
        closeOperation.get(ClientConstants.OP).set("close-consumer-connections-for-address");
        closeOperation.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        closeOperation.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        closeOperation.get("address-match").set(address);

        ModelNode result;
        try {
            result = this.applyUpdate(closeOperation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result.asBoolean();
    }

    @Override
    public boolean closeClientsByUserName(String username) {
        ModelNode closeOperation = createModelNode();
        closeOperation.get(ClientConstants.OP).set("close-connections-for-user");
        closeOperation.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        closeOperation.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        closeOperation.get("user").set(username);

        ModelNode result;
        try {
            result = this.applyUpdate(closeOperation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result.asBoolean();
    }

    @Override
    public List<String> getJNDIEntriesForQueue(String destinationCoreName) {
        return getJNDIEntries(DESTINATION_TYPE_QUEUE, destinationCoreName);
    }

    public List<String> getJNDIEntries(String destinationType, String destinationCoreName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add(destinationType, destinationCoreName);
        model.get("name").set("entries");

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<String> entries = new ArrayList<String>();
        for (ModelNode m : result.get("result").asList()) {
            entries.add(m.toString().replaceAll("\"", ""));
        }
        return entries;
    }

    @Override
    public List<String> getJNDIEntriesForTopic(String destinationCoreName) {
        return getJNDIEntries(DESTINATION_TYPE_TOPIC, destinationCoreName);
    }

    @Override
    public void setDiscoveryGroupOnConnectionFactory(String connectionFactoryName, String discoveryGroupName) {

        ModelNode composite = new ModelNode();
        composite.get(ClientConstants.OP).set("composite");
        composite.get(ClientConstants.OP_ADDR).setEmptyList();
        composite.get(ClientConstants.OPERATION_HEADERS, ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        ModelNode undefineConnector = new ModelNode();
        undefineConnector.get(ClientConstants.OP).set(ClientConstants.UNDEFINE_ATTRIBUTE_OPERATION);
        undefineConnector.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        undefineConnector.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        undefineConnector.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        undefineConnector.get("name").set("connectors");

        ModelNode setDiscoveryGroup = new ModelNode();
        setDiscoveryGroup.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        setDiscoveryGroup.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        setDiscoveryGroup.get("name").set("discovery-group");
        setDiscoveryGroup.get("value").set(discoveryGroupName);
        composite.get(ClientConstants.STEPS).add(undefineConnector);
        composite.get(ClientConstants.STEPS).add(setDiscoveryGroup);

        try {
            this.applyUpdate(composite);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getNumberOfActiveClientConnections() {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("list-connection-ids");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result.get("result").asList().size();
    }

    public int getNumberOfConsumersOnQueue(String queue) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("list-consumers-as-json");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("jms-queue", queue);

        ModelNode result;
        try {
            result = this.applyUpdate(model);
            String res = result.get("result").asString();
            JSONArray array = new JSONArray(res);
            return array.length();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public int getNumberOfConsumersOnTopic(String clientId, String subscriptionName) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("list-consumers-as-json");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("runtime-queue", clientId + "." + subscriptionName);

        ModelNode result;
        try {
            result = this.applyUpdate(model);
            String res = result.get("result").asString();
            JSONArray array = new JSONArray(res);
            return array.length();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void removeMessageFromQueue(String queueName, String jmsMessageID) {
        final ModelNode removeMessagesFromQueue = new ModelNode();
        removeMessagesFromQueue.get(ClientConstants.OP).set("remove-message");
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER,
                NAME_OF_MESSAGING_DEFAULT_SERVER);
        removeMessagesFromQueue.get(ClientConstants.OP_ADDR).add(DESTINATION_TYPE_QUEUE, queueName);
        removeMessagesFromQueue.get("message-id").set(jmsMessageID);

        try {
            this.applyUpdate(removeMessagesFromQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forceFailover() {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("force-failover");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setTransactionTimeout(long hornetqTransactionTimeout) {
        setTransactionTimeout(NAME_OF_MESSAGING_DEFAULT_SERVER, hornetqTransactionTimeout);
    }

    @Override
    public void setTransactionTimeout(String serverName, long hornetqTransactionTimeout) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get("name").set("transaction-timeout");
        model.get("value").set(hornetqTransactionTimeout);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // HA CONFIGURATION - START

    @Override
    public void removeHAPolicy(String serverName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);

        ModelNode result;
        try {
            result = this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String haPolicy = null;
        try {
            haPolicy = result.get("result").get("ha-policy").keys().iterator().next();
            logger.info(haPolicy);
        } catch (IllegalArgumentException ex) {
            // no ha-policy
        }

        if (!isEmpty(haPolicy)) {
            model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.REMOVE_OPERATION);
            model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
            model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
            model.get(ClientConstants.OP_ADDR).add("ha-policy", haPolicy);

            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public void addHAPolicySharedStoreMaster(long failbackDelay, boolean failoverOnServerShutdown) {
        addHAPolicySharedStoreMaster(NAME_OF_MESSAGING_DEFAULT_SERVER, failbackDelay, failoverOnServerShutdown);
    }

    @Override
    public void addHAPolicySharedStoreMaster(String serverName, long failbackDelay, boolean failoverOnServerShutdown) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "shared-store-master");
        model.get("failback-delay").set(failbackDelay);
        model.get("failover-on-server-shutdown").set(failoverOnServerShutdown);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHAPolicySharedStoreSlave(boolean allowFailback, long failbackDelay, boolean failoverOnServerShutdown,
                                            boolean restartBackup, boolean scaleDown, String scaleDownClusterName, List<String> scaleDownConnectors,
                                            String scaleDownDiscoveryGroup, String scaleDownGroupName) {
        addHAPolicySharedStoreSlave(NAME_OF_MESSAGING_DEFAULT_SERVER, allowFailback, failbackDelay, failoverOnServerShutdown,
                restartBackup, scaleDown, scaleDownClusterName, scaleDownConnectors, scaleDownDiscoveryGroup,
                scaleDownGroupName);
    }

    @Override
    public void addHAPolicySharedStoreSlave(String serverName, boolean allowFailback, long failbackDelay,
                                            boolean failoverOnServerShutdown, boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
                                            List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "shared-store-slave");
        model.get("allow-failback").set(allowFailback);
        model.get("failback-delay").set(failbackDelay);
        model.get("failover-on-server-shutdown").set(failoverOnServerShutdown);
        model.get("restart-backup").set(restartBackup);
        //model.get("scale-down").set(scaleDown);
        if (!isEmpty(scaleDownClusterName)) {
            model.get("scale-down-cluster-name").set(scaleDownClusterName);
        }
        if (!isEmpty(scaleDownConnectors)) {
            model.get("scale-down-connectors").set(createModelNodeForList(scaleDownConnectors));
        }
        if (!isEmpty(scaleDownDiscoveryGroup)) {
            model.get("scale-down-discovery-group").set(scaleDownDiscoveryGroup);
        }
        if (!isEmpty(scaleDownGroupName)) {
            model.get("scale-down-group-name").set(scaleDownGroupName);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHAPolicyReplicationMaster(boolean checkForLiveServer, String clusterName, String groupName) {
        addHAPolicyReplicationMaster(NAME_OF_MESSAGING_DEFAULT_SERVER, checkForLiveServer, clusterName, groupName);
    }

    @Override
    public void addHAPolicyReplicationMaster(String serverName, boolean checkForLiveServer, String clusterName, String groupName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "replication-master");
        model.get("check-for-live-server").set(checkForLiveServer);
        model.get("cluster-name").set(clusterName);
        model.get("group-name").set(groupName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Configures Artemis as replication slave
     * @param allowFailback whether failback is allowed
     * @param clusterName name of cluster for master-slave pair
     * @param failbackDelay time to wait before failback can occur
     * @param groupName name of replication master-slave pair, bind slave with master
     * @param maxSavedReplicatedJournalSize number of replicated journals=failbacks (default 2) before slave just stops
     * @param restartBackup if backup should be restarted after failback
     * @param scaleDown whether server should scale down for clean shutdown
     * @param scaleDownClusterName name cluster connection to use for scaling down
     */
    @Override
    public void addHAPolicyReplicationSlave(boolean allowFailback, String clusterName, long failbackDelay, String groupName,
                                            int maxSavedReplicatedJournalSize, boolean restartBackup, boolean scaleDown, String scaleDownClusterName,
                                            List<String> scaleDownConnectors, String scaleDownDiscoveryGroup, String scaleDownGroupName) {

        addHAPolicyReplicationSlave(NAME_OF_MESSAGING_DEFAULT_SERVER, allowFailback, clusterName, failbackDelay, groupName,
                maxSavedReplicatedJournalSize, restartBackup, scaleDown, scaleDownClusterName, scaleDownConnectors,
                scaleDownDiscoveryGroup, scaleDownGroupName);
    }

    /**
     * Configures Artemis as replication slave
     * @param serverName name of Artemis server inside EAP 7 to configure ("default" is default)
     * @param allowFailback whether failback is allowed
     * @param clusterName name of cluster for master-slave pair
     * @param failbackDelay time to wait before failback can occur
     * @param groupName name of replication master-slave pair, bind slave with master
     * @param maxSavedReplicatedJournalSize number of replicated journals=failbacks (default 2) before slave just stops
     * @param restartBackup if backup should be restarted after failback
     * @param scaleDown whether server should scale down for clean shutdown
     * @param scaleDownClusterName name cluster connection to use for scaling down
     */
    @Override
    public void addHAPolicyReplicationSlave(String serverName, boolean allowFailback, String clusterName, long failbackDelay,
                                            String groupName, int maxSavedReplicatedJournalSize, boolean restartBackup, boolean scaleDown,
                                            String scaleDownClusterName, List<String> scaleDownConnectors, String scaleDownDiscoveryGroup,
                                            String scaleDownGroupName) {

        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "replication-slave");
        model.get("allow-failback").set(allowFailback);
        model.get("failback-delay").set(failbackDelay);
        model.get("cluster-name").set(clusterName);
        model.get("group-name").set(groupName);
        model.get("group-name").set(groupName);
        model.get("max-saved-replicated-journal-size").set(maxSavedReplicatedJournalSize);
        model.get("restart-backup").set(restartBackup);
        model.get("scale-down").set(scaleDown);
        if (!isEmpty(scaleDownClusterName)) {
            model.get("scale-down-cluster-name").set(scaleDownClusterName);
        }
        if (!isEmpty(scaleDownConnectors)) {
            model.get("scale-down-connectors").set(createModelNodeForList(scaleDownConnectors));
        }
        if (!isEmpty(scaleDownDiscoveryGroup)) {
            model.get("scale-down-discovery-group").set(scaleDownDiscoveryGroup);
        }
        if (!isEmpty(scaleDownGroupName)) {
            model.get("scale-down-group-name").set(scaleDownGroupName);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHAPolicyColocatedSharedStore() {
        addHAPolicyColocatedSharedStore("default", 1000, -1, 5000, 1, true, true);

    }

    @Override
    public void addHAPolicyColocatedSharedStore(String serverName, int backupPortOffest, int backupRequestRetries,
                                                int backupRequestRetryInterval, int maxBackups, boolean requestBackup, boolean failoverOnServerShutdown) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "shared-store-colocated");
        model.get("backup-port-offset").set(backupPortOffest);
        model.get("backup-request-retries").set(backupRequestRetries);
        model.get("backup-request-retry-interval").set(backupRequestRetryInterval);
        model.get("max-backups").set(maxBackups);
        model.get("request-backup").set(requestBackup);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setFailoverOnServerShutdownOnColocatedHAPolicy(serverName, "shared-store-colocated", "master", failoverOnServerShutdown);
        setFailoverOnServerShutdownOnColocatedHAPolicy(serverName, "shared-store-colocated", "slave", failoverOnServerShutdown);
    }

    protected void setFailoverOnServerShutdownOnColocatedHAPolicy(String serverName, String haPolicy, String nodeType, boolean value) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", haPolicy);
        model.get(ClientConstants.OP_ADDR).add("configuration", nodeType);
        model.get("failover-on-server-shutdown").set(value);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHAPolicyCollocatedReplicated() {
        addHAPolicyCollocatedReplicated("default", 1000, -1, 5000, 1, true);

    }

    @Override
    public void addHAPolicyCollocatedReplicated(String serverName, int backupPortOffest, int backupRequestRetries,
                                                int backupRequestRetryInterval, int maxBackups, boolean requestBackup, String... excludedConnectors) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "replication-colocated");
        model.get("backup-port-offset").set(backupPortOffest);
        model.get("backup-request-retries").set(backupRequestRetries);
        model.get("backup-request-retry-interval").set(backupRequestRetryInterval);
        model.get("max-backups").set(maxBackups);

        model.get("request-backup").set(requestBackup);
        if (!isEmpty(excludedConnectors)) {
            List<String> list = new ArrayList<String>();
            for (String excludedConnector : excludedConnectors) {
                list.add(excludedConnector);
            }
            model.get("excluded-connectors").set(createModelNodeForList(list));
        }
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setFailoverOnServerShutdownOnColocatedHAPolicy(serverName, "replication-colocated", "master", true);
        setFailoverOnServerShutdownOnColocatedHAPolicy(serverName, "replication-colocated", "slave", true);
        model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.WRITE_ATTRIBUTE_OPERATION);
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, serverName);
        model.get(ClientConstants.OP_ADDR).add("ha-policy", "replication-colocated");
        model.get(ClientConstants.OP_ADDR).add("configuration", "master");
        model.get("name").set("check-for-live-server");
        model.get("value").set(true);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    @Override
    public void createNewResourceAdapter(String name, String cfName, String user, String password,
                                         List<String> destinationNames, String hostUrl) {

        HashMap<String, String> props = new HashMap<String, String>();
        props.put("ServerUrl", hostUrl);
        props.put("UserName", user);
        props.put("Password", password);
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
        model.get(ClientConstants.OP_ADDR).add("resource-adapter", name);
        model.get("archive").set("artemis-ra-1.1.0.jar");
        model.get("transaction-support").set("XATransaction");
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        createConnectionDefinitions(name, cfName, user, password, hostUrl, props);
        for (String key : props.keySet()) {
            model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.ADD);
            model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
            model.get(ClientConstants.OP_ADDR).add("resource-adapter", name);
            model.get(ClientConstants.OP_ADDR).add("config-properties", key);
            model.get("value").set(props.get(key));
            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        createAdminObjects(name, destinationNames);

    }

    @Override
    public void createUndertowReverseProxyHandler(String name){
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("configuration", "handler");
        model.get(ClientConstants.OP_ADDR).add("reverse-proxy", name);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addHostToUndertowReverseProxyHandler(String handlerName, String host, String outboundSocketBinding, String scheme, String intanceId, String path, String securityRealm){
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("configuration", "handler");
        model.get(ClientConstants.OP_ADDR).add("reverse-proxy", handlerName);
        model.get(ClientConstants.OP_ADDR).add("host", host);

        model.get("outbound-socket-binding").set(outboundSocketBinding);
        model.get("scheme").set(scheme);
        model.get("intance-id").set(intanceId);
        model.get("path").set(path);
        if(securityRealm != null){
            model.get("security-realm").set(securityRealm);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addFilterToUndertowServerHost(String filterRef){
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER, NAME_OF_UNDERTOW_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("host", "default-host");
        model.get(ClientConstants.OP_ADDR).add("filter-ref", filterRef);


        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addLocationToUndertowServerHost(String location, String handler){
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER, NAME_OF_UNDERTOW_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("host", "default-host");
        model.get(ClientConstants.OP_ADDR).add("location", location);

        model.get("handler").set(handler);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeLocationFromUndertowServerHost(String location){
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_UNDERTOW_SERVER, NAME_OF_UNDERTOW_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("host", "default-host");
        model.get(ClientConstants.OP_ADDR).add("location", location);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setModClusterConnector(String connectorName) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MODCLUSTER_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("mod-cluster-config", "configuration");

        model.get("name").set("connector");
        model.get("value").set(connectorName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setModClusterAdvertiseKey(String key) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_MODCLUSTER_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("mod-cluster-config", "configuration");

        model.get("name").set("advertise-security-key");
        model.get("value").set(key);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addModClusterFilterToUndertow(String filterName, String managementSocketBinding, String advertiseSocketBinding
            , String advertiseKey, String securityRealm) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add("configuration", "filter");
        model.get(ClientConstants.OP_ADDR).add("mod-cluster", filterName);

        model.get("management-socket-binding").set(managementSocketBinding);
        model.get("advertise-socket-binding").set(advertiseSocketBinding);
        model.get("security-key").set(advertiseKey);

        if (securityRealm != null) {
            model.get("security-realm").set(securityRealm);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setRebalanceConnectionsOnPooledConnectionFactory(String pooledConnectionFactoryName, boolean rebalanceConnections) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        model.get("name").set("rebalance-connections");
        model.get("value").set(true);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setThreadPoolMaxSizeOnPooledConnectionFactory(String pooledConnectionFactoryName, int threadPoolMaxSize) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        model.get("name").set("thread-pool-max-size");
        model.get("value").set(threadPoolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setScheduledThreadPoolMaxSizeOnPooledConnectionFactory(String pooledConnectionFactoryName, int scheduledThreadPoolMaxSize) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        model.get("name").set("scheduled-thread-pool-max-size");
        model.get("value").set(scheduledThreadPoolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setThreadPoolMaxSizeOnConnectionFactory(String connectionFactoryName, int threadPoolMaxSize) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("thread-pool-max-size");
        model.get("value").set(threadPoolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setScheduledThreadPoolMaxSizeOnConnectionFactory(String connectionFactoryName, int scheduledThreadPoolMaxSize) {
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("scheduled-thread-pool-max-size");
        model.get("value").set(scheduledThreadPoolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setUseGlobalPoolsOnPooledConnectionFactory(String pooledConnectionFactoryName, boolean useGlobalPools){
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("pooled-connection-factory", pooledConnectionFactoryName);
        model.get("name").set("use-global-pools");
        model.get("value").set(useGlobalPools);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setUseGlobalPoolsOnConnectionFactory(String connectionFactoryName, boolean useGlobalPools){
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get(ClientConstants.OP_ADDR).add(NAME_OF_ATTRIBUTE_FOR_MESSAGING_SERVER, NAME_OF_MESSAGING_DEFAULT_SERVER);
        model.get(ClientConstants.OP_ADDR).add("connection-factory", connectionFactoryName);
        model.get("name").set("use-global-pools");
        model.get("value").set(useGlobalPools);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalClientThreadPoolMaxSize(int poolMaxSize){
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get("name").set("global-client-thread-pool-max-size");
        model.get("value").set(poolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalClientScheduledThreadPoolMaxSize(int poolMaxSize){
        final ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add("subsystem", NAME_OF_MESSAGING_SUBSYSTEM);
        model.get("name").set("global-client-scheduled-thread-pool-max-size");
        model.get("value").set(poolMaxSize);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void setUndertowInstanceId(String id) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set("write-attribute");
        model.get(ClientConstants.OP_ADDR).add(ClientConstants.SUBSYSTEM, NAME_OF_UNDERTOW_SUBSYSTEM);

        model.get("name").set("instance-id");
        model.get("value").set(id);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createConnectionDefinitions(String raName, String cfName, String user, String password, String hostUrl,
                                             HashMap<String, String> props) {
        ModelNode model = createModelNode();
        model.get(ClientConstants.OP).set(ClientConstants.ADD);
        model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
        model.get(ClientConstants.OP_ADDR).add("resource-adapter", raName);
        model.get(ClientConstants.OP_ADDR).add("connection-definitions", cfName);
        model.get("class-name").set("org.apache.activemq.artemis.ra.ActiveMQRAManagedConnectionFactory");
        model.get("jndi-name").set("java:/jms/" + cfName);
        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (String key : props.keySet()) {
            model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.ADD);
            model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
            model.get(ClientConstants.OP_ADDR).add("resource-adapter", raName);
            model.get(ClientConstants.OP_ADDR).add("connection-definitions", cfName);
            model.get(ClientConstants.OP_ADDR).add("config-properties", key);
            model.get("config-properties").add(key, props.get(key));
            model.get("value").set(props.get(key));
            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void createAdminObjects(String raName, List<String> destinationNames) {
        for (String destName : destinationNames) {
            ModelNode model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.ADD);
            model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
            model.get(ClientConstants.OP_ADDR).add("resource-adapter", raName);
            model.get(ClientConstants.OP_ADDR).add("admin-objects", destName);
            model.get("class-name").set("rg.apache.activemq.artemis.jms.client.ActiveMQQueue");
            model.get("jndi-name").set("java:/jms/queue/" + destName);
            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            model = createModelNode();
            model.get(ClientConstants.OP).set(ClientConstants.ADD);
            model.get(ClientConstants.OP_ADDR).add("subsystem", "resource-adapters");
            model.get(ClientConstants.OP_ADDR).add("resource-adapter", raName);
            model.get(ClientConstants.OP_ADDR).add("admin-objects", destName);
            model.get(ClientConstants.OP_ADDR).add("config-properties", "PhysicalName");
            model.get("value").set(destName);
            try {
                this.applyUpdate(model);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

    }

    // HA CONFIGURATION - END

    public static void main(String[] args) {
        ActiveMQAdminOperationsEAP7 jmsAdminOperations = new ActiveMQAdminOperationsEAP7();
        try {
            jmsAdminOperations.setHostname("127.0.0.1");
            jmsAdminOperations.setPort(9999);
            jmsAdminOperations.connect();

//            LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
//            Properties tcpPingProperties = new Properties();
//            tcpPingProperties.put("initial_hosts", "127.0.0.1[7600],127.0.0.1[8600]");
//            tcpPingProperties.put("port_range", "10");
//            tcpPingProperties.put("timeout", "3000");
//            tcpPingProperties.put("num_initial_members", "2");
//            protocols.put("TCPPING", tcpPingProperties);
//            protocols.put("MERGE2", null);
//            protocols.put("FD_SOCK", null);
//            protocols.put("FD", null);
//            protocols.put("VERIFY_SUSPECT", null);
//            protocols.put("pbcast.NAKACK", null);
//            protocols.put("UNICAST2", null);
//            protocols.put("pbcast.STABLE", null);
//            protocols.put("pbcast.GMS", null);
//            protocols.put("UFC", null);
//            protocols.put("MFC", null);
//            protocols.put("FRAG2", null);
//            protocols.put("RSVP", null);
//
//            Properties transportProperties = new Properties();
//            transportProperties.put("socket-binding", "jgroups-tcp");
//            transportProperties.put("type", "TCP");
//            jmsAdminOperations.addJGroupsStack("tcp2", protocols, transportProperties);


//            jmsAdminOperations.setClusterConnections("my-cluster", "jms", "dg-group1", false, 1, 1000, true, "http-connector");

//            jmsAdminOperations.addDivert("div", "jms.queue.testQueue0", "jms.queue.DLQ", true, null, null, null);
//            System.out.println(jmsAdminOperations.isActive("default"));
//            jmsAdminOperations.reloadServer();
            // jmsAdminOperations.setPersistenceEnabled(true);
            // jmsAdminOperations.removeAddressSettings("#");
            // jmsAdminOperations.addAddressSettings("#", "PAGE", 512 * 1024, 0, 0, 50 * 1024);
            // jmsAdminOperations.removeClusteringGroup("my-cluster");
            // jmsAdminOperations.removeBroadcastGroup("bg-group1");
            // jmsAdminOperations.removeDiscoveryGroup("dg-group1");
            // jmsAdminOperations.setNodeIdentifier(1234567);

            // Map<String, String> params = new HashMap<String, String>();
            // params.put("java.naming.provider.url", "jnp://localhost:5599");
            // params.put("java.naming.factory.url.pkgs", "org.jnp.interfaces");
            // params.put("java.naming.factory.initial", "org.jboss.legacy.jnp.factory.WatchfulContextFactory");
            // jmsAdminOperations.addExternalContext("java:global/client-context2", "javax.naming.InitialContext",
            // "org.jboss.legacy.naming.spi", "external-context", params);
            // System.out.println(jmsAdminOperations.getNumberOfActiveClientConnections());
            // jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);

            // String jmsServerBindingAddress = "192.168.40.1";
            // String inVmConnectorName = "in-vm";
            // String remoteConnectorName = "netty-remote";
            // String messagingGroupSocketBindingName = "messaging-group";
            // String inVmHornetRaName = "local-hornetq-ra";
//            String bridgeName = "myBridge";
//            String sourceConnectionFactory = "java:/ConnectionFactory";
//            String sourceDestination = "jms/queue/InQueue";
//
//            String targetConnectionFactory = "jms/RemoteConnectionFactory";
//            String targetDestination = "jms/queue/OutQueue";
//            Map<String, String> targetContext = new HashMap<String, String>();
//            targetContext.put("java.naming.factory.initial", Constants.INITIAL_CONTEXT_FACTORY_EAP7);
//            targetContext.put("java.naming.provider.url", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + "127.0.0.1:10080");
//
//            Map<String, String> sourceContext = new HashMap<String, String>();
//            sourceContext.put("java.naming.factory.initial", Constants.INITIAL_CONTEXT_FACTORY_EAP7);
//            sourceContext.put("java.naming.provider.url", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + "127.0.0.1:8080");
//            // Map<String,String> sourceContext = null;
//            String qualityOfService = "ONCE_AND_ONLY_ONCE";
//
//            jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, sourceContext,
//                    targetConnectionFactory, targetDestination, targetContext, qualityOfService, 1000, -1, 10, 100, true);

            // System.out.println(jmsAdminOperations.getJournalLargeMessageDirectoryPath());

            // now reconfigure hornetq-ra which is used for inbound to connect to remote server
            // jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServerBindingAddress, 5445);
            // jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
            // List<String> list = new ArrayList<String>();
            // list.add("http-connector");
            // jmsAdminOperations.setConnectorOnPooledConnectionFactory("activemq-ra", list);

            // jmsAdminOperations.createHttpAcceptor("myacceptor", null, null);
            // jmsAdminOperations.removeHAPolicy("default");
            // jmsAdminOperations.addHAPolicySharedStoreMaster(5000, true);
            // jmsAdminOperations.removeHAPolicy("default");
            // jmsAdminOperations.addHAPolicySharedStoreSlave(true, 5000, true, true, false, null, null, null, null);
            // jmsAdminOperations.removeHAPolicy("default");
            // jmsAdminOperations.addHAPolicyReplicationMaster(true, "my-cluster", "my-group");
            // jmsAdminOperations.removeHAPolicy("default");
            // jmsAdminOperations.addHAPolicyReplicationSlave(true, "my-cluster", 3000, "my-group", 3, true, false, null, null,
            // null, null);

            // jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
            // jmsAdminOperations.setJndiNameForPooledConnectionFactory("hornetq-ra", "java:/remoteJmsXA");
            // jmsAdminOperations.reload();
            //
            // // create new in-vm pooled connection factory and configure it as default for inbound communication
            // jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/JmsXA", inVmConnectorName);
            // eap6AdmOps.createSocketBinding("messaging2", 5445);

            // String pathToCertificates = "/home/mnovak/tmp/tools_for_patches/tmp";
            //
            // Map<String, String> props = new HashMap<String, String>();
            // props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
            // props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME,
            // pathToCertificates.concat(File.separator).concat("server.truststore"));
            // props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "123456");
            // props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME,
            // pathToCertificates.concat(File.separator).concat("server.keystore"));
            // props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "123456");
            // props.put("need-client-auth", "true");
            // eap6AdmOps.createRemoteAcceptor("netty2", "messaging2", props);
            //
            //
            // Map<String, String> props2 = new HashMap<String, String>();
            // props2.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
            // props2.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME,
            // pathToCertificates.concat(File.separator).concat("client.truststore"));
            // props2.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "123456");
            // props2.put(TransportConstants.KEYSTORE_PATH_PROP_NAME,
            // pathToCertificates.concat(File.separator).concat("client.keystore"));
            // props2.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "123456");
            // //
            // jmsAdminOperations.createRemoteConnector("netty2", "messaging2", props2);

            jmsAdminOperations.close();
        } finally {
            jmsAdminOperations.close();
        }
    }

private static class PrefixEntry {
    private final String key;
    private final String value;

    private PrefixEntry(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
}
