package org.jboss.qa.hornetq.test.faultinjection;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.FaultInjectionClient;
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

@RunWith(Arquillian.class)
public class FaultInjectionTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(HornetQTestCase.class);

    String hostname = "localhost";

    String queueName = "testQueue";

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

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        jmsAdminOperations.cleanUpQueue(MY_QUEUE);
        jmsAdminOperations.createQueue(MY_QUEUE, MY_QUEUE_JNDI);
        jmsAdminOperations.addQueueJNDIName(MY_QUEUE, MY_QUEUE_JNDI_NEW);

        FaultInjectionClient client = new FaultInjectionClient("localhost", 4447, MESSAGES, Session.AUTO_ACKNOWLEDGE, false);
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

    /**
     * Server is killed before is commit written int the journal during send
     *
     * @throws InterruptedException is something is wrong
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "Kill before transaction commit is written into journal - send",
                    targetClass = "org.hornetq.core.persistence.impl.journal.JournalStorageManager",
                    targetMethod = "commit",
                    action = "System.out.println(\"xxxxxxxxxx   Byteman will invoke kill\");killJVM();"))
    public void transactionBeforeWriteCommitSend() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        final String MY_QUEUE_JNDI = "/queue/dummyQueue";

        controller.start(CONTAINER1);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        jmsAdminOperations.cleanUpQueue(MY_QUEUE);
        jmsAdminOperations.createQueue(MY_QUEUE, MY_QUEUE_JNDI);

        log.info("Installing Byteman rule ...");

        // Let's install byteman rule
        RuleInstaller.installRule(this.getClass());

        log.info("Execution of the client ...");
        FaultInjectionClient client = new FaultInjectionClient("localhost", 4447, 10, 0, true);
        client.sendMessages(MY_QUEUE_JNDI);
        Thread.sleep(100);
        controller.kill(CONTAINER1);

        controller.start(CONTAINER1);
        client.receiveMessages(MY_QUEUE_JNDI);
        assertNotNull(client.getExceptionDuringSend());
        assertNull(client.getExceptionDuringReceive());
        assertEquals(0, client.getReceivedMessages());
        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(MY_QUEUE));

        jmsAdminOperations.removeQueue(MY_QUEUE);
        jmsAdminOperations.close();
        controller.stop(CONTAINER1);
    }
}
