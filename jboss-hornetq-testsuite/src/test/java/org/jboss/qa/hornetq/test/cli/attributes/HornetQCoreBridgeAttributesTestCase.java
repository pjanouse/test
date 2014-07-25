package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
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
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 * @author mnovak
 *
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
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

    CliConfiguration cliConf = new CliConfiguration(getHostname(CONTAINER1), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1));

    private void prepareServerWithHornetQCoreBridge(String containerName, String targeServerName) {

        String messagingBridgeConnectorAndSocketBindingName = "messaging-bridge";

        JMSOperations jmsAdminContainer1 = this.getJMSOperations(containerName);
        jmsAdminContainer1.addRemoteSocketBinding(messagingBridgeConnectorAndSocketBindingName, getHostname(targeServerName), getHornetqPort(targeServerName));
        jmsAdminContainer1.createRemoteConnector(messagingBridgeConnectorAndSocketBindingName, messagingBridgeConnectorAndSocketBindingName, null);
        jmsAdminContainer1.createQueue(inQueueName, inQueueJndiName);
        jmsAdminContainer1.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminContainer1.close();

        stopServer(containerName);
        controller.start(containerName);

        jmsAdminContainer1 = this.getJMSOperations(containerName);
        jmsAdminContainer1.createCoreBridge(HORNETQ_CORE_BRIDGE_NAME, "jms.queue." + inQueueName, "jms.queue." + outQueueName, -1,
                messagingBridgeConnectorAndSocketBindingName);
        jmsAdminContainer1.close();
    }


    private void prepareTargetServerForHornetQCoreBridge(String containerName) {

        JMSOperations jmsAdminContainer1 = this.getJMSOperations(containerName);
        jmsAdminContainer1.createQueue(outQueueName, outQueueJndiName);
        jmsAdminContainer1.setClusterUserPassword(CLUSTER_PASSWORD);
        jmsAdminContainer1.close();

    }

    @Before
    public void startServer() throws InterruptedException {

        controller.start(CONTAINER1);
        controller.start(CONTAINER2);

        prepareServerWithHornetQCoreBridge(CONTAINER1, CONTAINER2);
        prepareTargetServerForHornetQCoreBridge(CONTAINER2);
    }


    @After
    public void stopServer() {

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
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
