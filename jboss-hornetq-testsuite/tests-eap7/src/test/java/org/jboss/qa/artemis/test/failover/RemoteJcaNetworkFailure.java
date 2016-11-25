package org.jboss.qa.artemis.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.MdbToDBAndRemoteInOutQueue;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by okalman on 6.5.16.
 */
@RunWith(Arquillian.class)
public class RemoteJcaNetworkFailure extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaNetworkFailure.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

//    public final Archive mdbToQueueAndDb = getSimpleMdb();
//    public final Archive dbUtilServlet = createDbUtilServlet();

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    private ControllableProxy proxyToJmsServer1, proxyToJmsServer3, proxyToJmsServer1Cluster, proxyToJmsServer3Cluster;
    protected int proxyToJmsServer1Port = 43812;
    protected int proxyToJmsServer3Port = 43822;
    private int proxyToJmsServer1ClusterPort = 43912;
    private int proxyToJmsServer3ClusterPort = 43922;
    private String proxyListenIpAddress="127.0.0.1";
    private Map<String, String> databaseProperties = null;
    private String database = ORACLE11GR2;
    private String poolName = "lodhDb";
    private final Archive dbUtilServlet = createDbUtilServlet();
    private int maxCountRecordsRetry=10;




    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleNetworkFailureMdbToDbAndQueueTestCase() throws Exception{
        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }
        logger.info("WAIT FOR CLUSTER");
        Thread.sleep(60000);
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();


        container(4).deploy(getMdbToDBAndQueue());
        container(2).deploy(getMdbToDBAndQueue());

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);


        if(!stopAllProxies()){
            Assert.fail("Proxies failed to stop");
        }

        Thread.sleep(60000);
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }

        int counter=0;

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(610000, container(1));
        Thread.sleep(10000);
        while(countRecords(container(2),dbUtilServlet)!=NUMBER_OF_MESSAGES_PER_PRODUCER && counter<maxCountRecordsRetry){
            Thread.sleep(10000);
            counter++;
        }
        logger.info("Print lost messages:");
        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1),outQueueJndiName);
        receiver.start();
        receiver.join();
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> listOfReceivedMessages = new ArrayList<String>();
        for (Map<String, String> m : receiver.getListOfReceivedMessages()){
            listOfReceivedMessages.add(m.get("inMessageId"));
        }
        List<String> messagesInDb=printAll(container(2));
        List<String> lostMessagesInDb = checkLostMessages(listOfSentMessages, messagesInDb);
        for (String m : lostMessagesInDb) {
            logger.info("Lost Message in DB: " + m);
        }
        List<String> lostMessagesInQueue = checkLostMessages(listOfSentMessages,listOfReceivedMessages);
        for (String m : lostMessagesInQueue) {
            logger.info("Lost Message in queue: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDb.size());
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, listOfReceivedMessages.size());

        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

        stopAllProxies();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void jmsServerNetworkFailureMdbToDbAndQueueTestCase() throws Exception{
        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }
        logger.info("WAIT FOR CLUSTER");
        Thread.sleep(60000);
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();


        container(4).deploy(getMdbToDBAndQueue());
        container(2).deploy(getMdbToDBAndQueue());

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);


        if(!stopClusterProxies()){
            Assert.fail("Proxies failed to stop");
        }
        proxyToJmsServer1.stop();
        Thread.sleep(60000);
        if(!startClusterProxies()){
            Assert.fail("Proxies failed to start");
        }
        proxyToJmsServer1.start();
        int counter=0;

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(610000, container(1));
        Thread.sleep(10000);
        while(countRecords(container(2),dbUtilServlet)!=NUMBER_OF_MESSAGES_PER_PRODUCER && counter<maxCountRecordsRetry){
            Thread.sleep(10000);
            counter++;
        }
        logger.info("Print lost messages:");
        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1),outQueueJndiName);
        receiver.start();
        receiver.join();
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> listOfReceivedMessages = new ArrayList<String>();
        for (Map<String, String> m : receiver.getListOfReceivedMessages()){
            listOfReceivedMessages.add(m.get("inMessageId"));
        }
        List<String> messagesInDb=printAll(container(2));
        List<String> lostMessagesInDb = checkLostMessages(listOfSentMessages, messagesInDb);
        for (String m : lostMessagesInDb) {
            logger.info("Lost Message in DB: " + m);
        }
        List<String> lostMessagesInQueue = checkLostMessages(listOfSentMessages,listOfReceivedMessages);
        for (String m : lostMessagesInQueue) {
            logger.info("Lost Message in queue: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDb.size());
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, listOfReceivedMessages.size());

        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();
        stopAllProxies();

    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void jmsServerNetworkFailureMdbToDbTestCase() throws Exception{
        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }
        logger.info("WAIT FOR CLUSTER");
        Thread.sleep(60000);
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();


        container(4).deploy(getSimpleMdb());
        container(2).deploy(getSimpleMdb());

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);


        if(!stopClusterProxies()){
            Assert.fail("Proxies failed to stop");
        }
        proxyToJmsServer1.stop();
        Thread.sleep(60000);
        if(!startClusterProxies()){
            Assert.fail("Proxies failed to start");
        }
        proxyToJmsServer1.start();
        int counter=0;

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, container(1));
        Thread.sleep(10000);
        while(countRecords(container(2),dbUtilServlet)!=NUMBER_OF_MESSAGES_PER_PRODUCER && counter<maxCountRecordsRetry){
            Thread.sleep(10000);
            counter++;
        }
        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> messagesInDb=printAll(container(2));
        List<String> lostMessages = checkLostMessages(listOfSentMessages, messagesInDb);
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDb.size());
        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

        stopAllProxies();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleNetworkFailureMdbToDbTestCase() throws Exception{

        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(3).start();
        container(2).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }
        logger.info("WAIT FOR CLUSTER");
        Thread.sleep(60000);
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();
        container(4).deploy(getSimpleMdb());
        container(2).deploy(getSimpleMdb());

        logger.info("MDB deployed, wait to process some messages");
        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);
        if(!stopAllProxies()){
            Assert.fail("Proxies failed to stop");
        }
        Thread.sleep(60000);
        if(!startAllProxies()){
            Assert.fail("Proxies failed to start");
        }
        new JMSTools().waitForMessages(inQueueName, 0 , 120000, container(1));
        new JMSTools().waitForMessages(inQueueName, 0 , 120000, container(3));
        int counter=0;

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(600000, container(1));
        Thread.sleep(10000);
        while(countRecords(container(2),dbUtilServlet)!=NUMBER_OF_MESSAGES_PER_PRODUCER && counter<maxCountRecordsRetry){
            Thread.sleep(10000);
            counter++;
        }
        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> messagesInDb=printAll(container(2));
        List<String> lostMessages = checkLostMessages(listOfSentMessages, messagesInDb);
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDb.size());
        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

        stopAllProxies();

    }

    public void prepareProxyServers(){
        proxyToJmsServer1 = new SimpleProxyServer(container(1).getHostname(),container(1).getHttpPort(),proxyToJmsServer1Port);
        proxyToJmsServer3 = new SimpleProxyServer(container(3).getHostname(),container(3).getHttpPort(), proxyToJmsServer3Port);
        proxyToJmsServer1Cluster = new SimpleProxyServer(container(1).getHostname(), 5445, proxyToJmsServer1ClusterPort);
        proxyToJmsServer3Cluster = new SimpleProxyServer(container(3).getHostname(), 5445+container(3).getPortOffset(), proxyToJmsServer3ClusterPort);
    }

    private void prepareJmsServerEAP7(Container container, int proxyPort, int clusterProxyPort, int remoteServerProxy){

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String fakeBindingForProxy = "proxy-binding";
        String fakeBindingForClusterProxy = "cluster-proxy-binding";
        String remoteSocketBinding = "remote-socket-binding";
        String remoteNettyConnector = "netty-remote-connector";
        String nettyConnectorForCluster = "netty-connector";
        String nettyAcceptorForCluster = "netty-acceptor";
        String messagingBinging = "messaging";
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeHttpConnector(connectorName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");

        //create fake bindings
        jmsAdminOperations.addAddressSettings("default","#","PAGE",10485760,0,0,2097152,"jms.queue.ExpiryQueue","jms.queue.DLQ",-1);
        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForProxy,"127.0.0.1",proxyPort);
        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForClusterProxy,"127.0.0.1",clusterProxyPort);

        jmsAdminOperations.addSocketBinding(messagingBinging, 5445);
        jmsAdminOperations.createRemoteConnector(nettyConnectorForCluster, fakeBindingForClusterProxy, null);
        jmsAdminOperations.createRemoteAcceptor(nettyAcceptorForCluster, messagingBinging, null);
        jmsAdminOperations.addRemoteSocketBinding(remoteSocketBinding, proxyListenIpAddress, remoteServerProxy);
        jmsAdminOperations.createRemoteConnector(remoteNettyConnector,remoteSocketBinding,null);
        jmsAdminOperations.createHttpConnector(connectorName, fakeBindingForProxy,null);
        jmsAdminOperations.setStaticClusterConnections("default",clusterGroupName,"#",true,10,1,true,nettyConnectorForCluster,remoteNettyConnector);
        jmsAdminOperations.createQueue(inQueueName,inQueueJndiName);
        jmsAdminOperations.createQueue(outQueueName,outQueueJndiName);
        jmsAdminOperations.setClusterUserPassword("heslo");




        jmsAdminOperations.close();
        container.stop();
    }
    private void prepareMDBServerEAP7(Container container, int proxyToJmsServer) throws Exception{
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String pooledConnectionFactoryName = "activemq-ra";

        String remoteSocketBinding = "remote-socket-binding";
        String remoteHttpConnector = "http-remote-connector";
        String jndiPooledConnectionFactory="java:/JmsXA java:jboss/DefaultJMSConnectionFactory "+Constants.TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.addRemoteSocketBinding(remoteSocketBinding, proxyListenIpAddress, proxyToJmsServer);
        jmsAdminOperations.createHttpConnector(remoteHttpConnector,remoteSocketBinding,null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteHttpConnector);
        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName,false);
        jmsAdminOperations.setJndiNameForPooledConnectionFactory(pooledConnectionFactoryName,jndiPooledConnectionFactory);

      //  jmsAdminOperations.setLoadbalancingPolicyOnPooledConnectionFactory(pooledConnectionFactoryName,"org.apache.activemq.artemis.api.core.client.loadbalance.FirstElementConnectionLoadBalancingPolicy");

        jmsAdminOperations.close();
        container.stop();
        setupDataSourceForMdbServer(container);
    }

    private void setupDataSourceForMdbServer(Container container) throws Exception{

        container.start();
        JdbcUtils.installJdbcDriverModule(container, database);
        JMSOperations jmsAdminOperations = container.getJmsOperations();
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
        xaDataSourceProperties.put("ServerName", serverName);
        xaDataSourceProperties.put("DatabaseName", "crashrec");
        xaDataSourceProperties.put("PortNumber", portNumber);
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
        jmsAdminOperations.close();
        container.stop();

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

    @Before
    public void allocateDatabase() throws  Exception{
        databaseProperties = DBAllocatorUtils.allocateDatabase(database);
    }

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

    private boolean startAllProxies(){
        try {
            logger.info("Starting all proxies");
            proxyToJmsServer1.start();
            proxyToJmsServer3.start();
            proxyToJmsServer1Cluster.start();
            proxyToJmsServer3Cluster.start();
            logger.info("All proxies started");
            return true;
        }catch (Exception e){
            logger.info("All proxies startup failed");
            logger.error(e.getMessage());
            return false;
        }
    }
    private boolean startMdbProxies(){
        try {
            logger.info("Starting MDB proxies");
            proxyToJmsServer1.start();
            proxyToJmsServer3.start();
            logger.info("MDB proxies started");
            return true;
        }catch (Exception e){
            logger.info("MDB proxies startup failed");
            logger.error(e.getMessage());
            return false;
        }
    }

    private boolean startClusterProxies(){
        try {
            logger.info("Starting MDB proxies");
            proxyToJmsServer1Cluster.start();
            proxyToJmsServer3Cluster.start();
            logger.info("MDB proxies started");
            return true;
        }catch (Exception e){
            logger.info("MDB proxies startup failed");
            logger.error(e.getMessage());
            return false;
        }
    }

    private boolean stopAllProxies(){
        try {
            logger.info("Stop All proxies");
            proxyToJmsServer1.stop();
            proxyToJmsServer3.stop();
            proxyToJmsServer1Cluster.stop();
            proxyToJmsServer3Cluster.stop();
            logger.info("Proxies stopped");
            return true;
        }catch (Exception e){
            logger.info("Proxies stop failed");
            logger.error(e.getMessage());
            return false;
        }
    }

    private boolean stopMdbProxies(){
        try {
            logger.info("Stop mdb proxies");
            proxyToJmsServer1.stop();
            proxyToJmsServer3.stop();
            logger.info("Mdb proxies stopped");
            return true;
        }catch (Exception e){
            logger.info("Mdb proxies stop failed");
            logger.error(e.getMessage());
            return false;
        }
    }

    private boolean stopClusterProxies(){
        try {
            logger.info("Stop cluster proxies");
            proxyToJmsServer1Cluster.stop();
            proxyToJmsServer3Cluster.stop();
            logger.info("Cluster proxies stopped");
            return true;
        }catch (Exception e){
            logger.info("Cluster proxies stop failed");
            logger.error(e.getMessage());
            return false;
        }
    }





    public Archive getSimpleMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(SimpleMdbToDb.class);
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

    public Archive getMdbToDBAndQueue() {
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
        return messageIds;
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

    public int countRecords(Container container, Archive dbServlet) throws Exception {
        boolean wasStarted = true;
        int numberOfRecords = -1;
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
        } finally {
            container.undeploy(dbServlet);
        }
        return numberOfRecords;
    }

}
