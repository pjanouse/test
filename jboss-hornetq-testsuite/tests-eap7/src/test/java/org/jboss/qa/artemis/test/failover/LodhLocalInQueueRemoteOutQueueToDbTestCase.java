package org.jboss.qa.artemis.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.JMSTools;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by okalman on 16.5.16.
 */
public class LodhLocalInQueueRemoteOutQueueToDbTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(LodhLocalInQueueRemoteOutQueueToDbTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

//    public final Archive mdbToQueueAndDb = getSimpleMdb();
//    public final Archive dbUtilServletForOutQueueCluster = createDbUtilServlet();

    // queue to send messages in
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    private ControllableProxy proxyToOutQueueServer2, proxyToOutQueueServer4, proxyToJmsServer1Cluster, proxyToJmsServer2Cluster,
            proxyToJmsServer3Cluster, proxyToJmsServer4Cluster, proxyToDbForInQueueServer1,proxyToDbForInQueueServer3,
            proxyToDbForOutQueueServer2,proxyToDbForOutQueueServer4;
    protected int proxyToOutQueueServer2Port = 43812;
    protected int proxyToOutQueueServer4Port = 43822;
    private int proxyToJmsServer1ClusterPort = 43912;
    private int proxyToJmsServer2ClusterPort = 43922;
    private int proxyToJmsServer3ClusterPort = 43932;
    private int proxyToJmsServer4ClusterPort = 43942;
    private int proxyToDbForInQueueServer1Port = 43741;
    private int proxyToDbForInQueueServer3Port = 43743;
    private int proxyToDbForOutQueueServer2Port = 43742;
    private int proxyToDbForOutQueueServer4Port = 43744;
    private String proxyListenIpAddress="127.0.0.1";
    private Map<String, String> databaseProperties = null;
    private String database = ORACLE11GR2;
    private String poolName = "lodhDb";
    private final Archive dbUtilServletForOutQueueCluster = createDbUtilServletForOutQueueCluster();
    private final Archive dbUtilServletForInQueueCluster = createDbUtilServletForInQueueCluster();
    private int maxCountRecordsRetry=10;




    public void prepareProxyServers(){
        proxyToOutQueueServer2 = new SimpleProxyServer(container(2).getHostname(),container(2).getHttpPort(), proxyToOutQueueServer2Port);
        proxyToOutQueueServer4 = new SimpleProxyServer(container(4).getHostname(),container(4).getHttpPort(), proxyToOutQueueServer4Port);
        proxyToJmsServer1Cluster = new SimpleProxyServer(container(1).getHostname(), 5445, proxyToJmsServer1ClusterPort);
        proxyToJmsServer2Cluster = new SimpleProxyServer(container(2).getHostname(), 5445+container(2).getPortOffset(), proxyToJmsServer2ClusterPort);
        proxyToJmsServer3Cluster = new SimpleProxyServer(container(3).getHostname(), 5445+container(3).getPortOffset(), proxyToJmsServer3ClusterPort);
        proxyToJmsServer4Cluster = new SimpleProxyServer(container(4).getHostname(), 5445+container(4).getPortOffset(), proxyToJmsServer4ClusterPort);

    }

    public void prepareDatabaseProxyServers(String databaseHost, int databasePort){
        proxyToDbForInQueueServer1 = new SimpleProxyServer(databaseHost, databasePort, proxyToDbForInQueueServer1Port);
        proxyToDbForOutQueueServer2 = new SimpleProxyServer(databaseHost, databasePort, proxyToDbForOutQueueServer2Port);
        proxyToDbForInQueueServer3 = new SimpleProxyServer(databaseHost, databasePort, proxyToDbForInQueueServer3Port);
        proxyToDbForOutQueueServer4 = new SimpleProxyServer(databaseHost, databasePort, proxyToDbForOutQueueServer4Port);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mdbServerNetworkFailureTestCase() throws Exception{
        prepareProxyServers();
        prepareServers();
        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();

        Assert.assertTrue("Failed to start proxies",startAllProxies());
        deleteRecords(container(2),dbUtilServletForOutQueueCluster);
        deleteRecords(container(2),dbUtilServletForInQueueCluster);
        Assert.assertEquals("Cleanup of databases failed",0,printAll(container(2),dbUtilServletForInQueueCluster).size()+printAll(container(2),dbUtilServletForOutQueueCluster).size());
        logger.info("Wait for cluster");
        Thread.sleep(90000);
        List<String> listOfSentMessages = sendMessagesToInQueueCluster();

        container(2).deploy(getMdbForOutQueueCluster());
        container(4).deploy(getMdbForOutQueueCluster());
        container(1).deploy(getMdbForInQueueCluster());
        container(3).deploy(getMdbForInQueueCluster());
        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/4, 120000);
        proxyToDbForOutQueueServer2.stop();
        proxyToOutQueueServer2.stop();
        proxyToJmsServer4Cluster.stop();
        proxyToJmsServer2Cluster.stop();
        Thread.sleep(60000);
        proxyToDbForOutQueueServer2.start();
        proxyToOutQueueServer2.start();
        proxyToJmsServer4Cluster.start();
        proxyToJmsServer2Cluster.start();
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(700000, container(1),0,false);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(700000, container(3),0,false);
        Thread.sleep(10000);
        List<String> messagesInDbInQueue=printAll(container(2),dbUtilServletForInQueueCluster);
        List<String> messagesInDbOutQueue=printAll(container(2),dbUtilServletForOutQueueCluster);
        List<String> lostMessagesInDbInQueue = checkLostMessages(listOfSentMessages, messagesInDbInQueue);
        for (String m : lostMessagesInDbInQueue) {
            logger.info("Lost Message in DB from InQueue: " + m);
        }
        List<String> lostMessagesInDbOutQueue = checkLostMessages(listOfSentMessages, messagesInDbOutQueue);
        for (String m : lostMessagesInDbOutQueue) {
            logger.info("Lost Message in DB from OutQueue: " + m);
        }
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));

        Assert.assertEquals("Number of messages sent to InQueue differs from DB row count", NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDbInQueue.size());
        Assert.assertEquals("Number of messages sent to OutQueue differs from DB row count", NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDbOutQueue.size());


        container(1).stop();
        container(3).stop();
        container(2).stop();
        container(4).stop();
        stopAllProxies();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleNetworkFailureTestCase() throws Exception{
        prepareProxyServers();
        prepareServers();
        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        Assert.assertTrue("Failed to start proxies",startAllProxies());
        deleteRecords(container(2),dbUtilServletForOutQueueCluster);
        deleteRecords(container(2),dbUtilServletForInQueueCluster);
        Assert.assertEquals("Cleanup of databases failed",0,printAll(container(2),dbUtilServletForInQueueCluster).size()+printAll(container(2),dbUtilServletForOutQueueCluster).size());
        logger.info("Wait for cluster");
        Thread.sleep(90000);
        List<String> listOfSentMessages = sendMessagesToInQueueCluster();

        container(2).deploy(getMdbForOutQueueCluster());
        container(4).deploy(getMdbForOutQueueCluster());
        container(1).deploy(getMdbForInQueueCluster());
        container(3).deploy(getMdbForInQueueCluster());
        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/4, 120000);
        Assert.assertTrue("Failed to start proxies",stopAllProxies());
        Thread.sleep(60000);
        Assert.assertTrue("Failed to start proxies",startAllProxies());
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(700000, container(1),0,false);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(700000, container(3),0,false);
        Thread.sleep(10000);
        List<String> messagesInDbInQueue=printAll(container(2),dbUtilServletForInQueueCluster);
        List<String> messagesInDbOutQueue=printAll(container(2),dbUtilServletForOutQueueCluster);
        List<String> lostMessagesInDbInQueue = checkLostMessages(listOfSentMessages, messagesInDbInQueue);
        for (String m : lostMessagesInDbInQueue) {
            logger.info("Lost Message in DB from InQueue: " + m);
        }
        List<String> lostMessagesInDbOutQueue = checkLostMessages(listOfSentMessages, messagesInDbOutQueue);
        for (String m : lostMessagesInDbOutQueue) {
            logger.info("Lost Message in DB from OutQueue: " + m);
        }
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));

        Assert.assertEquals("Number of messages sent to InQueue differs from DB row count", NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDbInQueue.size());
        Assert.assertEquals("Number of messages sent to OutQueue differs from DB row count", NUMBER_OF_MESSAGES_PER_PRODUCER, messagesInDbOutQueue.size());
        container(1).stop();
        container(3).stop();
        container(2).stop();
        container(4).stop();
        stopAllProxies();
    }

    public List<String> sendMessagesToInQueueCluster() throws Exception{
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        ProducerTransAck producer1 = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER/2);
        ProducerTransAck producer2 = new ProducerTransAck(container(3),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER/2);
        producer1.setCommitAfter(500);
        producer2.setCommitAfter(500);
        producer1.setMessageBuilder(messageBuilder);
        producer2.setMessageBuilder(messageBuilder);
        producer1.setTimeout(0);
        producer2.setTimeout(0);
        producer1.start();
        producer2.start();
        producer1.join();
        producer2.join();
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer1.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        for (Map<String, String> m : producer2.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        return listOfSentMessages;

    }

    private void prepareServers() throws Exception{

        prepareInQueueClusterServerEAP7(container(1),proxyToOutQueueServer2Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort);
        prepareInQueueClusterServerEAP7(container(3),proxyToOutQueueServer4Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort);
        prepareOutQueueClusterServerEAP7(container(2), proxyToOutQueueServer2Port, proxyToJmsServer2ClusterPort, proxyToJmsServer4ClusterPort);
        prepareOutQueueClusterServerEAP7(container(4), proxyToOutQueueServer4Port, proxyToJmsServer4ClusterPort, proxyToJmsServer2ClusterPort);
    }


    private void prepareInQueueClusterServerEAP7(Container container, int proxyToJmsServer, int clusterProxyPort, int remoteClusterServerProxy)throws Exception{

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String fakeBindingForProxy = "proxy-binding";
        String fakeBindingForClusterProxy = "cluster-proxy-binding";
        String remoteSocketBinding = "remote-socket-binding";
        String remoteNettyConnector = "netty-remote-connector";
        String nettyConnectorForCluster = "netty-connector";
        String nettyAcceptorForCluster = "netty-acceptor";
        String remoteHttpConnector = "remote-http-connector";
        String messagingBinging = "messaging";
        String pooledConnectionFactoryName = "activemq-ra";
        String jndiPooledConnectionFactory="java:/JmsXA "+Constants.TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME;


        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");

        //create fake bindings
        jmsAdminOperations.addAddressSettings("default","#","PAGE",10485760,0,0,2097152,"jms.queue.ExpiryQueue","jms.queue.DLQ",-1);
        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForClusterProxy,"127.0.0.1",clusterProxyPort);

        jmsAdminOperations.addSocketBinding(messagingBinging, 5445);
        jmsAdminOperations.createRemoteConnector(nettyConnectorForCluster, fakeBindingForClusterProxy, null);
        jmsAdminOperations.createRemoteAcceptor(nettyAcceptorForCluster, messagingBinging, null);
        jmsAdminOperations.addRemoteSocketBinding(remoteSocketBinding, proxyListenIpAddress, remoteClusterServerProxy);
        jmsAdminOperations.createRemoteConnector(remoteNettyConnector,remoteSocketBinding,null);
        jmsAdminOperations.setStaticClusterConnections("default",clusterGroupName,"#",true,10,1,true,nettyConnectorForCluster,remoteNettyConnector);
        jmsAdminOperations.createQueue(inQueueName,inQueueJndiName);
        jmsAdminOperations.setClusterUserPassword("heslo1");

        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForProxy, proxyListenIpAddress, proxyToJmsServer);
        jmsAdminOperations.createHttpConnector(remoteHttpConnector,fakeBindingForProxy,null);
