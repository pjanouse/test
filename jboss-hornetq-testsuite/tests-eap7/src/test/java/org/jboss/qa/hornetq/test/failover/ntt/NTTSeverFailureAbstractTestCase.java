package org.jboss.qa.hornetq.test.failover.ntt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.creaper.commands.foundation.offline.ConfigurationFileBackup;
import org.jboss.qa.creaper.core.CommandFailedException;
import org.jboss.qa.creaper.core.ManagementClient;
import org.jboss.qa.creaper.core.offline.OfflineManagementClient;
import org.jboss.qa.creaper.core.offline.OfflineOptions;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.apps.servlets.ServletConstants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by okalman on 8/10/15.
 */

@RunWith(Arquillian.class)
public abstract class NTTSeverFailureAbstractTestCase extends HornetQTestCase {

    private static Logger log = Logger.getLogger(NTTSeverFailureAbstractTestCase.class);

    public static final String PRODUCER_FILE_NAME_PREFIX = "producerServlet";
    public static final String CONSUMER_FILE_NAME_PREFIX = "consumerServlet";
    public static int MAX_MESSAGES;
    public static final int COMMIT_AFTER = 1;
    public static final long RECEIVE_TIMEOUT = 10000;
    public static final String NIO_JOURNAL = "NIO";


    private String queueName = "targetQueue0";
    private String queueJndiName = "jms/queue/" + queueName;

    private static String runningTestCase = "";

    private static ConfigurationFileBackup configurationFileBackupCont1 = new ConfigurationFileBackup();
    private static ConfigurationFileBackup configurationFileBackupCont2 = new ConfigurationFileBackup();
    private static ConfigurationFileBackup configurationFileBackupCont3 = new ConfigurationFileBackup();
    private static ConfigurationFileBackup configurationFileBackupCont4 = new ConfigurationFileBackup();

    private static final String cfgPath = "standalone" + File.separator + "configuration" + File.separator
            + "standalone-full-ha.xml";
    OfflineManagementClient clientCont1;
    OfflineManagementClient clientCont2;
    OfflineManagementClient clientCont3;
    OfflineManagementClient clientCont4;

    public NTTSeverFailureAbstractTestCase() {
        try {
            clientCont1 = ManagementClient.offline(OfflineOptions.standalone()
                    .configurationFile(new File(container(1).getServerHome() + File.separator + cfgPath)).build());
            clientCont2 = ManagementClient.offline(OfflineOptions.standalone()
                    .configurationFile(new File(container(2).getServerHome() + File.separator + cfgPath)).build());
            clientCont3 = ManagementClient.offline(OfflineOptions.standalone()
                    .configurationFile(new File(container(3).getServerHome() + File.separator + cfgPath)).build());
            clientCont4 = ManagementClient.offline(OfflineOptions.standalone()
                    .configurationFile(new File(container(4).getServerHome() + File.separator + cfgPath)).build());
        } catch (Exception IOException) {
        }

    }

    @RunAsClient
    @Before
    public void prepareServersForTest() throws CommandFailedException {
        MAX_MESSAGES = 10;
        if (!serversReady(this.getClass().toString())) {
            prepareServers(isClusteredTest(), isHATest(), NIO_JOURNAL);
            setRunningTestCase(this.getClass().toString());
            clientCont1.apply(configurationFileBackupCont1.backup());
            clientCont2.apply(configurationFileBackupCont2.backup());
            clientCont3.apply(configurationFileBackupCont3.backup());
            clientCont4.apply(configurationFileBackupCont4.backup());
        } else {
            clientCont1.apply(configurationFileBackupCont1.restore());
            clientCont2.apply(configurationFileBackupCont2.restore());
            clientCont3.apply(configurationFileBackupCont3.restore());
            clientCont4.apply(configurationFileBackupCont4.restore());

            clientCont1.apply(configurationFileBackupCont1.backup());
            clientCont2.apply(configurationFileBackupCont2.backup());
            clientCont3.apply(configurationFileBackupCont3.backup());
            clientCont4.apply(configurationFileBackupCont4.backup());
        }
    }

