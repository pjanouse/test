package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.naming.Context;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

    private static final String JMS_BRIDGE_NAME = "myBridge";

    private enum TestType {SOURCE_SECURITY, TARGET_SECURITY, SOURCE_TARGET_SECURITY, CORRECT_SECURITY}

    private MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 200);

    private FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

    // Queue to send messages in
    private String inQueueName = "InQueue";
    private String inQueueJndiName = "jms/queue/" + inQueueName;
    private String sourceDestination = inQueueJndiName;
    // queue for receive messages out
    private String outQueueName = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueueName;
    private String targetDestination = outQueueJndiName;

    //servers
    private Container inServer = container(1);
    private Container outServer = container(2);

    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }


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
    public void testSecureBridgeCorrectConfiguration() throws Exception {
        testSecureBridge(TestType.CORRECT_SECURITY);
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
    public void testSecureBridgeSourceSettings() throws Exception {
        testSecureBridge(TestType.SOURCE_SECURITY);
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
    public void testSecureBridgeTargetSettings() throws Exception {
        testSecureBridge(TestType.TARGET_SECURITY);
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
    public void testSecureBridgeBothSecuritySet() throws Exception {
        testSecureBridge(TestType.SOURCE_TARGET_SECURITY);
    }


    public void testSecureBridge(TestType testType) throws Exception {
        prepareServers(inServer, outServer, Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE, testType);
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

    private void sendReceiveSubTest(Container inServer, Container outServer, TestType testType) throws Exception {
        ProducerClientAck producer = new ProducerClientAck(inServer,
                inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(messageBuilder);
        producer.setTimeout(0);
        producer.addMessageVerifier(messageVerifier);
        producer.start();

        ReceiverClientAck receiver = new ReceiverClientAck(outServer,
                outQueueJndiName, 10000, 100, 10);
        receiver.addMessageVerifier(messageVerifier);
        receiver.start();
        receiver.join();
        producer.join();

        logger.info("Producer: " + producer.getListOfSentMessages().size());
        logger.info("Receiver: " + receiver.getListOfReceivedMessages().size());

        messageVerifier.verifyMessages();

        if (testType.equals(TestType.CORRECT_SECURITY)) {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
            Assert.assertTrue("No send messages.", producer.getListOfSentMessages().size() > 0);
        } else {
            Assert.assertEquals("Message should be sent", NUMBER_OF_MESSAGES_PER_PRODUCER, producer.getCount());
            Assert.assertEquals("Message should not be received", 0, receiver.getCount());
        }

    }

    /**
     * Prepares servers
     *
     * @param inServer         source server
     * @param outServer        targetServer
     * @param qualityOfService desired quality of service @see Constants.QUALITY_OF_SERVICE
     */
    private void prepareServers(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, TestType testType) {
        if (ContainerUtils.isEAP6(inServer)) {
            prepareServersEAP6(inServer, outServer, qualityOfService, testType);
        } else {
            prepareServersEAP7(inServer, outServer, qualityOfService, testType);
        }
    }

    private void prepareServersEAP6(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, TestType testType) {
        prepareServerEAP6(inServer);
        if (!inServer.getName().equals(outServer.getName())) {
            prepareServerEAP6(outServer);
        }
        deployBridgeEAP6(inServer, outServer, qualityOfService, -1, testType);
    }

    private void prepareServersEAP7(Container inServer, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, TestType testType) {
        prepareServerEAP7(inServer);
        if (!inServer.getName().equals(outServer.getName())) {
            prepareServerEAP7(outServer);
        }
        deployBridgeEAP7(inServer, outServer, qualityOfService, -1, testType);
    }

    protected void prepareServerEAP6(Container container) {

        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "RemoteConnectionFactory";
        String clusterName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);
        jmsAdminOperations.removeClusteringGroup(clusterName);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setFactoryType(inVmConnectionFactory, "XA_GENERIC");
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.setSecurityEnabled(true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.disableSecurity();

        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

        createDestinations(jmsAdminOperations);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "bridge");
        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "consume", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "send", false);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", false);


        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsAdminOperations.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations.rewriteLoginModule("RealmDirect", opts);

        try {
            UsersSettings.forEapServer(container(1))
                    .withUser("guest", null, "guest")
                    .withUser("user", "user", "users")
                    .withUser("admin", "adminadmin", "admin")
                    .withUser("bridge", "bridge", "bridge")
                    .create();
            UsersSettings.forEapServer(container(2))
                    .withUser("guest", null, "guest")
                    .withUser("user", "user", "users")
                    .withUser("admin", "adminadmin", "admin")
                    .withUser("bridge", "bridge", "bridge")
                    .create();
        } catch (IOException e) {
            e.printStackTrace();
        }

        jmsAdminOperations.close();
        container.stop();
    }

    protected void createDestinations(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
    }

    protected void prepareServerEAP7(Container container) {

        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "RemoteConnectionFactory";
        String clusterName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeClusteringGroup(clusterName);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setFactoryType(inVmConnectionFactory, "XA_GENERIC");
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

        createDestinations(jmsAdminOperations);

        jmsAdminOperations.setSecurityEnabled(true);
        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "bridge");
        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "consume", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "bridge", "send", false);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", false);


        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        opts.put("unauthenticatedIdentity", "guest");
        jmsAdminOperations.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations.rewriteLoginModule("RealmDirect", opts);

        try {
            UsersSettings.forEapServer(container(1))
                    .withUser("guest", null, "guest")
                    .withUser("user", "user", "users")
                    .withUser("admin", "adminadmin", "admin")
                    .withUser("bridge", "bridge", "bridge")
                    .create();
            UsersSettings.forEapServer(container(2))
                    .withUser("guest", null, "guest")
                    .withUser("user", "user", "users")
                    .withUser("admin", "adminadmin", "admin")
                    .withUser("bridge", "bridge", "bridge")
                    .create();
        } catch (IOException e) {
            e.printStackTrace();
        }

        jmsAdminOperations.close();
        container.stop();
    }


    private void deployBridgeEAP6(Container containerToDeploy, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, int maxRetries, TestType testType) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String targetConnectionFactory = "jms/RemoteConnectionFactory";

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP6);
        if(JMSTools.isIpv6Address(outServer.getHostname())){
            targetContext.put(Context.PROVIDER_URL, String.format("%s[%s]:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6, outServer.getHostname(), outServer.getJNDIPort()));
        }else{
            targetContext.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP6, outServer.getHostname(), outServer.getJNDIPort()));
        }

        if (qualityOfService == null || "".equals(qualityOfService.toString())) {
            qualityOfService = Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        switch (testType) {
            case TARGET_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, null, null, "bridge", "bridge");
                break;
            case SOURCE_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", null, null);
                break;
            case SOURCE_TARGET_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", "bridge", "bridge");
                break;
            case CORRECT_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "admin", "adminadmin", "admin", "adminadmin");
        }

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }

    private void deployBridgeEAP7(Container containerToDeploy, Container outServer, Constants.QUALITY_OF_SERVICE qualityOfService, int maxRetries, TestType testType) {

        String sourceConnectionFactory = "java:/ConnectionFactory";
        String targetConnectionFactory = "jms/RemoteConnectionFactory";

        Map<String, String> targetContext = new HashMap<String, String>();
        targetContext.put(Context.INITIAL_CONTEXT_FACTORY, Constants.INITIAL_CONTEXT_FACTORY_EAP7);
        if(JMSTools.isIpv6Address(outServer.getHostname())){
            targetContext.put(Context.PROVIDER_URL, String.format("%s[%s]:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, outServer.getHostname(), outServer.getJNDIPort()));
        }else{
            targetContext.put(Context.PROVIDER_URL, String.format("%s%s:%s", Constants.PROVIDER_URL_PROTOCOL_PREFIX_EAP7, outServer.getHostname(), outServer.getJNDIPort()));
        }

        if (qualityOfService == null || "".equals(qualityOfService.toString())) {
            qualityOfService = Constants.QUALITY_OF_SERVICE.ONCE_AND_ONLY_ONCE;
        }

        long failureRetryInterval = 100;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        containerToDeploy.start();
        JMSOperations jmsAdminOperations = containerToDeploy.getJmsOperations();

        // set XA on sourceConnectionFactory
        jmsAdminOperations.setFactoryType("InVmConnectionFactory", "XA_GENERIC");

        switch (testType) {
            case TARGET_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, null, null, "bridge", "bridge");
                break;
            case SOURCE_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", null, null);
                break;
            case SOURCE_TARGET_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "bridge", "bridge", "bridge", "bridge");
                break;
            case CORRECT_SECURITY:
                jmsAdminOperations.createJMSBridge(JMS_BRIDGE_NAME, sourceConnectionFactory, sourceDestination, null,
                        targetConnectionFactory, targetDestination, targetContext, qualityOfService.toString(), failureRetryInterval, maxRetries,
                        maxBatchSize, maxBatchTime, addMessageIDInHeader, "admin", "adminadmin", "admin", "adminadmin");
        }

        jmsAdminOperations.close();
        containerToDeploy.stop();
    }

}