//        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteHttpConnector);
//        jmsAdminOperations.setHaForPooledConnectionFactory(pooledConnectionFactoryName,false);
        jmsAdminOperations.setJndiNameForPooledConnectionFactory(pooledConnectionFactoryName,"java:jboss/DefaultJMSConnectionFactory");
        jmsAdminOperations.close();
        container.stop();
        container.start();
        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createPooledConnectionFactory("outbound-cf", jndiPooledConnectionFactory, remoteHttpConnector);
        




        jmsAdminOperations.close();
        container.stop();
        setupDataSourceForMdbServer(container);
    }
    private void prepareOutQueueClusterServerEAP7(Container container, int proxyPort, int clusterProxyPort, int remoteClusterServerProxy) throws Exception{
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String fakeBindingForClusterProxy = "cluster-proxy-binding";
        String fakeBindingForProxy = "proxy-binding";
        String remoteNettyConnector = "netty-remote-connector";
        String remoteSocketBinding = "remote-socket-binding";
        String connectorName = "http-connector";
        String nettyConnectorForCluster = "netty-connector";
        String nettyAcceptorForCluster = "netty-acceptor";
        String messagingBinging = "messaging";



        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.removeHttpConnector(connectorName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.addAddressSettings("default","#","PAGE",10485760,0,0,2097152,"jms.queue.ExpiryQueue","jms.queue.DLQ",-1);
        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForClusterProxy,"127.0.0.1",clusterProxyPort);
        jmsAdminOperations.addSocketBinding(messagingBinging, 5445);
        jmsAdminOperations.createRemoteConnector(nettyConnectorForCluster, fakeBindingForClusterProxy, null);
        jmsAdminOperations.createRemoteAcceptor(nettyAcceptorForCluster, messagingBinging, null);
        jmsAdminOperations.addRemoteSocketBinding(remoteSocketBinding, proxyListenIpAddress, remoteClusterServerProxy);
        jmsAdminOperations.createRemoteConnector(remoteNettyConnector,remoteSocketBinding,null);
        jmsAdminOperations.setStaticClusterConnections("default",clusterGroupName,"#",true,10,1,true,nettyConnectorForCluster,remoteNettyConnector);
        jmsAdminOperations.addRemoteSocketBinding(fakeBindingForProxy,"127.0.0.1",proxyPort);
        jmsAdminOperations.createQueue(inQueueName,inQueueJndiName);
        jmsAdminOperations.createHttpConnector(connectorName, fakeBindingForProxy,null);


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
        String serverName = "127.0.0.1";
        String portNumber;
        if(container == container(1)){
            portNumber = String.valueOf(proxyToDbForInQueueServer1Port);
        }else if (container == container(2)){
            portNumber = String.valueOf(proxyToDbForOutQueueServer2Port);
        }else if (container == container(3)){
            portNumber = String.valueOf(proxyToDbForInQueueServer3Port);
        }else{
            portNumber = String.valueOf(proxyToDbForOutQueueServer4Port);
        }

        String url = databaseProperties.get("db.jdbc_url");
        String[] dburl=url.split(":");
        dburl[3]="@localhost";
        dburl[4]=portNumber;
        url="";
        for(int i=0; i<dburl.length; i++){
            if(i<dburl.length-1) {
                url = url + dburl[i] + ":";
            }else {
                url = url + dburl[i];
            }
        }
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
        DBAllocatorUtils dbAllocatorUtils = new DBAllocatorUtils();
        databaseProperties = dbAllocatorUtils.allocateDatabase(database);
        String host = databaseProperties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
        int portNumber = Integer.parseInt(databaseProperties.get("db.port")); // db.port=5432
        prepareDatabaseProxyServers(host,portNumber);

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
            proxyToOutQueueServer2.start();
            proxyToOutQueueServer4.start();
            proxyToJmsServer1Cluster.start();
            proxyToJmsServer2Cluster.start();
            proxyToJmsServer3Cluster.start();
            proxyToJmsServer4Cluster.start();
            proxyToDbForInQueueServer1.start();
            proxyToDbForInQueueServer3.start();
            proxyToDbForOutQueueServer2.start();
            proxyToDbForOutQueueServer4.start();
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
            proxyToOutQueueServer2.start();
            proxyToOutQueueServer4.start();
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
            proxyToOutQueueServer2.stop();
            proxyToOutQueueServer4.stop();
            proxyToJmsServer1Cluster.stop();
            proxyToJmsServer2Cluster.stop();
            proxyToJmsServer3Cluster.stop();
            proxyToJmsServer4Cluster.stop();
            proxyToDbForInQueueServer1.stop();
            proxyToDbForOutQueueServer2.stop();
            proxyToDbForInQueueServer3.stop();
            proxyToDbForOutQueueServer4.stop();
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
            proxyToOutQueueServer2.stop();
            proxyToOutQueueServer4.stop();
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





    public Archive getMdbForOutQueueCluster() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(SimpleMdbToDb.class);
        mdbJar.addClasses(Constants.class);
        mdbJar.addClasses(MessageInfo.class);
        mdbJar.addClasses(JMSImplementation.class);
        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdb1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    public Archive getMdbForInQueueCluster() {
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

        } catch (Exception ex)  {
            logger.error("Calling print all records failed: ",ex);
        } finally {
            container.undeploy(dbServlet);
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

    public WebArchive createDbUtilServletForInQueueCluster() {

        final WebArchive dbUtilServlet = ShrinkWrap.create(WebArchive.class, "dbUtilServletForOutQueueCluster.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>dbUtilServletForOutQueueCluster</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.DbUtilServletForMessageInfo1Table</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>dbUtilServletForOutQueueCluster</servlet-name>\n");
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

    public WebArchive createDbUtilServletForOutQueueCluster() {

        final WebArchive dbUtilServlet = ShrinkWrap.create(WebArchive.class, "dbUtilServletForOutQueueCluster.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append("<servlet><servlet-name>dbUtilServletForOutQueueCluster</servlet-name>\n");
        webXml.append("<servlet-class>org.jboss.qa.hornetq.apps.servlets.DbUtilServlet</servlet-class></servlet>\n");
        webXml.append("<servlet-mapping><servlet-name>dbUtilServletForOutQueueCluster</servlet-name>\n");
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