    public void overrideMaxMessagesForTest(int maxMessages) {
        MAX_MESSAGES = maxMessages;
    }

//    public void testSequence(int expectedMessagesCount, boolean expectedProducerFailure, boolean expectedConsumerFailure,
//            Container containerForBytemanRule) throws Exception {
//        testSequence(expectedMessagesCount, expectedProducerFailure, expectedConsumerFailure, containerForBytemanRule, true);
//    }
//
//    public void testSequence(int expectedMessagesCount, boolean expectedProducerFailure, boolean expectedConsumerFailure,
//            Container containerForBytemanRule, boolean checkMessagesOnServer) throws Exception {
//        testSequence(expectedMessagesCount, expectedProducerFailure, expectedConsumerFailure, containerForBytemanRule, true,
//                false);
//    }
//
//    public void testSequence(int expectedMessagesCount, boolean expectedProducerFailure, boolean expectedConsumerFailure,
//            Container containerForBytemanRule, boolean checkMessagesOnServer, boolean waitForPeriodicRecovery) throws Exception {
//        boolean verifierSet = false;
//        startAllServers();
//        container(3).deploy(PRODUCER);
//        container(4).deploy(CONSUMER);
//        long messagesOnServer = 0;
//
//        HttpClient httpclient = HttpClients.createDefault();
//        HttpPost producerRunPost = createHttpPostForProducerToRun();
//        HttpPost consumerRunPost = createHttpPostForConsumerToRun();
//        RuleInstaller.installRule(this.getClass(), containerForBytemanRule);
//        httpclient.execute(producerRunPost);
//        httpclient.execute(consumerRunPost);
//        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
//        int messagesCount = waitForAllMessages(15000, MAX_MESSAGES);
//        if (expectedConsumerFailure == false && expectedConsumerFailure == false) {
//            Assert.assertEquals("Number of received messages doesn't match", expectedMessagesCount, messagesCount);
//            if (containerForBytemanRule != container(3) && containerForBytemanRule != container(4)) {
//                String responseSent = (new BasicResponseHandler().handleResponse(httpclient
//                        .execute(createHttpPostForProducerForMessages())));
//                String responseReceived = (new BasicResponseHandler().handleResponse(httpclient
//                        .execute(createHttpPostForConsumerForMessages())));
//                messageVerifier.addSendMessages(deserializeResponse(responseSent));
//                messageVerifier.addReceivedMessages(deserializeResponse(responseReceived));
//
//                messageVerifier.verifyMessages();
//                verifierSet = true;
//
//            }
//
//        }
//
//        // CHECK FOR EXCEPTIONS ON CLIENTS
//        String producerException = "";
//        String consumerException = "";
//        if (containerForBytemanRule != container(3)) { // if we kill this container, we cant send him http request
//            producerException = new BasicResponseHandler().handleResponse(httpclient
//                    .execute(createHttpPostForProducerForExceptions()));
//        }
//        if (containerForBytemanRule != container(4)) { // if we kill this container, we cant send him http request
//            consumerException = new BasicResponseHandler().handleResponse(httpclient
//                    .execute(createHttpPostForConsumerForExceptions()));
//        }
//        if (expectedConsumerFailure) {
//            Assert.assertTrue("Consumer didn't throw any exception", consumerException.toLowerCase().contains("exception"));
//        } else {
//            if (consumerException.toLowerCase().contains("exception")) {
//                log.error("EXCEPTION: " + producerException);
//            }
//            Assert.assertFalse("Consumer threw exception", consumerException.toLowerCase().contains("exception"));
//        }
//        if (expectedProducerFailure) {
//            Assert.assertTrue("Producer didn't throw any exception", producerException.toLowerCase().contains("exception"));
//        } else {
//            if (producerException.toLowerCase().contains("exception")) {
//                log.error("EXCEPTION: " + producerException);
//            }
//            Assert.assertFalse("Producer threw exception", producerException.toLowerCase().contains("exception"));
//        }
//        container(3).stop(); // this may be unstable because servlet may produce some massages after verifier is filled, if so
//                             // send only one message instead of 10.
//        container(4).stop();
//
//        // WHEN WE KILL JMS Server we have to check that no messages were lost
//        if (checkMessagesOnServer && verifierSet) {
//            if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1))) {
//                container(1).start();
//            }
//            if (waitForPeriodicRecovery) {
//                Thread.sleep(240000);
//            }
//            JMSOperations adminOperationsLiveServer = container(1).getJmsOperations();
//            messagesOnServer = messagesOnServer + adminOperationsLiveServer.getCountOfMessagesOnQueue(queueName);
//            container(1).stop();
//            if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(2))) {
//                container(2).start();
//            }
//            JMSOperations adminOperationsBackup = container(2).getJmsOperations();
//            try {
//                messagesOnServer = messagesOnServer + adminOperationsBackup.getCountOfMessagesOnQueue(queueName);
//            } catch (Exception e) {
//                // queue doesn't have to be defined on server (if we are running test for standalone server)
//            }
//            container(2).stop();
//            Assert.assertEquals("Number of messages on server and client doesn't match", messageVerifier.getSentMessages()
//                    .size(), messagesOnServer + messageVerifier.getReceivedMessages().size());
//        }
//
//        // CHECK WHEN CONSUMER IS KILLED (we have to check expected messages count on JMS server not on client)
//        if (containerForBytemanRule == container(4)) {
//            if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1))) {
//                container(1).start();
//            }
//            if (waitForPeriodicRecovery) {
//                Thread.sleep(240000);
//            }
//            JMSOperations adminOperationsLiveServer = container(1).getJmsOperations();
//            messagesOnServer = messagesOnServer + adminOperationsLiveServer.getCountOfMessagesOnQueue(queueName);
//            container(1).stop();
//            if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(2))) {
//                container(2).start();
//            }
//            JMSOperations adminOperationsBackup = container(2).getJmsOperations();
//            try {
//                messagesOnServer = messagesOnServer + adminOperationsBackup.getCountOfMessagesOnQueue(queueName);
//            } catch (Exception e) {
//                // queue doesn't have to be defined on server (if we are running test for standalone server)
//            }
//            container(2).stop();
//            Assert.assertEquals("Number of messages on server ", expectedMessagesCount, messagesOnServer);
//
//        }
//    }

