
package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.InfoMessageBuilder;
import org.jboss.qa.hornetq.tools.HighCPUUtils;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
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
public class Lodh5LoadTestCase extends Lodh5TestBase {

    private static final Logger logger = Logger.getLogger(Lodh5LoadTestCase.class);


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
    public void testLoadPosgrePlus93() throws Exception {
        testFail(POSTGRESQLPLUS93);
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
    public void testLoadOracle11gr2() throws Exception {
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
    public void testLoadOracle12c() throws Exception {
        testFail(ORACLE12C);
    }

    public void testFail(String databaseName) throws Exception {

        int numberOfMessages = 5000;

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

        Process highCpuLoader = null;
        try {
            // bind mdb EAP server to cpu core
            String cpuToBind = "0";
            highCpuLoader = HighCPUUtils.causeMaximumCPULoadOnContainer(container(1), cpuToBind);
            logger.info("High Cpu loader was bound to cpu: " + cpuToBind);

            // Wait until some messages are consumes from InQueue
            new JMSTools().waitUntilMessagesAreStillConsumed(inQueueHornetQName, 300000, container(1));
            logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");
            new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
            logger.info("There are no prepared transactions on node-1 and node-3.");
        } finally {
            if (highCpuLoader != null) {
                highCpuLoader.destroy();
            }
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


}

