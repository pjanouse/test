package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
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
public class HornetQCoreBridgeAttributesTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(HornetQCoreBridgeAttributesTestCase.class);

    private static final String HORNETQ_CORE_BRIDGE_NAME = "myBridge";
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

    CliConfiguration cliConf = new CliConfiguration(getHostname(CONTAINER1_NAME), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1_NAME), getPassword(CONTAINER1_NAME));

    private void prepareServerWithHornetQCoreBridge(Container container, String targeServerName) {

        String messagingBridgeConnectorAndSocketBindingName = "messaging-bridge";

        JMSOperations jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.addRemoteSocketBinding(messagingBridgeConnectorAndSocketBindingName, getHostname(targeServerName), getHornetqPort(targeServerName));
        jmsAdminContainer1.createRemoteConnector(messagingBridgeConnectorAndSocketBindingName, messagingBridgeConnectorAndSocketBindingName, null);
        jmsAdminContainer1.createQueue(inQueueName, inQueueJndiName);
        jmsAdminContainer1.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminContainer1.close();

        container.restart();

        jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.createCoreBridge(HORNETQ_CORE_BRIDGE_NAME, "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1,
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

        controller.start(CONTAINER1_NAME);
        controller.start(CONTAINER2_NAME);

        prepareServerWithHornetQCoreBridge(container(1), CONTAINER2_NAME);
        prepareTargetServerForHornetQCoreBridge(container(2));
    }


    @After
    public void stopServer() {

        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeHornetqCoreBridgeTest() throws Exception {

        String address = "/subsystem=messaging/hornetq-server=default/bridge=" + HORNETQ_CORE_BRIDGE_NAME;

        writeReadAttributeTest(address, "/hornetqCoreBridgeAttributes.txt");
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