    public void producerFailureTestSequence(int expectedMessagesCount, boolean jvmFailure) throws Exception {
        startAllServers();
        container(3).deploy(createProducerServletDeployment(this.getProducerClass()));
        container(4).deploy(createConsumerServletDeployment(this.getConsumerClass()));
        HttpClient httpclient = HttpClients.createDefault();
        HttpPost producerRunPost = createHttpPostForProducerToRun();
        HttpPost consumerRunPost = createHttpPostForConsumerToRun();
        RuleInstaller.installRule(this.getClass(), container(3));
        httpclient.execute(producerRunPost);
        httpclient.execute(consumerRunPost);
        int messagesCount = waitForAllMessages(15000, MAX_MESSAGES);
        Assert.assertEquals("Number of received messages doesn't match", expectedMessagesCount, messagesCount);
        String consumerException = new BasicResponseHandler().handleResponse(httpclient
                .execute(createHttpPostForConsumerForExceptions()));
        if(consumerException !=null && consumerException.toLowerCase().contains("exception")){
            Assert.assertFalse("Consumer threw exception", consumerException.toLowerCase().contains("exception"));
        }

    }

    public void serverFailureTestSequence(int expectedMessagesOnConsumer, int expectedMessagesOnServers, int expectedSummary, boolean expectProducerException, boolean expectConsumerException, boolean verifyMessages, boolean jvmFailure ) throws Exception{
        startAllServers();
        container(3).deploy(createProducerServletDeployment(this.getProducerClass()));
        container(4).deploy(createConsumerServletDeployment(this.getConsumerClass()));


        HttpClient httpclient = HttpClients.createDefault();
        HttpPost producerRunPost = createHttpPostForProducerToRun();
        HttpPost consumerRunPost = createHttpPostForConsumerToRun();
        RuleInstaller.installRule(this.getClass(), container(1));
        httpclient.execute(producerRunPost);
        httpclient.execute(consumerRunPost);
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        int messagesCount = waitForAllMessages(15000, MAX_MESSAGES);
        String responseSent = (new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForProducerForMessages())));
        String responseReceived = (new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForConsumerForMessages())));
        messageVerifier.addSendMessages(deserializeResponse(responseSent));
        messageVerifier.addReceivedMessages(deserializeResponse(responseReceived));
        if(verifyMessages) {
            messageVerifier.verifyMessages();
        }
        String producerException = "";
        String consumerException = "";
        producerException = new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForProducerForExceptions()));
        consumerException = new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForConsumerForExceptions()));

        container(3).stop();
        container(4).stop();

        if(expectedMessagesOnConsumer >= 0){
            Assert.assertEquals("Expected number of received messages doesn't match", expectedMessagesOnConsumer,messagesCount);
        }
        if(expectedMessagesOnServers >=0){
            Assert.assertEquals("Expected number of messages on JMS server(s) doesn't match", expectedMessagesOnServers, getMessagesCountFromServers());
        }else{
            Assert.assertEquals("Expected number of messages on JMS server(s) doesn't match", messageVerifier.getSentMessages().size(), messagesCount+getMessagesCountFromServers());
        }
        if(expectedSummary >=0){
            Assert.assertEquals("Expected number of messages on consumer and servers doesn't match", expectedSummary,getMessagesCountFromServers()+messagesCount);
        }


        if (expectConsumerException) {
            Assert.assertTrue("Consumer didn't throw any exception", consumerException.toLowerCase().contains("exception"));
        } else {
            Assert.assertFalse("Consumer threw exception", consumerException.toLowerCase().contains("exception"));
        }
        if (expectProducerException) {
            Assert.assertTrue("Producer didn't throw any exception", producerException.toLowerCase().contains("exception"));
        } else {
            Assert.assertFalse("Producer threw exception", producerException.toLowerCase().contains("exception"));
        }

    }

    public void consumerFailureTestSequence(int expectedMessagesOnServers, boolean jvmFailure) throws Exception{
        startAllServers();
        container(3).deploy(createProducerServletDeployment(this.getProducerClass()));
        container(4).deploy(createConsumerServletDeployment(this.getConsumerClass()));


        HttpClient httpclient = HttpClients.createDefault();
        HttpPost producerRunPost = createHttpPostForProducerToRun();
        HttpPost consumerRunPost = createHttpPostForConsumerToRun();
        RuleInstaller.installRule(this.getClass(), container(4));
        httpclient.execute(producerRunPost);
        httpclient.execute(consumerRunPost);
        waitForAllMessages(15000, MAX_MESSAGES);
        String producerException = new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForProducerForExceptions()));
        String responseSent = (new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForProducerForMessages())));
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();
        container(3).stop();
        messageVerifier.addSendMessages(deserializeResponse(responseSent));
        if(expectedMessagesOnServers >=0){
            Assert.assertEquals("Expected number of messages on JMS server(s) doesn't match", expectedMessagesOnServers, getMessagesCountFromServers());
        }else{
            Assert.assertEquals("Expected number of messages on JMS server(s) doesn't match", messageVerifier.getSentMessages().size(), getMessagesCountFromServers());
        }
        if(producerException !=null && producerException.toLowerCase().contains("exception")){
            Assert.assertFalse("Consumer threw exception", producerException.toLowerCase().contains("exception"));
        }
    }

    long getMessagesCountFromServers() throws Exception{
        long messagesOnServer=0;
        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1))) {
            container(1).start();
        }

        JMSOperations adminOperationsLiveServer = container(1).getJmsOperations();
        messagesOnServer = messagesOnServer + adminOperationsLiveServer.getCountOfMessagesOnQueue(queueName);
        container(1).stop();
        if (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(2))) {
            container(2).start();
        }
        JMSOperations adminOperationsBackup = container(2).getJmsOperations();
        try {
            messagesOnServer = messagesOnServer + adminOperationsBackup.getCountOfMessagesOnQueue(queueName);
        } catch (Exception e) {
            // queue doesn't have to be defined on server (if we are running test for standalone server)
        }
        container(2).stop();
        return messagesOnServer;

    }

    public void prepareServers(boolean cluster, boolean ha, String journalType) {
        if (cluster == false && ha == false) { // for standalone node topology
            prepareStandaloneServer(container(1), journalType);
        } else if (cluster == false && ha == true) { // for dedicated HA topology 1 live 1 backup node (shared store)
            prepareLiveServer(container(1), false, JOURNAL_DIRECTORY_A, journalType);
            prepareBackupServer(container(2), JOURNAL_DIRECTORY_A, journalType);
        } else if (cluster = true && ha == false) { // for pure cluster topology (no HA)
            prepareLiveServer(container(1), false, JOURNAL_DIRECTORY_A, journalType);
            prepareLiveServer(container(2), false, JOURNAL_DIRECTORY_B, journalType);
        } else if (cluster == true && ha == true) { // for colocated HA topology
            prepareLiveServer(container(1), true, JOURNAL_DIRECTORY_A, journalType);
            prepareLiveServer(container(2), true, JOURNAL_DIRECTORY_B, journalType);
        }
        prepareClientsServer(container(3));
        prepareClientsServer(container(4));
    }

    public void prepareStandaloneServer(Container container, String journalType) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setJournalDirectory(JOURNAL_DIRECTORY_A);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.createQueue(queueName, queueJndiName);
        jmsAdminOperations.removeHAPolicy("default");

        jmsAdminOperations.close();
        container.stop();

    }

    public void prepareLiveServer(Container container, boolean requestBackup, String journalDirectoryPath, String journalType) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setJournalDirectory(journalDirectoryPath);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "udp", "udp", 1000, "http-connector");
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, "udp", "udp");
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                "http-connector");
        jmsAdminOperations.createQueue(queueName, queueJndiName);
        if (requestBackup) {
            jmsAdminOperations.addHAPolicyColocatedSharedStore();
        } else {
            jmsAdminOperations.addHAPolicySharedStoreMaster(1000, true);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    public void prepareBackupServer(Container container, String journalDirectoryPath, String journalType) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setJournalDirectory(journalDirectoryPath);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "udp", "udp", 1000, "http-connector");
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, "udp", "udp");
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                "http-connector");
        jmsAdminOperations.createQueue(queueName, queueJndiName);
        jmsAdminOperations.addHAPolicySharedStoreSlave(true, 1000, true, true, false, null, null, null, null);

        jmsAdminOperations.close();
        container.stop();

    }

    public void prepareClientsServer(Container container) {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.createQueue(queueName, queueJndiName);
        jmsAdminOperations.removeHAPolicy("default");
        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Web servlet with jms producer
     *
     * @return mdb
     */
    public static WebArchive createProducerServletDeployment(Class producerClass) {
        final WebArchive servletWar = ShrinkWrap.create(WebArchive.class, PRODUCER_FILE_NAME_PREFIX + ".war");

        servletWar.addClass(producerClass);
        servletWar.addAsLibraries(createToolsLibrary());
        servletWar.addAsLibraries(Maven.resolver().resolve("com.fasterxml.jackson.core:jackson-databind:2.6.1")
                .withTransitivity().asFile());
        servletWar.addAsManifestResource(new StringAsset(getManifest()), "MANIFEST.MF");
        log.info(servletWar.toString(true));
        File target = new File("/tmp/" + PRODUCER_FILE_NAME_PREFIX + ".war");
        if (target.exists()) {
            target.delete();
        }
        servletWar.as(ZipExporter.class).exportTo(target, true);
        return servletWar;
    }

    public static WebArchive createConsumerServletDeployment(Class consumerClass) {
        final WebArchive servletWar = ShrinkWrap.create(WebArchive.class, CONSUMER_FILE_NAME_PREFIX + ".war");
        servletWar.addClass(consumerClass);
        servletWar.addAsLibraries(createToolsLibrary());
        servletWar.addAsLibraries(Maven.resolver().resolve("com.fasterxml.jackson.core:jackson-databind:2.6.1")
                .withTransitivity().asFile());
        servletWar.addAsManifestResource(new StringAsset(getManifest()), "MANIFEST.MF");
        log.info(servletWar.toString(true));
        File target = new File("/tmp/" + CONSUMER_FILE_NAME_PREFIX + ".war");
        if (target.exists()) {
            target.delete();
        }
        servletWar.as(ZipExporter.class).exportTo(target, true);
        return servletWar;

    }

    public static JavaArchive createToolsLibrary() {
        JavaArchive lib = ShrinkWrap.create(JavaArchive.class, "tools.jar");
        lib.addClass(TextMessageBuilder.class);
        lib.addClass(MessageBuilder.class);
        lib.addClass(org.jboss.qa.hornetq.apps.Clients.class);
        lib.addClass(org.jboss.qa.hornetq.apps.clients.Client.class);
        lib.addClass(org.jboss.qa.hornetq.constants.Constants.class);
        lib.addClass(org.jboss.qa.hornetq.tools.CheckServerAvailableUtils.class);
        lib.addClass(org.jboss.qa.hornetq.tools.JMSOperations.class);
        lib.addClass(org.jboss.qa.hornetq.JMSTools.class);
        lib.addClass(org.jboss.qa.hornetq.Container.class);

        return lib;

    }

    private static String getManifest() {
        return "Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis";
    }

    protected HttpPost createHttpPostForProducerToRun() throws UnsupportedEncodingException {
        return createHttpPostForProducerToRun("http://127.0.0.1:10080/" + PRODUCER_FILE_NAME_PREFIX
                + "/ServletProducer", "127.0.0.1", 8080, queueJndiName, MAX_MESSAGES, COMMIT_AFTER);
    }

    protected HttpPost createHttpPostForProducerToRun(String servletUrl, String messagingHost, int messagingPort,
            String queueJNDIName, int maxMessages, int commitAfter) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("host", messagingHost));
        postParameters.add(new BasicNameValuePair("port", String.valueOf(messagingPort)));
        postParameters.add(new BasicNameValuePair("queueJNDIName", queueJNDIName));
        postParameters.add(new BasicNameValuePair("maxMessages", String.valueOf(maxMessages)));
        postParameters.add(new BasicNameValuePair("commitAfter", String.valueOf(commitAfter)));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    protected HttpPost createHttpPostForProducerForExceptions() throws UnsupportedEncodingException {
        return createHttpPostForConsumerForExceptions("http://127.0.0.1:10080/" + PRODUCER_FILE_NAME_PREFIX
                + "/ServletProducer");
    }

    protected HttpPost createHttpPostForConsumerToRun() throws UnsupportedEncodingException {
        return createHttpPostForConsumerToRun("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
                + "/ServletConsumer", "127.0.0.1", 8080, queueJndiName, RECEIVE_TIMEOUT, COMMIT_AFTER);
    }

    protected HttpPost createHttpPostForConsumerToRun(String servletUrl, String messagingHost, int messagingPort,
            String queueJNDIName, long receiveTimeout, int commitAfter) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("host", messagingHost));
        postParameters.add(new BasicNameValuePair("port", String.valueOf(messagingPort)));
        postParameters.add(new BasicNameValuePair("queueJNDIName", queueJNDIName));
        postParameters.add(new BasicNameValuePair("receiveTimeout", String.valueOf(receiveTimeout)));
        postParameters.add(new BasicNameValuePair("commitAfter", String.valueOf(commitAfter)));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    protected HttpPost createHttpPostForConsumerForReceivedCount() throws UnsupportedEncodingException {
        return createHttpPostForReceivedCount("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
                + "/ServletConsumer");
    }

    protected HttpPost createHttpPostForReceivedCount(String servletUrl) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("method", "getCount"));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    protected HttpPost createHttpPostForConsumerForExceptions() throws UnsupportedEncodingException {
        return createHttpPostForConsumerForExceptions("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
                + "/ServletConsumer");
    }

    protected HttpPost createHttpPostForConsumerForExceptions(String servletUrl) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair(ServletConstants.PARAM_METHOD, ServletConstants.METHOD_GET_EXCEPTIONS));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    protected HttpPost createHttpPostForConsumerForMessages() throws UnsupportedEncodingException {
        return createHttpPostForMessages("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX + "/ServletConsumer");
    }

    protected HttpPost createHttpPostForProducerForMessages() throws UnsupportedEncodingException {
        return createHttpPostForMessages("http://127.0.0.1:10080/" + PRODUCER_FILE_NAME_PREFIX + "/ServletProducer");
    }

    protected HttpPost createHttpPostForMessages(String servletUrl) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair(ServletConstants.PARAM_METHOD, ServletConstants.METHOD_GET_MESSAGES));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    /**
     *
     * @param timeout in miliseconds
     * @return number of received messages
     */
    protected int waitForAllMessages(long timeout, int maxMessages) throws Exception {
        long start = System.currentTimeMillis();
        long duration = 0;
        int messagesCount = 0;
        HttpClient client = HttpClients.createDefault();
        try {
            HttpPost post = createHttpPostForConsumerForReceivedCount();
            while (messagesCount < maxMessages && duration < timeout) {
                HttpResponse response = client.execute(post);
                String responseString = new BasicResponseHandler().handleResponse(response).replace("\n", "");
                log.info("RESPONSE_COUNTER: " + responseString);
                messagesCount = Integer.parseInt(responseString);
                log.info("receiving: " + messagesCount);
                duration = System.currentTimeMillis() - start;
                Thread.sleep(1000);
            }
        } catch (UnsupportedEncodingException e) {
            throw e;
        } catch (Exception e) {
            // if we kill consumer
        }
        return messagesCount;
    }



    public void startAllServers(){
        container(1).start();
        container(2).start();
        container(3).start();
        container(4).start();
    }

    public void stopAllServers(){
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
    }

    private List<Map<String, String>> deserializeFile(File f) throws Exception {
        FileReader reader = new FileReader(f);
        BufferedReader br = new BufferedReader(reader);
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            builder.append(line);
        }
        return deserializeToListOfMap(deserializeToList(builder.toString()));

    }

    private List<Map<String, String>> deserializeResponse(String json) throws IOException {
        return deserializeToListOfMap(deserializeToList(json));
    }

    private List<Object> deserializeToList(String json) throws IOException {
        List<Object> list;
        ObjectMapper om = new ObjectMapper();
        TypeReference<List<Object>> typeRef = new TypeReference<List<Object>>() {
        };
        list = om.readValue(json, typeRef);
        return list;
    }

    private List<Map<String, String>> deserializeToListOfMap(List<Object> list) throws IOException {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        for (Object item : list) {
            HashMap<String, String> o = (HashMap<String, String>) item;
            messages.add(o);
        }
        return messages;
    }

    public boolean serversReady(String testCase) {
        return (testCase.equals(runningTestCase) ? true : false);
    }

    public void setRunningTestCase(String testCase) {
        runningTestCase = testCase;
    }

    public abstract Class getProducerClass();

    public abstract Class getConsumerClass();

    public abstract boolean isClusteredTest();
    public abstract boolean isHATest();


}
