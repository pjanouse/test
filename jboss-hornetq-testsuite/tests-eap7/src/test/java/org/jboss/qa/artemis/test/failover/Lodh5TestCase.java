package org.jboss.qa.artemis.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.*;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.apps.mdb.SimpleMdbToDb;
import org.jboss.qa.hornetq.apps.servlets.DbUtilServlet;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DBAllocatorUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.JdbcUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.io.File;

import org.apache.commons.io.FileUtils;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH HORNETQ RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh5/           /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh5TestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(Lodh5TestCase.class);

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

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Sybase ASE 15.7 database. Kill server when the MDB is
     * processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testSybase157() throws Exception {
        testFail(SYBASE157);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to IBM DB2 Enterprise e10.5 database. Kill server when the
     * MDB is processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.4.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDb2105() throws Exception {
        testFail(DB2105);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to PostgreSQL 9.3 database. Kill server when the MDB is
     * processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.4.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgre93() throws Exception {
        testFail(POSTGRESQL93);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to PostgreSQL 9.4 database. Kill server when the MDB is
     * processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 7.0.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgre94() throws Exception {
        testFail(POSTGRESQL94);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.3 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.4.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgrePlus93() throws Exception {
        testFail(POSTGRESQLPLUS93);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.4 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 7.0.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgrePlus94() throws Exception {
        testFail(POSTGRESQLPLUS94);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Oracle 11g R2 database. Kill server when the MDB is
     * processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle11gr2() throws Exception {
        testFail(ORACLE11GR2);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Oracle 12c database. Kill server when the MDB is
     * processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.2.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle12c() throws Exception {
        testFail(ORACLE12C);
    }

    /**
     * @throws Exception
     */
//    @RunAsClient
//    @Test
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void testMssql2008r2() throws Exception {
//        testFail(MSSQL2008R2);
//    }
    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Microsoft SQL Server 2014 database. Kill server when the
     * MDB is processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.4.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMssql2014() throws Exception {
        testFail(MSSQL2014);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Microsoft SQL Server 2012 database. Kill server when the
     * MDB is processing messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.0.1
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMssql2012() throws Exception {
        testFail(MSSQL2012);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to MySQL 5.5 database. Kill server when the MDB is processing
     * messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMysql55() throws Exception {
        testFail(MYSQL55);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to MySQL 5.7 database. Kill server when the MDB is processing
     * messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and
     * restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     * @tpSince 6.4.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMysql57() throws Exception {
        testFail(MYSQL57);
    }

    public void testFail(String databaseName) throws Exception {

        int numberOfMessages = 2000;

        prepareServerEAP7(container(1), databaseName);

        container(1).start();

        rollbackPreparedTransactions(databaseName, properties.get("db.username"));  // if dbutilservlet can do it
        deleteRecords();
        countRecords();

        ProducerTransAck producer = new ProducerTransAck(container(1), inQueueRelativeJndiName, numberOfMessages);

        producer.setMessageBuilder(new InfoMessageBuilder());
        producer.setCommitAfter(1000);
        producer.setTimeout(0);
        producer.start();
        producer.join();

        container(1).deploy(mdbToDb);

        long howLongToWait = 360000;
        long startTime = System.currentTimeMillis();

        while (countRecords() < numberOfMessages / 10 && (System.currentTimeMillis() - startTime) < howLongToWait) {
            Thread.sleep(5000);
        }

        for (int i = 0; i < 1; i++) {

            container(1).kill();
            container(1).getPrintJournal().printJournal(databaseName + "journal_content_after_kill1.txt");
            container(1).start();
            container(2).getPrintJournal().printJournal(databaseName + "journal_content_after_restart2.txt");
            Thread.sleep(10000);

        }

        startTime = System.currentTimeMillis();
        long lastValue = 0;
        long newValue;
        while ((newValue = countRecords()) < numberOfMessages && (newValue > lastValue
                || (System.currentTimeMillis() - startTime) < howLongToWait)) {
            lastValue = newValue;
            Thread.sleep(5000);
        }
        container(1).getPrintJournal().printJournal(databaseName + "journal_content_after_recovery.txt");

        logger.info("Print lost messages:");
        List<String> listOfSentMessages = new ArrayList<String>();
        for (Map<String, String> m : producer.getListOfSentMessages()) {
            listOfSentMessages.add(m.get("messageId"));
        }
        List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll());
        for (String m : lostMessages) {
            logger.info("Lost Message: " + m);
        }
        Assert.assertEquals(numberOfMessages, countRecords());

        // check that there are no prepared transactions under this user
        int count = rollbackPreparedTransactions(databaseName, properties.get("db.username"));
        Assert.assertEquals("After LODH 5 test there must be 0 transactions in prepared stated in DB. Current value is " + count,
                0, count);
                
        container(1).undeploy(mdbToDb);
        container(1).stop();
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
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the beginning of the transaction start phase to see if
     * the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the start of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the end of the transaction start phase to see if the
     * kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the start phase of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the beginning of the transaction end phase to see if
     * the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the end of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the end of the transaction end phase to see if the
     * kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the end phase of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the beginning of the transaction prepare phase to see
     * if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the prepare phase of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the end of the transaction prepare phase to see if the
     * kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the prepare phase of
     * the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the beginning of the transaction commit phase to see
     * if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the commit phase of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and
     * insert them to Enterprise DB Postgres Plus Advanced Server 9.2 database.
     * Kill the server at the end of the transaction commit phase to see if the
     * kill leads to lost messages.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the commit phase of the
     * transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message
     * inserts a new record to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server
     * again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the
     * number of sent messages
     */
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
    }

    public void testFail() throws Exception {

        int numberOfMessages = 15;

        try {
            logger.info("!!!!! preparing server !!!!!");

            prepareServerEAP7(container(1), POSTGRESQLPLUS93);

            container(1).start();

            logger.info("!!!!! deleting data in DB !!!!!");
            deleteRecords();
            countRecords();

            logger.info("!!!!! sending messages !!!!!");
            ProducerClientAck producer = new ProducerClientAck(container(1), inQueueRelativeJndiName,
                    numberOfMessages);

            producer.setMessageBuilder(new InfoMessageBuilder());
            producer.start();
            producer.join();

            logger.info("!!!!! installing byteman rules !!!!!");
            //HornetQCallsTracking.installTrackingRules(CONTAINER1_NAME_IP, BYTEMAN_CONTAINER1_NAME_PORT);
            RuleInstaller.installRule(this.getClass(), container(1));

            logger.info("!!!!! deploying MDB !!!!!");
            try {
                container(1).deploy(mdbToDb);
            } catch (Exception e) {
                // byteman might kill the server before control returns back here from deploy method, which results
                // in arquillian exception; it's safe to ignore, everything is deployed and running correctly on the server
                logger.debug("Arquillian got an exception while deploying", e);
            }

            container(1).kill();
//            PrintJournal.printJournal(CTRACEONTAINER1, "journal_content_after_kill1.txt");
            logger.info("!!!!! starting server again !!!!!");
            container(1).start();
//            PrintJournal.printJournal(CONTAINER1_NAME_NAME, "journal_content_after_restart2.txt");
            Thread.sleep(10000);

            // 5 min
            logger.info("!!!!! waiting for MDB !!!!!");
            long howLongToWait = 300000;
            long startTime = System.currentTimeMillis();
            while (countRecords() < numberOfMessages && (System.currentTimeMillis() - startTime)
                    < howLongToWait) {
                Thread.sleep(10000);
            }
//        PrintJournal.printJournal(CONTAINER1_NAME_NAME, "journal_content_before_shutdown3.txt");

            logger.info("Print lost messages:");
            List<String> listOfSentMessages = new ArrayList<String>();
            for (Map<String, String> m : producer.getListOfSentMessages()) {
                listOfSentMessages.add(m.get("messageId"));
            }
            List<String> lostMessages = checkLostMessages(listOfSentMessages, printAll());
            for (String m : lostMessages) {
                logger.info("Lost Message: " + m);
            }
            Assert.assertEquals(numberOfMessages, countRecords());
        } finally {
            container(1).undeploy(mdbToDb);
            container(1).stop();
//        PrintJournal.printJournal(CONTAINER1_NAME_NAME, "journal_content_after_shutdown4.txt");
        }

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServerEAP7(Container container, String database) throws Exception {

        String poolName = "lodhDb";
        final String driverName = "mylodhdb";
        final String driverModuleName = "com.mylodhdb";

        String jdbcDriverFileName = JdbcUtils.installJdbcDriverModule(container, database);
        properties = DBAllocatorUtils.allocateDatabase(database);

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        Random r = new Random();
        jmsAdminOperations.setNodeIdentifier(r.nextInt(9999));
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);
        jmsAdminOperations.createQueue("default", inQueueHornetQName, inQueueRelativeJndiName, true);

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
          
            jmsAdminOperations.createJDBCDriver( driverName, driverModuleName , jdbcClassName, datasourceClassName);   
//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
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
            
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);
            
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName , "TRANSACTION_READ_COMMITTED",
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
            
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", url);
            
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName , "TRANSACTION_READ_COMMITTED",
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

            jmsAdminOperations.createJDBCDriver( driverName, driverModuleName , jdbcClassName, datasourceClassName); 

//            String databaseName = "crashrec"; // db.name
//            String datasourceClassName = "oracle.jdbc.xa.client.OracleXADataSource"; // datasource.class.xa
//            String serverName = "dev151.mw.lab.eng.bos.redhat.com:1521"; // db.hostname=db14.mw.lab.eng.bos.redhat.com
//            String portNumber = "1521"; // db.port=5432
//            String recoveryUsername = "crashrec";
//            String recoveryPassword = "crashrec";
//            String url = "jdbc:oracle:thin:@dev151.mw.lab.eng.bos.redhat.com:1521:qaora12";
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
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
          
            jmsAdminOperations.createJDBCDriver( driverName, driverModuleName , jdbcClassName, datasourceClassName);         

            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("URL", "jdbc:mysql://" + serverName + ":" + portNumber + "/crashrec");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName , "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);           
            jmsAdminOperations.setXADatasourceAtribute(poolName,"user-name", "crashrec");
            jmsAdminOperations.setXADatasourceAtribute(poolName,"password", "crashrec");


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
          
            jmsAdminOperations.createJDBCDriver( driverName, driverModuleName , jdbcClassName, datasourceClassName); 
            
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("DatabaseName", databaseName);
            
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true, xaDataSourceProperties);
            
            jmsAdminOperations.setXADatasourceAtribute(poolName,"user-name", recoveryUsername);
            jmsAdminOperations.setXADatasourceAtribute(poolName,"password", recoveryPassword);

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

            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("SelectMethod", "cursor");
            
            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true,xaDataSourceProperties);
            
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
            
            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
            xaDataSourceProperties.put("ServerName", serverName);
            xaDataSourceProperties.put("DatabaseName", "crashrec");
            xaDataSourceProperties.put("PortNumber", portNumber);
            xaDataSourceProperties.put("SelectMethod", "cursor");

            jmsAdminOperations.createXADatasource("java:/jdbc/lodhDS", poolName, false, false, driverName, "TRANSACTION_READ_COMMITTED",
                    datasourceClassName, false, true,xaDataSourceProperties);
            
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

            jmsAdminOperations.createJDBCDriver( driverName, driverModuleName , jdbcClassName, datasourceClassName); 

            Map<String,String> xaDataSourceProperties = new HashMap<String,String>();
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
//        File target = new File("/tmp/DbUtilServlet.war");
//        if (target.exists()) {
//            target.delete();
//        }
//        dbUtilServlet.as(ZipExporter.class).exportTo(target, true);

        return dbUtilServlet;
    }

    public List<String> printAll() throws Exception {

        List<String> messageIds = new ArrayList<String>();

        try {
            container(1).deploy(dbUtilServlet);
            String response = HttpRequest.get("http://" + container(1).getHostname() + ":8080/DbUtilServlet/DbUtilServlet?op=printAll", 120, TimeUnit.SECONDS);

            StringTokenizer st = new StringTokenizer(response, ",");
            while (st.hasMoreTokens()) {
                messageIds.add(st.nextToken());
            }

            logger.info("Number of records: " + messageIds.size());

        } finally {
            container(1).undeploy(dbUtilServlet);
        }

        return messageIds;
    }

    public int rollbackPreparedTransactions(String database, String owner) throws Exception {
        int count = 0;

        try {
            container(1).deploy(dbUtilServlet);

            String response = HttpRequest.get("http://" + container(1).getHostname() + ":8080/DbUtilServlet/DbUtilServlet?op=rollbackPreparedTransactions&owner=" + owner
                    + "&database=" + database, 30, TimeUnit.SECONDS);
            container(1).undeploy(dbUtilServlet);

            logger.info("Response is: " + response);

            // get number of rollbacked transactions
            Scanner lines = new Scanner(response);
            String line;
            while (lines.hasNextLine()) {
                line = lines.nextLine();
                logger.info("Print line: " + line);
                if (line.contains(NUMBER_OF_ROLLBACKED_TRANSACTIONS)) {
                    String[] numberOfRollbackedTransactions = line.split(":");
                    logger.info(NUMBER_OF_ROLLBACKED_TRANSACTIONS + " is " + numberOfRollbackedTransactions[1]);
                    count = Integer.valueOf(numberOfRollbackedTransactions[1]);
                }
            }
        } finally {
            container(1).undeploy(dbUtilServlet);
        }

        return count;

    }

    public int countRecords() throws Exception {
        return countRecords(container(1), dbUtilServlet);
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

    public void deleteRecords() throws Exception {
        try {
            container(1).deploy(dbUtilServlet);
            String response = HttpRequest.get("http://" + container(1).getHostname() + ":8080/DbUtilServlet/DbUtilServlet?op=deleteRecords", 300, TimeUnit.SECONDS);

            logger.info("Response from delete records is: " + response);
        } finally {
            container(1).undeploy(dbUtilServlet);
        }
    }

}
