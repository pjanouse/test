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
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
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
public class LodhNetworkFailure1 extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(LodhNetworkFailure1.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000;

//    public final Archive mdbToQueueAndDb = getDeployment();
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
    public void test() throws Exception{
        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort, false);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort, false);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startProxies()){
            Assert.fail("Proxies failed to start");
        }
        //

        Thread.sleep(60000);
//        logger.info("/////////////////////////////////////////////////////////////////////////////////////////////");
//        //
//        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
//        messageBuilder.setAddDuplicatedHeader(true);
//        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
//        producer.setCommitAfter(1000);
//        producer.setMessageBuilder(messageBuilder);
//        producer.setTimeout(0);
//        producer.start();
//        producer.join();
////        int sessionsOnServer1= container(1).getJmsOperations().countConnections();
////        int sessionsOnserver3= container(3).getJmsOperations().countConnections();
//
//
//        proxyToJmsServer3Cluster.stop();
//        proxyToJmsServer1Cluster.stop();
//
//        Thread.sleep(20000);
//        logger.info("aaaaa/////////////////////////////////////////////////////////////////////////////////////////////");
//        proxyToJmsServer3Cluster.start();
//        proxyToJmsServer1Cluster.start();
//
//
//        Thread.sleep(20000);
        container(1).stop();
        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mdbServerNetworkFailureTestCase() throws Exception{
        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort, false);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort, false);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startProxies()){
            Assert.fail("Proxies failed to start");
        }
        //


        //
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();
//        int sessionsOnServer1= container(1).getJmsOperations().countConnections();
//        int sessionsOnserver3= container(3).getJmsOperations().countConnections();
        //
        proxyToJmsServer3.stop();
        container(3).stop();
        //
        container(2).deploy(getDeployment());

        //
            Thread.sleep(10000);
            container(3).start();
            proxyToJmsServer3.start();
        int sessionsOnServer1= container(1).getJmsOperations().countConnections();
        int sessionsOnserver3= container(3).getJmsOperations().countConnections();
        //
//        container(4).deploy(getDeployment());
        logger.info("Connections on server1: " +sessionsOnServer1 + "  Connections on server3 + " + sessionsOnserver3);
        logger.info("MDB deployed, wait to process some messages");
        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);


    //    logger.info("Connections on server1: " +sessionsOnServer1 + "  Connections on server3 + " + sessionsOnserver3);
        if(!stopProxies()){
            Assert.fail("Proxies failed to stop");
        }
        Thread.sleep(60000);
        if(!startProxies()){
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
        container(1).stop();
        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
       // container(3).stop();
        container(4).stop();

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleNetworkFailureTestCase() throws Exception{

        prepareJmsServerEAP7(container(1), proxyToJmsServer1Port, proxyToJmsServer1ClusterPort, proxyToJmsServer3ClusterPort, false);
        prepareJmsServerEAP7(container(3), proxyToJmsServer3Port, proxyToJmsServer3ClusterPort, proxyToJmsServer1ClusterPort, false);

        prepareMDBServerEAP7(container(2),proxyToJmsServer1Port);
        prepareMDBServerEAP7(container(4), proxyToJmsServer3Port);
        prepareProxyServers();

        container(1).start();
        container(3).start();
        container(2).start();
        container(4).start();
        deleteRecords(container(2),dbUtilServlet);
        Assert.assertEquals("Database is not clean",0,countRecords(container(2),dbUtilServlet));
        if(!startProxies()){
            Assert.fail("Proxies failed to start");
        }
        MessageBuilder messageBuilder = new InfoMessageBuilder(100);
        messageBuilder.setAddDuplicatedHeader(true);
        ProducerTransAck producer = new ProducerTransAck(container(1),inQueueJndiName,NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setCommitAfter(1000);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.start();
        producer.join();
        container(2).deploy(getDeployment());
        container(4).deploy(getDeployment());
        logger.info("MDB deployed, wait to process some messages");
        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER/2, 120000);
        if(!stopProxies()){
            Assert.fail("Proxies failed to stop");
        }
        Thread.sleep(60000);
        if(!startProxies()){
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
        container(1).stop();
        deleteRecords(container(2),dbUtilServlet);
        container(2).stop();
        container(3).stop();
        container(4).stop();



    }

    public void prepareProxyServers(){
        proxyToJmsServer1 = new SimpleProxyServer(container(1).getHostname(),container(1).getHttpPort(),proxyToJmsServer1Port);
        proxyToJmsServer3 = new SimpleProxyServer(container(3).getHostname(),container(3).getHttpPort(), proxyToJmsServer3Port);
        proxyToJmsServer1Cluster = new SimpleProxyServer(container(1).getHostname(), 5445, proxyToJmsServer1ClusterPort);
        proxyToJmsServer3Cluster = new SimpleProxyServer(container(3).getHostname(), 5445+container(3).getPortOffset(), proxyToJmsServer3ClusterPort);
    }

    private void prepareJmsServerEAP7(Container container, int proxyPort, int clusterProxyPort, int remoteServerProxy, boolean useDifferentClusterConnector){

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
        jmsAdminOperations.setClusterUserPassword("password");

        //create fake bindings
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
        DBAllocatorUtils dbAllocatorUtils = new DBAllocatorUtils();
        databaseProperties = dbAllocatorUtils.allocateDatabase(database);
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

    private boolean startProxies(){
        try {
            logger.info("Starting proxies");
            proxyToJmsServer1.start();
            proxyToJmsServer3.start();
            proxyToJmsServer1Cluster.start();
            proxyToJmsServer3Cluster.start();
            logger.info("Proxies started");
            return true;
        }catch (Exception e){
            logger.info("Proxies startup failed");
            logger.error(e.getMessage());
            return false;
        }
    }

    private boolean stopProxies(){
        try {
            logger.info("Stop proxies");
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


    public Archive getDeployment() {
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
