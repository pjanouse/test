package org.jboss.qa.hornetq.test.failover;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.HttpRequest;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.PrintJournal;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh5TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh5TestCase.class);
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;

    private static final String ORACLE11GR2 = "oracle11gr2";
    private static final String MYSQL55 = "mysql55";
    private static final String POSTGRESQLPLUS92 = "postgresplus92";
    private static final String MDBTODB = "mdbToDb";

    // queue to send messages
    static String inQueueHornetQName = "InQueue";
    static String inQueueRelativeJndiName = "jms/queue/" + inQueueHornetQName;

    Map<String, String> properties;

    /**
     * This mdb reads messages from remote InQueue
     *
     * @return test artifact with MDBs
     */
    @Deployment(managed = false, testable = false, name = MDBTODB)
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeployment() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbToDb.jar");
        mdbJar.addClass(SimpleMdbToDb.class);
        mdbJar.addClass(MessageInfo.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.hornetq \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbtodb.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgrePlus() throws Exception {
        testFail(POSTGRESQLPLUS92);
    }

    /**
     * @throws Exception
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle() throws Exception {
        testFail(ORACLE11GR2);
    }

    public void testFail(String databaseName) throws Exception {

        prepareJmsServer(CONTAINER1, databaseName);

        controller.start(CONTAINER1);

        if (POSTGRESQLPLUS92.equals(databaseName)) {
            rollbackPreparedTransactions(properties.get("db.username"));
        }
        deleteRecords();
        countRecords();

        ProducerTransAck producer = new ProducerTransAck(CONTAINER1_IP, 4447, inQueueRelativeJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer.setMessageBuilder(new InfoMessageBuilder());
        producer.setCommitAfter(1000);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        deployer.deploy(MDBTODB);

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
        long howLongToWait = 250000;
        long startTime = System.currentTimeMillis();
        long lastValue = 0;
        long newValue;

        while ((newValue = countRecords()) < NUMBER_OF_MESSAGES_PER_PRODUCER && (newValue > lastValue
                || (System.currentTimeMillis() - startTime) < howLongToWait)) {
            lastValue = newValue;
            PrintJournal.printJournal(CONTAINER1, "journal_content_during_final_receive_and_recovery" + newValue + ".txt");
            Thread.sleep(10000);
        }

        PrintJournal.printJournal(CONTAINER1, "journal_content_after_recovery.txt");

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
        if (POSTGRESQLPLUS92.equals(databaseName)) {
            rollbackPreparedTransactions(properties.get("db.username"));
        }
        deployer.undeploy(MDBTODB);
        stopServer(CONTAINER1);



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
     * Prepares jms server for remote jca topology.
     *
     * @param containerName  Name of the container - defined in arquillian.xml
     *
     */
    private void prepareJmsServer(String containerName, String database) throws Exception {

        String poolName = "lodhDb";
        String postgreJdbDriver = "edb-jdbc14.jar";

        if (POSTGRESQLPLUS92.equals(database)) {
            // copy jdbc driver to deployements directory
            File postgresJdbcDriverFile = new File("src/test/resources/com/posgresql/jdbc/" + postgreJdbDriver);
            File targetDirDeployments = new File(getJbossHome(containerName) + File.separator + "standalone" + File.separator
                    + "deployments" + File.separator + postgreJdbDriver);
            copyFile(postgresJdbcDriverFile, targetDirDeployments);
        }

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);

        Random r = new Random();
        jmsAdminOperations.setNodeIdentifier(r.nextInt(9999));

        if (ORACLE11GR2.equalsIgnoreCase(database)) {

            /** ORACLE 11GR2 XA DATASOURCE **/
            File oracleModuleDir = new File("src/test/resources/org/jboss/hornetq/configuration/modules/oracle");
            logger.info("source: " + oracleModuleDir.getAbsolutePath());
            File targetDir = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "modules" + File.separator
                    + "system" + File.separator + "layers" + File.separator + "base" + File.separator
                    + "com" + File.separator + "oracle");
            //jboss-eap-6.1/modules/system/layers/base
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(oracleModuleDir, targetDir);

            jmsAdminOperations.createJDBCDriver("oracle", "com.oracle.db", "oracle.jdbc.driver.OracleDriver", "oracle.jdbc.xa.client.OracleXADataSource");
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, "oracle", "TRANSACTION_READ_COMMITTED",
                    "oracle.jdbc.xa.client.OracleXADataSource", false, true);
//        jmsAdminOperations.addXADatasourceProperty(poolName, "URL", "jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=vmg27-vip.mw.lab.eng.bos.redhat.com)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=vmg28-vip.mw.lab.eng.bos.redhat.com)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=qarac.jboss)))");
            jmsAdminOperations.addXADatasourceProperty(poolName, "URL", "jdbc:oracle:thin:@db04.mw.lab.eng.bos.redhat.com:1521:qaora11");
            jmsAdminOperations.addXADatasourceProperty(poolName, "User", "MESSAGING");
            jmsAdminOperations.addXADatasourceProperty(poolName, "Password", "MESSAGING");
            jmsAdminOperations.addXADatasourceProperty(poolName, "min-pool-size", "10"); //<min-pool-size>10</min-pool-size>
            jmsAdminOperations.addXADatasourceProperty(poolName, "max-pool-size", "20"); // <max-pool-size>20</max-pool-size>
            jmsAdminOperations.addXADatasourceProperty(poolName, "pool-prefill", "true"); // <prefill>true</prefill>
            jmsAdminOperations.addXADatasourceProperty(poolName, "pool-use-strict-min", "false"); //<use-strict-min>false</use-strict-min>
            jmsAdminOperations.addXADatasourceProperty(poolName, "flush-strategy", "FailingConnectionOnly"); //<flush-strategy>FailingConnectionOnly</flush-strategy>
            jmsAdminOperations.addXADatasourceProperty(poolName, "valid-connection-checker-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker"); //<valid-connection-checker class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker"/>
            jmsAdminOperations.addXADatasourceProperty(poolName, "validate-on-match", "false"); //<validate-on-match>false</validate-on-match>
            jmsAdminOperations.addXADatasourceProperty(poolName, "background-validation", "false"); //<background-validation>false</background-validation>
