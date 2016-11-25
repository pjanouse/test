package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.ejb.*;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/*
 *TODO add to job
 */
public class ReplicatedDedicatedFailoverTestWithMdbAndDb extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ReplicatedDedicatedFailoverTestWithMdbAndDb.class);
    private JMSTools jmsTools = new JMSTools();

    private String inQueueName = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueueName;
    String clusterConnectionName = "my-cluster";
    String replicationGroupName = "replication-group-name-1";

    private JavaArchive mdbToDb = createMdbToDbDeployment();
    private JavaArchive ejbProducerBean = createEjbSenderStatelessBean();
    private WebArchive dbUtilServlet = createDbUtilServlet();

    // this is filled by allocateDatabase() method
    private Map<String, String> properties = null;


    /**
     * Deallocate db from db allocator if there is anything to deallocate
     *
     * @throws Exception
     */
    @After
    public void deallocateDatabase() throws Exception {
        String response = "";
        try {
            if (properties != null) {
                response = HttpRequest.get("http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=dealloc&uuid=" + properties.get("uuid"),
                        20, TimeUnit.SECONDS);
            }
        } catch (TimeoutException e) {
            logger.error("Database could not be deallocated. Response: " + response, e);

        }
        logger.trace("Response from deallocating database is: " + response);
    }

    @Before
    @After
    public void removeInstalledModules() {

        try {
            FileUtils.deleteDirectory(new File(container(1).getServerHome() + File.separator + "modules" + File.separator + "system" + File.separator
                    + "layers" + File.separator + "base" + File.separator + "com" + File.separator + "mylodhdb"));
        } catch (Exception e) {
            //ignored
        }

    }

    /**
     * @tpTestDetails Start two server (node 1, node 2) in replicated dedicated topology with InQueue deployed.
     * Start third server with pooled connection factory configured to connect to live and backup nodes (node 1, node 2).
     * On node 3, deploy EJB. This EJB does two things in UserTransaction - sends message to live`s InQueue, performs
     * "select" statement on DB. On node 3 deploy MDB which reads messages from live-backup pair`s InQueue and performs
     * insert into DB. During processing shutdown live server. EJB and MDB fail over to backup. Then start live and
     * let MDB and EJB failback to live. Verify all messages send by EJB were inserted into database by MDB.
     *
     * @tpProcedure <ul>
     * <li>start 3 servers (nodes) in replicated dedicated topology</li>
     * <li>producer EJB and MDB inserting to DB are deployed on node 3</li>
     * <li>EJB on node 3 sends messages to live`s InQueue on node 1. EJB also performs DB SELECT</li>
     * <li>MDB on node 3 reads messages from live`s InQueue on node 1 and inserts hem into DB</li>
     * <li>Shutdown live server</li>
     * <li>Let EJB and MDB failover to backup</li>
     * <li>Start live server and failback</li>
     * <li>Stop sending messages</li>
     * <li>Verify all messages send by EJB were inserted into DB by MDB</li>
     * </ul>
     * @tpPassCrit all messages send by EJB were inserted into DB by MDB
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testShutdownLiveWithProducerAndMdbToDbOnRemoteServer() throws Exception {
        testFailOfLiveWithFailoback(Constants.FAILURE_TYPE.SHUTDOWN);
    }

    /**
     * @tpTestDetails Start two server (node 1, node 2) in replicated dedicated topology with InQueue deployed.
     * Start third server with pooled connection factory configured to connect to live and backup nodes (node 1, node 2).
     * On node 3, deploy EJB. This EJB does two things in UserTransaction - sends message to live`s InQueue, performs
     * "select" statement on DB. On node 3 deploy MDB which reads messages from live-backup pair`s InQueue and performs
     * insert into DB. During processing kill live server. EJB and MDB fail over to backup. Then start live and
     * let MDB and EJB failback to live. Verify all messages send by EJB were inserted into database by MDB.
     *
     * @tpProcedure <ul>
     * <li>start 3 servers (nodes) in replicated dedicated topology</li>
     * <li>producer EJB and MDB inserting to DB are deployed on node 3</li>
     * <li>EJB on node 3 sends messages to live`s InQueue on node 1. EJB also performs DB SELECT</li>
     * <li>MDB on node 3 reads messages from live`s InQueue on node 1 and inserts hem into DB</li>
     * <li>Kill live server</li>
     * <li>Let EJB and MDB failover to backup</li>
     * <li>Start live server and failback</li>
     * <li>Stop sending messages</li>
     * <li>Verify all messages send by EJB were inserted into DB by MDB</li>
     * </ul>
     * @tpPassCrit all messages send by EJB were inserted into DB by MDB
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testKillLiveWithProducerAndMdbToDbOnRemoteServer() throws Exception {
        testFailOfLiveWithFailoback(Constants.FAILURE_TYPE.KILL);
    }

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testFailOfLiveWithFailoback(Constants.FAILURE_TYPE failureType) throws Exception {


        String ejbLookup = ContainerUtils.isEAP6(container(3)) ? "ejb/SenderEJBWithDatabaseAccess!org.jboss.qa.hornetq.apps.ejb.SimpleSendEJB" :
                "ejb/SenderEJBWithDatabaseAccessUsingExecutor!org.jboss.qa.hornetq.apps.ejb.SimpleSendEJB";
        int timeout = ContainerUtils.isEAP6(container(3)) ? 0 : 50;

        prepareRemoteJcaTopology();

        // start live-backup servers
        container(1).start();
        container(2).start();
        container(3).start();

        // setup database
        deleteRecords();

        //deploy on 3rd server
        container(3).deploy(mdbToDb);
        container(3).deploy(ejbProducerBean);


        EjbProducer ejbProducer = new EjbProducer(container(3), ejbLookup, 5000);
        ejbProducer.setTimeout(timeout);

        ejbProducer.start();

        Thread.sleep(TimeUnit.SECONDS.toMillis(45));
        long addedOnLiveBeforeFail = jmsTools.getAddedMessagesCount(inQueueName, container(1));
        logger.info("Number of messages added in live`s InQueue is " + addedOnLiveBeforeFail);
        Assert.assertTrue("No messages were send to InQueue before live failure", addedOnLiveBeforeFail > 0);

        logger.info("#######################################");
        logger.info("Stopping live");
        logger.info("#######################################");
        container(1).fail(failureType);
        logger.info("#######################################");
        logger.info("Live stopped.");
        logger.info("#######################################");

        CheckServerAvailableUtils.waitForBrokerToActivate(container(2), TimeUnit.MINUTES.toMillis(1));
        Thread.sleep(TimeUnit.SECONDS.toMillis(45));
        long addedOnBackup = jmsTools.getAddedMessagesCount(inQueueName, container(2));
        logger.info("Number of messages added in backup`s InQueue is " + addedOnBackup);
        Assert.assertTrue("No messages were send to InQueue on backup", addedOnBackup > 0);

        logger.info("#######################################");
        logger.info("Starting live again");
        logger.info("#######################################");
        container(1).start();
        CheckServerAvailableUtils.waitForBrokerToActivate(container(1), TimeUnit.MINUTES.toMillis(3));
        logger.info("#######################################");
        logger.info("Live started.");
        logger.info("#######################################");

        Thread.sleep(TimeUnit.SECONDS.toMillis(90));
        ejbProducer.stopSending();

        jmsTools.waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, 0, TimeUnit.MINUTES.toMillis(3));
        logger.info("All messages were read by MDB @ container3");

        long addedOnLiveAfterFailback = jmsTools.getAddedMessagesCount(inQueueName, container(1));
        logger.info("Number of messages added in live`s InQueue after failback is " + addedOnLiveAfterFailback);
        Assert.assertTrue("No messages were send to InQueue after failback", addedOnLiveAfterFailback > 0);


        // give mdb time to insert last record into database
        Thread.sleep(TimeUnit.SECONDS.toMillis(15));
        int numberOfMessages = ejbProducer.getSendMessagesCount();

        Assert.assertEquals("All messages send are not saved in database", numberOfMessages, countRecords());

        container(3).undeploy(mdbToDb);
        container(3).undeploy(ejbProducerBean);

        container(3).stop();
        container(2).stop();
        container(1).stop();

    }

    public void prepareRemoteJcaTopology() throws Exception {
        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareLiveServerEAP6(container(1));
            prepareBackupServerEAP6(container(2));
            prepareMdbServerEAP6(container(3), container(1), container(2));
            copyApplicationPropertiesFiles();

        } else {
            prepareLiveServerEAP7(container(1));
            prepareBackupServerEAP7(container(2));
            prepareMdbServerEAP7(container(3), container(1), container(2));
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareLiveServerEAP6(Container container) {
        prepareLiveServerEAP6(container, "firstPair");
    }

    protected void prepareLiveServerEAP6(Container container, String backupGroupName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.kill();
        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.setBackupGroupName(backupGroupName);
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setBackup(false);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);

        jmsAdminOperations.setClusterUserPassword("heslo");
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);


        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareBackupServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(false);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container The container - defined in arquillian.xml
     */
    protected void prepareLiveServerEAP7(Container container) {
        prepareLiveServerEAP7(container, replicationGroupName);
    }

    protected void prepareLiveServerEAP7(Container container, String backupGroupName) {

        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster(true, clusterConnectionName, replicationGroupName);
        jmsAdminOperations.setNodeIdentifier(454545);

        jmsAdminOperations.close();

        container.stop();
    }


    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareBackupServerEAP7(Container container) {

        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setNodeIdentifier(65789);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(true, clusterConnectionName, 5000, replicationGroupName, 60, true, false, null, null, null, null);

        jmsAdminOperations.close();
        container.stop();
    }



    /**
     * Prepare database and server communicating with database
     *
     * @param container
     **/
    private void prepareDatabaseConnection(Container container) {

        final String database = POSTGRESQL93;
        final String poolName = "db";
        final String driverName = "mylodhdb";
        final String driverModuleName = "com.mylodhdb";

        try {
            JdbcUtils.installJdbcDriverModule(container, database);
            if (properties == null) {
                properties = DBAllocatorUtils.allocateDatabase(database);
            }
        } catch (Exception e) {
            throw new RuntimeException("Can not allocate database");
        }


        String databaseName = properties.get("db.name");   // db.name
        String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
        String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
        String portNumber = properties.get("db.port"); // db.port=5432
        String recoveryUsername = properties.get("db.username");
        String recoveryPassword = properties.get("db.password");
        String jdbcClassName = properties.get("db.jdbc_class");


        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true);

            jmsAdminOperations.addXADatasourceProperty(poolName, "ServerName", serverName);
            jmsAdminOperations.addXADatasourceProperty(poolName, "PortNumber", portNumber);
            jmsAdminOperations.addXADatasourceProperty(poolName, "DatabaseName", databaseName);

        } else {
            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("DatabaseName", databaseName);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
        }

        jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", recoveryUsername);
        jmsAdminOperations.setXADatasourceAtribute(poolName, "password", recoveryPassword);
        jmsAdminOperations.close();

    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareMdbServerEAP6(Container container, Container containerLive, Container containerBackup) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String remoteConnectorName = "netty-remote";
        String remoteConnectorNameBackup = "netty-remote-backup";
        String pooledConnectionFactoryName = "hornetq-ra";

        container.start();

        prepareDatabaseConnection(container);

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown("RemoteConnectionFactory", true);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
//        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
//        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
//        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", containerLive.getHostname(), containerLive.getHornetqPort());
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", containerBackup.getHostname(), containerBackup.getHornetqPort());
        jmsAdminOperations.createRemoteConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(remoteConnectorName);
