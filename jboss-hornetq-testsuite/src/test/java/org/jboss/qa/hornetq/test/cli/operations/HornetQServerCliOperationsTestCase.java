package org.jboss.qa.hornetq.test.cli.operations;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.utils.UUIDGenerator;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JmsServerInfo;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.test.cli.CliTestUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import static org.junit.Assert.*;


/**
 * needs a connected client:
 * list-all-consumers-as-json      - done
 * list-producers-info-as-json      - done
 * reset-all-message-counter-histories  - done
 * reset-all-message-counters        - done
 * list-connections-as-json    - done
 * <p/>
 * list-connection-ids -> take connection id and use in        - done
 * <p/>
 * list-sessions              - done
 * list-sessions-as-json - > take session id and use in       - done
 * get-session-creation-time                  - done
 * list-consumers-as-json                  - done
 * <p/>
 * list-remote-addresses -> take IP + session from list-sessions and use in
 * <p/>
 * get-last-sent-message-id                - done
 * close-connections-for-address                - done
 * <p/>
 * list-sessions -> take session id and use in
 * <p/>
 * list-target-destinations        - done
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * needs prepared transaction
 * commit-prepared-transaction                 - done
 * list-heuristic-committed-transactions         - done
 * list-heuristic-rolled-back-transactions              - done
 * list-prepared-transaction-details-as-html        - done
 * list-prepared-transaction-details-as-json        - done
 * list-prepared-transaction-jms-details-as-html    - done
 * list-prepared-transaction-jms-details-as-json    - done
 * list-prepared-transactions           - done
 * rollback-prepared-transaction          - done
 * <p/>
 * needs live backup pair
 * force-failover      -done
 * <p/>
 * needs default config
 * get-address-settings-as-json  - done
 * get-connectors-as-json        - done
 * get-roles                     -done
 * get-roles-as-json   -done
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class HornetQServerCliOperationsTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(HornetQServerCliOperationsTestCase.class);


    private static final String MODULE = "/subsystem=messaging/hornetq-server=default";

    private final CliClient cli = new CliClient(new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1)));

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100000;

    String coreQueueName = "testQueue";
    String coreTopicName = "testTopic";
    String queueJndiName = "jms/queue/" + coreQueueName;
    String topicJndiName = "jms/topic/" + coreTopicName;

    @Before
    public void startServer() {
        this.controller.start(CONTAINER1);
    }

    @After
    public void stopServer() {
        stopServer(CONTAINER1);
    }

    private void prepareServerForXATransactions() {

        // start server with XA connection factory
        controller.start(CONTAINER1);
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

        jmsAdminOperations.setFactoryType("RemoteConnectionFactory", "XA_GENERIC");
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName);

        jmsAdminOperations.close();

        stopServer(CONTAINER1);
    }

    private void createPreparedTransaction() {
        Context context = null;

        XAConnection con = null;

        XASession xaSession = null;

        Session session = null;

        Queue queue = null;

        try {

            context = getContext(CONTAINER1_IP);

            queue = (Queue) context.lookup(queueJndiName);

            XAConnectionFactory cf = (XAConnectionFactory) context.lookup(getConnectionFactoryName());

            con = cf.createXAConnection();

            con.start();

            xaSession = con.createXASession();

            session = xaSession.getSession();

            XAResource xaResource = xaSession.getXAResource();

            Random r = new Random();

            Xid xid = new XidImpl(("xa-example1" + r.nextInt()).getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

            xaResource.start(xid, XAResource.TMNOFLAGS);

            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage("my message with xid: " + xid.toString()));

            // Step 17. Stop the work
            xaResource.end(xid, XAResource.TMSUCCESS);

            // Step 18. Prepare
            xaResource.prepare(xid);

            con.close();

        } catch (Exception ex) {
            logger.error("Error: ", ex);
        }
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testPreparedTransactions() throws Exception {

        int numberOfPreparedTransactions = 10;

        prepareServerForXATransactions();

        controller.start(CONTAINER1);

        for (int i = 0; i < numberOfPreparedTransactions; i++)  {
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
        if (numberOfPreparedTransactions -2 == r10.getResponse().get("result").asList().size()) {
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

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testForceFailover() throws Exception {

        prepareSimpleDedicatedTopology();
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        // invoke operation
        Result r1 = runOperation("force-failover", null);
        logger.info("Result force-failover " + r1.getResponse().asString());
//        CliTestUtils.assertSuccess(r1);

        // check that backup started
        waitHornetQToAlive(CONTAINER2_IP, 5445, 60000);

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
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER1_IP, 4447, queueJndiName);
        receiver.setTimeout(1000);

        // start clients
        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(CONTAINER1_IP, 4447, topicJndiName, "testSubscriberClientId-hornetqCliOperations", "testSubscriber-hqServerCliOperations");
        subscriberClientAck.setTimeout(1000);
        subscriberClientAck.subscribe();
        PublisherClientAck publisher = new PublisherClientAck(CONTAINER1_IP, 4447, topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
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
        waitForReceiversUntil(receivers, 20, 60000);

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

        // stop clients
        producer.stopSending();
        publisher.stopSending();
        receiver.join();
        subscriberClientAck.join();


    }

    private void prepareServer(String containerName) {

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.createQueue(coreQueueName, queueJndiName, true);
        jmsAdminOperations.createTopic(coreTopicName, topicJndiName);

        jmsAdminOperations.close();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectors() {
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


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingAddressSettings() {


        Result response = this.runOperation("get-address-settings-as-json", "address-match=#");
        assertTrue("Operation should not fail", response.isSuccess());

        String expected = "{\"maxSizeBytes\":10485760,"
                + "\"expiryAddress\":\"jms.queue.ExpiryQueue\","
                + "\"redeliveryMultiplier\":1,"
                + "\"addressFullMessagePolicy\":\"PAGE\","
                + "\"pageSizeBytes\":2097152,"
                + "\"expiryDelay\":-1,"
                + "\"DLA\":\"jms.queue.DLQ\","
                + "\"maxRedeliveryDelay\":0,"
                + "\"pageCacheMaxSize\":5,"
                + "\"lastValueQueue\":false,"
                + "\"redeliveryDelay\":0,"
                + "\"redistributionDelay\":1000,"
                + "\"sendToDLAOnNoRoute\":false,"
                + "\"maxDeliveryAttempts\":10}";
        assertEquals("Incorrect address settings info", expected, response.getResponse().get("result").asString());
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingRoles() {
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
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectionInfo() throws Exception {
        this.runOperation("list-connection-ids");
        this.runOperation("list-connections-as-json");
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectionIds() throws Exception {
        Result response = this.runOperation("list-connection-ids");
        assertTrue("Operation should not fail", response.isSuccess());
        assertEquals("Incorrect response size", 1, response.getResponse().get("result").asList().size());
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testListingSessions() throws Exception {
        this.prepareQueueOnServer();

        Context ctx = this.getContext();
        ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryName());
        Connection conn = cf.createConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue("testQueue");
        MessageProducer producer = session.createProducer(queue);
        Message msg = session.createTextMessage("test message");
        producer.send(msg);

        JmsServerInfo serverInfo = new JmsServerInfo();
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
    }


    private Result runOperation(final String operation, final String... params) {
        String cmd = CliUtils.buildCommand(MODULE, ":" + operation, params);
        return this.cli.executeCommand(cmd);
    }


    private void prepareQueueOnServer() {
        JMSOperations ops = this.getJMSOperations(CONTAINER1);
        ops.createQueue("testQueue", "jms/testQueue");
        ops.close();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();

        controller.stop(containerName);

    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareBackupServer(String containerName, String bindingAddress, String journalDirectory) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.close();

        controller.stop(containerName);
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {

        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        prepareBackupServer(CONTAINER2, CONTAINER2_IP, JOURNAL_DIRECTORY_A);

    }

}