//        jmsAdminOperations.addDatasourceProperty(poolName, "query-timeout", "true"); //<set-tx-query-timeout>true</set-tx-query-timeout>
            jmsAdminOperations.addXADatasourceProperty(poolName, "blocking-timeout-wait-millis", "30000"); //<blocking-timeout-millis>30000</blocking-timeout-millis>
            jmsAdminOperations.addXADatasourceProperty(poolName, "idle-timeout-minutes", "30"); //<idle-timeout-minutes>30</idle-timeout-minutes>
            jmsAdminOperations.addXADatasourceProperty(poolName, "prepared-statements-cache-size", "32"); //<prepared-statement-cache-size>32</prepared-statement-cache-size>
            jmsAdminOperations.addXADatasourceProperty(poolName, "exception-sorter-class-name", "org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter"); //<exception-sorter class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter"/>
            jmsAdminOperations.addXADatasourceProperty(poolName, "use-try-lock", "60"); //<use-try-lock>60</use-try-lock>
        } else if (MYSQL55.equalsIgnoreCase(database)) {
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
            File targetDir = new File(getJbossHome(containerName) + File.separator + "modules"
                    + File.separator + "system" + File.separator + "layers" + File.separator + "base" + File.separator
                    + "com" + File.separator + "mysql");
            logger.info("target: " + targetDir.getAbsolutePath());
            copyFolder(mysqlModuleDir, targetDir);
            jmsAdminOperations.createJDBCDriver("mysql", "com.mysql.jdbc", "com.mysql.jdbc.Driver", "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource");
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, true, false, "mysql", "TRANSACTION_READ_COMMITTED",
                    "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource", false, true);
            jmsAdminOperations.addXADatasourceProperty(poolName, "ServerName", "db01.mw.lab.eng.bos.redhat.com");
            jmsAdminOperations.addXADatasourceProperty(poolName, "DatabaseName", "messaging");
            jmsAdminOperations.addXADatasourceProperty(poolName, "User", "messaging");
            jmsAdminOperations.addXADatasourceProperty(poolName, "Password", "messaging");
        } else if (POSTGRESQLPLUS92.equals(database)) {
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
            // http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=alloc&label=$DATABASE&expiry=800&requestee=jbm_$JOB_NAME"

            String response = null;

            // ALLOCATE DB
            try {
                response = HttpRequest.get("http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=alloc&label="
                        + POSTGRESQLPLUS92 + "&expiry=60&requestee=eap6-hornetq-lodh5", 20, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.error("Error during allocating Database.", e);
            }

            logger.info("Response is: " + response);

            Scanner lines = new Scanner(response);
            String line;
            properties = new HashMap<String, String>();
            while (lines.hasNextLine()) {
                line = lines.nextLine();
                logger.info("Print line: " + line);
                if (!line.startsWith("#")) {
                    String[] property = line.split("=");
                    properties.put((property[0]), property[1].replaceAll("\\\\", ""));
                    logger.info("Add property: " + property[0] + " " + property[1].replaceAll("\\\\", ""));
                }
            }

            String databaseName = properties.get("db.name");   // db.name
            String driverName = properties.get("datasource.class.xa"); // datasource.class.xa
            String serverName = properties.get("db.hostname"); // db.hostname=db14.mw.lab.eng.bos.redhat.com
            String portNumber = properties.get("db.port"); // db.port=5432
            String recoveryUsername = properties.get("db.username");
            String recoveryPassword = properties.get("db.password");

//            // Clean up DB
//            try {
//                HttpRequest.get("http://dballocator.mw.lab.eng.bos.redhat.com:8080/Allocator/AllocatorServlet?operation=erase&uuid=" +
//                        properties.get("uuid"), 120, TimeUnit.SECONDS);
//            } catch (TimeoutException e) {
//                throw new Exception("Error during clean up of database using DBAllocator.", e);
//            }

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, postgreJdbDriver, "TRANSACTION_READ_COMMITTED",
                    driverName, false, true);

            jmsAdminOperations.addXADatasourceProperty(poolName, "ServerName", serverName);
            jmsAdminOperations.addXADatasourceProperty(poolName, "PortNumber", portNumber);
            jmsAdminOperations.addXADatasourceProperty(poolName, "DatabaseName", databaseName);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "user-name", recoveryUsername);
            jmsAdminOperations.setXADatasourceAtribute(poolName, "password", recoveryPassword);

        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);

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

//            logger.info("Print all messages: " + response);

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

    public void rollbackPreparedTransactions(String owner) throws Exception {
        try {
            deployer.deploy("dbUtilServlet");

            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=rollbackPreparedTransactions&owner=" + owner, 30, TimeUnit.SECONDS);
            deployer.undeploy("dbUtilServlet");

            logger.info("Response is: " + response);

        } finally {
            deployer.undeploy("dbUtilServlet");
        }
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
            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/DbUtilServlet/DbUtilServlet?op=deleteRecords", 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
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
