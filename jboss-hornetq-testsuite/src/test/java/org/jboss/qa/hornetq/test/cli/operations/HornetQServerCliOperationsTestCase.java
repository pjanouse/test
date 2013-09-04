package org.jboss.qa.hornetq.test.cli.operations;


import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.JmsServerInfo;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
public class HornetQServerCliOperationsTestCase extends HornetQTestCase {

    private static final String MODULE = "/subsystem=messaging/hornetq-server=default";

    private final CliClient cli = new CliClient(new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6));


    @Before
    public void startServer() {
        this.controller.start(CONTAINER1);
    }


    @After
    public void stopServer() {
        this.controller.stop(CONTAINER1);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testGettingConnectors() {
        Result response = this.runOperation("get-connectors-as-json");
        assertTrue("Operation should not fail", response.isSuccess());

        String expected = "["
                + "{\"name\":\"netty\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.netty.NettyConnectorFactory\","
                + "\"params\":{\"port\":\"5445\",\"host\":\"192.168.48.1\"}},"
                + "{\"name\":\"in-vm\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.invm.InVMConnectorFactory\","
                + "\"params\":{\"server-id\":0}},"
                + "{\"name\":\"netty-throughput\",\"factoryClassName\":\"org.hornetq.core.remoting.impl.netty.NettyConnectorFactory\","
                + "\"params\":{\"batch-delay\":\"50\",\"port\":\"5455\",\"host\":\"192.168.48.1\"}}]";
        assertEquals("Incorrect connectors info", expected, response.getResponse().get("result").asString());
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
                + "\"addressFullMessagePolicy\":\"BLOCK\","
                + "\"pageSizeBytes\":10485760,"
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
        // connectionIds returns two IDs in random order - TODO need to identify the correct one
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

}
