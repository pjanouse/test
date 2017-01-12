package org.jboss.qa.artemis.test.threads;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.QueueClientsAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueue0;
import org.jboss.qa.hornetq.constants.Constants;
import category.Functional;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DebugTools;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.ArtemisClientThreadPoolMeasurement;
import org.jboss.qa.resourcemonitor.MeasurementObserver;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Testing of client thread pool implementation. Client thread pool is tested when used inside EAP server, and also
 * when used in client. Thread pools need to have correct size. Thread must time out after 60sec of idling.
 * <p>
 * Created by mstyk on 11/8/16.
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class ClientThreadPoolTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ClientThreadPoolTestCase.class);

    private final int numberOfMessagesPerProducer = 100;
    private final int numberOfProducers = 10;
    private final int numberOfConsumers = 10;
    private final int mdbMaxSession = 10;

    private Archive mdb1 = getMdb();

    private String inQueueName = "InQueue0";
    private String inQueueJndiName = "jms/queue/" + inQueueName;

    private String outQueueName = "OutQueue0";
    private String outQueueJndiName = "jms/queue/" + outQueueName;

    public static final String THREAD_POOL_MAX_SIZE_PROPERTY_KEY = "activemq.artemis.client.global.thread.pool.max.size";
    public static final String SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY = "activemq.artemis.client.global.scheduled.thread.pool.core.size";

    //max size of client thread pool on server
    private static final int containerMaxClientPoolSize = 8;

    //max size of scheduled client thread pool on server, e.g. core threads
    private static final int containerMaxScheduledClientPoolSize = 20;

    //max size of client thread pool of external clients
    private static final int clientMaxClientPoolSize = 7;

    //max size of scheduled client thread pool on client, e.g. core threads
    private static final int clientMaxScheduledClientPoolSize = 6;

    /**
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first.
     * Configure properties activemq.artemis.client.global.thread.pool.max.size
     * and activemq.artemis.client.global.scheduled.thread.pool.core.size for
     * both client and server.
     * Configure HornetQ RA on second sever to connect to first server. Send
     * messages to InQueue. Deploy MDB to second server which reads messages
     * from InQueue and sends them to OutQueue. Read messages from OutQueue.
     * Client and server will use artemis global thread pools (use-global-thread
     * -pools = true). Check that client pool size is respected, and threads
     * time out after 60seconds.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>configure properties activemq.artemis.client.global.thread.pool.max.size
     * and activemq.artemis.client.global.scheduled.thread.pool.core.size for
     * both client and server.</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * <li>check that client pool size is respected, and threads time out after 60seconds.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>receiver consumes all messages</li>
     * <li>thread pools have correct maximum size</li>
     * <li>threads timeout after 60seconds</li>
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGlobalPoolsConfiguredBySystemProperty() throws Exception {
        testInternal(TestType.GLOBAL_SYSTEM_PROP);
    }


    /**
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first.
     * Configure properties activemq.artemis.client.global.thread.pool.max.size
     * and activemq.artemis.client.global.scheduled.thread.pool.core.size for
     * both client. Configure global thread pools size in artemis subsystem.
     * Configure HornetQ RA on second sever to connect to first server. Send
     * messages to InQueue. Deploy MDB to second server which reads messages
     * from InQueue and sends them to OutQueue. Read messages from OutQueue.
     * Client and server will use artemis global thread pools (use-global-thread
     * -pools = true). Check that client pool size is respected, and threads
     * time out after 60seconds.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>configure properties activemq.artemis.client.global.thread.pool.max.size
     * and activemq.artemis.client.global.scheduled.thread.pool.core.size for both client</li>
     * <li>Configure global thread pools size in artemis subsystem</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * <li>check that client pool size is respected, and threads time out after 60seconds.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>receiver consumes all messages</li>
     * <li>thread pools have correct maximum size</li>
     * <li>threads timeout after 60seconds</li>
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testGlobalPoolsConfiguredByAttributeInMessagingSubsystem() throws Exception {
        testInternal(TestType.GLOBAL_EAP_ATTRIBUTE);
    }


    /**
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first. Configure HornetQ
     * RA on second sever to connect to first server. Client and server will use artemis global
     * thread pools (use-global-thread -pools = false). Configure connection factories with
     * adjusted values of scheduled-thread-pool-max-size and thread-pool-max-size. Deploy MDB
     * to second server which reads messages from InQueue and sends them to OutQueue. Now second
     * EAP acts as client and Artemis is using client thread pool. Send messages to InQueue. Read
     * messages from OutQueue. Check that client pool size is respected, and threads time out after
     * 60seconds.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>configure connection factories with adjusted values of scheduled-thread-pool-max-size and thread-pool-max-size.</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * <li>check that client pool size is respected, and threads time out after 60seconds.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>receiver consumes all messages</li>
     * <li>thread pools have correct maximum size</li>
     * <li>threads timeout after 60seconds</li>
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testConnectionFactoryPools() throws Exception {
        testInternal(TestType.CONNECTION_FACTORY_ATTRIBUTE);
    }

    private void testInternal(TestType testType) throws Exception {

        Assume.assumeFalse(System.getProperty("os.name").contains("indows"));
        Assume.assumeTrue((System.getProperty("java.vm.name").contains("Java HotSpot")
                || System.getProperty("java.vm.name").contains("OpenJDK")));

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO, testType);

        container(1).start();

        switch (testType) {
            case GLOBAL_SYSTEM_PROP:
                //set artemis client global thread pool max size on client and server. We need to set it using system properties
                container(2).start(getContainer2Properties(containerMaxClientPoolSize, containerMaxScheduledClientPoolSize), 180000);

                System.setProperty(THREAD_POOL_MAX_SIZE_PROPERTY_KEY, "" + clientMaxClientPoolSize);
                System.setProperty(SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY, "" + clientMaxScheduledClientPoolSize);
                break;
            case GLOBAL_EAP_ATTRIBUTE:
                // this setting affects only server which is already configured. just configure clients using properties
                System.setProperty(THREAD_POOL_MAX_SIZE_PROPERTY_KEY, "" + clientMaxClientPoolSize);
                System.setProperty(SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY, "" + clientMaxScheduledClientPoolSize);
                container(2).start();
                break;
            default:
                container(2).start();

        }

        ResourceMonitor serverMeasurement = new ResourceMonitor.Builder()
                .setMeasurable(ArtemisClientThreadPoolMeasurement.class, TimeUnit.SECONDS.toMillis(15))
                .outFileNamingPattern("jms-server")
                .pid(container(2).getProcessId())
                .generateCharts()
                .keepCsv()
                .build();
        ResourceMonitor clientMeasurement = new ResourceMonitor.Builder()
                .setMeasurable(ArtemisClientThreadPoolMeasurement.class, TimeUnit.SECONDS.toMillis(15))
                .outFileNamingPattern("jms-client")
                .pid(DebugTools.getSurefirePid())
                .generateCharts()
                .keepCsv()
                .build();

        serverMeasurement.startMeasuring();
        clientMeasurement.startMeasuring();

        MeasurementObserver serverObserver = new MeasurementObserver(ArtemisClientThreadPoolMeasurement.class);
        serverMeasurement.attachObserver(serverObserver);

        MeasurementObserver clientObserver = new MeasurementObserver(ArtemisClientThreadPoolMeasurement.class);
        clientMeasurement.attachObserver(clientObserver);

        container(2).deploy(mdb1);

        Clients producer = new QueueClientsAutoAck(container(1), "jms/queue/InQueue", 1, numberOfProducers, 0, numberOfMessagesPerProducer);
        producer.startClients();

        Clients consumers = new QueueClientsAutoAck(container(1), "jms/queue/OutQueue", 1, 0, numberOfConsumers, 100);
        consumers.startClients();

        while (!consumers.isFinished()) {
            Thread.sleep(2000);
        }

        //wait for all threads to timeout
        Thread.sleep(TimeUnit.SECONDS.toMillis(80));

        Map<String, List<String>> resultsServer = serverMeasurement.getCurrentValues(ArtemisClientThreadPoolMeasurement.class);
        serverMeasurement.stopMeasuring();
        Map<String, List<String>> resultsClient = clientMeasurement.getCurrentValues(ArtemisClientThreadPoolMeasurement.class);
        clientMeasurement.stopMeasuring();

        container(2).undeploy(mdb1);
        container(2).stop();
        container(1).stop();

        Assert.assertEquals("There is different number of sent and received messages.",
                numberOfProducers * numberOfMessagesPerProducer, getConsumedMessages(consumers));

        switch (testType){
            case GLOBAL_SYSTEM_PROP:
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_THREAD_POOL_COUNT, containerMaxClientPoolSize, false);
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_SCHEDULED_THREAD_POOL_COUNT, containerMaxScheduledClientPoolSize, true);

                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_THREAD_POOL_COUNT, clientMaxClientPoolSize, false);
                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_SCHEDULED_THREAD_POOL_COUNT, clientMaxScheduledClientPoolSize, true);
                break;
            case GLOBAL_EAP_ATTRIBUTE:
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_THREAD_POOL_COUNT, containerMaxClientPoolSize, false);
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_SCHEDULED_THREAD_POOL_COUNT, containerMaxScheduledClientPoolSize, true);

                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_THREAD_POOL_COUNT, clientMaxClientPoolSize, false);
                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_GLOBAL_SCHEDULED_THREAD_POOL_COUNT, clientMaxScheduledClientPoolSize, true);
                break;
            case CONNECTION_FACTORY_ATTRIBUTE: // this depends on how many connection factory object is created, checking maximum possible value, just "smoke"
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_FACTORY_THREAD_POOL_COUNT, mdbMaxSession * containerMaxClientPoolSize, false);
                checkThreadPools(resultsServer, ArtemisClientThreadPoolMeasurement.CLIENT_FACTORY_PINGER_THREAD_POOL_COUNT, mdbMaxSession * containerMaxScheduledClientPoolSize, true);

                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_FACTORY_THREAD_POOL_COUNT, (numberOfProducers + numberOfConsumers) * clientMaxClientPoolSize, false);
                checkThreadPools(resultsClient, ArtemisClientThreadPoolMeasurement.CLIENT_FACTORY_PINGER_THREAD_POOL_COUNT, (numberOfProducers + numberOfConsumers) * clientMaxScheduledClientPoolSize, true);
        }

    }

    private void checkThreadPools(Map<String, List<String>> results, String resourceMonitorParameter, int maxSize, boolean isScheduledPool) {
        List<String> countsString = results.get(resourceMonitorParameter);
        List<Integer> counts = new ArrayList<>();
        int max = -1;
        int last = -1;
        for (String s : countsString) {
            last = Integer.parseInt(s);
            if (last > max) max = last;
            counts.add(last);
        }
        logger.info(String.format("Last number of threads [%d], maximal number of threads [%d], is pool scheduled: {%b}", last, max, isScheduledPool));

        //there is a possibility that slightly more threads will be created. This is by design and is considered OK.
        maxSize = maxSize + 1;

        if (!isScheduledPool) {
            Assert.assertTrue("Maximal number of threads should be higher then last measured value", max > last);
            Assert.assertEquals("Thread pool should be empty", 0, last);
            Assert.assertTrue("Maximal number of threads should not exceed configured max size", max <= maxSize);
        } else {
            if (max >= maxSize) {
                Assert.assertTrue("At least core threads of scheduled executors should be still alive", last <= maxSize);
            }
        }

    }


    public void prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE connectorType, TestType testType) throws Exception {

        prepareJmsServerEAP7(container(1), connectorType, testType, container(1));
        prepareMdbServerEAP7(container(2), connectorType, testType, container(1));

        copyApplicationPropertiesFiles();

    }

    private void prepareJmsServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, TestType testType, Container... remoteContainers) {

        String clusterGroupName = "my-cluster";
        String defaultNettySocketBindingName = "messaging";
        String defaultNettyAcceptorName = "netty-acceptor";
        String defaultNettyConnectorName = "netty-connector";
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        String socketBindingPrefix = "socket-binding-to-";
        String connectorPrefix = "connector-to-";
        switch (connectorType) {
            case NETTY_NIO:
                // create netty acceptor
                jmsAdminOperations.createSocketBinding(defaultNettySocketBindingName, Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7);
                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(),
                            Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + remoteContainer.getPortOffset());
                }
                jmsAdminOperations.close();

                container.restart();

                jmsAdminOperations = container.getJmsOperations();
                jmsAdminOperations.createRemoteAcceptor(defaultNettyAcceptorName, defaultNettySocketBindingName, null);
                jmsAdminOperations.createRemoteConnector(defaultNettyConnectorName, defaultNettySocketBindingName, null);
                List<String> staticNIOConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    String staticConnectorName = connectorPrefix + remoteContainer.getName();
                    jmsAdminOperations.createRemoteConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), null);
                    staticNIOConnectorsNames.add(staticConnectorName);
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND,
                        1, 1000, true, defaultNettyConnectorName, staticNIOConnectorsNames.toArray(new String[remoteContainers.length]));
                break;
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue");

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        switch (testType) {
            case CONNECTION_FACTORY_ATTRIBUTE:
                //set values on connection factory used by clients - we will check this value against size of client thread pool on client.
                jmsAdminOperations.setThreadPoolMaxSizeOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, clientMaxClientPoolSize);
                jmsAdminOperations.setScheduledThreadPoolMaxSizeOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, clientMaxScheduledClientPoolSize);
                jmsAdminOperations.setUseGlobalPoolsOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, false);
                break;
            case GLOBAL_EAP_ATTRIBUTE:
                //do nothing
            case GLOBAL_SYSTEM_PROP:
                //do nothing

        }


        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, TestType testType, Container... remoteContainers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String remoteConnectorName = "http-remote-connector";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        setConnectorTypeForPooledConnectionFactoryEAP7(container, connectorType, remoteContainers);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error(e);
        }
        jmsAdminOperations.setHaForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);

        switch (testType) {
            case CONNECTION_FACTORY_ATTRIBUTE:
                //set values on pooled connection factory used by MDB - we will check this value against size of client thread pool on server.
                jmsAdminOperations.setThreadPoolMaxSizeOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, containerMaxClientPoolSize);
                jmsAdminOperations.setScheduledThreadPoolMaxSizeOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, containerMaxScheduledClientPoolSize);
                jmsAdminOperations.setUseGlobalPoolsOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, false);
                break;
            case GLOBAL_EAP_ATTRIBUTE:
                jmsAdminOperations.setGlobalClientThreadPoolMaxSize(containerMaxClientPoolSize);
                jmsAdminOperations.setGlobalClientScheduledThreadPoolMaxSize(containerMaxScheduledClientPoolSize);
                break;
            case GLOBAL_SYSTEM_PROP:
                //do nothing, we will handle this on server startup
                break;
        }

        jmsAdminOperations.close();
        container.stop();
    }

    private void setConnectorTypeForPooledConnectionFactoryEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {
        String remoteSocketBindingPrefix = "socket-binding-to-";
        String remoteConnectorNamePrefix = "connector-to-node-";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        switch (connectorType) {
            case NETTY_NIO:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + c.getPortOffset());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> nioConnectorList = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null);
                    nioConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, nioConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
        }
        jmsAdminOperations.close();

    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

    private Archive getMdb() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueue0.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    private Map<String, String> getContainer2Properties(int globalClientThreadPoolSize, int globalClientScheduledThreadPoolSize) {
        StringBuilder sb = null;
        for (GroupDef groupDef : getArquillianDescriptor().getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(container(2).getName())) {
                    if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
                        sb = new StringBuilder(containerDef.getContainerProperties().get("javaVmArguments"));
                        sb.append(" -D" + THREAD_POOL_MAX_SIZE_PROPERTY_KEY + "=" + globalClientThreadPoolSize);
                        sb.append(" -D" + SCHEDULED_THREAD_POOL_SIZE_PROPERTY_KEY + "=" + globalClientScheduledThreadPoolSize);
//                        sb.append(" -Dmaven.surefire.debug=\"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE\"");
                        containerDef.getContainerProperties().put("javaVmArguments", sb.toString());
                    }
                }
            }
        }
        Map<String, String> properties = new HashMap();
        properties.put("javaVmArguments", sb.toString());
        return properties;
    }

    private int getConsumedMessages(Clients clients) {
        int result = 0;
        for (Client c : clients.getConsumers()) {
            result += ((ReceiverAutoAck) c).getListOfReceivedMessages().size();
        }
        return result;
    }

    private enum TestType {
        GLOBAL_SYSTEM_PROP,
        GLOBAL_EAP_ATTRIBUTE,
        CONNECTION_FACTORY_ATTRIBUTE,

    }

}
