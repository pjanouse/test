
package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh
 * /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh5TestCase extends Lodh5TestBase {

    private static final Logger logger = Logger.getLogger(Lodh5TestCase.class);

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Sybase ASE 15.7 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to IBM DB2 Enterprise e10.5 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to PostgreSQL 9.2 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgre92() throws Exception {
        testFail(POSTGRESQL92);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to PostgreSQL 9.3 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
     * @tpSince 6.1.0
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testPosgrePlus92() throws Exception {
        testFail(POSTGRESQLPLUS92);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.3 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Oracle 11g R1 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle11gr1() throws Exception {
        testFail(ORACLE11GR1);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Oracle 11g R2 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testOracle11gr2() throws Exception {
        testFail(ORACLE11GR2);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Oracle 12c database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Microsoft SQL Server 2014 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Microsoft SQL Server 2012 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to MySQL 5.5 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testMysql55() throws Exception {
        testFail(MYSQL55);
    }

    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to MySQL 5.7 database.
     * Kill server when the MDB is processing messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>kill the server container when the MDB is processing messages and restart it</li>
     * <li>read the messages from OutQueue</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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

        prepareServerEAP6(container(1), databaseName);

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


    /**
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the beginning of the transaction start phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the start of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the end of the transaction start phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the start phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the beginning of the transaction end phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the end of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the end of the transaction end phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the end phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the beginning of the transaction prepare phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the prepare phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the end of the transaction prepare phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the prepare phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the beginning of the transaction commit phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server on the commit phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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
     * @tpTestDetails Start server with MDB which read messages from queue and insert them to Enterprise DB Postgres
     * Plus Advanced Server 9.2 database. Kill the server at the end of the transaction commit phase
     * to see if the kill leads to lost messages.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     * <li>start server container with deployed InQueue</li>
     * <li>send messages to InQueue</li>
     * <li>install Byteman rule to kill the server after the commit phase of the transaction</li>
     * <li>deploy MDB which reads messages from InQueue and for each message inserts a new record
     * to the database (in XA transaction)</li>
     * <li>let the Byteman rule kill the server and then restart the server again</li>
     * <li>count the number of records in the database</li>
     * </ul>
     * @tpPassCrit The database must contain the same number of records as the number of sent messages
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

            prepareServerEAP6(container(1), POSTGRESQLPLUS92);

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

}

