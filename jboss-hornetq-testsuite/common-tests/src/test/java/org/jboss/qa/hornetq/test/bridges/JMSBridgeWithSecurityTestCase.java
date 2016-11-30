package org.jboss.qa.hornetq.test.bridges;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.specific.JMSBridgeWithSecurityPrepare;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * @tpChapter Functinal testing
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Test jms bridge with configured security
 */
@Category(FunctionalTests.class)
@RestoreConfigBeforeTest
@RunAsClient
@RunWith(Arquillian.class)
public class JMSBridgeWithSecurityTestCase extends HornetQTestCase {
    private static final Logger logger = Logger.getLogger(JMSBridgeWithSecurityTestCase.class);

    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    // Test type constants
    private static final String SOURCE_SECURITY = "SOURCE_SECURITY";
    private static final String TARGET_SECURITY = "TARGET_SECURITY";
    private static final String SOURCE_TARGET_SECURITY = "SOURCE_TARGET_SECURITY";
    private static final String CORRECT_SECURITY = "CORRECT_SECURITY";

    private MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

    private FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

    //servers
    private Container inServer = container(1);
    private Container outServer = container(2);

    /**
     * @tpTestDetails Start servers with deployed jms bridge. Set correct security and everything should work.
     * @tpProcedure <ul>
     * <li>Start one server with deployed jms bridge</li>
     * <li>Configure security</li>
     * <li>Send messages to source queue</li>
     * <li>Receive messages from target queue</li>
     * </ul>
     * @tpPassCrit Messages are correctly transmited over the bridge
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "JMSBridgeWithSecurity", params = {
            @Param(name = JMSBridgeWithSecurityPrepare.TEST_TYPE, value = CORRECT_SECURITY)
    })
    public void testSecureBridgeCorrectConfiguration() throws Exception {
        testSecureBridge(JMSBridgeWithSecurityPrepare.TestType.CORRECT_SECURITY);
    }


    /**
     * @tpTestDetails Start servers with deployed jms bridge. Set security which should not work
     * @tpProcedure <ul>
     * <li>Start one server with deployed jms bridge</li>
     * <li>Configure security on source server</li>
     * <li>Send messages to source queue</li>
     * <li>Try to receive messages from target queue</li>
     * </ul>
     * @tpPassCrit Messages are not transmitted over the bridge
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "JMSBridgeWithSecurity", params = {
            @Param(name = JMSBridgeWithSecurityPrepare.TEST_TYPE, value = SOURCE_SECURITY)
    })
    public void testSecureBridgeSourceSettings() throws Exception {
        testSecureBridge(JMSBridgeWithSecurityPrepare.TestType.SOURCE_SECURITY);
    }

    /**
     * @tpTestDetails Start servers with deployed jms bridge. Set security which should not work
     * @tpProcedure <ul>
     * <li>Start one server with deployed jms bridge</li>
     * <li>Configure security on target server</li>
     * <li>Send messages to source queue</li>
     * <li>Try to receive messages from target queue</li>
     * </ul>
     * @tpPassCrit Messages are not transmitted over the bridge
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "JMSBridgeWithSecurity", params = {
            @Param(name = JMSBridgeWithSecurityPrepare.TEST_TYPE, value = TARGET_SECURITY)
    })
    public void testSecureBridgeTargetSettings() throws Exception {
        testSecureBridge(JMSBridgeWithSecurityPrepare.TestType.TARGET_SECURITY);
    }

    /**
     * @tpTestDetails Start servers with deployed jms bridge. Set security which should not work
     * @tpProcedure <ul>
     * <li>Start one server with deployed jms bridge</li>
     * <li>Configure security on source and target server</li>
     * <li>Send messages to source queue</li>
     * <li>Try to receive messages from target queue</li>
     * </ul>
     * @tpPassCrit Messages are not transmitted over the bridge
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "JMSBridgeWithSecurity", params = {
            @Param(name = JMSBridgeWithSecurityPrepare.TEST_TYPE, value = SOURCE_TARGET_SECURITY)
    })
    public void testSecureBridgeBothSecuritySet() throws Exception {
        testSecureBridge(JMSBridgeWithSecurityPrepare.TestType.SOURCE_TARGET_SECURITY);
    }


    public void testSecureBridge(JMSBridgeWithSecurityPrepare.TestType testType) throws Exception {
        inServer.start();
        outServer.start();

        Thread.sleep(10000);
        logger.info("#############################");
        logger.info("JMS bridge should be connected now. Check logs above that is really so!");
        logger.info("#############################");

        sendReceiveSubTest(inServer, outServer, testType);

        outServer.stop();
        inServer.stop();
    }

    private void sendReceiveSubTest(Container inServer, Container outServer, JMSBridgeWithSecurityPrepare.TestType testType) throws Exception {
        ProducerClientAck producer = new ProducerClientAck(inServer,
                PrepareConstants.IN_QUEUE_JNDI, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.addMessageVerifier(messageVerifier);
        producer.start();

        ReceiverClientAck receiver = new ReceiverClientAck(outServer,
                PrepareConstants.OUT_QUEUE_JNDI, 10000, 100, 10);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();
        producer.join();

        logger.info("Producer: " + producer.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        if (testType.equals(JMSBridgeWithSecurityPrepare.TestType.CORRECT_SECURITY)) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
            Assert.assertTrue("No send messages.", producer.getListOfSentMessages().size() > 0);
        } else {
            Assert.assertEquals("Message should be sent", NUMBER_OF_MESSAGES_PER_PRODUCER, producer.getCount());
            Assert.assertEquals("Message should not be received", 0, receiver.getCount());
        }

    }

}
