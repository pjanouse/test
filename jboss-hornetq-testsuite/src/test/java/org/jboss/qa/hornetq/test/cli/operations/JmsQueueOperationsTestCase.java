package org.jboss.qa.hornetq.test.cli.operations;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.cli.CliTestUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;


/**
 * OK
 * <p/>
 * Tested operations:
 * <p/>
 * add-jndi - create queue with dlq in address settings     - done
 * <p/>
 * Needs some messages:
 * <p/>
 * change-message-priority                                  - done
 * change-messages-priority                                 - done
 * count-messages                                           - done
 * expire-message                                           - done
 * expire-messages                                          - done
 * list-message-counter-as-html                             - done
 * list-message-counter-as-json                             - done
 * list-message-counter-history-as-html                     - done
 * list-message-counter-history-as-json                     - done
 * list-messages                                            - done
 * list-messages-as-json                                    - done
 * move-message                                             - done
 * move-messages                                            - done
 * remove-message                                           - done
 * remove-messages                                          - done
 * reset-message-counter                                    - done
 * send-message-to-dead-letter-address                      - done
 * send-messages-to-dead-letter-address                     - done
 * <p/>
 * Needs clients sending/receiving messages:
 * <p/>
 * pause                                                    - done
 * resume                                                   - done
 *
 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class JmsQueueOperationsTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(JmsQueueOperationsTestCase.class);

    private final CliClient cli = new CliClient(new CliConfiguration(getHostname(CONTAINER1), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1)));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;

    String coreQueueName = "testQueue";
    String queueJndiName = "jms/queue/" + coreQueueName;

    String dlqCoreQueueName = "DLQ";
    String dlqCQueueJndiName = "jms/queue/" + dlqCoreQueueName;

    String expireCoreQueueName = "Expire";
    String expireQueueJndiName = "jms/queue/" + expireCoreQueueName;

    private final String ADDRESS = "/subsystem=messaging/hornetq-server=default/jms-queue=" + coreQueueName;

    @Before
    public void startServer() {
        this.controller.start(CONTAINER1);
    }

    @After
    public void stopServer() {
        stopServer(CONTAINER1);
    }

// TODO uncomment when bz: https://bugzilla.redhat.com/show_bug.cgi?id=1155247 gets clear
//    /**
//     * When queue is destroyed with connected consumers then nothing should happen.
//     */
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    public void testDestroyWithActiveClients() {
//
//        // setup server
//        prepareServer(CONTAINER1);
//
//        // send some messages to queue and receive them
//        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
//
//        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
//
//        producer.start();
//
//        ReceiverClientAck receiverClientAck = new ReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiName, 1000, 100, 10);
//
//        receiverClientAck.start();
//
//        Result r1 = runOperation("remove");
//
//        CliTestUtils.assertFailure(r1);
//
//    }
//
//    /**
//     * When queue is destroyed with connected consumers then nothing should happen.
//     */
//    @Test
//    @RunAsClient
//    @RestoreConfigBeforeTest
//    @CleanUpBeforeTest
//    public void testDestroyWithoutClients() {
//
//        // setup server
//        prepareServer(CONTAINER1);
//
//        Result r1 = runOperation("remove");
//
//        CliTestUtils.assertSuccess(r1);
//
//    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOperationsWithConnectedClients() throws Exception {

        // setup server
        prepareServer(CONTAINER1);

        // send some messages to it
        ProducerClientAck producer = new ProducerClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producer.start();
        List<Client> producers = new ArrayList<Client>();
        producers.add(producer);

        waitForProducersUntil(producers, 10, 60000);

        Result r1 = runOperation("add-jndi ", "jndi-binding=" + queueJndiName + "2");
        logger.info("Result add-jndi : " + r1.getResponse().asString());
        CliTestUtils.assertSuccess(r1);

        Result r2 = runOperation("list-message-counter-as-html", null);
        logger.info("Result list-message-counter-as-html : " + r2.getResponse().asString());
        CliTestUtils.assertSuccess(r2);

        Result r3 = runOperation("list-message-counter-as-json", null);
        logger.info("Result list-message-counter-as-json : " + r3.getResponse().asString());
        CliTestUtils.assertSuccess(r3);

        Result r4 = runOperation("list-message-counter-history-as-html", null);
        logger.info("Result list-message-counter-history-as-html : " + r4.getResponse().asString());
        CliTestUtils.assertSuccess(r4);

        Result r5 = runOperation("list-message-counter-history-as-json", null);
        logger.info("Result list-message-counter-history-as-json: " + r5.getResponse().asString());
        CliTestUtils.assertSuccess(r5);

        Result r6 = runOperation("list-messages", null);
        logger.info("Result list-messages: " + r6.getResponse().asString());
        CliTestUtils.assertSuccess(r6);

        Result r7 = runOperation("list-messages-as-json", null);
        logger.info("Result list-messages-as-json: " + r7.getResponse().asString());
        CliTestUtils.assertSuccess(r7);

        Result r8 = runOperation("list-consumers-as-json", null);
        logger.info("Result list-consumers-as-json: " + r8.getResponse().asString());
        CliTestUtils.assertSuccess(r8);

        Result r9 = runOperation("count-messages", null);
        logger.info("Result count-messages: " + r9.getResponse().asString());
        CliTestUtils.assertSuccess(r9);

        String expireMessageId = r6.getResponse().get("result").asList().get(0).get("JMSMessageID").asString();
        String moveMessageId = r6.getResponse().get("result").asList().get(1).get("JMSMessageID").asString();
        String removeMessageId = r6.getResponse().get("result").asList().get(2).get("JMSMessageID").asString();
        String dlqMessageId = r6.getResponse().get("result").asList().get(3).get("JMSMessageID").asString();

        Result r10 = runOperation("expire-message", "message-id=" + expireMessageId);
        logger.info("Result expire-message: " + r10.getResponse().asString());
        CliTestUtils.assertSuccess(r10);

        //move message to dlq
        Result r11 = runOperation("move-message", "message-id=" + moveMessageId + ",other-queue-name=" + dlqCoreQueueName);
        logger.info("Result move-message: " + r11.getResponse().asString());
        CliTestUtils.assertSuccess(r11);

        //move message to dlq
        Result r12 = runOperation("remove-message", "message-id=" + removeMessageId);
        logger.info("Result remove-message: " + r12.getResponse().asString());
        CliTestUtils.assertSuccess(r12);

        Result r13 = runOperation("send-message-to-dead-letter-address", "message-id=" + dlqMessageId);
        logger.info("Result send-message-to-dead-letter-address: " + r13.getResponse().asString());
        CliTestUtils.assertSuccess(r13);

        Result r24 = runOperation("pause", null);
        logger.info("Result pause: " + r24.getResponse().asString());
        CliTestUtils.assertSuccess(r24);

        Result r14 = runOperation("expire-messages", null);
        logger.info("Result expire-messages: " + r14.getResponse().asString());
        CliTestUtils.assertSuccess(r14);

        Result r15 = runOperation("move-messages", "other-queue-name=" + dlqCoreQueueName);
        logger.info("Result move-messages: " + r15.getResponse().asString());
        CliTestUtils.assertSuccess(r15);

        Result r16 = runOperation("move-messages", "other-queue-name=" + dlqCoreQueueName);
        logger.info("Result move-messages: " + r16.getResponse().asString());
        CliTestUtils.assertSuccess(r16);

        Result r22 = runOperation("resume", null);
        logger.info("Result resume: " + r22.getResponse().asString());
        CliTestUtils.assertSuccess(r22);

        Result r17 = runOperation("remove-messages", null);
        logger.info("Result remove-messages: " + r17.getResponse().asString());
        CliTestUtils.assertSuccess(r17);

        Result r18 = runOperation("reset-message-counter", null);
        logger.info("Result reset-message-counter: " + r18.getResponse().asString());
        CliTestUtils.assertSuccess(r18);

        Result r19 = runOperation("send-messages-to-dead-letter-address", null);
        logger.info("Result send-messages-to-dead-letter-address: " + r19.getResponse().asString());
        CliTestUtils.assertSuccess(r19);

        ReceiverClientAck receiverClientAck = new ReceiverClientAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiName, 1000, 100, 10);
        receiverClientAck.start();

        List<Client> receivers = new ArrayList<Client>();
        receivers.add(receiverClientAck);
        waitForReceiversUntil(receivers, 10, 60000);

        Result r20 = runOperation("list-consumers-as-json", null);
        logger.info("Result :list-consumers-as-json: " + r20.getResponse().asString());
        CliTestUtils.assertSuccess(r20);

        producer.stopSending();
        producer.join();
        receiverClientAck.join();
    }

    private Result runOperation(final String operation, final String... params) {
        String cmd = CliUtils.buildCommand(ADDRESS, ":" + operation, params);
        return this.cli.executeCommand(cmd);
    }

    private void prepareServer(String containerName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.disableSecurity();
        try {
            jmsAdminOperations.removeQueue(coreQueueName);
        } catch (Exception ex) { // ignore
        }
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName);
        try {
            jmsAdminOperations.removeQueue(dlqCoreQueueName);
        } catch (Exception ex) { // ignore
        }
        jmsAdminOperations.createQueue(dlqCoreQueueName, dlqCQueueJndiName);
        try {
            jmsAdminOperations.removeQueue(expireCoreQueueName);
        } catch (Exception ex) { // ignore
        }
        jmsAdminOperations.createQueue(expireCoreQueueName, expireQueueJndiName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "BLOCK", 1024 * 1024 * 10, 0, 0, 1024 * 1024, "jms.queue." + expireCoreQueueName, "jms.queue." + dlqCoreQueueName);

        jmsAdminOperations.close();
    }

}
