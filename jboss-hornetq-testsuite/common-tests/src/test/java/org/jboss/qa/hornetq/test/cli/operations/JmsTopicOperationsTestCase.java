package org.jboss.qa.hornetq.test.cli.operations;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import category.Functional;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.cli.CliTestUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Miroslav Novak mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Tested operations: add-jndi count-messages-for-subscription
 * drop-all-subscriptions drop-durable-subscription list-all-subscriptions
 * list-all-subscriptions-as-json list-durable-subscriptions
 * list-durable-subscriptions-as-json list-messages-for-subscription
 * list-messages-for-subscription-as-json list-non-durable-subscriptions
 * list-non-durable-subscriptions-as-json remove-messages
 * @tpInfo For more details see current coverage: https://mojo.redhat.com/docs/DOC-185811
 */
@RunWith(Arquillian.class)
@Category(Functional.class)
public class JmsTopicOperationsTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(JmsTopicOperationsTestCase.class);

    private final CliClient cli = new CliClient(new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword()));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;

    String coreTopicName = "testTopic";
    String topicJndiName = "jms/topic/" + coreTopicName;

    String dlqCoreQueueName = "DLQ";
    String dlqCQueueJndiName = "jms/queue/" + dlqCoreQueueName;

    String expireCoreQueueName = "Expire";
    String expireQueueJndiName = "jms/queue/" + expireCoreQueueName;

    private final String ADDRESS_EAP6 = "/subsystem=messaging/hornetq-server=default/jms-topic=" + coreTopicName;
    private final String ADDRESS_EAP7 = "/subsystem=messaging-activemq/server=default/jms-topic=" + coreTopicName;

    @Before
    public void startServer() {
        container(1).stop();
        container(1).start();
    }

    @After
    public void stopServer() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Server is started. Create subscriber and start
     * subscription to servers topic. Create publisher and start publishing
     * messages to servers topic. Using CLI commands try to call topic
     * operations. Optionally validate for operations whether they are working
     * correctly.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>create and start subscriber for topic deployed on server</li>
     * <li>create and start publisher for topic deployed on server</li>
     * <li>connect to CLI</li>
     * <li>Try to invoke operation</li>
     * <li>Optional: Validate that operation is working as expected</li>
     * <li>stop publisher<li/>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit invocation of topic operations was successful
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOperationsWithConnectedClients() throws Exception {

        // setup server
        prepareServer(container(1));

        String clientId = "testSubscriberClientIdjmsTopicOperations";
        String subscriberName = "testSubscriber";
        logger.info("Starting subscriber");
        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(container(1), topicJndiName, clientId, subscriberName);
        addClient(subscriberClientAck);
        subscriberClientAck.setTimeout(1000);
        subscriberClientAck.subscribe();
        logger.info("Starting publisher");
        PublisherClientAck publisher = new PublisherClientAck(container(1), topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
        addClient(publisher);
        publisher.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        publisher.start();

        List<Client> producers = new ArrayList<Client>();
        producers.add(publisher);
        logger.info("Waiting for messages");
        ClientUtils.waitForProducersUntil(producers, 10, 60000);

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

        subscriberClientAck = new SubscriberClientAck(container(1), topicJndiName, clientId, subscriberName);
        addClient(subscriberClientAck);
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

    /**
     * @tpTestDetails Server is started. Create subscriber and start 200
     * subscribers to server topic. Create publisher and start publishing
     * messages to servers topic. Using CLI commands try to call topic
     * operations. Optionally validate for operations whether they are working
     * correctly.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>create and start 200 subscribers for topic deployed on server</li>
     * <li>create and start publisher for topic deployed on server</li>
     * <li>connect to CLI</li>
     * <li>Try to invoke operation on topic</li>
     * <li>Optional: Validate that operation is working as expected</li>
     * <li>stop publisher<li/>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit invocation of topic operations was successful
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOperationsWithConnectedClientsUnderLoad() throws Exception {

        // setup server
        prepareServer(container(1));

        logger.info("Starting subscribers.");
        // start slow subscribers - try 200 subscriptions
        int numberOfSubscribers = 200;
        List<SubscriberTransAck> subscribers = new ArrayList<SubscriberTransAck>();
        for (int i = 0; i < numberOfSubscribers; i++) {
            SubscriberTransAck topicSubscriber = new SubscriberTransAck(container(1), topicJndiName, 120000, 5, 10, "subs" + i, "name" + i);
            addClient(topicSubscriber);
            topicSubscriber.setTimeout(1000); // make subscriber really slow 1msb/sec
            topicSubscriber.subscribe();
            subscribers.add(topicSubscriber);
            Thread.sleep(50); // do not create subscribers too fast
            topicSubscriber.start();
        }
        usedClients.addAll(subscribers);
        logger.info("Subscribers started.");

        logger.info("Starting publisher");
        // send some messages to it
        PublisherTransAck publisherTransAck = new PublisherTransAck(container(1), topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "publisher");
        addClient(publisherTransAck);
        publisherTransAck.setMessageBuilder(new TextMessageBuilder(1));
        publisherTransAck.setCommitAfter(7);
        publisherTransAck.setTimeout(0);
        publisherTransAck.start();

        List<Client> producers = new ArrayList<Client>();
        producers.add(publisherTransAck);
        logger.info("Waiting for messages");
        ClientUtils.waitForProducersUntil(producers, 2000, 300000);

        // call different cli operations 100 times in a row
        int numberOfOperationTries = 10;
        long timeout = 60000;
        for (int i = 0; i < numberOfOperationTries; i++) {

            logger.info("Call adminoperations - iteration - " + i);
            Result r3 = runOperation("list-all-subscriptions", timeout, null);
            logger.info("Result list-all-subscriptions : " + r3.getResponse().asString());
            CliTestUtils.assertSuccess(r3);

            Result r4 = runOperation("list-all-subscriptions-as-json", timeout, null);
            logger.info("Result list-all-subscriptions-as-json : " + r4.getResponse().asString());
            CliTestUtils.assertSuccess(r4);

            Result r33 = runOperation("list-all-subscriptions", timeout, null);
            logger.info("Result list-all-subscriptions : " + r33.getResponse().asString());
            CliTestUtils.assertSuccess(r33);

            Result r5 = runOperation("list-durable-subscriptions", timeout, null);
            logger.info("Result list-durable-subscriptions: " + r5.getResponse().asString());
            CliTestUtils.assertSuccess(r5);

            Result r6 = runOperation("list-durable-subscriptions-as-json", timeout, null);
            logger.info("Result list-durable-subscriptions-as-json: " + r6.getResponse().asString());
            CliTestUtils.assertSuccess(r6);

            Result r9 = runOperation("list-non-durable-subscriptions", timeout, null);
            logger.info("Result list-non-durable-subscriptions: " + r9.getResponse().asString());
            CliTestUtils.assertSuccess(r9);

            Thread.sleep(1000);
        }

        publisherTransAck.stopSending();
        publisherTransAck.join();
        logger.info("Stopping publisher");

        // make all subscribers fast again
        for (SubscriberTransAck s : subscribers)    {
            s.setTimeout(0);
        }

        for (SubscriberTransAck s : subscribers)    {
            s.join();
        }

        container(1).stop();
    }

    private Result runOperation(final String operation, final String... params) {
        String cmd;
        if (container(1).getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
            cmd = CliUtils.buildCommand(ADDRESS_EAP6, ":" + operation, params);
        } else {
            cmd = CliUtils.buildCommand(ADDRESS_EAP7, ":" + operation, params);
        }
        return this.cli.executeCommand(cmd);
    }

    private Result runOperation(final String operation, long timeout, final String... params) throws Exception {
        String cmd;
        if (container(1).getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
            cmd = CliUtils.buildCommand(ADDRESS_EAP6, ":" + operation, params);
        } else {
            cmd = CliUtils.buildCommand(ADDRESS_EAP7, ":" + operation, params);
        }
        return this.cli.executeCommand(cmd, timeout);
    }

    private void prepareServer(Container container) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.setSecurityEnabled(false);
        try {
            jmsAdminOperations.removeTopic(coreTopicName);
        } catch (Exception ex) { // ignore
        }
        jmsAdminOperations.createTopic(coreTopicName, topicJndiName);
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
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 1024 * 1024 * 10, 0, 0, 1024 * 1024, "jms.queue." + expireCoreQueueName, "jms.queue." + dlqCoreQueueName);

        // disable trace logging
        jmsAdminOperations.seRootLoggingLevel("INFO");
        jmsAdminOperations.disableTraceLoggingToFile();

        jmsAdminOperations.close();
    }

}
