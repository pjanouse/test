// TODO JUST ADD DB
package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.MdbToDBAndRemoteInOutQueue;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.*;
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
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * This is modified lodh 2 (kill/shutdown mdb servers) test case which is
 * testing remote jca  and have remote inqueue and outqueue (on different servers).
 * This test can work with EAP 6/7.
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails Test case simulates network failures and capability to recover with XA transaction.
 * There are 3 servers and Oracle database. Server 1 has deployed queue InQueue and 3 servers queue OutQueue. Server 2 is connected
 * to servers 1 and 3 through resource adapter. MDB deployed to server 2 is resending messaging from InQueue to OutQuee and also inserts
 * row to Oracle database in XA transaction. During this process network is disconnected for more than 30s and connected again.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class LodhNetworkFailureTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(LodhNetworkFailureTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

    public final Archive mdbToQueueAndDb = getDeployment();
    public final Archive dbUtilServlet = createDbUtilServlet();

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    ControllableProxy proxyToInServer;
    protected int proxyToInServerPort = 43812;

    ControllableProxy proxyToOutServer;
    protected int proxyToOutServerPort = 43815;

    ControllableProxy proxyToDB;
    protected int proxyToDBPort = 43816;

    int defaultPortForMessagingSocketBinding = 5445;

    FinalTestMessageVerifier messageVerifier = new MdbMessageVerifier();

    private Map<String, String> databaseProperties = null;

    /**
     *
     * @tpTestDetails There are 3 servers and Oracle database. Server 1 has deployed queue InQueue and 3 servers queue OutQueue. Server 2 is connected
     * to servers 1 and 3 through resource adapter. MDB deployed to server 2 is resending messaging from InQueue to OutQuee and also inserts
     * row to Oracle database in XA transaction. During this process network is disconnected for more than 30s and connected again.
     * @tpProcedure <ul>
     *     <li>start node 1 with queue InQueue</li>
     *     <li>start node 3 with queue OutQueueu</li>
     *     <li>start node 2 with deployed MDB which receives messages from InQueue and sends to OutQueue and inserts rows to database</li>
     *     <li>producer sends 1000 small and large messages to InQueue</li>
     *     <li>wait for producer to finish</li>
     *     <li>disconnect from network node-1 while MDB is processing messages</li>
     *     <li>restore network for node-1</li>
     *     <li>wait until all messages are processed</li>
     *     <li>start Consumer which consumes messages form outQueue</li>
     * </ul>
     * @tpPassCrit there is the same number of sent and received messages
     */
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testLodhWithNetworkFail() throws Exception {
        testNetworkFailureWithRemoteJCA(container(1), container(3));
    }

    /**
     * @throws Exception
     */
    public void testNetworkFailureWithRemoteJCA(Container inServer, Container outServer) throws Exception {

        prepareRemoteJcaTopology(inServer, outServer);
        container(1).start();
        container(3).start();
        startProxies();
        container(2).start();

        // clean up database
        deleteRecords();
        countRecords();

        ProducerTransAck producer1 = new ProducerTransAck(inServer, inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//        ClientMixMessageBuilder builder = new ClientMixMessageBuilder(10, 110);
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        producer1.setMessageBuilder(messageBuilder);
        producer1.setMessageVerifier(messageVerifier);
        producer1.setTimeout(0);
        producer1.setCommitAfter(1000);
        producer1.start();
        producer1.join();

        // deploy mdbs
        container(2).deploy(mdbToQueueAndDb);

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER / 5, 120000, container(3));

        logger.info("Some messages were received. Disconnect network.");
        stopProxies();

        // wait more than 30s to break connections
        Thread.sleep(70000);

        logger.info("Reconnect network.");
        startProxies();

        new JMSTools().waitForMessages(outQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER, 300000, container(3));

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));

        // set longer timeouts so xa recovery is done at least once
        ReceiverTransAck receiver1 = new ReceiverTransAck(outServer, outQueueJndiName, 3000, 10, 10);
        receiver1.setMessageVerifier(messageVerifier);
        receiver1.setCommitAfter(1000);
        receiver1.start();
        receiver1.join();

        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer1.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }

        logger.info("Number of sent messages: " + (listOfSentMessages.size()
                + ", Producer to jms1 server sent: " + listOfSentMessages.size() + " messages"));
        logger.info("Number of received messages: " + (receiver1.getListOfReceivedMessages().size()
                + ", Consumer from jms1 server received: " + receiver1.getListOfReceivedMessages().size() + " messages"));

        List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll());
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, countRecords());

        Assert.assertTrue("Test failed: ", messageVerifier.verifyMessages());
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        Assert.assertTrue("Receivers did not get any messages.",
                receiver1.getCount() > 0);

        container(2).undeploy(mdbToQueueAndDb.getName());

        container(2).stop();
        container(1).stop();
        container(3).stop();
        stopProxies();
    }


    /**
     * Executes kill sequence.
     *
     * @param failureSequence  list of containers
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeFailureSequence(List<Container> failureSequence, long timeBetweenKills, boolean isShutdown) throws InterruptedException {

        if (isShutdown) {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.stop();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container : failureSequence) {
                Thread.sleep(timeBetweenKills);
                container.kill();
                Thread.sleep(3000);
                logger.info("Start server: " + container.getName());
                container.start();
                logger.info("Server: " + container.getName() + " -- STARTED");
            }
        }
    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
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
            response = HttpRequest.get("http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=dealloc&uuid="
                    + databaseProperties.get("uuid"), 20, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Database could not be deallocated. Response: " + response, e);

        }
        logger.trace("Response from deallocating database is: " + response);
    }

    public void prepareRemoteJcaTopology(Container inServer, Container outServer) throws Exception {
        if (inServer.getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopologyEAP6(inServer, outServer);
        } else {
            prepareRemoteJcaTopologyEAP7(inServer, outServer);
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7(Container inServer, Container outServer) throws Exception {

        prepareJmsServerEAP7(container(1));
        prepareMdbServerEAP7(container(2), inServer, outServer);
        prepareJmsServerEAP7(container(3));

        copyApplicationPropertiesFiles();
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Container inServer, Container outServer) throws Exception {

        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), inServer, outServer);
        prepareJmsServerEAP6(container(3));

        copyApplicationPropertiesFiles();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.close();

        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String broadCastGroupName = "bg-group1";
        String discoveryGroupName = "dg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String acceptorName = "netty";
        String messagingGroupSocketBindingForConnector = "messaging";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
        jmsAdminOperations.close();
        container.stop();
        container.start();
        jmsAdminOperations = container.getJmsOperations();
        // add connector with BIO
        jmsAdminOperations.removeRemoteConnector(connectorName);
        jmsAdminOperations.createRemoteConnector(connectorName, messagingGroupSocketBindingForConnector, null);
        // add acceptor wtih BIO
        Map<String, String> acceptorParams = new HashMap<String, String>();
        jmsAdminOperations.removeRemoteAcceptor(acceptorName);
        jmsAdminOperations.createRemoteAcceptor(acceptorName, messagingGroupSocketBindingForConnector, null);
        jmsAdminOperations.close();

        container.stop();
    }


    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Container inServer, Container outServer) throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String toInServerRemoteConnectorName = "netty-remote-in";
        String toOutServerRemoteConnectorName = "netty-remote-out";
        String toInServerSocketBinding = "messaging-remote-in";
        String toOutServerSocketBinding = "messaging-remote-out";

        String poolName = "lodhDb";
        String database = ORACLE11GR2;
        String jdbcDriverFileName = JdbcUtils.downloadJdbcDriver(container, database);
        DBAllocatorUtils dbAllocatorUtils = new DBAllocatorUtils();
        databaseProperties = dbAllocatorUtils.allocateDatabase(database);


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);

        // enable trace logging
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");
        jmsAdminOperations.addLoggerCategory("oracle", "TRACE");

        // both are remote
        String proxyListenIpAdress = "127.0.0.1";
        jmsAdminOperations.addRemoteSocketBinding(toInServerSocketBinding, proxyListenIpAdress, proxyToInServerPort);
        jmsAdminOperations.createRemoteConnector(toInServerRemoteConnectorName, toInServerSocketBinding, null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP6, toInServerRemoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP6, -1);

        // create JMSXAOut pooled connection factory pointing to outServer
        jmsAdminOperations.addRemoteSocketBinding(toOutServerSocketBinding, proxyListenIpAdress, proxyToOutServerPort);
        jmsAdminOperations.createRemoteConnector(toOutServerRemoteConnectorName, toOutServerSocketBinding, null);

        jmsAdminOperations.close();
        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createPooledConnectionFactory(Constants.TO_OUT_SERVER_CONNECTION_FACTORY_NAME, Constants.TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME,
                toOutServerRemoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.TO_OUT_SERVER_CONNECTION_FACTORY_NAME, -1);


        // setup datasource for Oracle DB
        String datasourceClassName = databaseProperties.get("datasource.class.xa"); // datasource.class.xa
        String serverName = databaseProperties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
        String portNumber = databaseProperties.get("db.port"); // db.port=5432
        String url = databaseProperties.get("db.jdbc_url");

        jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, true, false, jdbcDriverFileName, "TRANSACTION_READ_COMMITTED",
                datasourceClassName, false, true);
        jmsAdminOperations.addXADatasourceProperty(poolName, "ServerName", proxyListenIpAdress);
        jmsAdminOperations.addXADatasourceProperty(poolName, "PortNumber", String.valueOf(proxyToDBPort));
        jmsAdminOperations.addXADatasourceProperty(poolName, "DatabaseName", "crashrec");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");
        jmsAdminOperations.addXADatasourceProperty(poolName, "URL", url);
        jmsAdminOperations.setXADatasourceAtribute(poolName, "min-pool-size", String.valueOf(1));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "max-pool-size", String.valueOf(30));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "idle-timeout-minutes", String.valueOf(15));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "valid-connection-checker-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker");
//        jmsAdminOperations.setXADatasourceAtribute(poolName, "exception-sorter-class-name", "org.jboss.resource.adapter.jdbc.vendor.OracleExceptionSorter");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "set-tx-query-timeout", String.valueOf(true));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "validate-on-match", String.valueOf(true));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "prepared-statements-cache-size", String.valueOf(30));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "share-prepared-statements", String.valueOf(true));


        setupProxies(inServer.getHostname(), inServer.getHornetqPort(), proxyToInServerPort,
                outServer.getHostname(), outServer.getHornetqPort(), proxyToOutServerPort,
                serverName, Integer.valueOf(portNumber), proxyToDBPort);

        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container inServer, Container outServer) throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String toInServerRemoteConnectorName = "netty-remote-in";
        String toOutServerRemoteConnectorName = "netty-remote-out";
        String toInServerSocketBinding = "messaging-remote-in";
        String toOutServerSocketBinding = "messaging-remote-out";

        String poolName = "lodhDb";
        String database = ORACLE11GR2;
//        String jdbcDriverFileName = JdbcUtils.downloadJdbcDriver(container, database);
        DBAllocatorUtils dbAllocatorUtils = new DBAllocatorUtils();
        databaseProperties = dbAllocatorUtils.allocateDatabase(database);

        JdbcUtils.installJdbcDriverModule(container, database);

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);

        // enable trace logging
//        jmsAdminOperations.addLoggerCategory("org.apache.activemq.artemis", "TRACE");
//        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");
//        jmsAdminOperations.addLoggerCategory("oracle", "TRACE");

        // both are remote
        String proxyListenIpAdress = "127.0.0.1";
        jmsAdminOperations.addRemoteSocketBinding(toInServerSocketBinding, proxyListenIpAdress, proxyToInServerPort);
        jmsAdminOperations.createRemoteConnector(toInServerRemoteConnectorName, toInServerSocketBinding, null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, toInServerRemoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);

        // create JMSXAOut pooled connection factory pointing to outServer
        jmsAdminOperations.addRemoteSocketBinding(toOutServerSocketBinding, proxyListenIpAdress, proxyToOutServerPort);
        jmsAdminOperations.createRemoteConnector(toOutServerRemoteConnectorName, toOutServerSocketBinding, null);

        jmsAdminOperations.close();
        container.restart();
        jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createPooledConnectionFactory(Constants.TO_OUT_SERVER_CONNECTION_FACTORY_NAME, Constants.TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME,
                toOutServerRemoteConnectorName);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.TO_OUT_SERVER_CONNECTION_FACTORY_NAME, -1);




        // setup datasource for Oracle DB
        String datasourceClassName = databaseProperties.get("datasource.class.xa"); // datasource.class.xa
        String serverName = databaseProperties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
        String portNumber = databaseProperties.get("db.port"); // db.port=5432
        String url = databaseProperties.get("db.jdbc_url");
        String jdbcClassName = databaseProperties.get("db.jdbc_class");
        final String driverName = "mylodhdb";
        final String driverModuleName = "com.mylodhdb";

        jmsAdminOperations.createJDBCDriver(driverName, driverModuleName, jdbcClassName, datasourceClassName);

        Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
        xaDataSourceProperties.put("ServerName", proxyListenIpAdress);
        xaDataSourceProperties.put("DatabaseName", "crashrec");
        xaDataSourceProperties.put("PortNumber", String.valueOf(proxyToDBPort));
        xaDataSourceProperties.put("URL", url);
        jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                datasourceClassName, false, true, xaDataSourceProperties);

        jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", "crashrec");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "password", "crashrec");

        jmsAdminOperations.setXADatasourceAtribute(poolName, "min-pool-size", String.valueOf(1));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "max-pool-size", String.valueOf(30));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "idle-timeout-minutes", String.valueOf(15));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "valid-connection-checker-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "exception-sorter-class-name", "org.jboss.resource.adapter.jdbc.vendor.OracleExceptionSorter");
        jmsAdminOperations.setXADatasourceAtribute(poolName, "set-tx-query-timeout", String.valueOf(true));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "validate-on-match", String.valueOf(true));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "prepared-statements-cache-size", String.valueOf(30));
        jmsAdminOperations.setXADatasourceAtribute(poolName, "share-prepared-statements", String.valueOf(true));

        setupProxies(inServer.getHostname(),defaultPortForMessagingSocketBinding + inServer.getPortOffset(), proxyToInServerPort,
                outServer.getHostname(), defaultPortForMessagingSocketBinding + outServer.getPortOffset(), proxyToOutServerPort,
                serverName, Integer.valueOf(portNumber), proxyToDBPort);
        jmsAdminOperations.close();
        container.stop();

    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
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

    protected void setupProxies(String serverInHostname, int serverInHornetQport, int proxyToInServerPort,
                                String serverOutHostname, int serverOutHornetQport, int proxyToOutServerPort,
                                String dbHostname, int dbPort, int proxyToDBPort) {

        logger.info("Create proxy pointing to inServer: " + serverInHostname + ":" + serverInHornetQport + " and listenning on: 127.0.0.1:" + proxyToInServerPort );
        proxyToInServer = new SimpleProxyServer(serverInHostname, serverInHornetQport, proxyToInServerPort);
        logger.info("Create proxy pointing to outServer: " + serverOutHostname + ":" + serverOutHornetQport + " and listenning on: 127.0.0.1:" + proxyToOutServerPort );
        proxyToOutServer = new SimpleProxyServer(serverOutHostname, serverOutHornetQport, proxyToOutServerPort);
        logger.info("Create proxy pointing to database: " + dbHostname + ":" + dbPort + " and listenning on: 127.0.0.1:" + proxyToDBPort );
        proxyToDB = new SimpleProxyServer(dbHostname, dbPort, proxyToDBPort);
    }

    protected void startProxies() throws Exception {
        if (proxyToInServer == null || proxyToOutServer == null || proxyToDB == null) {
            throw new NullPointerException("Proxies were not setup. Call setupProxies(...) first.");
        }

        logger.info("Start all proxies.");
        proxyToInServer.start();
        proxyToOutServer.start();
        proxyToDB.start();
        logger.info("All proxies started.");

    }

    protected void stopProxies() throws Exception {
        if (proxyToInServer == null || proxyToOutServer == null || proxyToDB == null) {
            throw new NullPointerException("Proxies were not setup. Call setupProxies(...) first.");
        }

        logger.info("Stop all proxies.");
        proxyToDB.stop();
        proxyToInServer.stop();
        proxyToOutServer.stop();
        logger.info("All proxies stopped.");
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

    public Archive getDeployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbToDBAndRemoteInOutQueue.class);
        mdbJar.addClasses(Constants.class);
        mdbJar.addClasses(MessageInfo.class);
        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
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

    public List<String> printAll() throws Exception {

        List<String> messageIds = new ArrayList<String>();

        try {
            container(2).deploy(dbUtilServlet);
            String response = HttpRequest.get("http://" + container(1).getHostname() + ":" + container(2).getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=printAll", 120, TimeUnit.SECONDS);

            StringTokenizer st = new StringTokenizer(response, ",");
            while (st.hasMoreTokens()) {
                messageIds.add(st.nextToken());
            }

            logger.info("Number of records: " + messageIds.size());

        } finally {
            container(2).undeploy(dbUtilServlet);
        }

        return messageIds;
    }

    public int countRecords() throws Exception {
        int numberOfRecords = -1;
        try {
            container(2).deploy(dbUtilServlet);

            String response = HttpRequest.get("http://" + container(1).getHostname() + ":" + container(2).getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=countAll", 60, TimeUnit.SECONDS);
            container(2).undeploy(dbUtilServlet);

            logger.info("Response is: " + response);

            StringTokenizer st = new StringTokenizer(response, ":");

            while (st.hasMoreTokens()) {
                if (st.nextToken().contains("Records in DB")) {
                    numberOfRecords = Integer.valueOf(st.nextToken().trim());
                }
            }
            logger.info("Number of records " + numberOfRecords);
        } finally {
            container(2).undeploy(dbUtilServlet);
        }
        return numberOfRecords;
    }
    public void deleteRecords() throws Exception {
        try {
            container(2).deploy(dbUtilServlet);
            String response = HttpRequest.get("http://" + container(1).getHostname() + ":" + container(2).getHttpPort() + "/DbUtilServlet/DbUtilServlet?op=deleteRecords", 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
        } finally {
            container(2).undeploy(dbUtilServlet);
        }
    }
}