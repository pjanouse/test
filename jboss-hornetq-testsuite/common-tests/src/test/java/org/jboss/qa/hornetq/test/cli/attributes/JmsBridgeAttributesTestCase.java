package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
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
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Read and write values to queue attributes. Tested
 * attributes : add-messageID-in-header, client-id, failure-retry-interval,
 * max-batch-size, max-batch-time, max-retries, module, quality-of-service,
 * selector, source-connection-factory, source-context, source-destination,
 * source-password, source-user, subscription-name, target-connection-factory,
 * target-context, target-destination, target-password, target-user
 *
 * @author mnovak@redhat.com
 *
 */
@Category(FunctionalTests.class)
public class JmsBridgeAttributesTestCase extends CliTestBase {

    private static final Logger logger = Logger.getLogger(JmsBridgeAttributesTestCase.class);

    private static final String BRIDGE_NAME = "myBridge";
    private String address = "/subsystem=" + (ContainerUtils.isEAP6(container(1)) ? "messaging" : "messaging-activemq") + "/jms-bridge=" + BRIDGE_NAME;

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

    private void prepareServerWithJMSBridge(Container container, Container targetServer) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String bridgeConnectionFactoryJndiName = "java:/jms/RemoteConnectionFactory";

        String sourceDestination = inQueueJndiName;
        String targetDestination = outQueueJndiName;

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", targetServer.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER) ? Constants.INITIAL_CONTEXT_FACTORY_EAP7 : Constants.INITIAL_CONTEXT_FACTORY_EAP6);
        targetContext.put("java.naming.provider.url", targetServer.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER) ? Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7 + targetServer.getHostname() + ":" + targetServer.getJNDIPort() : Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6 + targetServer.getHostname() + ":" + targetServer.getJNDIPort());
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

    private void prepareTargetServerForJMSBridge(Container container) {

        String connectionFactoryName = container.getContainerType().equals(Constants.CONTAINER_TYPE.EAP7_CONTAINER) ? Constants.CONNECTION_FACTORY_EAP7 : Constants.CONNECTION_FACTORY_EAP6;

        JMSOperations jmsAdminContainer1 = container.getJmsOperations();
        jmsAdminContainer1.createQueue(outQueueName, outQueueJndiName);
        jmsAdminContainer1.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminContainer1.close();

    }

    @Before
    public void startServer() throws InterruptedException {
        container(1).stop();
        container(2).stop();

        container(1).start();
        container(2).start();

        prepareTargetServerForJMSBridge(container(2));
        prepareServerWithJMSBridge(container(1), container(2));

        container(1).stop();
        container(2).stop();
    }

    @After
    public void stopServer() {
        container(1).stop();
        container(2).stop();
    }

    /**
     *
     * @tpTestDetails There are two servers started. Deploy InQueue to Node1
     * (source server) and OutQueue to Node2 (target server). Configure Jms
     * Bridge between these two servers. Try to read and write values to jms bridge
     * attributes. Then send 100 messages to InQueue. Try to receive messages
     * from OutQueue. Check number of received messages.
     *
     * @tpProcedure <ul>
     * <li>start two servers and configure jms bridge between them and deploy
     * queues</li>
     * <li>Write and read jms bridge attributes</li>
     * <li>Create producer and send messages to InQueue</li>
     * <li>Once producer finishes, create consumer and receive messages from
     * OutQueue</li>
     * </ul>
     * @tpPassCrit Consumer received correct number of messages, reading and
     * writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeHornetqCoreBridgeTest() throws Exception {
        container(1).start();
        container(2).start();

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
