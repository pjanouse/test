package org.jboss.qa.artemis.test.clients.clients;


import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import category.Functional;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.management.cli.CliUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import java.lang.reflect.Method;

/**
 * @tpChapter Functional testing
 * @tpSubChapter CLOSE CONNECTIONS - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpTestCaseDetails This test case is focused on testing management operations
 * to allow forced client disconnection by destination address. There is one
 * server running with connected clients. Based on destination address, clients
 * are disconnected using Cli, Jmx or ModelNode approach.
 * 
*/
@RunWith(Arquillian.class)
@Category(Functional.class)
public class CloseConnectionsForAddressTestCase extends AbstractClientCloseTestCase {

    @After
    public void shutdownServerAfterTest() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener. Start producer and consumer for queue on the server.
     * Process some messages, then force closing of clients. Client
     * disconnection is based on their destination address and model node
     * approach is used to close connections. Check clients disconnection and
     * proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, model node approach)</li>
     * <li>Check clients were disconnected and closed properly</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(new ModelNodeCloser());
    }

     /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener. Start connection to the
     * server with user role. Start producer and consumer
     * for queue on the server. Process some messages, then force closing of
     * clients. Client disconnection is based on their destination address and
     * model node approach is used to close connections. Check clients
     * disconnection and proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Start connection to the server with user role</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, model node approach)</li>
     * <li>Check clients were disconnected and closed properly</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(true, "user", new ModelNodeCloser());
    }
    /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener. Start producer and consumer
     * for queue on the server. Process some messages, then force closing of
     * clients. Client disconnection is based on their destination address and
     * JMX is used to close connections. Check clients
     * disconnection and proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, use JMX to close connections)</li>
     * <li>Check clients were disconnected and closed properly</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(new JmxCloser());
    }
    /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener. Start connection to the
     * server with user role. Start producer and consumer
     * for queue on the server. Process some messages, then force closing of
     * clients. Client disconnection is based on their destination address and
     * JMX is used to close connections. Check clients
     * disconnection and proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Start connection to the server with user role</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, use JMX to close connections)</li>
     * <li>Check clients were disconnected and closed properly</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(true, "user", new JmxCloser());
    }
    /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener.  Start producer and consumer
     * for queue on the server. Process some messages, then force closing of
     * clients. Client disconnection is based on their destination address and
     * CLI is used to close connections. Check clients
     * disconnection and proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, CLI is used to close connections)</li>
     * <li>Check clients were disconnected and closed properly</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughCli() throws Exception {
        clientForcedDisconnectTest(new CliCloser());
    }
    /**
     * @tpTestDetails Single server with deployed queue is started. Connect to
     * JMX server and attach notification listener. Start connection to the
     * server with user role. Start producer and consumer
     * for queue on the server. Process some messages, then force closing of
     * clients. Client disconnection is based on their destination address and
     * CLI is used to close connections. Check clients
     * disconnection and proper notifications about their disconnection.
     * @tpProcedure <ul>
     * <li>Start server with single queue deployed.</li>
     * <li>Attach notification listener, set up error listener for clients</li>
     * <li>Start connection to the server with user role</li>
     * <li>Connect to the server with the clients, start sending and receiving
     * messages to the queue.</li>
     * <li>Force closing of clients (based on their destination address, CLI is used to close connections)</li>
     * <li>Check JMX got proper notifications about clients</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Clients were disconnected and closed properly.</li>
     * <li>JMX server received proper notifications.</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughCli() throws Exception {
        clientForcedDisconnectTest(true, "user", new CliCloser());
    }

    private class ModelNodeCloser implements ClientCloser {

        @Override
        public boolean closeClients() throws Exception {
            JMSOperations ops = null;
            try {
                ops = container(1).getJmsOperations();
                return ops.closeClientsByDestinationAddress("jms.queue.#");
            } finally {
                if (ops != null) {
                    ops.close();
                }
            }
        }
    }

    private class JmxCloser implements ClientCloser {

        @Override
        public boolean closeClients() throws Exception {
            JMXConnector jmxConnector = null;
            try {
                jmxConnector = container(1).getJmxUtils().getJmxConnectorForEap(container(1));
                jmxConnector.connect();
                MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
                ActiveMQServerControl serverControl = container(1).getJmxUtils().getServerMBean(connection, ActiveMQServerControl.class);

                // This is workaround for direct method call, that would make the TS non-compilable
                // with older client versions. Throws NoSuchMethod on older org.jboss.qa.hornetq.apps.clients thus failing the test
                Class<? extends ActiveMQServerControl> controlClass = serverControl.getClass();
                Method closeMethod = controlClass.getMethod("closeConsumerConnectionsForAddress", String.class);
                return (Boolean) closeMethod.invoke(serverControl, "jms.queue.#");
            } finally {
                if (jmxConnector != null) {
                    jmxConnector.close();
                }
            }
        }
    }

    private class CliCloser implements ClientCloser {

        @Override
        public boolean closeClients() throws Exception {

            CliConfiguration config = new CliConfiguration(container(1).getHostname(), container(1).getPort(),
                    container(1).getUsername(), container(1).getPassword());
            CliClient cliClient = new CliClient(config);

            CLI.Result result = cliClient.executeCommand(CliUtils.buildCommand(
                    "/subsystem=messaging-activemq/server=default",
                    ":close-consumer-connections-for-address", "address-match=jms.queue.#"));
            return result.getResponse().asBoolean();
        }
    }

}
