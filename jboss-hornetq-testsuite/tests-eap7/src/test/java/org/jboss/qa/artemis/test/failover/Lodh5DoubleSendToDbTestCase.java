package org.jboss.qa.artemis.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDbAndRemoteInQueue;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServletForMessageInfo1Table;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DBAllocatorUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.JdbcUtils;
import org.jboss.qa.hornetq.tools.MulticastAddressUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jboss.qa.hornetq.constants.Constants.RESOURCE_ADAPTER_NAME_EAP7;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5-double-send-to-db/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public abstract class Lodh5DoubleSendToDbTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh5DoubleSendToDbTestCase.class);

    public static final String NUMBER_OF_ROLLBACKED_TRANSACTIONS = "Number of prepared transactions:";

    protected static final int MAX_SIZE_BYTES_PAGING = 50 * 1024;
    protected static final int PAGE_SIZE_BYTES_PAGING = 1024;

    protected static final int MAX_SIZE_BYTES_DEFAULT = 10 * 1024 * 1024;
    protected static final int PAGE_SIZE_BYTES_DEFAULT = 1024 * 1024 * 2;

    private final int NORMAL_MESSAGE_SIZE_BYTES = 100;
    private static final int LARGE_MESSAGE_SIZE_BYTES = 150 * 1024;

    private final Archive mdbForMdbCluster = createMDBDeployment();
    private final Archive mdbForJmsCluster = createJMSDeployement();
    private final Archive dbUtilServletForJmsServer = createDbUtilServletForJmsServer();
    private final Archive dbUtilServletForMdbServer = createDbUtilServletForMdbServer();
    private final String groupAddressJmsCluster = MulticastAddressUtils.generateMulticastAddress();
    private final String groupAddressMdbCluster = MulticastAddressUtils.generateMulticastAddress();

    // queue to send messages
    static String inQueueHornetQName = "InQueue";
    static String inQueueRelativeJndiName = "jms/queue/" + inQueueHornetQName;

    // this is filled by allocateDatabase() method
    private Map<String, String> properties = null;

    /**
     * This mdb reads messages from local InQueue and sends to database.
     *
     * @return test artifact with MDBs
     */
    private JavaArchive createMDBDeployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDb.jar");
        mdbJar.addClass(SimpleMdbToDb.class);
        mdbJar.addClass(MessageInfo.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(2));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbtodb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * This mdb reads messages from local InQueue and sends to database and remote InQueue
     *
     * @return test artifact with MDBs
     */
    private JavaArchive createJMSDeployement() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDbAndRemoteInQueue.jar");
        mdbJar.addClass(SimpleMdbToDbAndRemoteInQueue.class);
        mdbJar.addClass(MessageInfo.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbtodb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2KillJmsNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.KILL, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2KillJmsLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.KILL, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsShutdownNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.SHUTDOWN, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsShutdownLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.SHUTDOWN, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsOOMNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsOOMLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsCpuNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.CPU_OVERLOAD, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2JmsCpuLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(1), Constants.FAILURE_TYPE.CPU_OVERLOAD, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbJmsNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.KILL, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbJmsLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.KILL, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbShutdownNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.SHUTDOWN, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbShutdownLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.SHUTDOWN, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbOOMNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbOOMLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE, true);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbCpuNormalMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.CPU_OVERLOAD, false);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testORACLE11GR2MdbCpuLargeMessages() throws Exception {
        testFail(ORACLE11GR2, container(2), Constants.FAILURE_TYPE.CPU_OVERLOAD, true);
    }

    public void testFail(String databaseName, Container containerToFail, Constants.FAILURE_TYPE failureType, boolean isLargeMessages) throws Exception {

        MessageBuilder messageBuilder = isLargeMessages ? new InfoMessageBuilder(LARGE_MESSAGE_SIZE_BYTES) : new InfoMessageBuilder(NORMAL_MESSAGE_SIZE_BYTES);
        int numberOfMessages = 2000;

        prepareServers(databaseName);

        container(1).start();
        container(3).start();

        cleanDatabase();

        ProducerTransAck producer = new ProducerTransAck(container(1), inQueueRelativeJndiName, numberOfMessages);
        producer.setMessageBuilder(messageBuilder);
        producer.setCommitAfter(1000);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        container(2).start();
        container(4).start();
        container(2).deploy(mdbForMdbCluster);
        container(4).deploy(mdbForMdbCluster);
        container(1).deploy(mdbForJmsCluster);
        container(3).deploy(mdbForJmsCluster);

        long howLongToWait = 360000;
        long startTime = System.currentTimeMillis();
        while (countRecords(container(2), dbUtilServletForMdbServer) < numberOfMessages / 10 && (System.currentTimeMillis() - startTime) < howLongToWait) {
            Thread.sleep(5000);
        }

        executeFailure(containerToFail, failureType);

        startTime = System.currentTimeMillis();
        long lastValue = 0;
        long newValue;
        while ((newValue = countRecords(container(2), dbUtilServletForMdbServer)) < numberOfMessages && (newValue > lastValue
                || (System.currentTimeMillis() - startTime) < howLongToWait)) {
            lastValue = newValue;
            logger.info("Messages are still processed - old value: " + lastValue + ", new value: " + newValue);
            Thread.sleep(5000);
        }

        logger.info("Print lost messages for MDB server:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> lostMdbMessages = checkLostMessages(listOfSentMessages, printAll(container(2), dbUtilServletForMdbServer));
        for (String m : lostMdbMessages) {
            logger.info("Lost Message: " + m);
        }

        logger.info("Print lost messages for JMS server:");
        List<String> lostJmsMessages = checkLostMessages(listOfSentMessages, printAll(container(1), dbUtilServletForJmsServer));
        for (String m : lostJmsMessages) {
            logger.info("Lost Message: " + m);
        }

        Assert.assertEquals(numberOfMessages, countRecords(container(2), dbUtilServletForMdbServer));
        Assert.assertEquals(numberOfMessages, countRecords(container(1), dbUtilServletForJmsServer));

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    private void cleanDatabase() throws Exception {
        deleteRecords(container(2), dbUtilServletForMdbServer);
        countRecords(container(2), dbUtilServletForMdbServer);
        deleteRecords(container(1), dbUtilServletForJmsServer);
        countRecords(container(1), dbUtilServletForJmsServer);
    }

    private void executeFailure(Container containerToFail, Constants.FAILURE_TYPE failureType) {

        containerToFail.fail(failureType);

        if (Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE.equals(failureType) ||
                Constants.FAILURE_TYPE.CPU_OVERLOAD.equals(failureType)) {
            try {
                Thread.sleep(300000);
            } catch (InterruptedException e) {
                // ignore
            }
            containerToFail.kill();
        }
        containerToFail.start();

    }

    private void prepareServers(String databaseName) throws Exception {
        prepareJmsServerEAP7(container(1), container(2), databaseName);
        prepareJmsServerEAP7(container(3), container(4), databaseName);
        prepareServerWithMdbEAP7(container(2), databaseName);
        prepareServerWithMdbEAP7(container(4), databaseName);
    }

    private List<String> checkLostMessages(List<String> listOfSentMessages, List<String> listOfReceivedMessages) {
        //get lost messages
        List<String> listOfLostMessages = new ArrayList<String>();

        Set<String> setOfReceivedMessages = new HashSet<String>();
        for (String id : listOfReceivedMessages) {
            setOfReceivedMessages.add(id);
        }

        for (String sentMessageId : listOfSentMessages) {
            // if true then message can be added and it means that it's lost
            if (setOfReceivedMessages.add(sentMessageId)) {
                listOfLostMessages.add(sentMessageId);
            }
        }
        return listOfLostMessages;
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
     * Be sure that both of the servers are stopped before and after the test.
     */
    @Before
    @After
    public void stopAllServers() {
        container(4).stop();
        container(2).stop();
        container(1).stop();
        container(3).stop();
    }


    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container, Container mdbContainer, String databaseName) throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String httpSocketBindingName = "http";
        String messagingGroupSocketBindingName = "messaging-group";
        String inVmConnectorName = "in-vm";
        String remoteConnectorName = "http-connector-to-mdb-server";
        String inVmHornetRaName = "local-activemq-ra";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBindingName, null);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);

        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        jmsAdminOperations.createQueue("default", inQueueHornetQName, inQueueRelativeJndiName, true);

        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");

        // prepare pooled connection factory pointing to mdb server
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", mdbContainer.getHostname(), mdbContainer.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);

        // create new in-vm pooled connection factory and configure it as default for inbound communication
        jmsAdminOperations.createPooledConnectionFactory(inVmHornetRaName, "java:/LocalJmsXA java:/jms/DefaultJMSConnectionFactory", inVmConnectorName);
        jmsAdminOperations.setDefaultResourceAdapter(inVmHornetRaName);

        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsAdminOperations.close();
        container.restart();
        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddressJmsCluster, 55874);

        setupDatasource(databaseName, container, jmsAdminOperations);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServerWithMdbEAP7(Container container, String database) throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.disableSecurity();
        Random r = new Random();
        jmsAdminOperations.setNodeIdentifier(r.nextInt(9999));

        setAddressSettings(jmsAdminOperations);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");
        jmsAdminOperations.seRootLoggingLevel("TRACE");

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddressMdbCluster);

        jmsAdminOperations.createQueue("default", inQueueHornetQName, inQueueRelativeJndiName, true);

        setupDatasource(database, container, jmsAdminOperations);

        jmsAdminOperations.close();
        container.stop();
    }

    private void setupDatasource(String database, Container container, JMSOperations jmsAdminOperations) throws Exception {

        String poolName = "lodhDb";
        final String driverName = "mylodhdb";
        final String driverModuleName = "com.mylodhdb";

        JdbcUtils.installJdbcDriverModule(container, database);
        if (properties == null) {
            properties = DBAllocatorUtils.allocateDatabase(database);
        }

        Random r = new Random();

        if (DB2105.equalsIgnoreCase(database)) {
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String databaseName = properties.get("db.name");   // db.name
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);
            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("DriverType", "4");
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", databaseName);
            xaDataSourceProperties.put("PortNumber", portNumber);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (ORACLE11GR2.equalsIgnoreCase(database)) {

            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (ORACLE11GR2.equalsIgnoreCase(database)) {
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (ORACLE11GR1.equalsIgnoreCase(database)) {
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (MYSQL55.equalsIgnoreCase(database) || MYSQL57.equalsIgnoreCase(database)) {

            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", "jdbc:mysql://" + serverName + ":" + portNumber + "/crashrec");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");


        } else if (POSTGRESQL92.equalsIgnoreCase(database) || POSTGRESQLPLUS92.equalsIgnoreCase(database) ||
                POSTGRESQL93.equalsIgnoreCase(database) || POSTGRESQLPLUS93.equalsIgnoreCase(database) ||
                POSTGRESQL94.equalsIgnoreCase(database) || POSTGRESQLPLUS94.equalsIgnoreCase(database)) {

            String databaseName = properties.get("db.name");   // db.name
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String recoveryUsername = properties.get("db.username");
            String recoveryPassword = properties.get("db.password");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("DatabaseName", databaseName);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", recoveryUsername);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", recoveryPassword);

        } else if (MSSQL2008R2.equals(database)) {

            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("SelectMethod", "cursor");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (MSSQL2012.equals(database) || MSSQL2014.equals(database)) {

            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("SelectMethod", "cursor");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (SYBASE157.equals(database)) {

            String databaseName = properties.get("db.name");   // db.name
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String recoveryUsername = properties.get("db.username");
            String recoveryPassword = properties.get("db.password");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", databaseName);
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("NetworkProtocol", "Tds");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", recoveryUsername);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", recoveryPassword);

        }
    }

    public WebArchive createDbUtilServletForMdbServer() {

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
        File target = new File("/tmp/DbUtilServlet.war");
        if (target.exists()) {
            target.delete();
        }
        dbUtilServlet.as(ZipExporter.class).exportTo(target, true);

        return dbUtilServlet;
    }

    public WebArchive createDbUtilServletForJmsServer() {

        final WebArchive dbUtilServlet = ShrinkWrap.create(WebArchive.class, "dbUtilServlet.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>dbUtilServlet</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.DbUtilServletForMessageInfo1Table</servlet-class></servlet>\n");
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
        dbUtilServlet.addClass(DbUtilServletForMessageInfo1Table.class);
        logger.info(dbUtilServlet.toString(true));
//      Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/DbUtilServletMessageInfo1.war");
        if (target.exists()) {
            target.delete();
        }
        dbUtilServlet.as(ZipExporter.class).exportTo(target, true);

        return dbUtilServlet;
    }

    public List<String> printAll(Container container, Archive dbServlet) throws Exception {
        boolean wasStarted = true;
        List<String> messageIds = new ArrayList<String>();

        try {
            if (!ContainerUtils.isStarted(container)) {
                container.start();
                wasStarted = false;
            }
            container.deploy(dbServlet);
            String url = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=printAll";
            logger.info("Calling servlet: " + url);
            String response = HttpRequest.get(url, 120, TimeUnit.SECONDS);

            StringTokenizer st = new StringTokenizer(response, ",");
            while (st.hasMoreTokens()) {
                messageIds.add(st.nextToken());
            }

            logger.info("Number of records: " + messageIds.size());

        } catch (Exception ex) {
            logger.error("Calling print all records failed: ", ex);
        } finally {
            container.undeploy(dbServlet);
        }
        if (!wasStarted) {
            container.stop();
        }
        return messageIds;
    }

    public int countRecords(Container container, Archive dbServlet) throws Exception {
        boolean wasStarted = true;
        int numberOfRecords = -1;

        int maxNumberOfTries = 3;
        int numberOfTries = 0;

        while (numberOfRecords == -1 && numberOfTries < maxNumberOfTries) {
            try {
                if (!ContainerUtils.isStarted(container)) {
                    container.start();
                    wasStarted = false;
                }
                container.deploy(dbServlet);

                String url = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=countAll";
                logger.info("Calling servlet: " + url);
                String response = HttpRequest.get(url, 60, TimeUnit.SECONDS);

                logger.info("Response is: " + response);

                StringTokenizer st = new StringTokenizer(response, ":");

                while (st.hasMoreTokens()) {
                    if (st.nextToken().contains("Records in DB")) {
                        numberOfRecords = Integer.valueOf(st.nextToken().trim());
                    }
                }
                logger.info("Number of records " + numberOfRecords);
            } catch (Exception ex)  {
                numberOfTries++;
                if (numberOfTries > maxNumberOfTries)   {
                    throw new Exception("DbUtilServlet could not get number of records in database. Failing the test.", ex);

                }
                logger.warn("Exception thrown during counting records by DbUtilServlet. Number of tries: " + numberOfTries
                        + ", Maximum number of tries is: " + maxNumberOfTries);
            } finally {
                container.undeploy(dbServlet);
            }
            if (!wasStarted) {
                container.stop();
            }
        }
        return numberOfRecords;
    }

    public void deleteRecords(Container container, Archive dbServlet) throws Exception {
        boolean wasStarted = true;
        try {
            if (!ContainerUtils.isStarted(container)) {
                container.start();
                wasStarted = false;
            }
            container.deploy(dbServlet);
            String url = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=deleteRecords";
            logger.info("Calling servlet: " + url);
            String response = HttpRequest.get(url, 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
        } finally {
            container.undeploy(dbServlet);
        }

        if (!wasStarted) {
            container.stop();
        }
    }

    protected abstract void setAddressSettings(JMSOperations jmsAdminOperations);

}


