package org.jboss.qa.hornetq.test.clients;


import org.hornetq.api.core.management.HornetQServerControl;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
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
public class CloseConnectionsForUserTestCase extends AbstractClientCloseTestCase {

    @After
    public void shutdownServerAfterTest() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(new ModelNodeCloser("guest"));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughModelNode() throws Exception {
        clientForcedDisconnectTest(true, "user", new ModelNodeCloser("user"));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(new JmxCloser("guest"));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughJmx() throws Exception {
        clientForcedDisconnectTest(true, "user", new JmxCloser("user"));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testClientDisconnectionThroughCli() throws Exception {
        clientForcedDisconnectTest(new CliCloser("guest"));
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSecuredClientDisconnectionThroughCli() throws Exception {
        clientForcedDisconnectTest(true, "user", new CliCloser("user"));
    }

    private class ModelNodeCloser implements ClientCloser {

        private final String username;

        public ModelNodeCloser(String username) {
            this.username = username;
        }

        @Override
        public boolean closeClients() throws Exception {
            JMSOperations ops = null;
            try {
                ops = container(1).getJmsOperations();
                return ops.closeClientsByUserName(username);
            } finally {
                if (ops != null) {
                    ops.close();
                }
            }
        }
    }


    private class JmxCloser implements ClientCloser {

        private final String username;

        public JmxCloser(String username) {
            this.username = username;
        }

        @Override
        public boolean closeClients() throws Exception {
            JMXConnector jmxConnector = null;
            try {
                jmxConnector = jmxUtils.getJmxConnectorForEap(CONTAINER1_INFO);
                jmxConnector.connect();
                MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
                HornetQServerControl serverControl = jmxUtils.getHornetQServerMBean(connection);

                // This is workaround for direct method call, that would make the TS non-compilable
                // with older client versions. Throws NoSuchMethod on older org.jboss.qa.hornetq.apps.clients thus failing the test
                Class<? extends HornetQServerControl> controlClass = serverControl.getClass();
                Method closeMethod = controlClass.getMethod("closeConnectionsForUser", String.class);
                return (Boolean) closeMethod.invoke(serverControl, username);
            } finally {
                if (jmxConnector != null) {
                    jmxConnector.close();
                }
            }
        }
    }


    private class CliCloser implements ClientCloser {

        private final String username;

        public CliCloser(String username) {
            this.username = username;
        }

        @Override
        public boolean closeClients() throws Exception {
            CliConfiguration config = new CliConfiguration(container(1).getHostname(), container(1).getPort(),
                    getUsername(CONTAINER1_NAME), getPassword(CONTAINER1_NAME));
            CliClient cliClient = new CliClient(config);

            CLI.Result result = cliClient.executeCommand(CliUtils.buildCommand(
                    "/subsystem=messaging/hornetq-server=default",
                    ":close-connections-for-user", "user=" + username));
            return result.getResponse().asBoolean();
        }
    }



    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @BMRule(name = "who is calling interrupt",
            targetClass = "java.lang.Thread",
            targetMethod = "interrupt",
            isAfter = false,
            //targetLocation = "WRITE $channel",
            targetLocation = "ENTRY",
            action = "traceStack(\"called interrupt on thread \" + $0 + \" from thread \" + Thread.currentThread(), 50);")
    public void testClientDisconnectionWithByteman() throws Exception {
        clientForcedDisconnectTest(new JmxCloser("guest"));

    }
}
