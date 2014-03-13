package org.jboss.qa.hornetq.test.cli.operations;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.cli.CliTestUtils;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;


/**
* Tested operations:
 All done:
    add-jndi
    count-messages-for-subscription
    drop-all-subscriptions
    drop-durable-subscription
    list-all-subscriptions
    list-all-subscriptions-as-json
    list-durable-subscriptions
    list-durable-subscriptions-as-json
    list-messages-for-subscription
    list-messages-for-subscription-as-json
    list-non-durable-subscriptions
    list-non-durable-subscriptions-as-json
    remove-messages

* @author Miroslav Novak mnovak@redhat.com
*/
@RunWith(Arquillian.class)
public class JmsTopicOperationsTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(JmsTopicOperationsTestCase.class);

    private final CliClient cli = new CliClient(new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1)));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;

    String coreTopicName = "testTopic";
    String topicJndiName = "jms/topic/" + coreTopicName;

    String dlqCoreQueueName = "DLQ";
    String dlqCQueueJndiName = "jms/queue/" + dlqCoreQueueName;

    String expireCoreQueueName = "Expire";
    String expireQueueJndiName = "jms/queue/" + expireCoreQueueName;

    private final String ADDRESS = "/subsystem=messaging/hornetq-server=default/jms-topic=" + coreTopicName;


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

        String clientId = "testSubscriberClientIdjmsTopicOperations";
        String subscriberName = "testSubscriber";

        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(CONTAINER1_IP, 4447, topicJndiName, clientId, subscriberName);
        subscriberClientAck.setTimeout(1000);
        subscriberClientAck.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(CONTAINER1_IP, 4447, topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
        publisher.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        publisher.start();

        List<Client> producers = new ArrayList<Client>();
        producers.add(publisher);

        waitForProducersUntil(producers, 10, 60000);

        Result r1 = runOperation("add-jndi ", "jndi-binding=" + topicJndiName + "2");
        logger.info("Result add-jndi : " + r1.getResponse().asString());
        CliTestUtils.assertSuccess(r1);

        Result r2 = runOperation("drop-all-subscriptions", null);
        logger.info("Result drop-all-subscriptions: " + r2.getResponse().asString());
        CliTestUtils.assertFailure(r2);

        Result r3 = runOperation("list-all-subscriptions", null);
        logger.info("Result list-all-subscriptions : " + r3.getResponse().asString());
        CliTestUtils.assertSuccess(r3);

        Result r4 = runOperation("list-all-subscriptions-as-json", null);
        logger.info("Result list-all-subscriptions-as-json : " + r4.getResponse().asString());
        CliTestUtils.assertSuccess(r4);

        subscriberClientAck.close();
        Result r22 = runOperation("drop-all-subscriptions", null);
        logger.info("Result drop-all-subscriptions: " + r22.getResponse().asString());
        CliTestUtils.assertSuccess(r22);

        subscriberClientAck = new SubscriberClientAck(CONTAINER1_IP, 4447, topicJndiName, clientId, subscriberName);
        subscriberClientAck.setTimeout(1000);
        subscriberClientAck.subscribe();

        Result r33 = runOperation("list-all-subscriptions", null);
        logger.info("Result list-all-subscriptions : " + r33.getResponse().asString());
        CliTestUtils.assertSuccess(r33);
        Assert.assertEquals("Bad client id on subscriber.", clientId, r33.getResponse().get("result").asList().get(0).get("clientID").asString());

        Result r5 = runOperation("list-durable-subscriptions", null);
        logger.info("Result list-durable-subscriptions: " + r5.getResponse().asString());
        CliTestUtils.assertSuccess(r5);

        Result r6 = runOperation("list-durable-subscriptions-as-json", null);
        logger.info("Result list-durable-subscriptions-as-json: " + r6.getResponse().asString());
        CliTestUtils.assertSuccess(r6);

        Result r7 = runOperation("list-messages-for-subscription", "queue-name=" + clientId + "." + subscriberName);
        logger.info("Result list-messages-for-subscription: " + r7.getResponse().asString());
        CliTestUtils.assertSuccess(r7);

        Result r8 = runOperation("list-messages-for-subscription-as-json", "queue-name=" + clientId + "." + subscriberName);
        logger.info("Result list-messages-for-subscription-as-json: " + r8.getResponse().asString());
        CliTestUtils.assertSuccess(r8);

        Result r9 = runOperation("list-non-durable-subscriptions", null);
        logger.info("Result list-non-durable-subscriptions: " + r9.getResponse().asString());
        CliTestUtils.assertSuccess(r9);

        Result r10 = runOperation("remove-messages", null);
        logger.info("Result remove-messages: " + r10.getResponse().asString());
        CliTestUtils.assertSuccess(r10);

        publisher.stopSending();
        publisher.join();

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
        jmsAdminOperations.removeTopic(coreTopicName);
        jmsAdminOperations.createTopic(coreTopicName, topicJndiName);
        jmsAdminOperations.removeQueue(dlqCoreQueueName);
        jmsAdminOperations.createQueue(dlqCoreQueueName, dlqCQueueJndiName);
        jmsAdminOperations.removeQueue(expireCoreQueueName);
        jmsAdminOperations.createQueue(expireCoreQueueName, expireQueueJndiName);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "BLOCK", 1024 * 1024 * 10, 0, 0, 1024 * 1024, "jms.queue." + expireCoreQueueName, "jms.queue." + dlqCoreQueueName);

        jmsAdminOperations.close();
    }

}

