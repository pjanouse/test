package org.jboss.qa.hornetq.test.cli.operations;

import org.apache.log4j.Logger;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.utils.UUIDGenerator;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JmsServerInfo;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.cli.CliTestUtils;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import static org.junit.Assert.*;

/**
 * needs a connected client: list-all-consumers-as-json - done
 * list-producers-info-as-json - done reset-all-message-counter-histories - done
 * reset-all-message-counters - done list-connections-as-json - done
 * <p/>
 * list-connection-ids -> take connection id and use in - done
 * <p/>
 * list-sessions - done list-sessions-as-json - > take session id and use in -
 * done get-session-creation-time - done list-consumers-as-json - done
 * <p/>
 * list-remote-addresses -> take IP + session from list-sessions and use in
 * <p/>
 * get-last-sent-message-id - done close-connections-for-address - done
 * <p/>
 * list-sessions -> take session id and use in
 * <p/>
 * list-target-destinations - done
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * needs prepared transaction commit-prepared-transaction - done
 * list-heuristic-committed-transactions - done
 * list-heuristic-rolled-back-transactions - done
 * list-prepared-transaction-details-as-html - done
 * list-prepared-transaction-details-as-json - done
 * list-prepared-transaction-jms-details-as-html - done
 * list-prepared-transaction-jms-details-as-json - done
 * list-prepared-transactions - done rollback-prepared-transaction - done
 * <p/>
 * needs live backup pair force-failover -done
 * <p/>
 * needs default config get-address-settings-as-json - done
 * get-connectors-as-json - done get-roles -done get-roles-as-json -done
 *
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Perform operations on server. Tested operations :
 * list-all-consumers-as-json, list-producers-info-as-json,
 * reset-all-message-counter-histories, reset-all-message-counters,
 * list-connections-as-json, list-connection-ids, list-sessions,
 * list-sessions-as-json, get-session-creation-time, list-consumers-as-json ,
 * list-remote-addresses, get-last-sent-message-id,
 * close-connections-for-address, list-sessions, list-target-destinations, needs
 * prepared transaction, commit-prepared-transaction,
 * list-heuristic-committed-transactions,
 * list-heuristic-rolled-back-transactions,
 * list-prepared-transaction-details-as-html,
 * list-prepared-transaction-details-as-json,
 * list-prepared-transaction-jms-details-as-html,
 * list-prepared-transaction-jms-details-as-json, list-prepared-transactions,
 * rollback-prepared-transaction, needs live backup pair, force-failover,
 * get-address-settings-as-json, get-connectors-as-json, get-roles,
 * get-roles-as-json
 *
 * @tpInfo For more details see current coverage:
 * https://mojo.redhat.com/docs/DOC-185811
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class HornetQServerCliOperationsTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(HornetQServerCliOperationsTestCase.class);

    private static final String MODULE_EAP6 = "/subsystem=messaging/hornetq-server=default";
    private static final String MODULE_EAP7 = "/subsystem=messaging-activemq/server=default";

    private final CliClient cli = new CliClient(new CliConfiguration(container(1).getHostname(), container(1).getPort(),
            container(1).getUsername(), container(1).getPassword()));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;

    String coreQueueName = "testQueue";
    String coreTopicName = "testTopic";
    String queueJndiName = "jms/queue/" + coreQueueName;
    String topicJndiName = "jms/topic/" + coreTopicName;

    private void prepareServerForXATransactions() {

        // start server with XA connection factory
        container(1).start();
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();

        jmsAdminOperations.setFactoryType("RemoteConnectionFactory", "XA_GENERIC");
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName);

        jmsAdminOperations.close();

        container(1).stop();
    }

    private void createPreparedTransaction() {
        Context context = null;

        XAConnection con = null;

        XASession xaSession;

        Session session;

        Queue queue;

        try {

            context = container(1).getContext();

            queue = (Queue) context.lookup(queueJndiName);

            XAConnectionFactory cf = (XAConnectionFactory) context.lookup(container(1).getConnectionFactoryName());

            con = cf.createXAConnection();

            con.start();

            xaSession = con.createXASession();

            session = xaSession.getSession();

            XAResource xaResource = xaSession.getXAResource();

            Random r = new Random();

            Xid xid = new XidImpl((System.currentTimeMillis() + "xa-example1" + r.nextInt()).getBytes(), 1, (System.currentTimeMillis() + UUIDGenerator.getInstance().generateStringUUID()).getBytes());

            xaResource.start(xid, XAResource.TMNOFLAGS);

            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage("my message with xid: " + xid.toString()));

            // Step 17. Stop the work
            xaResource.end(xid, XAResource.TMSUCCESS);

            // Step 18. Prepare
            xaResource.prepare(xid);

        } catch (Exception ex) {
            logger.error("Error: ", ex);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    //ignore
                }

                try {
                    context.close();
                } catch (NamingException e) {
                    //ignore
                }

            }

        }
    }

    @Before
    @After
    public void after() {
        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails Server is started and prepared for XA transactions. 10
     * transactions are prepared. Using CLI invoke operations with transactions.
     * Optionally validate for operations whether they are working correctly.
     * @tpProcedure <ul>
     * <li>start one server configured for XA transactions</li>
     * <li>prepare transactions</li>
     * <li>connect to CLI</li>
     * <li>try to invoke operation</li>
     * <li>optional: Validate that operation is working as expected</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit Invocation of operations was successful. Optionally:
     * operations returned expected result
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testPreparedTransactions() throws Exception {

        int numberOfPreparedTransactions = 10;

        prepareServerForXATransactions();

        container(1).start();

        for (int i = 0; i < numberOfPreparedTransactions; i++) {
            createPreparedTransaction();
        }

        Result r1 = runOperation("list-prepared-transactions", null);
        logger.info("Result list-prepared-transactions: " + r1.getResponse().asString());
        CliTestUtils.assertSuccess(r1);

        Result r2 = runOperation("list-prepared-transaction-details-as-html", null);
        logger.info("Result list-prepared-transaction-details-as-html: " + r2.getResponse().asString());
        CliTestUtils.assertSuccess(r2);

        Result r3 = runOperation("list-prepared-transaction-details-as-json", null);
        logger.info("Result list-prepared-transaction-details-as-json: " + r3.getResponse().asString());
        CliTestUtils.assertSuccess(r3);

        Result r4 = runOperation("list-prepared-transaction-jms-details-as-html", null);
        logger.info("Result list-prepared-transaction-jms-details-as-html: " + r4.getResponse().asString());
        CliTestUtils.assertSuccess(r4);

        Result r5 = runOperation("list-prepared-transaction-jms-details-as-json", null);
        logger.info("Result list-prepared-transaction-jms-details-as-json: " + r5.getResponse().asString());
        CliTestUtils.assertSuccess(r5);

        Result r7 = runOperation("list-prepared-transactions", null);
        logger.info("Result list-prepared-transactions: " + r7.getResponse().asString());
        CliTestUtils.assertSuccess(r7);
        if (numberOfPreparedTransactions == r7.getResponse().get("result").asList().size()) {
            logger.info("number of prepared transaction is: " + numberOfPreparedTransactions);
        } else {
            logger.info("number of prepared transaction is: " + r7.getResponse().get("result").asList().size());
        }
        String preparedTransaction = r7.getResponse().get("result").asList().get(0).asString();
        StringTokenizer stringTokenizer = new StringTokenizer(preparedTransaction);
        String base64ToRollback = null;
        while (stringTokenizer.hasMoreTokens()) {
            if ("base64:".equals(stringTokenizer.nextToken())) {
                base64ToRollback = stringTokenizer.nextToken();
                break;
            }
        }
        logger.info("base64: " + base64ToRollback);

        // rollback prepared transactions
        Result r6 = runOperation("rollback-prepared-transaction", "transaction-as-base-64=\"" + base64ToRollback + "\"");
        logger.info("Result rollback-prepared-transaction: " + r6.getResponse().asString());
        CliTestUtils.assertSuccess(r6);
        Assert.assertTrue("rollback-prepared-transaction result must be true", Boolean.valueOf(r6.getResponse().get("result").asString()));

        preparedTransaction = r7.getResponse().get("result").asList().get(1).asString();
        stringTokenizer = new StringTokenizer(preparedTransaction);
        String base64ToCommit = null;
        while (stringTokenizer.hasMoreTokens()) {
            if ("base64:".equals(stringTokenizer.nextToken())) {
                base64ToCommit = stringTokenizer.nextToken();
                break;
            }
        }
        logger.info("base64: " + base64ToCommit);
        // commit prepared transactions
        Result r8 = runOperation("commit-prepared-transaction", "transaction-as-base-64=\"" + base64ToCommit + "\"");
        logger.info("Result commit-prepared-transaction: " + r8.getResponse().asString());
        CliTestUtils.assertSuccess(r8);
        Assert.assertTrue("commit-prepared-transaction result must be true", Boolean.valueOf(r8.getResponse().get("result").asString()));

        Result r10 = runOperation("list-prepared-transactions", null);
        logger.info("Result list-prepared-transactions: " + r10.getResponse().asString());
        CliTestUtils.assertSuccess(r10);
        if (numberOfPreparedTransactions - 2 == r10.getResponse().get("result").asList().size()) {
            logger.info("number of prepared transaction is: " + r10.getResponse().get("result").asList().size());
        } else {
            Assert.fail("Number of prepared transaction must be: " + (numberOfPreparedTransactions - 2));
        }

        Result r11 = runOperation("list-heuristic-committed-transactions", null);
        logger.info("Result list-heuristic-committed-transactions: " + r11.getResponse().asString());
        Assert.assertEquals("base64 must be " + base64ToCommit, "[\"" + base64ToCommit + "\"]", r11.getResponse().get("result").asString());
        CliTestUtils.assertSuccess(r11);

        Result r12 = runOperation("list-heuristic-rolled-back-transactions", null);
        logger.info("Result list-heuristic-rolled-back-transactions: " + r12.getResponse().asString());
        Assert.assertEquals("base64 must be " + base64ToRollback, "[\"" + base64ToRollback + "\"]", r12.getResponse().get("result").asString());
        CliTestUtils.assertSuccess(r12);
        container(1).stop();

    }

    /**
     * @tpTestDetails Two servers prepared for dedicated topology are started
     * (Server1 - live, Server2 - backup). Invoke operation force-failover on
     * live server and check whether backup server is alive.
     *
     * @tpProcedure <ul>
     * <li>start two server configured for dedicated topology</li>
     * <li>use Cli operation to force failover on live server</li>
     * <li>check backup server is alive</li>
     * </ul>
     * @tpPassCrit Backup server is alive.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testForceFailover() throws Exception {

        prepareSimpleDedicatedTopology();
        container(1).start();
        container(2).start();

        // invoke operation
        Result r1 = runOperation("force-failover", null);
        logger.info("Result force-failover " + r1.getResponse().asString());
//        CliTestUtils.assertSuccess(r1);

        // check that backup started
        CheckServerAvailableUtils.waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 60000);

    }

    /**
     * @tpTestDetails Server with deployed queue and topic is started. Start
     * producer/publisher and consumer/subscriber. Using CLI commands try to
     * invoke operations. Optionally validate for operations whether they are
     * working correctly.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>start clients for server</li>
     * <li>connect to CLI</li>
     * <li>try to invoke operation</li>
     * <li>optional: Validate that operation is working as expected</li>
     * <li>stop clients and server<li/>
     * </ul>
     * @tpPassCrit Invocation of operations was successful. Optionally:
     * operations returned expected result
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOperationsWithConnectedClients() throws Exception {

        // setup server
        prepareServer(container(1));
        container(1).start();
        // send some messages to it
        ProducerClientAck producer = new ProducerClientAck(container(1), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        ReceiverClientAck receiver = new ReceiverClientAck(container(1), queueJndiName);
        receiver.setTimeout(1000);

        // start org.jboss.qa.hornetq.apps.clients
        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(container(1), topicJndiName, "testSubscriberClientId-hornetqCliOperations", "testSubscriber-hqServerCliOperations");
        subscriberClientAck.setTimeout(1000);
        subscriberClientAck.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(container(1).getContainerType().toString(),
                container(1).getHostname(), container(1).getJNDIPort(), topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
        publisher.setMessageBuilder(new ClientMixMessageBuilder(10, 200));

        producer.start();
        publisher.start();
//        producer.join();
//        publisher.join();
        receiver.start();
        subscriberClientAck.start();

        List<Client> receivers = new ArrayList<Client>();
        receivers.add(receiver);
        receivers.add(subscriberClientAck);
        ClientUtils.waitForReceiversUntil(receivers, 20, 60000);

        // test operations
        Result r1 = runOperation("list-all-consumers-as-json", null);
        logger.info("Result list-all-consumers-as-json: " + r1.getResponse().asString());
        CliTestUtils.assertSuccess(r1);

        Result r2 = runOperation("list-producers-info-as-json", null);
        logger.info("Result list-producers-info-as-json: " + r2.getResponse().asString());
        CliTestUtils.assertSuccess(r2);

        Result r3 = runOperation("reset-all-message-counter-histories", null);
        logger.info("Result reset-all-message-counter-histories: " + r3.getResponse().asString());
        CliTestUtils.assertSuccess(r3);

        Result r4 = runOperation("reset-all-message-counters", null);
        logger.info("Result reset-all-message-counters: " + r4.getResponse().asString());
        CliTestUtils.assertSuccess(r4);

        Result r5 = runOperation("list-connection-ids", null);
        logger.info("Result list-connection-ids: " + r5.getResponse().asString());
        CliTestUtils.assertSuccess(r5);

        Result r6 = runOperation("list-connections-as-json", null);
        logger.info("Result list-connections-as-json: " + r6.getResponse().asString());
        CliTestUtils.assertSuccess(r6);

        // take connection id and use for testing of list-sessions
        String connectionId = null;
        String sessionId = null;
        // for each connection id do
        for (ModelNode connectionIdModelNode : r5.getResponse().get("result").asList()) {
            // list-sessions
            Result sessionsResult = runOperation("list-sessions", "connection-id=" + connectionIdModelNode.asString());
            logger.info("Print sessions for connectionId: " + connectionIdModelNode.asString() + " ---" + sessionsResult.getResponse().asString());
            // if it not empty then
            if (sessionsResult.getResponse().get("result").asList().size() > 1) {
                // set session-id
                sessionId = sessionsResult.getResponse().get("result").asList().get(0).asString();
                // set connectionId
                connectionId = connectionIdModelNode.asString();
            }

            // test       list-consumers-as-json
            Result r10 = runOperation("list-consumers-as-json", "connection-id=" + connectionIdModelNode.asString());
            logger.info("Result list-consumers-as-json for connection id: " + connectionIdModelNode.asString() + " is: " + r10.getResponse().asString());
            CliTestUtils.assertSuccess(r10);
            Result r14 = runOperation("list-target-destinations", "session-id=" + sessionId);
            logger.info("Result list-target-destinations: " + r14.getResponse().asString());
            CliTestUtils.assertSuccess(r14);
        }

        Assert.assertNotNull(connectionId);
        Assert.assertNotNull(sessionId);

        Result r7 = runOperation("list-sessions", "connection-id=" + connectionId);
        logger.info("Result list-sessions: " + r7.getResponse().asString());
        CliTestUtils.assertSuccess(r7);

        Result r8 = runOperation("list-sessions-as-json", "connection-id=" + connectionId);
        logger.info("Result list-sessions-as-json: " + r8.getResponse().asString());
        CliTestUtils.assertSuccess(r8);

        Result r9 = runOperation("get-session-creation-time", "session-id=" + sessionId);
        logger.info("Result get-session-creation-time: " + r9.getResponse().asString());
        CliTestUtils.assertSuccess(r9);

        // test list-remote-addresses
        Result r11 = runOperation("list-remote-addresses", null);
        logger.info("Result list-remote-addresses: " + r11.getResponse().asString());
        CliTestUtils.assertSuccess(r11);

        // test get-last-sent-message-id
        logger.info("Print result for json: " + r2.getResponse().get("result").toJSONString(false));
        JSONArray json = new JSONArray(r2.getResponse().get("result").asString());
        JSONObject jsonObject = json.getJSONObject(0);
        String producerSessionId = (String) jsonObject.get("sessionID");
        String addressName = (String) jsonObject.get("destination");
        Result r12 = runOperation("get-last-sent-message-id", "session-id=" + producerSessionId, "address-name=" + addressName);
        logger.info("Result list-remote-addresses: " + r12.getResponse().asString());
        CliTestUtils.assertSuccess(r12);

        String ipAddress = r11.getResponse().get("result").asList().get(0).asString();
        Result r13 = runOperation("close-connections-for-address", "ip-address=" + ipAddress);
        logger.info("Result close-connections-for-address: " + r13.getResponse().asString());
        CliTestUtils.assertSuccess(r13);

        // stop org.jboss.qa.hornetq.apps.clients
        producer.stopSending();
        publisher.stopSending();
        receiver.join();
        subscriberClientAck.join();
        container(1).stop();

    }

    private void prepareServer(Container container) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName, true);
        jmsAdminOperations.createTopic(coreTopicName, topicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * @tpTestDetails One server is started. Invoke Cli operation
     * get-connectors-as-json. Check success of operation.
     *
     * @tpProcedure <ul>
     * <li>start server</li>
     * <li>use Cli operation to get connectors</li>
     * <li>check operation success</li>
     * </ul>
     * @tpPassCrit Operation was executed successfully
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectors() {
        container(1).start();
        Result response = this.runOperation("get-connectors-as-json");
        assertTrue("Operation should not fail", response.isSuccess());

//        String expected = "["
//                + "{\"name\":\"netty\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.netty.NettyConnectorFactory\","
//                + "\"params\":{\"port\":\"5445\",\"host\":\"192.168.48.1\"}},"
//                + "{\"name\":\"in-vm\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.invm.InVMConnectorFactory\","
//                + "\"params\":{\"server-id\":0}},"
//                + "{\"name\":\"netty-throughput\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.netty.NettyConnectorFactory\","
//                + "\"params\":{\"batch-delay\":\"50\",\"port\":\"5455\",\"host\":\"192.168.48.1\"}}]";
//        assertEquals("Incorrect connectors info", expected, response.getResponse().get("result").asString());
    }

    /**
     * @tpTestDetails One server with configured address settings is started.
     * Invoke Cli operation focused on getting address settings and check
     * correct result and success of operation. Tested operation :
     * get-address-settings-as-json
     *
     * @tpProcedure <ul>
     * <li>start configured server</li>
     * <li>invoke Cli operation</li>
     * <li>check operation success and result</li>
     * </ul>
     * @tpPassCrit Operation was executed successfully with result containing
     * expected output
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingAddressSettings() {
        container(1).start();
        JMSOperations jmsOperations = container(1).getJmsOperations();
        jmsOperations.removeAddressSettings("#");
        jmsOperations.addAddressSettings("#", "PAGE", 10485760, 0, 1000, 2097152);

        container(1).stop();
        container(1).start();

        Result response = this.runOperation("get-address-settings-as-json", "address-match=#");
        assertTrue("Operation should not fail", response.isSuccess());

        String output = response.getResponse().get("result").asString();
        assertTrue(output.contains("\"expiryDelay\":-1"));
        assertTrue(output.contains("\"maxRedeliveryDelay\":0"));
        assertTrue(output.contains("\"maxSizeBytes\":10485760"));
        assertTrue(output.contains("\"pageCacheMaxSize\":5"));
        assertTrue(output.contains("\"addressFullMessagePolicy\":\"PAGE\""));
        assertTrue(output.contains("\"pageSizeBytes\":2097152"));
        assertTrue(output.contains("\"maxDeliveryAttempts\":200"));
        assertTrue(output.contains("\"redistributionDelay\":1000"));

//        String expected = "{\"expiryDelay\":-1,\"maxRedeliveryDelay\":0,\"maxSizeBytes\":10485760," +
//                "\"pageCacheMaxSize\":5,\"lastValueQueue\":false,\"redeliveryMultiplier\":1," +
//                "\"addressFullMessagePolicy\":\"PAGE\",\"redistributionDelay\":1000," +
//                "\"redeliveryDelay\":0,\"pageSizeBytes\":2097152,\"sendToDLAOnNoRoute\":false," +
//                "\"maxDeliveryAttempts\":200}";
//        String expected = "{\"maxSizeBytes\":10485760,"
//                + "\"expiryAddress\":\"jms.queue.ExpiryQueue\","
//                + "\"redeliveryMultiplier\":1,"
//                + "\"addressFullMessagePolicy\":\"PAGE\","
//                + "\"pageSizeBytes\":2097152,"
//                + "\"expiryDelay\":-1,"
//                + "\"DLA\":\"jms.queue.DLQ\","
//                + "\"maxRedeliveryDelay\":0,"
//                + "\"pageCacheMaxSize\":5,"
//                + "\"lastValueQueue\":false,"
//                + "\"redeliveryDelay\":0,"
//                + "\"redistributionDelay\":1000,"
//                + "\"sendToDLAOnNoRoute\":false,"
//                + "\"maxDeliveryAttempts\":10}";
//        assertEquals("Incorrect address settings info", expected, response.getResponse().get("result").asString());
    }
    /**
     * @tpTestDetails One server is started. Invoke Cli operation focused on
     * getting roles and check correct result and success of operations. Tested
     * operations : get-roles-as-json, get-roles
     *
     * @tpProcedure <ul>
     * <li>start  server</li>
     * <li>invoke Cli operations</li>
     * <li>check operations success and result</li>
     * </ul>
     * @tpPassCrit Operations were executed successfully with result containing
     * expected output
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingRoles() {
        container(1).start();
        CliTestUtils.assertSuccess(this.runOperation("get-roles-as-json", "address-match=#"));

        Result response = this.runOperation("get-roles", "address-match=#");
        assertTrue("Operation should not fail", response.isSuccess());
        assertEquals("Incorrect resposne size", 1, response.getResponse().get("result").asList().size());

        ModelNode result = response.getResponse().get("result").asList().get(0);

        assertEquals("Incorrect role name", "guest", result.get("name").asString());
        assertTrue("Send permission should be enabled", result.get("send").asBoolean());
        assertTrue("Consume permission should be enabled", result.get("consume").asBoolean());
        assertFalse("Manage permission should be disabled", result.get("manage").asBoolean());
        assertFalse("Creating durable queue permission should be disabled",
                result.get("create-durable-queue").asBoolean());
        assertFalse("Deleting durable queue permission should be disabled",
                result.get("delete-durable-queue").asBoolean());
        assertTrue("Creating non-durable queue permission should be enabled",
                result.get("create-non-durable-queue").asBoolean());
        assertTrue("Deleting non-durable queue permission should be enabled",
                result.get("delete-non-durable-queue").asBoolean());
        container(1).stop();
    }
    /**
     * @tpTestDetails One server is started. Invoke Cli operations focused on
     * getting connection info. Check success of operations.Tested operations :
     * list-connection-ids, list-connections-as-json
     *
     * @tpProcedure <ul>
     * <li>start server</li>
     * <li>invoke Cli operations</li>
     * <li>check operations success</li>
     * </ul>
     * @tpPassCrit Operations were executed successfully.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectionInfo() throws Exception {
        container(1).start();
        
        Result response1 = this.runOperation("list-connection-ids");
        assertTrue("Operation list-connection-ids should not fail", response1.isSuccess());
        
        Result response2 = this.runOperation("list-connections-as-json");
        assertTrue("Operation list-connections-as-json should not fail", response2.isSuccess());
        
        container(1).stop();
    }
    /**
     * @tpTestDetails One server is started. Invoke Cli operations
     * list-connection-ids and list-remote-addresses. Check that operation
     * list-connection-ids was successful and response has a correct size.
     *
     * @tpProcedure <ul>
     * <li>start server</li>
     * <li>invoke Cli operations</li>
     * <li>check operation success and response</li>
     * </ul>
     * @tpPassCrit Operations were executed successfully and response contains
     * correct number of connection ids.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectionIds() throws Exception {
        container(1).start();
        int numberOfExpectedConnections = this.runOperation("list-remote-addresses").getResponse().get("result").asList().size();
        Result response = this.runOperation("list-connection-ids");
        assertTrue("Operation should not fail", response.isSuccess());
        assertEquals("Incorrect response size", numberOfExpectedConnections, response.getResponse().get("result").asList().size());
        container(1).stop();
    }
    /**
     * @tpTestDetails One server with deployed queue is started. Create producer
     * and send message to queue.Invoke Cli operations to list sessions for
     * connection (list-sessions) and operation list-producers-info-as-json.
     *
     * @tpProcedure <ul>
     * <li>start server with deployed queue</li>
     * <li>send message to queue</li>
     * <li>invoke Cli operations</li>
     * </ul>
     * @tpPassCrit Operations were executed successfully.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testListingSessions() throws Exception {
        this.prepareQueueOnServer();
        container(1).start();
        Context ctx = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) ctx.lookup(container(1).getConnectionFactoryName());
        Connection conn = cf.createConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue("testQueue");
        MessageProducer producer = session.createProducer(queue);
        Message msg = session.createTextMessage("test message");
        producer.send(msg);

        JmsServerInfo serverInfo = new JmsServerInfo(container(1), "default");
        String[] connectionIds = serverInfo.getConnectionIds();

        //assertEquals("Incorrect number of client connections to server", 1, connectionIds.length);
        // connectionIds returns two IDs in random order
        String connId = connectionIds[0];

        Result response = this.runOperation("list-sessions", "connection-id=" + connId);
        Result r2 = this.runOperation("list-producers-info-as-json");

        producer.close();
        session.close();
        conn.stop();
        conn.close();
        container(1).stop();
    }

    private Result runOperation(final String operation, final String... params) {
        String cmd;
        if (container(1).getContainerType() == CONTAINER_TYPE.EAP6_CONTAINER) {
            cmd = CliUtils.buildCommand(MODULE_EAP6, ":" + operation, params);
        } else {
            cmd = CliUtils.buildCommand(MODULE_EAP7, ":" + operation, params);
        }
        return this.cli.executeCommand(cmd);
    }

    private void prepareQueueOnServer() {
        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();
        ops.createQueue("testQueue", "jms/testQueue");
        ops.close();
        container(1).stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container test container - defined in arquillian.xml
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(Container container, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorNameEAP6 = "netty";
        String connectorNameEAP7 = "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setFailoverOnShutdown(true);
            jmsAdminOperations.setSharedStore(true);
            jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);
        } else {
            jmsAdminOperations.addHAPolicySharedStoreMaster(5000, true);
        }
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorNameEAP6, "");
        } else {
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorNameEAP7, "");
        }

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);

        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorNameEAP6);
        } else {
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorNameEAP7);
        }

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareBackupServer(Container container, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorNameEAP6 = "netty";
        String connectorNameEAP7 = "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setBackup(true);
            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setFailoverOnShutdown(true);
            jmsAdminOperations.setSharedStore(true);
            jmsAdminOperations.setAllowFailback(true);
            jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);
        } else {
            jmsAdminOperations.addHAPolicySharedStoreSlave(true, 1000, true, false, false, null, null, null, null);
        }

        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorNameEAP6, "");
        } else {
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorNameEAP7, "");
        }

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        if (ContainerUtils.isEAP6(container)) {
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorNameEAP6);
        } else {
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorNameEAP7);
        }

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {
        prepareLiveServer(container(1), JOURNAL_DIRECTORY_A);
        prepareBackupServer(container(2), JOURNAL_DIRECTORY_A);
    }

}
