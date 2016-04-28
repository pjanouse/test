package org.jboss.qa.hornetq.test.faultinjection;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import javax.jms.Session;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Test case covers basic fault injection tests for standalone node.
 * <p/>
 * Scenarios are inherited from EAP5 test plan and from NTT customer scenarios.
 *
 * @author pslavice@redhat.com
 * @author dpogrebn@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter FAULT INJECTION - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class FaultInjectionTestCase extends HornetQTestCase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    private static final String BYTEMAN_KILL_MSG = "Byteman is going to kill JVM...now";

    private static final String BYTEMAN_KILL_ACTION = "System.out.println(\""+BYTEMAN_KILL_MSG+"\");killJVM();";

    private static final String TEST_QUEUE = "dummyQueue";
    private static final String TEST_QUEUE_JNDI = "/queue/dummyQueue";
    private static final String TEST_QUEUE_JNDI_NEW = "java:jboss/exported/jms/queue/dummyQueue_new_name";
    private static final String TEST_QUEUE_JNDI_CLIENT = "jms/queue/dummyQueue_new_name";
    private static final String CONNECTION_FACTORY = "RemoteConnectionFactory";

    @Before
    public void preActionPrepareServers() throws Exception {
        container(1).stop();
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(CONNECTION_FACTORY, 0);
        jmsAdminOperations.close();
    }

    /**
     * Stops all servers
     */
    @After
    public void postActionStopAllServers() throws Exception {
        container(1).stop();
    }

    /**
     * Dummy smoke test which sends and receives messages
     *
     * @throws InterruptedException if something is wrong
     * @tpTestDetails Start server, then send 10 messages and receive them
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>receive all messages</li>
     *     <li>start subscribers one by one so there is a huge difference in number of messages between subscriptions</li>
     * </ul>
     * @tpPassCrit Producer sends successfully 10 messages, consumer gets 10 messages, no messages left on server.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    public void dummySendReceiveTest() throws InterruptedException {

        final int MESSAGES = 10;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        jmsAdminOperations.createQueue(TEST_QUEUE, TEST_QUEUE_JNDI);
        jmsAdminOperations.addQueueJNDIName(TEST_QUEUE, TEST_QUEUE_JNDI_NEW);

        SimpleJMSClient client = new SimpleJMSClient(
                container(1),
                MESSAGES,
                Session.AUTO_ACKNOWLEDGE,
                false);

        client.sendMessages(TEST_QUEUE_JNDI_CLIENT);
        assertNull(client.getExceptionDuringSend());
        assertEquals(MESSAGES, client.getSentMessages());

        assertEquals(MESSAGES, jmsAdminOperations.getCountOfMessagesOnQueue(TEST_QUEUE));

        client.receiveMessages(TEST_QUEUE_JNDI_CLIENT);
        assertNull(client.getExceptionDuringReceive());
        assertEquals(MESSAGES, client.getReceivedMessages());

        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(TEST_QUEUE));

        jmsAdminOperations.removeQueue(TEST_QUEUE);
        jmsAdminOperations.close();
    }


    //============================================================================================================
    //============================================================================================================
    // Transactional session - commit
    //============================================================================================================
    //============================================================================================================

    /**
     * Server is killed before transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     * @id commit02
     * @tpTestDetails Start server and send messages in transacted session. Kill before transactional data are written
     * into journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server before commit is stored to journal</li>
     *     <li>send commit</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit receiver will not consume any messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    action = BYTEMAN_KILL_ACTION)
    })
    public void commitAtSendingBeforeOperationWrittenTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after transactional data are written into the journal during send. This is not during commit() but
     * when sending messages in transaction is written to journal.
     *
     * @throws InterruptedException is something is wrong
     * @id commit03
     * @tpTestDetails  Start server and send messages in transacted session. Kill after transactional data are written
     * into journal but transaction is not commited.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server after commit is stored to journal</li>
     *     <li>send commit</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit receiver will not consume any messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after transactional data are written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtSendingAfterOperationWrittenTest()
    {
    	int numMessagesSent = 1;
    	// Should be 0 message because server is killed before commit
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed before is commit written into the journal during send
     *
     * @id commit05
     * @tpTestDetails  Start server and send messages in transacted session. Kill before commit is written into journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server before commit is stored to journal</li>
     *     <li>send commit</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit receiver will not consume any messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = BYTEMAN_KILL_ACTION)
    })
    public void commitAtSendingBeforeWriteCommitTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after is commit written into the journal during send
     *
     * @id commit06
     * @tpTestDetails Start server and send messages in transacted session. Kill after commit is written into journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server after commit is stored to journal</li>
     *     <li>send commit</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit receiver will consume messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after transaction commit is written into journal - send",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtSendingAfterWriteCommitTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 1;

    	boolean isFaultOnReceive = false;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed before is commit written into the journal during receive
     *
     * @throws InterruptedException is something is wrong
     * @id commit12
     * @tpTestDetails Start server and send messages. Receive messages and kill before commit is written into journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server before commit is stored to journal</li>
     *     <li>receive messages and call session.commit()</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit calling commit() will throw exception and receiver does not get any messages, after restart messages
     * can be read again
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before transaction commit is written into journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtReceivingBeforeWriteCommitTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after is commit written into the journal during receive
     *
     * @throws InterruptedException is something is wrong
     * @id commit13
     * @tpTestDetails  Start server and send messages. Receive messages and kill after commit is written into journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill server after commit is stored to journal</li>
     *     <li>receive messages and call session.commit()</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  calling commit() will throw exception and receiver does not get any messages, after restart
     * messages can not be read again
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after transaction commit is written into journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtReceivingAfterWriteCommitTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * TODO - check this scenario whether it's correct
     * Server is killed after is message deleted from journal after receive
     *
     * @id commit14
     * @tpTestDetails  Start server and send messages. Receive messages and kill after message is deleted from journal.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill after message is deleted from journal</li>
     *     <li>receive messages</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  Message will not be received when server is killed
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "deleteMessage",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after message is deleted from journal - receive",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "deleteMessage",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtReceivingAfterDeleteMessageFromJournalTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Kill before delivering message to the consumer.
     *
     * @id commit09
     * @tpTestDetails Start server and send messages. Receive messages and kill before message is delivered to consumer
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill before message is delivered to consumer</li>
     *     <li>receive messages</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit Message will no be received, there will be messages in queue after restart
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before delivered to the consumer - recieve",
                targetClass = "org.hornetq.core.server.impl.ServerConsumerImpl",
                targetMethod = "deliverStandardMessage",
                action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before delivered to the consumer - recieve",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl",
                    targetMethod = "deliverStandardMessage",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtReceivingBeforeDeliveringToConsumerTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
   	 * Kill after delivering a message to the consumer.
     *
     * @id commit10
     * @tpTestDetails Start server and send messages. Receive messages and kill after message is delivered to consumer
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill after message is delivered to consumer</li>
     *     <li>receive messages</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  Message will be received, there will be no messages in queue after restart
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after delivered to the consumer - recieve",
                targetClass = "org.hornetq.core.server.impl.ServerConsumerImpl",
                targetMethod = "deliverStandardMessage",
                targetLocation = "EXIT",
                action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after delivered to the consumer - recieve",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl",
                    targetMethod = "deliverStandardMessage",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void commitAtReceivingAfterDeliveringToConsumerTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithCommit(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    //============================================================================================================
    //============================================================================================================
    // Transactional session - rollback
    //============================================================================================================
    //============================================================================================================

    /**
     * Server is killed before transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     * @id rollback05
     * @tpTestDetails Start server and send messages. Kill before rollback is written to journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill before rollback is written to journal</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. No messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before do rollback - send",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before do rollback - send",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtSendingBeforeDoRollbackTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after transactional data are written into the journal during send
     *
     * @id rollback06
     * @tpTestDetails Start server and send messages. Kill after rollback is written to journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill after rollback is written to journal</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. No messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after do rollback - send",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after do rollback - send",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtSendingAfterDoRollbackTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed before transactional data are written into the journal during send
     *
     * @id rollback12
     * @tpTestDetails Start server and send messages. Receive messages and kill before rollback is written to journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill before rollback is written to journal for receive</li>
     *     <li>receive messages and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before do rollback - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before do rollback - receive",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtReceivingBeforeDoRollbackTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after transactional data are written into the journal during send
     *
     * @id rollback13
     * @tpTestDetails  Start server and send messages. Receive messages and kill after rollback is written to journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill after rollback is written to journal for receive</li>
     *     <li>receive messages and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after do rollback - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after do rollback - receive",
                    targetClass = "org.apache.activemq.artemis.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtReceivingAfterDoRollbackTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed before record is written into the journal.
     * Rollback-only transaction.
     *
     * @id rollback02
     * @tpTestDetails  Start server and send messages and call rollback. Kill before record is written into the journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>deploy byteman rule which kill before record is written into the journal</li>
     *     <li>send messages to queue and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. No messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before record is written into the journal - send",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    targetLocation = "INVOKE org.hornetq.core.persistence.StorageManager.storeMessageTransactional",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before record is written into the journal - send",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    targetLocation = "INVOKE org.apache.activemq.artemis.core.persistence.StorageManager.storeMessageTransactional",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtSendingBeforeWrittenToJournalTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after record is written into the journal.
     * Rollback-only transaction.
     *
     * @id rollback03
     * @tpTestDetails   Start server and send messages and call rollback. Kill after record is written into a journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>deploy byteman rule which kill after record is written into a journal</li>
     *     <li>send messages to queue and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. No messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after record is written into a journal - send",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after record is written into a journal - send",
                    targetClass = "org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtSendingAfterWrittenToJournalTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 0;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = false;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }


    /**
     * Kill before message is delivered to the client.
     * Rollback-only transaction.
     *
     * @id rollback09
     * @tpTestDetails Start server and send messages and call rollback. Kill before delivered to the consumer
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill before delivered to the consumer</li>
     *     <li>receive message from queue and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before delivered to the consumer - recieve",
                    targetClass = "org.hornetq.core.server.impl.ServerConsumerImpl",
                    targetMethod = "deliverStandardMessage",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before delivered to the consumer - recieve",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl",
                    targetMethod = "deliverStandardMessage",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtReceivingBeforeDeliveredToConsumerTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Kill after delivering message to the client.
     * Rollback-only transaction.
     *
     * @id rollback10
     * @tpTestDetails Start server and send messages and call rollback. Kill after delivered to the consumer
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kill after delivered to the consumer</li>
     *     <li>receive message from queue and rollback</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit  No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after delivered to the consumer - recieve",
            		targetClass = "org.hornetq.core.server.impl.ServerConsumerImpl",
            		targetMethod = "deliverStandardMessage",
            		targetLocation = "EXIT",
            		action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill after delivered to the consumer - recieve",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl",
                    targetMethod = "deliverStandardMessage",
                    targetLocation = "EXIT",
                    action = BYTEMAN_KILL_ACTION)})
    public void rollbackAtReceivingAfterDeliveredToConsumerTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithRollback(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    //============================================================================================================
    //============================================================================================================
    // No-transactional session - client ack mode
    //============================================================================================================
    //============================================================================================================

    /**
     * Server is killed before ack is stored into the journal
     *
     * @id ack08
     * @tpTestDetails Start server and send messages. Receive messages and server is killed before ack is stored
     * into the journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kills server before ack is stored into the journal</li>
     *     <li>receive message from queue and acknowledge</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before ack is written in journal",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledge",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Hornetq  Kill before ack is written in journal",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledgeTransactional",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before ack is written in journal",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledge",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before ack is written in journal",
                    targetClass = "org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledgeTransactional",
                    action = BYTEMAN_KILL_ACTION)}
    )
    public void clientAckAtReceivingBeforeWriteAckTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithClientAck(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed after QueueImpl.acknowledge
     * @tpTestDetails Start server and send messages. Receive messages and server is killed after ack is stored
     * into the journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kills server after ack is stored into the journal</li>
     *     <li>receive message from queue and acknowledge</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit No message will be received. Messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill after acknowledge()",
                    targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
                    targetMethod = "acknowledge",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");traceStack(\"found the caller!\\n\", 10);killJVM();"),
            @BMRule(name = "Artemis Kill after acknowledge()",
                    targetClass = "org.apache.activemq.artemis.core.server.impl.ServerSessionImpl",
                    targetMethod = "acknowledge",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");traceStack(\"found the caller!\\n\", 10);killJVM();")})
    public void clientAckAtReceivingAfterWriteAckTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesOnQueue = 1;
    	int expectedNumMessagesRecieved = 0;

    	boolean isFaultOnReceive = true;

    	executeWithClientAck(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    /**
     * Server is killed before the record is written into the journal
     * during sending to the server.
     *
     * @id nonTrans02
     * @tpTestDetails Start server and send messages. Server is killed before the record is written into the journal
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send messages to queue</li>
     *     <li>deploy byteman rule which kills server before the record is written into the journal</li>
     *     <li>receive message from queue and acknowledge</li>
     *     <li>restart server</li>
     * </ul>
     * @tpPassCrit No message will be received. No messages in queue after restart.
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test  @CleanUpBeforeTest
    @RunAsClient
    @RestoreConfigBeforeTest
    @BMRules({
            @BMRule(name = "Hornetq Kill before the record is written into the journal - send",
                    targetClass = "org.hornetq.core.journal.impl.JournalImpl",
                    targetMethod = "appendRecord",
                    action = BYTEMAN_KILL_ACTION),
            @BMRule(name = "Artemis Kill before the record is written into the journal - send",
                    targetClass = "org.apache.activemq.artemis.core.journal.impl.JournalImpl",
                    targetMethod = "appendRecord",
                    action = BYTEMAN_KILL_ACTION)})
    public void clientAckAtSendingBeforeWrittenToJournalTest()
    {
    	int numMessagesSent = 1;
    	int expectedNumMessagesRecieved = 0;
    	int expectedNumMessagesOnQueue = 0;

    	boolean isFaultOnReceive = false;

    	executeWithClientAck(
    			numMessagesSent,
    			expectedNumMessagesOnQueue,
    			expectedNumMessagesRecieved,
    			isFaultOnReceive);
    }

    //============================================================================================================
    //============================================================================================================
    // Protected methods
    //============================================================================================================
    //============================================================================================================

    /**
     * Executes next test sequence:
     * 	<li>preparing server</li>
     *  <li>sending messages	:producer->server</li>
     *  <li>receiving messages	:server->consumer</li>
     *  <li><b>test: </b>none exception during sending</li>
     *  <li><b>test: </b>exception during receiving</li>
     *  <li><b>test: </b>comparing counts(expected values)</li>
     *
     *  <p>
     *
     * Transactions are NOT marked as 'rollback-only'.
     *
     * @param numMessagesSent
     * 				number of messages to be send by a producer
     * @param expectedNumMessagesRecieved
     * 				expected number of messages to be received by a receiver
     * @param expectedNumMessagesOnQueue
     * 				expected number of messages to be on queue
     * 				(not delivered to a receiver)
     */
    protected void executeWithCommit(
    		int numMessagesSent,
    		int expectedNumMessagesOnQueue,
    		int expectedNumMessagesRecieved,
    		boolean isFaultOnReceive)
    {
    	boolean isRollbackOnly = false;
    	boolean isTransacted = true;

    	executeTestSequence(
                expectedNumMessagesRecieved,
    			expectedNumMessagesOnQueue,
    			isRollbackOnly,
    			isTransacted,
    			isFaultOnReceive);
    }

    /**
     * Executes next test sequence:
     * 	<li>preparing server</li>
     *  <li>sending messages</li>
     *  <li>marking consumer as	rollback-only</li>
     *  <li>receiving messages</li>
     *  <li><b>test: </b>none exception during sending</li>
     *  <li><b>test: </b>exception during receiving</li>
     *  <li><b>test: </b>comparing counts(expected values)</li>
     *
     *  <p>
     *
     * Transactions ARE marked as 'rollback-only'.
     *
     * @param numMessagesSent
     * 				number of messages to be send by a producer
     * @param expectedNumMessagesOnQueue
     * 				expected number of messages to be on queue
     * 				(not delivered to a receiver)
     * @param expectedNumMessagesRecieved
     * 				expected number of messages to be received by a receiver
     */
    protected void executeWithRollback(
    		int numMessagesSent,
    		int expectedNumMessagesOnQueue,
    		int expectedNumMessagesRecieved,
    		boolean isFaultOnReceive)
    {
    	boolean isRollbackOnly = true;
    	boolean isTransacted = true;

    	executeTestSequence(
                expectedNumMessagesRecieved,
    			expectedNumMessagesOnQueue,
    			isRollbackOnly,
    			isTransacted,
    			isFaultOnReceive);
    }

    /**
     *
     * TODO update javadoc
     *
     * @param numMessagesSent
     * 				number of messages to be send by a producer
     * @param expectedNumMessagesRecieved
     * 				expected number of messages to be received by a receiver
     * @param expectedNumMessagesOnQueue
     * 				expected number of messages to be on queue
     * 				(not delivered to a receiver)
     */
    protected void executeWithClientAck(
    		int numMessagesSent,
    		int expectedNumMessagesOnQueue,
    		int expectedNumMessagesRecieved,
    		boolean isFaultOnReceive)
    {
    	boolean isRollbackOnly = false;
    	boolean isTransacted = false;

    	executeTestSequence(
                expectedNumMessagesRecieved,
    			expectedNumMessagesOnQueue,
    			isRollbackOnly,
    			isTransacted,
    			isFaultOnReceive);
    }

    /**
     * TODO javadoc expected here
     */
    protected void executeTestSequence(
            int expectedNumMessagesRecieved,
            int expectedNumMessagesOnQueue,
            boolean isRollbackOnly,
            boolean isTransacted,
            boolean isFaultOnReceive)
    {
    	int ackMode = isTransacted
    			? Session.SESSION_TRANSACTED
    			: Session.CLIENT_ACKNOWLEDGE; // was requested AUTO_ACK according to the NTT document

        SimpleJMSClient client = createFaultInjection(
        		ackMode,
        		isTransacted,
        		isFaultOnReceive,
        		isRollbackOnly);

       	JMSOperations jmsAdminOperations = container(1).getJmsOperations();
       	long numMessagesOnQueue = jmsAdminOperations.getCountOfMessagesOnQueue(TEST_QUEUE);
       	jmsAdminOperations.close();

        if (isFaultOnReceive)
        {
        	assertNotNull("Expected exception on receive was not thrown",
        				  client.getExceptionDuringReceive());
        } else
        {
        	assertNotNull("Expected exception on send was not thrown",
        				  client.getExceptionDuringSend());
        }

        assertEquals("Incorrect number of messages received:",
        			 expectedNumMessagesRecieved,
        			 client.getReceivedMessages());

        assertEquals("Incorrect number of messages on the queue:",
        			 expectedNumMessagesOnQueue,
        			 numMessagesOnQueue);
    }

    //============================================================================================================
    //============================================================================================================
    // Private methods
    //============================================================================================================
    //============================================================================================================


    /**
     * Creates fault injection client and sends and receives one message - before send and commit
     *
     * @param ackMode    acknowledge mode for JMS client
     * @param transacted is JMS session transacted?
     * @return instance of {@link org.jboss.qa.hornetq.apps.clients.SimpleJMSClient}
     */
    private SimpleJMSClient createFaultInjection(int ackMode, boolean transacted) {
        return createFaultInjection(ackMode, transacted, false, false);
    }

    /**
     * Creates fault injection client and sends and receives one message.
     * If parameter <code>ruleBeforeReceive</code> is true,
     * server is not killed and calling method can evaluate results and
     * has to kill server.
     *
     * @param ackMode           acknowledge mode for JMS client
     * @param transacted        is JMS session transacted?
     * @param ruleBeforeReceive install Byteman rule before receive?
     * @param rollbackOnly      is rollback only?
     * @return instance of {@link org.jboss.qa.hornetq.apps.clients.SimpleJMSClient}
     */
    private SimpleJMSClient createFaultInjection(
    		int ackMode,
    		boolean transacted,
    		boolean ruleBeforeReceive,
    		boolean rollbackOnly)
    {
        SimpleJMSClient client = new SimpleJMSClient(container(1), 1, ackMode, transacted);
        if (!ruleBeforeReceive) {
            client.setRollbackOnly(rollbackOnly);

            log.info("Installing Byteman rule before sending message ...");
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());
            client.sendMessages(TEST_QUEUE_JNDI);

            container(1).kill();
            container(1).start();

            try
            {
                Thread.sleep(10000);
            } catch (Exception e) {
            }

            client.receiveMessages(TEST_QUEUE_JNDI);
        } else {
            log.info("Execution of the client ...");
            client.sendMessages(TEST_QUEUE_JNDI);
            client.setRollbackOnly(rollbackOnly);
            log.info("Installing Byteman rule before receiving message ...");
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());

            client.receiveMessages(TEST_QUEUE_JNDI);

            container(1).kill();

            container(1).start();
        }
        return client;
    }

}
