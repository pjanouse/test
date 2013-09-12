package org.jboss.qa.hornetq.test.cli.operations;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.management.CliTestUtils;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;


/**
 * Tested operations:

 add-jndi - create queue with dlq in address settings     - done

 Needs some messages:

 change-message-priority
 change-messages-priority
 count-messages                                            - done
 expire-message
 expire-messages
 list-message-counter-as-html                             - done
 list-message-counter-as-json                             - done
 list-message-counter-history-as-html                     - done
 list-message-counter-history-as-json                     - done
 list-messages                                            - done
 list-messages-as-json                                    - done
 move-message
 move-messages
 remove-message
 remove-messages
 reset-message-counter
 send-message-to-dead-letter-address
 send-messages-to-dead-letter-address

 Needs clients sending/receiving messages:

 list-consumers-as-json
 pause
 resume

 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class JmsQueueOperationsTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JmsQueueOperationsTestCase.class);

    private final CliClient cli = new CliClient(new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 10;

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

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOperationsWithConnectedClients() throws Exception {

        // setup server
        prepareServer(CONTAINER1);

        // send some messages to it
        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, 4447, queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producer.start();
        producer.join();

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
        Result r10 = runOperation("expire-message", "message-id=" + expireMessageId);
        logger.info("Result expire-message: " + r10.getResponse().asString());
        CliTestUtils.assertSuccess(r10);

        //move message to dlq
        String moveMessageId = r6.getResponse().get("result").asList().get(1).get("JMSMessageID").asString();
        Result r11 = runOperation("move-message", "message-id=" + moveMessageId + ",other-queue-name=" + dlqCoreQueueName);
        logger.info("Result move-message: " + r11.getResponse().asString());
        CliTestUtils.assertSuccess(r11);

        //move message to dlq
        String removeMessageId = r6.getResponse().get("result").asList().get(2).get("JMSMessageID").asString();
        Result r12 = runOperation("remove-message", "message-id=" + removeMessageId);
        logger.info("Result remove-message: " + r12.getResponse().asString());
        CliTestUtils.assertSuccess(r12);

        String dlqMessageId = r6.getResponse().get("result").asList().get(3).get("JMSMessageID").asString();
        Result r13 = runOperation("send-message-to-dead-letter-address", "message-id=" + dlqMessageId);
        logger.info("Result send-message-to-dead-letter-address: " + r13.getResponse().asString());
        CliTestUtils.assertSuccess(r13);

        // TODO do the rest
//        expire-messages

//        move-messages
//        remove-messages
//        reset-message-counter
//
//        send-messages-to-dead-letter-address

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
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName);
        jmsAdminOperations.createQueue(dlqCoreQueueName, dlqCQueueJndiName);
        jmsAdminOperations.createQueue(expireCoreQueueName, expireQueueJndiName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "BLOCK", 1024 * 1024 * 10, 0, 0, 1024 * 1024, "jms.queue." + expireCoreQueueName, "jms.queue" + dlqCoreQueueName);

        jmsAdminOperations.close();
    }

}
