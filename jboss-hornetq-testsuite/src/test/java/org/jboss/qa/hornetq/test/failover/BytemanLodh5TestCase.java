package org.jboss.qa.hornetq.test.failover;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.HttpRequest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.settings.DataSourceProperties;
import org.jboss.qa.hornetq.tools.jms.settings.DataSourceProperties.DsKeys;
import org.jboss.qa.hornetq.tools.jms.settings.DataSourceProperties.XaKeys;
import org.jboss.qa.hornetq.tools.jms.settings.JmsServerSettings;
import org.jboss.qa.hornetq.tools.jms.settings.SettingsBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * @author mnovak@redhat.com
 * @author msvehla@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class BytemanLodh5TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(BytemanLodh5TestCase.class);
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    //private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 15000;

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 15;
    // queue to send messages in 

    private static final String IN_QUEUE_NAME = "InQueue";

    private static final String IN_QUEUE = "jms/queue/" + IN_QUEUE_NAME;

    static boolean topologyCreated = false;


    /**
     * This mdb reads messages from remote InQueue
     *
     * @return test artifact with MDBs
     */
    @Deployment(managed = false, testable = false, name = "mdbToDb")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDb.jar");
        mdbJar.addClass(SimpleMdbToDb.class);
        mdbJar.addClass(MessageInfo.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        File target = new File("/tmp/mdbtodb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction start",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "start",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionStart() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction start",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "start",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionStart() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction end",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "end",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionEnd() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction end",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "end",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionEnd() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction prepare",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "prepare",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillOnTransactionPrepare() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction prepare",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "prepare",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    public void testServerKillAfterTransactionPrepare() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "commit",
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    /*@BMRule(name = "server kill on transaction commit",
     targetClass = "com.arjuna.ats.arjuna.coordinator.BasicAction",
     targetMethod = "phase2Commit",
     action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")*/
    public void testServerKillOnTransactionCommit() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "server kill on transaction commit",
            targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
            targetMethod = "commit",
            isAfter = true,
            action = "traceStack(\"!!!!! Killing server NOW !!!!!\\n\"); killJVM();")
    //targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
    public void testServerKillAfterTransactionCommit() throws Exception {
        this.testFail();
        topologyCreated = false;
    }


    public void testFail() throws Exception {

        try {
            logger.info("!!!!! preparing server !!!!!");
            prepareServer();

            controller.start(CONTAINER1);

            logger.info("!!!!! deleting data in DB !!!!!");
            deleteRecords();
            countRecords();

            logger.info("!!!!! sending messages !!!!!");
            ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, 4447, IN_QUEUE,
                    NUMBER_OF_MESSAGES_PER_PRODUCER);

            producer.setMessageBuilder(new InfoMessageBuilder());
            producer.start();
            producer.join();

            logger.info("!!!!! installing byteman rules !!!!!");
            //HornetQCallsTracking.installTrackingRules(CONTAINER1_IP, BYTEMAN_CONTAINER1_PORT);
            RuleInstaller.installRule(this.getClass(), CONTAINER1_IP, BYTEMAN_CONTAINER1_PORT);

            logger.info("!!!!! deploying MDB !!!!!");
            try {
                this.deployer.deploy("mdbToDb");
            } catch (Exception e) {
                // byteman might kill the server before control returns back here from deploy method, which results
                // in arquillian exception; it's safe to ignore, everything is deployed and running correctly on the server
                logger.debug("Arquillian got an exception while deploying", e);
            }

            //Thread.sleep(30000);
            Thread.sleep(30000);

            controller.kill(CONTAINER1);
//            PrintJournal.printJournal(CTRACEONTAINER1, "journal_content_after_kill1.txt");
            logger.info("!!!!! starting server again !!!!!");
            controller.start(CONTAINER1);
//            PrintJournal.printJournal(CONTAINER1, "journal_content_after_restart2.txt");
            Thread.sleep(10000);

            // 5 min
            logger.info("!!!!! waiting for MDB !!!!!");
            long howLongToWait = 300000;
            long startTime = System.currentTimeMillis();
            while (countRecords() < NUMBER_OF_MESSAGES_PER_PRODUCER && (System.currentTimeMillis() - startTime)
                    < howLongToWait) {
                Thread.sleep(10000);
            }
//        PrintJournal.printJournal(CONTAINER1, "journal_content_before_shutdown3.txt");

            logger.info("Print lost messages:");
            List<String> listOfSentMessages = new ArrayList<String>();
            for (Map<String, String> m : producer.getListOfSentMessages()) {
                listOfSentMessages.add(m.get("messageId"));
            }
            List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll());
            for (String m : lostMessages) {
                logger.info("Lost Message: " + m);
            }
            Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, countRecords());
        } finally {
            deployer.undeploy("mdbToDb");
            stopServer(CONTAINER1);
//        PrintJournal.printJournal(CONTAINER1, "journal_content_after_shutdown4.txt");
        }

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


    /**
     * Be sure that both of the servers are stopped before and after the test.
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
    }


    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareServer() throws Exception {

        if (!topologyCreated) {
            //prepareJmsServer(CONTAINER1, CONTAINER1_IP, "oracle11gr2");
            prepareJmsServer(CONTAINER1, CONTAINER1_IP, "mysql55");
            topologyCreated = true;
        }
    }


    /**
     * Prepares jms server for remote jca topology.
     *
     * @param containerName  Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(String containerName, String bindingAddress, String database) throws IOException {
        controller.start(containerName);
        SettingsBuilder builder = JmsServerSettings
                .forContainer(JmsServerSettings.ContainerType.EAP6_WITH_HORNETQ,
                CONTAINER1, this.getArquillianDescriptor())
                .withClustering(bindingAddress)
                .withPersistence()
                .withPaging(50 * 1024, 1024)
                .withTransactionIdentifier(23);

        DataSourceProperties dsProps = null;
        if ("oracle11gr2".equalsIgnoreCase(database)) {
            File oracleModuleDir = new File("src/test/resources/org/jboss/hornetq/configuration/modules/oracle");
            logger.info("source: " + oracleModuleDir.getAbsolutePath());
            File targetDir = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "modules" + File.separator
                    + "system" + File.separator + "layers" + File.separator + "base" + File.separator
                    + "com" + File.separator + "oracle");
            //jboss-eap-6.1/modules/system/layers/base
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(oracleModuleDir, targetDir);

            dsProps = DataSourceProperties.forDataSource("lodhDS")
                    .withXaProperty(XaKeys.URL, "jdbc:oracle:thin:@db04.mw.lab.eng.bos.redhat.com:1521:qaora11")
                    .withXaProperty(XaKeys.USER, "MESSAGING")
                    .withXaProperty(XaKeys.PASSWORD, "MESSAGING")
                    .withDataSourceProperty(DsKeys.TRANSACTION_ISOLATION, "TRANSACTION_READ_COMMITTED")
                    .withDataSourceProperty(DsKeys.NO_TX_SEPARATE_POOL, "true")
                    .withDataSourceProperty(DsKeys.MIN_POOL_SIZE, "10")
                    .withDataSourceProperty(DsKeys.MAX_POOL_SIZE, "20")
                    .withDataSourceProperty(DsKeys.POOL_PREFILL, "true")
                    .withDataSourceProperty(DsKeys.POOL_USE_STRICT_MIN, "false")
                    .withDataSourceProperty(DsKeys.FLUSH_STRATEGY, "FailingConnectionOnly")
                    .withDataSourceProperty(DsKeys.VALID_CONNECTION_CHECKER_CLASS_NAME,
                    "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker")
                    .withDataSourceProperty(DsKeys.VALIDATE_ON_MATCH, "false")
                    .withDataSourceProperty(DsKeys.BACKGROUND_VALIDATION, "false")
                    .withDataSourceProperty(DsKeys.BLOCKING_TIMEOUT_WAIT_MILLIS, "30000")
                    .withDataSourceProperty(DsKeys.IDLE_TIMEOUT_MINUTES, "30")
                    .withDataSourceProperty(DsKeys.PREPARED_STATEMENTS_CACHE_SIZE, "32")
                    .withDataSourceProperty(DsKeys.EXCEPTION_SORTER_CLASS_NAME,
                    "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter")
                    .withDataSourceProperty(DsKeys.USE_TRY_LOCK, "60")
                    .create();

            builder.withJdbcDriver("oracle", "com.oracle.db", "oracle.jdbc.driver.OracleDriver",
                    "oracle.jdbc.xa.client.OracleXADataSource")
                    .withXaDataSource("java:/jdbc/lodhDS", "lodhDb", "oracle",
                    "oracle.jdbc.xa.client.OracleXADataSource", dsProps);
        } else if ("mysql55".equalsIgnoreCase(database)) {
            File mysqlModuleDir = new File("src/test/resources/com/mysql");
            logger.info("source: " + mysqlModuleDir.getAbsolutePath());
            File targetDir = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "modules"
                    + File.separator + "system" + File.separator + "layers" + File.separator + "base" + File.separator
                    + "com" + File.separator + "mysql");
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(mysqlModuleDir, targetDir);

            dsProps = DataSourceProperties.forDataSource("lodhDS")
                    .withDataSourceProperty(DsKeys.TRANSACTION_ISOLATION, "TRANSACTION_READ_COMMITTED")
                    .withXaProperty(XaKeys.SERVER_NAME, "db01.mw.lab.eng.bos.redhat.com")
                    .withXaProperty(XaKeys.DATABASE_NAME, "messaging")
                    .withXaProperty(XaKeys.USER, "messaging")
                    .withXaProperty(XaKeys.PASSWORD, "messaging")
                    .create();

            builder.withJdbcDriver("mysql", "com.mysql.jdbc", "com.mysql.jdbc.Driver",
                    "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource")
                    .withXaDataSource("java:/jdbc/lodhDS", "lodhDS", "mysql",
                    "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource", dsProps);
        }

        builder.withQueue(IN_QUEUE_NAME, true).create();
        this.controller.stop(CONTAINER1);
    }


    @Deployment(managed = false, testable = false, name = "dbUtilServlet")
    @TargetsContainer(CONTAINER1)
    public static WebArchive createDbUtilServlet() {

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


    public List<String> printAll() throws Exception {

        List<String> messageIds = new ArrayList<String>();

        try {
            deployer.deploy("dbUtilServlet");
            String response = HttpRequest.get("http://" + CONTAINER1_IP
                    + ":8080/DbUtilServlet/DbUtilServlet?op=printAll", 20, TimeUnit.SECONDS);
            deployer.undeploy("dbUtilServlet");

            logger.info("Print all messages: " + response);

            StringTokenizer st = new StringTokenizer(response, ",");

            while (st.hasMoreTokens()) {
                messageIds.add(st.nextToken());
            }

            logger.info("Number of records: " + messageIds.size());

        } finally {
            deployer.undeploy("dbUtilServlet");
        }

        return messageIds;
    }


    public int countRecords() throws Exception {
        int numberOfRecords = -1;
        try {
            deployer.deploy("dbUtilServlet");

            String response = HttpRequest.get("http://" + CONTAINER1_IP
                    + ":8080/DbUtilServlet/DbUtilServlet?op=countAll", 30, TimeUnit.SECONDS);
            deployer.undeploy("dbUtilServlet");

            logger.info("Response is: " + response);

            StringTokenizer st = new StringTokenizer(response, ":");

            while (st.hasMoreTokens()) {
                if (st.nextToken().contains("Records in DB")) {
                    numberOfRecords = Integer.valueOf(st.nextToken().trim());
                }
            }
            logger.info("Number of records " + numberOfRecords);
        } finally {
            deployer.undeploy("dbUtilServlet");
        }

        return numberOfRecords;
    }

    //    public int insertRecords() throws Exception {
//        deployer.deploy("dbUtilServlet");
//        String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=insertRecord", 10, TimeUnit.SECONDS);
//        deployer.undeploy("dbUtilServlet");
//
//        logger.info("Response is: " + response);
//
//        return 0;
//    }

    public void deleteRecords() throws Exception {
        try {
            deployer.deploy("dbUtilServlet");
            String response = HttpRequest.get("http://" + CONTAINER1_IP
                    + ":8080/DbUtilServlet/DbUtilServlet?op=deleteRecords", 20, TimeUnit.SECONDS);

            logger.info("Response is: " + response);
        } finally {
            deployer.undeploy("dbUtilServlet");
        }
    }


    public static void copyFolder(File src, File dest)
            throws IOException {

        if (src.isDirectory()) {

            //if directory not exists, create it
            if (!dest.exists()) {
                dest.mkdir();
                System.out.println("Directory copied from "
                        + src + "  to " + dest);
            }

            //list all the directory contents
            String files[] = src.list();

            for (String file : files) {
                //construct the src and dest file structure
                File srcFile = new File(src, file);
                File destFile = new File(dest, file);
                //recursive copy
                copyFolder(srcFile, destFile);
            }

        } else {
            //if file, then copy it
            //Use bytes stream to support all file types
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);

            byte[] buffer = new byte[1024];

            int length;
            //copy the file content in bytes 
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
            System.out.println("File copied from " + src + " to " + dest);
        }
    }

}
