package org.jboss.qa.artemis.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DBAllocatorUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.JdbcUtils;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jboss.qa.hornetq.constants.Constants.RESOURCE_ADAPTER_NAME_EAP7;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh5RemoteInQueueTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh5RemoteInQueueTestCase.class);

    public static final String NUMBER_OF_ROLLBACKED_TRANSACTIONS = "Number of prepared transactions:";

    private final Archive mdbToDb = createLodh5Deployment();
    private final Archive dbUtilServlet = createDbUtilServlet();

    // queue to send messages
    static String inQueueHornetQName = "InQueue";
    static String inQueueRelativeJndiName = "jms/queue/" + inQueueHornetQName;

    // this is filled by allocateDatabase() method
    private Map<String, String> properties;

    /**
     * This mdb reads messages from remote InQueue and sends to database.
     *
     * @return test artifact with MDBs
     */
    private JavaArchive createLodh5Deployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDb.jar");
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


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cKillJms() throws Exception {
        testFail(ORACLE12C, container(1), Constants.FAILURE_TYPE.KILL);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cJmsShutdown() throws Exception {
        testFail(ORACLE12C, container(1), Constants.FAILURE_TYPE.SHUTDOWN);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cJmsOOM() throws Exception {
        testFail(ORACLE12C, container(1), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cJmsCpu() throws Exception {
        testFail(ORACLE12C, container(1), Constants.FAILURE_TYPE.CPU_OVERLOAD);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cMdbJms() throws Exception {
        testFail(ORACLE12C, container(2), Constants.FAILURE_TYPE.KILL);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cMdbShutdown() throws Exception {
        testFail(ORACLE12C, container(2), Constants.FAILURE_TYPE.SHUTDOWN);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cMdbOOM() throws Exception {
        testFail(ORACLE12C, container(2), Constants.FAILURE_TYPE.OUT_OF_MEMORY_HEAP_SIZE);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12cMdbCpu() throws Exception {
        testFail(ORACLE12C, container(2), Constants.FAILURE_TYPE.CPU_OVERLOAD);
    }




    public void testFail(String databaseName, Container containerToFail, Constants.FAILURE_TYPE failureType) throws Exception {

        int numberOfMessages = 2000;

        prepareServers(databaseName);

        container(1).start();
        container(3).start();

        deleteRecords(container(2));
        countRecords(container(2));

        ProducerTransAck producer = new ProducerTransAck(container(1), inQueueRelativeJndiName, numberOfMessages);
        producer.setMessageBuilder(new InfoMessageBuilder());
        producer.setCommitAfter(1000);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        container(2).start();
        container(4).start();
        container(2).deploy(mdbToDb);
        container(4).deploy(mdbToDb);

        long howLongToWait = 360000;
        long startTime = System.currentTimeMillis();
        while (countRecords(container(2)) < numberOfMessages / 10 && (System.currentTimeMillis() - startTime) < howLongToWait) {
            Thread.sleep(5000);
        }

        executeFailure(containerToFail, failureType);

        startTime = System.currentTimeMillis();
        long lastValue = 0;
        long newValue;
        while ((newValue = countRecords(container(2))) < numberOfMessages && (newValue > lastValue
                || (System.currentTimeMillis() - startTime) < howLongToWait)) {
            lastValue = newValue;
            logger.info("Messages are still processed - old value: " + lastValue + ", new value: " + newValue);
            Thread.sleep(5000);
        }

        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll(container(2)));
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(numberOfMessages, countRecords(container(2)));

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

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
        prepareJmsServerEAP7(container(1));
        prepareJmsServerEAP7(container(3));
        prepareServerWithMdbEAP7(container(2), container(1), databaseName);
        prepareServerWithMdbEAP7(container(4), container(3), databaseName);
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
        container(1).stop();
    }

    /**
     * Deallocate db from db allocator if there is anything to deallocate
     *
     * @throws Exception
     */
    @After
    public void deallocateDatabase() throws Exception {
        String response = "";
        try {
            response = HttpRequest.get("http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=dealloc&uuid=" + properties.get("uuid"),
                    20, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Database could not be deallocated. Response: " + response, e);

        }
        logger.trace("Response from deallocating database is: " + response);
    }


    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.3";
        String httpSocketBindingName = "http";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBindingName, null);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.createQueue("default", inQueueHornetQName, inQueueRelativeJndiName, true);

        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");

        jmsAdminOperations.close();

        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", groupAddress, 55874);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServerWithMdbEAP7(Container container, Container jmsServer, String database) throws Exception {

        String poolName = "lodhDb";
        final String driverName = "mylodhdb";
        final String driverModuleName = "com.mylodhdb";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String groupAddress = "233.6.88.5";

        String remoteConnectorName = "http-connector-to-jms-server";
        String httpSocketBinding = "http";
        String messagingGroupSocketBindingName = "messaging-group";

        String jdbcDriverFileName = JdbcUtils.installJdbcDriverModule(container, database);
        DBAllocatorUtils dbAllocatorUtils = new DBAllocatorUtils();
        properties = dbAllocatorUtils.allocateDatabase(database);

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", jmsServer.getHostname(), jmsServer.getHttpPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, -1);
        jmsAdminOperations.setJndiNameForPooledConnectionFactory(RESOURCE_ADAPTER_NAME_EAP7, "java:jboss/DefaultJMSConnectionFactory");
        jmsAdminOperations.createHttpConnector(connectorName, httpSocketBinding, null);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, groupAddress);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.close();

        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        Random r = new Random();
        jmsAdminOperations.setNodeIdentifier(r.nextInt(9999));
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);

        if (DB2105.equalsIgnoreCase(database)) {
            /*
             <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
             <xa-datasource-property name="ServerName">vmg05.mw.lab.eng.bos.redhat.com</xa-datasource-property>
             <xa-datasource-property name="PortNumber">1521</xa-datasource-property>
             <xa-datasource-property name="DatabaseName">crashrec</xa-datasource-property>
             <xa-datasource-class>oracle.jdbc.xa.client.OracleXADataSource</xa-datasource-class>
             <driver>oracle-jdbc-driver.jar</driver>
             <security>
             <user-name>crashrec</user-name>
             <password>crashrec</password>
             </security>
             </xa-datasource>
             */
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String databaseName = properties.get("db.name");   // db.name
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);
//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";
            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("DriverType", "4");
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", databaseName);
            xaDataSourceProperties.put("PortNumber", portNumber);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

            // jmsAdminOperations.addXADatasourceProperty(poolName, "URL", url);
        } else if (ORACLE11GR2.equalsIgnoreCase(database)) {
            /*
             <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
             <xa-datasource-property name="ServerName">vmg05.mw.lab.eng.bos.redhat.com</xa-datasource-property>
             <xa-datasource-property name="PortNumber">1521</xa-datasource-property>
             <xa-datasource-property name="DatabaseName">crashrec</xa-datasource-property>
             <xa-datasource-class>oracle.jdbc.xa.client.OracleXADataSource</xa-datasource-class>
             <driver>oracle-jdbc-driver.jar</driver>
             <security>
             <user-name>crashrec</user-name>
             <password>crashrec</password>
             </security>
             </xa-datasource>
             */
            // UNCOMMENT WHEN DB ALLOCATOR IS READY

            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";

            Map<String, String> xaDataSourceProperties = new HashMap<String, String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);

            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        } else if (ORACLE12C.equalsIgnoreCase(database)) {
            /*
             <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
             <xa-datasource-property name="ServerName">vmg05.mw.lab.eng.bos.redhat.com</xa-datasource-property>
             <xa-datasource-property name="PortNumber">1521</xa-datasource-property>
             <xa-datasource-property name="DatabaseName">crashrec</xa-datasource-property>
             <xa-datasource-class>oracle.jdbc.xa.client.OracleXADataSource</xa-datasource-class>
             <driver>oracle-jdbc-driver.jar</driver>
             <security>
             <user-name>crashrec</user-name>
             <password>crashrec</password>
             </security>
             </xa-datasource>
             */
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";

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
            /*
             <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
             <xa-datasource-property name="ServerName">vmg05.mw.lab.eng.bos.redhat.com</xa-datasource-property>
             <xa-datasource-property name="PortNumber">1521</xa-datasource-property>
             <xa-datasource-property name="DatabaseName">crashrec</xa-datasource-property>
             <xa-datasource-class>oracle.jdbc.xa.client.OracleXADataSource</xa-datasource-class>
             <driver>oracle-jdbc-driver.jar</driver>
             <security>
             <user-name>crashrec</user-name>
             <password>crashrec</password>
             </security>
             </xa-datasource>
             */
            // UNCOMMENT WHEN DB ALLOCATOR IS READY
            String datasourceClassName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String url = properties.get("db.jdbc_url");
            String jdbcClassName = properties.get("db.jdbc_class");

            jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";
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
            /**
             * MYSQL DS XA DATASOURCE *
             */
            /*
             <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
             <xa-datasource-property name="ServerName">
             db01.mw.lab.eng.bos.redhat.com
             </xa-datasource-property>
             <xa-datasource-property name="PortNumber">
             3306
             </xa-datasource-property>
             <xa-datasource-property name="DatabaseName">
             crashrec
             </xa-datasource-property>
             <xa-datasource-class>com.mysql.jdbc.jdbc2.optional.MysqlXADataSource</xa-datasource-class>
             <driver>mysql55-jdbc-driver.jar</driver>
             <security>
             <user-name>crashrec</user-name>
             <password>crashrec</password>
             </security>
             </xa-datasource>
             */
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
//            <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
//            <xa-datasource-property name="ServerName">db14.mw.lab.eng.bos.redhat.com</xa-datasource-property>
//            <xa-datasource-property name="PortNumber">5432</xa-datasource-property>
//            <xa-datasource-property name="DatabaseName">crashrec</xa-datasource-property>
//            <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
//            <driver>postgresql92-jdbc-driver.jar</driver>
//            <security>
//            <user-name>crashrec</user-name>
//            <password>crashrec</password>
//            </security>
//            </xa-datasource>
            //recovery-password=crashrec, recovery-username=crashrec
            // http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/torServlet?operation=alloc&label=$DATABASE&expiry=800&requestee=jbm_$JOB_NAME"

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
//            <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
//            <xa-datasource-property name="SelectMethod">
//                    cursor
//                    </xa-datasource-property>
//            <xa-datasource-property name="ServerName">
//                    db06.mw.lab.eng.bos.redhat.com
//                    </xa-datasource-property>
//            <xa-datasource-property name="PortNumber">
//                    1433
//                    </xa-datasource-property>
//            <xa-datasource-property name="DatabaseName">
//                    crashrec
//                    </xa-datasource-property>
//            <xa-datasource-class>com.microsoft.sqlserver.jdbc.SQLServerXADataSource</xa-datasource-class>
//            <driver>mssql2012-jdbc-driver.jar</driver>
//            <xa-pool>
//            <is-same-rm-override>false</is-same-rm-override>
//            </xa-pool>
//            <security>
//            <user-name>crashrec</user-name>
//            <password>crashrec</password>
//            </security>
//            </xa-datasource>

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
//            <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
//            <xa-datasource-property name="SelectMethod">
//                    cursor
//                    </xa-datasource-property>
//            <xa-datasource-property name="ServerName">
//                    db06.mw.lab.eng.bos.redhat.com
//                    </xa-datasource-property>
//            <xa-datasource-property name="PortNumber">
//                    1433
//                    </xa-datasource-property>
//            <xa-datasource-property name="DatabaseName">
//                    crashrec
//                    </xa-datasource-property>
//            <xa-datasource-class>com.microsoft.sqlserver.jdbc.SQLServerXADataSource</xa-datasource-class>
//            <driver>mssql2012-jdbc-driver.jar</driver>
//            <xa-pool>
//            <is-same-rm-override>false</is-same-rm-override>
//            </xa-pool>
//            <security>
//            <user-name>crashrec</user-name>
//            <password>crashrec</password>
//            </security>
//            </xa-datasource>

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
//            <xa-datasource jndi-name="java:jboss/xa-datasources/CrashRecoveryDS" pool-name="CrashRecoveryDS" enabled="true">
//            <xa-datasource-property name="SelectMethod">
//                    cursor
//                    </xa-datasource-property>
//            <xa-datasource-property name="ServerName">
//                    db06.mw.lab.eng.bos.redhat.com
//                    </xa-datasource-property>
//            <xa-datasource-property name="PortNumber">
//                    1433
//                    </xa-datasource-property>
//            <xa-datasource-property name="DatabaseName">
//                    crashrec
//                    </xa-datasource-property>
//            <xa-datasource-class>com.microsoft.sqlserver.jdbc.SQLServerXADataSource</xa-datasource-class>
//            <driver>mssql2012-jdbc-driver.jar</driver>
//            <xa-pool>
//            <is-same-rm-override>false</is-same-rm-override>
//            </xa-pool>
//            <security>
//            <user-name>crashrec</user-name>
//            <password>crashrec</password>
//            </security>
//            </xa-datasource>

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

        jmsAdminOperations.close();
        container.stop();
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
        File target = new File("/tmp/DbUtilServlet.war");
        if (target.exists()) {
            target.delete();
        }
        dbUtilServlet.as(ZipExporter.class).exportTo(target, true);

        return dbUtilServlet;
    }

    public List<String> printAll(Container container) throws Exception {
        boolean wasStarted = true;
        List<String> messageIds = new ArrayList<String>();

        try {
            if (!ContainerUtils.isStarted(container)) {
                container.start();
                wasStarted = false;
            }
            container.deploy(dbUtilServlet);
            String url = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=printAll";
            logger.info("Calling servlet: " + url);
            String response = HttpRequest.get(url, 120, TimeUnit.SECONDS);

            StringTokenizer st = new StringTokenizer(response, ",");
            while (st.hasMoreTokens()) {
                messageIds.add(st.nextToken());
            }

            logger.info("Number of records: " + messageIds.size());

        } catch (Exception ex)  {
            logger.error("Calling print all records failed: ",ex);
        } finally {
            container.undeploy(dbUtilServlet);
        }
        if (!wasStarted) {
            container.stop();
        }
        return messageIds;
    }

    public int countRecords(Container container) throws Exception {
        boolean wasStarted = true;
        int numberOfRecords = -1;
        try {
            if (!ContainerUtils.isStarted(container)) {
                container.start();
                wasStarted = false;
            }
            container.deploy(dbUtilServlet);

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
        } finally {
            container.undeploy(dbUtilServlet);
        }
        if (!wasStarted) {
            container.stop();
        }
        return numberOfRecords;
    }

    public void deleteRecords(Container container) throws Exception {
        boolean wasStarted = true;
        try {
            if (!ContainerUtils.isStarted(container)) {
                container.start();
                wasStarted = false;
            }
            container.deploy(dbUtilServlet);
            String url = "http://" + container.getHostname() + ":" + container.getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=deleteRecords";
            logger.info("Calling servlet: " + url);
            String response = HttpRequest.get(url, 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
        } finally {
            container.undeploy(dbUtilServlet);
        }

        if (!wasStarted) {
            container.stop();
        }
    }

}

