package org.jboss.qa.hornetq.test.failover;

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
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.HttpRequest;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.PrintJournal;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class Lodh5TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh5TestCase.class);
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 15000;
    // queue to send messages in 
    static String inQueueHornetQName = "InQueue";
    static String inQueueRelativeJndiName = "jms/queue/" + inQueueHornetQName;
    static boolean topologyCreated = false;

    /**
     * This mdb reads messages from remote InQueue
     *
     * @return
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

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    public void testFail() throws Exception {

        prepareServer();

        controller.start(CONTAINER1);

        deleteRecords();
        countRecords();

        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, 4447, inQueueRelativeJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer.setMessageBuilder(new InfoMessageBuilder());
        producer.start();
        producer.join();

        deployer.deploy("mdbToDb");

        Thread.sleep(30000);

        for (int i = 0; i < 1; i++) {

            killServer(CONTAINER1);
            controller.kill(CONTAINER1);
            PrintJournal.printJournal(CONTAINER1, "journal_content_after_kill1.txt");
            controller.start(CONTAINER1);
            PrintJournal.printJournal(CONTAINER1, "journal_content_after_restart2.txt");
            Thread.sleep(10000);
        }
        // 5 min
        long howLongToWait = 300000;
        long startTime = System.currentTimeMillis();
        while (countRecords() < NUMBER_OF_MESSAGES_PER_PRODUCER && (System.currentTimeMillis() - startTime) < howLongToWait) {
            Thread.sleep(5000);
        }
        PrintJournal.printJournal(CONTAINER1, "journal_content_before_shutdown3.txt");

        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String,String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll());
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(NUMBER_OF_MESSAGES_PER_PRODUCER, countRecords());
        deployer.undeploy("mdbToDb");
        stopServer(CONTAINER1);
        PrintJournal.printJournal(CONTAINER1, "journal_content_after_shutdown4.txt");

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
     * Delete also the journal directory.
     *
     * @throws Exception
     */
    @Before
    @After
    public void stopAllServers() throws Exception {

        stopServer(CONTAINER1);

    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */

    public void prepareServer() throws Exception {

        if (!topologyCreated) {
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

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);

        if ("oracle11gr2".equalsIgnoreCase(database)) {


            /** ORACLE 11GR2 XA DATASOURCE **/
            File oracleModuleDir = new File("src/test/resources/org/jboss/hornetq/configuration/modules/oracle");
            logger.info("source: " + oracleModuleDir.getAbsolutePath());
            File targetDir = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "modules" + File.separator
                    + "com" + File.separator + "oracle");
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(oracleModuleDir, targetDir);

            jmsAdminOperations.createJDBCDriver("oracle", "com.oracle.db", "oracle.jdbc.driver.OracleDriver", "oracle.jdbc.xa.client.OracleXADataSource");
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", "lodhDb", false, false, "oracle", "TRANSACTION_READ_COMMITTED",
                    "oracle.jdbc.xa.client.OracleXADataSource", false, true);
//        jmsAdminOperations.addXADatasourceProperty("lodhDb", "URL", "jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=vmg27-vip.mw.lab.eng.bos.redhat.com)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=vmg28-vip.mw.lab.eng.bos.redhat.com)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=qarac.jboss)))");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "URL", "jdbc:oracle:thin:@db04.mw.lab.eng.bos.redhat.com:1521:qaora11");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "User", "MESSAGING");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "Password", "MESSAGING");
            jmsAdminOperations.addDatasourceProperty("lodhDb", "min-pool-size", "10"); //<min-pool-size>10</min-pool-size>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "max-pool-size", "20"); // <max-pool-size>20</max-pool-size>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "pool-prefill", "true"); // <prefill>true</prefill>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "pool-use-strict-min", "false"); //<use-strict-min>false</use-strict-min>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "flush-strategy", "FailingConnectionOnly"); //<flush-strategy>FailingConnectionOnly</flush-strategy>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "valid-connection-checker-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker"); //<valid-connection-checker class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker"/>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "validate-on-match", "false"); //<validate-on-match>false</validate-on-match>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "background-validation", "false"); //<background-validation>false</background-validation>
//        jmsAdminOperations.addDatasourceProperty("lodhDb", "query-timeout", "true"); //<set-tx-query-timeout>true</set-tx-query-timeout>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "blocking-timeout-wait-millis", "30000"); //<blocking-timeout-millis>30000</blocking-timeout-millis>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "idle-timeout-minutes", "30"); //<idle-timeout-minutes>30</idle-timeout-minutes>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "prepared-statements-cache-size", "32"); //<prepared-statement-cache-size>32</prepared-statement-cache-size>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "exception-sorter-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter"); //<exception-sorter class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter"/>
            jmsAdminOperations.addDatasourceProperty("lodhDb", "use-try-lock", "60"); //<use-try-lock>60</use-try-lock>
        } else if ("mysql55".equalsIgnoreCase(database)) {
            /** MYSQL DS XA DATASOURCE **/
            /**
             * <xa-datasource jndi-name="java:jboss/datasources/MySqlXADS" enabled="true" use-java-context="true" pool-name="MySqlXADS">
             <xa-datasource-property name="ServerName">localhost</xa-datasource-property>
             <xa-datasource-property name="DatabaseName">test</xa-datasource-property>
             <xa-datasource-property name="User">root</xa-datasource-property>
             <xa-datasource-property name="Password"></xa-datasource-property>
             <driver>
             mysql
             </driver>
             </xa-datasource>
             */
            File mysqlModuleDir = new File("src/test/resources/com/mysql");
            logger.info("source: " + mysqlModuleDir.getAbsolutePath());
            File targetDir = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "modules" + File.separator
                    + "com" + File.separator + "mysql");
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(mysqlModuleDir, targetDir);
            jmsAdminOperations.createJDBCDriver("mysql", "com.mysql.jdbc", "com.mysql.jdbc.Driver", "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource");
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", "lodhDb", true, false, "mysql", "TRANSACTION_READ_COMMITTED",
                    "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource", false, true);
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "ServerName", "db01.mw.lab.eng.bos.redhat.com");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "DatabaseName", "messaging");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "User", "messaging");
            jmsAdminOperations.addXADatasourceProperty("lodhDb", "Password", "messaging");
            jmsAdminOperations.setNodeIdentifier(14);
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);
        jmsAdminOperations.addLoggerCategory("com.arjuna", "TRACE");
        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");
        jmsAdminOperations.addLoggerCategory("oracle", "TRACE");
//        jmsAdminOperations.setLoggingLevelForConsole("TRACE");

        jmsAdminOperations.setNodeIdentifier(23);

        jmsAdminOperations.createQueue("default", inQueueHornetQName, inQueueRelativeJndiName, true);

        jmsAdminOperations.close();

        controller.stop(containerName);

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
            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=printAll", 20, TimeUnit.SECONDS);
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

            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=countAll", 30, TimeUnit.SECONDS);
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
            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=deleteRecords", 20, TimeUnit.SECONDS);

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
