package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 * @author mnovak
 *
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class CoreBridgeAttributesTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(CoreBridgeAttributesTestCase.class);

    private static final String CORE_BRIDGE_NAME = "myBridge";
    private static final String CLUSTER_PASSWORD = "password";

    // queue to send messages in
    String inQueueName = "InQueue";
    String inQueueJndiName = "jms/queue/" + inQueueName;
    // queue for receive messages out
    String outQueueName = "OutQueue";
    String outQueueJndiName = "jms/queue/" + outQueueName;

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    private void prepareServerWithCoreBridge(Container container, Container targetContainer) {

        String messagingBridgeConnectorAndSocketBindingName = "messaging-bridge";

        JMSOperations jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.addRemoteSocketBinding(messagingBridgeConnectorAndSocketBindingName, targetContainer.getHostname(), targetContainer.getHornetqPort());
        jmsAdminContainer1.createRemoteConnector(messagingBridgeConnectorAndSocketBindingName, messagingBridgeConnectorAndSocketBindingName, null);
        jmsAdminContainer1.createQueue(inQueueName, inQueueJndiName);
        jmsAdminContainer1.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminContainer1.close();

        container.restart();

        jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.createCoreBridge(CORE_BRIDGE_NAME, "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1,
                messagingBridgeConnectorAndSocketBindingName);
        jmsAdminContainer1.close();
    }


    private void prepareTargetServerForHornetQCoreBridge(Container container) {

        JMSOperations jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.createQueue(outQueueName, outQueueJndiName);
        jmsAdminContainer1.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminContainer1.close();

    }

    @Before
    public void startServer() throws InterruptedException {

        container(1).stop();
        container(2).stop();

        container(1).start();
        container(2).start();

        prepareServerWithCoreBridge(container(1), container(2));
        prepareTargetServerForHornetQCoreBridge(container(2));
    }


    @After
    public void stopServer() {
        container(1).stop();
        container(2).stop();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeHornetqCoreBridgeTest() throws Exception {

        String address = getAddress();

        writeReadAttributeTest(address, "/hornetqCoreBridgeAttributes.txt");
    }

    private String getAddress() {
        String address = null;
        if (ContainerUtils.isEAP6(container(1))) {
            address = "/subsystem=messaging/hornetq-server=default/bridge=" + CORE_BRIDGE_NAME;
        } else {
            address = "/subsystem=messaging-activemq/server=default/bridge=" + CORE_BRIDGE_NAME;
        }
        return address;
    }

    public void writeReadAttributeTest(String address, String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);

            logger.info("Test attribute: " + attributeName + " with value: " + value);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }


}
