package org.jboss.qa.hornetq.test.faultinjection;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.FaultInjectionClient;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAckNonHA;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAckNonHa;
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

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
     * Start server "CONTAINER1". Deploy byteman rule in annotations. Run jms
     * producer which kills server - controller.kill() just wait for byteman to
     * kill server. Start server "CONTAINER1" again. Send some messages.
     */
    @Test
    @RunAsClient
    @BMRules(
            @BMRule(name = "rule - kill server after send message",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "System.out.println(\"Byteman will invoke kill\"); killJVM();"))
    public void simpleFaultInjectionTest() throws InterruptedException {
        final String MY_QUEUE = "dummyQueue";
        final String MY_QUEUE_JNDI = "/queue/dummyQueue";

        controller.start(CONTAINER1);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        jmsAdminOperations.cleanUpQueue(MY_QUEUE);
        jmsAdminOperations.createQueue(MY_QUEUE, MY_QUEUE_JNDI);

        // this will install byteman rule
        RuleInstaller.installRule(this.getClass());

        // run producer asynchronously
        Thread producer = new ProducerClientAckNonHA(hostname, queueName);
        // start producer which will lead to kill server by byteman
        producer.start();

        // this will wait for kill
        controller.kill(CONTAINER1);

        // start server again
        controller.start(CONTAINER1);

        //and produce messages
        Thread producer2 = new ProducerClientAckNonHA(hostname, queueName);

        producer2.start();

        // consumer messages
        Thread consumer = new ReceiverClientAckNonHa(queueName);

        consumer.start();

        // wait for producer2
        producer2.join(40000);
        // wait for consumer
        consumer.join(60000);

        jmsAdminOperations.removeQueue(MY_QUEUE);
        jmsAdminOperations.close();

        controller.stop(CONTAINER1);
    }
}
