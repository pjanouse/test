package org.jboss.qa.hornetq.test.faultinjection;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Session;

import static junit.framework.Assert.*;

/**
 * Test case covers basic fault injection tests for standalone node.
 * <p/>
 * Scenarios are inherited from EAP5 test plan and from NTT customer scenarios.
 *
 * @author pslavice@redhat.com
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
@RunWith(Arquillian.class)
public class FaultInjectionTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        controller.stop(CONTAINER1);
    }

    /**
     * Dummy smoke test which sends and receives messages
     *
     * @throws InterruptedException if something is wrong
     */
    @Test
    @RunAsClient
    public void dummySendReceiveTest() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        final String MY_QUEUE_JNDI = "/queue/dummyQueue";
        final String MY_QUEUE_JNDI_NEW = "java:jboss/exported/jms/queue/dummyQueue_new_name";
        final String MY_QUEUE_JNDI_CLIENT = "jms/queue/dummyQueue_new_name";
        final int MESSAGES = 10;

        controller.start(CONTAINER1);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.createQueue(MY_QUEUE, MY_QUEUE_JNDI);
        jmsAdminOperations.addQueueJNDIName(MY_QUEUE, MY_QUEUE_JNDI_NEW);

        SimpleJMSClient client = new SimpleJMSClient(CONTAINER1_IP, 4447, MESSAGES, Session.AUTO_ACKNOWLEDGE, false);
        client.sendMessages(MY_QUEUE_JNDI_CLIENT);
        assertNull(client.getExceptionDuringSend());
        assertEquals(MESSAGES, client.getSentMessages());

        assertEquals(MESSAGES, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));

        client.receiveMessages(MY_QUEUE_JNDI_CLIENT);
        assertNull(client.getExceptionDuringReceive());
        assertEquals(MESSAGES, client.getReceivedMessages());

        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));

        jmsAdminOperations.removeQueue(MY_QUEUE);
        jmsAdminOperations.close();

        controller.stop(CONTAINER1);
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
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeTransactionalOperationIsWrittenSend() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
    }

    /**
     * Server is killed after transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after transactional data are written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeMessageTransactional",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterTransactionalOperationIsWrittenSend() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true);
        // Should be 0 message because server is killed before commit
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
    }

    /**
     * Server is killed before is commit written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void transactionBeforeWriteCommitSendTest() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
    }

    /**
     * Server is killed after is commit written int the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void transactionAfterWriteCommitSendTest() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(1, client.getReceivedMessages());
    }

    /**
     * Server is killed before is commit written into the journal during receive
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void transactionBeforeWriteCommitReceiveTest() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        SimpleJMSClient client = createFaultInjection(0, true, true, false);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(1, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }

    /**
     * Server is killed after is commit written into the journal during receive
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after transaction commit is written into journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void transactionAfterWriteCommitReceiveTest() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        SimpleJMSClient client = createFaultInjection(0, true, true, false);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }

    /**
     * Server is killed after is message deleted from journal after receive
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after message is deleted from journal - receive",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "deleteMessage",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void transactionAfterDeleteMessageFromJournalReceiveTest() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        SimpleJMSClient client = createFaultInjection(0, true, true, false);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
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
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before do rollback - send",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeDoRollbackSend() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true, false, true);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
    }

    /**
     * Server is killed after transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after do rollback - send",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterDoRollbackSend() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true, false, true);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
    }

    /**
     * Server is killed before transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before do rollback - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void beforeDoRollbackReceive() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true, true, true);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        final String MY_QUEUE = "dummyQueue";
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(1, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }

    /**
     * Server is killed after transactional data are written into the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after do rollback - receive",
                    targetClass = "org.hornetq.core.transaction.impl.TransactionImpl",
                    targetMethod = "doRollback",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"))
    public void afterDoRollbackReceive() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(0, true, true, true);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        final String MY_QUEUE = "dummyQueue";
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(1, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }

    //============================================================================================================
    //============================================================================================================
    // No-transactional session - client ack mode
    //============================================================================================================
    //============================================================================================================

    /**
     * Server is killed before ack is stored into the journal
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules({
            @BMRule(name = "Kill before ack is written in journal",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledge",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();"),
            @BMRule(name = "Kill before ack is written in journal",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "storeAcknowledgeTransactional",
                    action = "System.out.println(\"Byteman will invoke kill\");killJVM();")}
    )
    public void clientAckBeforeWriteAckSendTest() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(Session.CLIENT_ACKNOWLEDGE, false, true, false);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        final String MY_QUEUE = "dummyQueue";
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(1, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }

    /**
     * Server is killed after QueueImpl.acknowledge
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill after acknowledge()",
                    targetClass = "org.hornetq.core.server.impl.ServerSessionImpl",
                    targetMethod = "acknowledge",
                    targetLocation = "EXIT",
                    action = "System.out.println(\"Byteman will invoke kill\");traceStack(\"found the caller!\\n\", 10);killJVM();"))
    public void clientAckAfterWriteAckSendTest() throws InterruptedException {
        SimpleJMSClient client = createFaultInjection(Session.CLIENT_ACKNOWLEDGE, false, true, false);
        assertNull(client.getExceptionDuringSend());
        assertNotNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());

        final String MY_QUEUE = "dummyQueue";
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        assertEquals(1, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
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
     * Creates fault injection client and sends and receives one message. If parameter <code>ruleBeforeReceive</code>
     * is true, server is not killed and calling method can evaluate results and has to kill server.
     *
     * @param ackMode           acknowledge mode for JMS client
     * @param transacted        is JMS session transacted?
     * @param ruleBeforeReceive install Byteman rule before receive?
     * @param rollbackOnly      is rollback only?
     * @return instance of {@link org.jboss.qa.hornetq.apps.clients.SimpleJMSClient}
     */
    private SimpleJMSClient createFaultInjection(int ackMode, boolean transacted, boolean ruleBeforeReceive, boolean rollbackOnly) {
        final String MY_QUEUE = "dummyQueue";
        final String MY_QUEUE_JNDI = "/queue/dummyQueue";

        controller.start(CONTAINER1);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(CONTAINER1_IP);
        jmsAdminOperations.cleanupQueue(MY_QUEUE);
        jmsAdminOperations.createQueue(MY_QUEUE, MY_QUEUE_JNDI);

        SimpleJMSClient client = new SimpleJMSClient(CONTAINER1_IP, 4447, 1, ackMode, transacted);
        if (!ruleBeforeReceive) {
            client.setRollbackOnly(rollbackOnly);
            log.info("Installing Byteman rule before sending message ...");
            RuleInstaller.installRule(this.getClass());
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                // Ignore it
            }

            log.info("Execution of the client ...");
            client.sendMessages(MY_QUEUE_JNDI);

            controller.kill(CONTAINER1);
            controller.start(CONTAINER1);
            client.receiveMessages(MY_QUEUE_JNDI);
            jmsAdminOperations.removeQueue(MY_QUEUE);
            jmsAdminOperations.close();
            controller.stop(CONTAINER1);
        } else {
            log.info("Execution of the client ...");
            client.sendMessages(MY_QUEUE_JNDI);
            client.setRollbackOnly(rollbackOnly);
            log.info("Installing Byteman rule before receiving message ...");
            RuleInstaller.installRule(this.getClass());
            client.receiveMessages(MY_QUEUE_JNDI);
            controller.kill(CONTAINER1);
            controller.start(CONTAINER1);
        }
        return client;
    }
}
