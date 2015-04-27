package org.jboss.qa.hornetq.test.clients.clients;


import org.apache.activemq.api.core.management.ActiveMQServerControl;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
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


@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class CloseConnectionsForAddressTestCase extends AbstractClientCloseTestCase {

    @After
    public void shutdownServerAfterTest() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(new ModelNodeCloser());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(true, "user", new ModelNodeCloser());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(new JmxCloser());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(true, "user", new JmxCloser());
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughCli() throws Exception {
        clientForcedDisconnectTest(new CliCloser());
    }

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