//        connectorList.add(remoteConnectorNameBackup);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorList);

        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(pooledConnectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(pooledConnectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(pooledConnectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(pooledConnectionFactoryName, -1);

        jmsAdminOperations.createPooledConnectionFactory("ra-connection-factory", "java:/jmsXALocal", "netty");
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("ra-connection-factory", connectorName);
        jmsAdminOperations.setFailoverOnShutdownOnPooledConnectionFactory("ra-connection-factory", true);
        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setNodeIdentifier(123);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareMdbServerEAP7(Container container, Container containerLive, Container containerBackup) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String remoteConnectorName = "remote-http-connector";
        String remoteConnectorNameBackup = "remote-http-connector-backup";

        container.start();

        prepareDatabaseConnection(container);

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", containerLive.getHostname(), containerLive.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", containerBackup.getHostname(), containerBackup.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(remoteConnectorName);
        connectorList.add(remoteConnectorNameBackup);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, connectorList);
        jmsAdminOperations.setHaForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1.0);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);
        jmsAdminOperations.setNodeIdentifier(123);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void copyApplicationPropertiesFiles() throws IOException {

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

    // DATABASE MANAGEMENT

    public void deleteRecords() throws Exception {
        try {
            container(3).deploy(dbUtilServlet);
            String response = HttpRequest.get("http://" + container(3).getHostname() + ":" + container(3).getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=deleteRecords", 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
        } finally {
            container(3).undeploy(dbUtilServlet);
        }
    }

    public int countRecords() throws Exception {
        int numberOfRecords = -1;
        try {
            container(3).deploy(dbUtilServlet);

            String response = HttpRequest.get("http://" + container(3).getHostname() + ":" + container(3).getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=countAll", 60, TimeUnit.SECONDS);
            container(3).undeploy(dbUtilServlet);

            logger.info("Response is: " + response);

            StringTokenizer st = new StringTokenizer(response, ":");

            while (st.hasMoreTokens()) {
                if (st.nextToken().contains("Records in DB")) {
                    numberOfRecords = Integer.valueOf(st.nextToken().trim());
                }
            }
            logger.info("Number of records " + numberOfRecords);
        } finally {
            container(3).undeploy(dbUtilServlet);
        }
        return numberOfRecords;
    }

    public WebArchive createDbUtilServlet() {

        final WebArchive dbUtilServlet = ShrinkWrap.create(WebArchive.class, "dbUtilServlet.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>dbUtilServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.DbUtilServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>dbUtilServlet</servlet-name>\n");
        webXml.append("<url-pattern>/DbUtilServlet</url-pattern>\n");
        webXml.append("</servlet-mapping>\n");
        webXml.append("</web-app>\n");
        logger.debug(webXml.toString());
        dbUtilServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");

        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("<context-root>/DbUtilServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        logger.debug(jbossWebXml.toString());
        dbUtilServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        dbUtilServlet.addClass(DbUtilServlet.class);
        logger.info(dbUtilServlet.toString(true));
//      Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/DbUtilServlet.war");
//        if (target.exists()) {
//            target.delete();
//        }
//        dbUtilServlet.as(ZipExporter.class).exportTo(target, true);

        return dbUtilServlet;
    }

    // DEPLOYMENTS CREATION MANAGEMENT

    private JavaArchive createMdbToDbDeployment() {
        final org.jboss.shrinkwrap.api.spec.JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDb.jar");
        mdbJar.addClass(SimpleMdbToDb.class);
        mdbJar.addClass(MessageInfo.class);
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbtodb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    private JavaArchive createEjbSenderStatelessBean() {
        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "ejb.jar");
        if (ContainerUtils.isEAP6(container(3))) {
            ejbJar.addClasses(SimpleSendEJB.class, SenderEJBWithDatabaseAccess.class, MessageInfo.class);
        } else {
            ejbJar.addClasses(SimpleSendEJB.class, SenderEJBWithDatabaseAccessUsingExecutor.class, SendAndDbInteractTask.class, MessageInfo.class);
            ejbJar.addAsManifestResource(new StringAsset("Dependencies: javax.enterprise.concurrent.api \n"), "MANIFEST.MF");
        }
        logger.info(ejbJar.toString(true));
//        File target = new File("/tmp/ejb-sender-stateless.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        ejbJar.as(ZipExporter.class).exportTo(target, true);
        return ejbJar;
    }

}