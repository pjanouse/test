package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author mnovak@redhat.com
 */
@Category(FunctionalTests.class)
public class JmsBridgeAttributesTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(JmsBridgeAttributesTestCase.class);

    private static final String BRIDGE_NAME = "myBridge";

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

    private void prepareServerWithHornetQCoreBridge(Container container, Container targetServer) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String bridgeConnectionFactoryJndiName = "java:/jms/RemoteConnectionFactory";

        String sourceDestination = inQueueJndiName;
        String targetDestination = outQueueJndiName;

        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + targetServer.getHostname() + ":" + targetServer.getJNDIPort());
        String qualityOfService = "ONCE_AND_ONLY_ONCE";
        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName);
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");
        jmsAdminOperations.createJMSBridge(BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                bridgeConnectionFactoryJndiName, targetDestination, targetContext, qualityOfService, failureRetryInterval, -1,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);
        jmsAdminOperations.close();

    }


    private void prepareTargetServerForHornetQCoreBridge(Container container) {

        String connectionFactoryName = "RemoteConnectionFactory";

        JMSOperations jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.createQueue(outQueueName, outQueueJndiName);
        jmsAdminContainer1.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminContainer1.close();

    }

    @Before
    public void startServer() throws InterruptedException {
        container(1).start();
        container(2).start();

        prepareServerWithHornetQCoreBridge(container(1), container(2));
        prepareTargetServerForHornetQCoreBridge(container(2));

        container(1).stop();
        container(2).stop();
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
        container(1).start();
        container(2).start();

        String address = "/subsystem=messaging/jms-bridge=" + BRIDGE_NAME;

        writeReadAttributeTest(address, "/hornetqJmsBridgeAttributes.txt");

        Container sourceServer = container(1);
        Container targetServer = container(2);
        int numberOfMessages = 100;

        ProducerTransAck prod = new ProducerTransAck(sourceServer, inQueueJndiName, numberOfMessages);
        prod.start();
        prod.join();
        ReceiverTransAck r = new ReceiverTransAck(targetServer, outQueueJndiName);
        r.setReceiveTimeOut(1000);
        r.start();
        r.join();

        Assert.assertEquals("There is different number of sent and received messages. Probably bridge was not correctly deployed",
                numberOfMessages, r.getListOfReceivedMessages().size());
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
