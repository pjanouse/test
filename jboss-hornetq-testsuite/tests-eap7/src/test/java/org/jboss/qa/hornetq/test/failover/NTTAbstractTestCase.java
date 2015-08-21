package org.jboss.qa.hornetq.test.failover;

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
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.servlets.ServletConsumerTransAck;
import org.jboss.qa.hornetq.apps.servlets.ServletProducerTransAck;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by okalman on 8/10/15.
 */
public class NTTAbstractTestCase extends HornetQTestCase {

    private static Logger log = Logger.getLogger(NTTAbstractTestCase.class);

    public static final String PRODUCER_FILE_NAME_PREFIX = "producerServlet";
    public static final String CONSUMER_FILE_NAME_PREFIX = "consumerServlet";
    public static final int MAX_MESSAGES=2000;
    public static final int COMMIT_AFTER=10;
    public static final long RECEIVE_TIMEOUT=10000;

    private final WebArchive PRODUCER_TRANSACK = createProducerServletDeployment();
    private final WebArchive CONSUMER_TRANSACK = createConsumerServletDeployment();

    String queueName = "targetQueue0";
    String queueJndiName = "jms/queue/" + queueName;


    public void testSequence(int expectedMessagesCount, boolean expectedProducerFailure, boolean expectedConsumerFailure, Container containerForBytemanRule) throws Exception {
        startAllServers();
        container(3).start();
        container(3).deploy(PRODUCER_TRANSACK);

        container(4).start();
        container(4).deploy(CONSUMER_TRANSACK);

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost producerRunPost = createHttpPostForProducerToRun();
        HttpPost consumerRunPost = createHttpPostForConsumerToRun();
        httpclient.execute(producerRunPost);
        httpclient.execute(consumerRunPost);
        waitForAllMessages(10000, (int) ((double) MAX_MESSAGES / 10.0));
        RuleInstaller.installRule(this.getClass(), containerForBytemanRule);
        int messagesCount = waitForAllMessages(120000,MAX_MESSAGES);
        Assert.assertEquals("Number of received messages doesn't match", expectedMessagesCount, messagesCount);

        String producerException = new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForProducerForExceptions()));
        String consumerException = new BasicResponseHandler().handleResponse(httpclient.execute(createHttpPostForConsumerForExceptions()));
        if(expectedConsumerFailure){
            Assert.assertTrue("Consumer didn't threw any exception", consumerException.contains("Exception") || consumerException.contains("exception"));
        }else{
            Assert.assertFalse("Consumer threw exception", consumerException.contains("Exception") || consumerException.contains("exception"));
        }
        if(expectedProducerFailure){
            Assert.assertTrue("Producer didn't threw any exception", producerException.contains("Exception") || producerException.contains("exception"));
        }else{
            Assert.assertFalse("Producer threw exception", producerException.contains("Exception") || producerException.contains("exception"));
        }


    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void archiverTestCase() throws Exception {
        prepareServers(false, false, "NIO");
        container(1).start();

        container(3).start();
        container(3).deploy(PRODUCER_TRANSACK);

        container(4).start();
        container(4).deploy(CONSUMER_TRANSACK);
        HttpClient httpclient = HttpClients.createDefault();

        HttpPost httpPostProducer = createHttpPostForProducerToRun();
        HttpResponse producerResponse = httpclient.execute(httpPostProducer);
        System.out.println("Producer response " + new BasicResponseHandler().handleResponse(producerResponse));

        HttpPost httpPostConsumer = createHttpPostForConsumerToRun();
        HttpResponse consumerStart = httpclient.execute(httpPostConsumer);
        System.out.println("Consumer response " + new BasicResponseHandler().handleResponse(consumerStart));
        int receivedMessagesCount=waitForAllMessages(300000, MAX_MESSAGES);
        log.info("RECEIVED: " + receivedMessagesCount);
        container(3).undeploy(PRODUCER_TRANSACK);
        container(3).stop();

        container(4).undeploy(CONSUMER_TRANSACK);
        container(4).stop();

        container(1).stop();
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
        String discoveryGroupName = "dg-group";
        String broadCastGroupName = "bg-group";
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
        String discoveryGroupName = "dg-group";
        String broadCastGroupName = "bg-group";
        String clusterGroupName = "my-cluster";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setJournalDirectory(journalDirectoryPath);
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
        String discoveryGroupName = "dg-group";
        String broadCastGroupName = "bg-group";
        String clusterGroupName = "my-cluster";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setJournalDirectory(journalDirectoryPath);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "udp", "udp", 1000, "http-connector");
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, "udp", "udp");
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                "http-connector");
        jmsAdminOperations.createQueue(queueName, queueJndiName);
        jmsAdminOperations.addHAPolicySharedStoreSlave(true, 1000, true, true, false, null, null, null, null);

        jmsAdminOperations.close();
        container.stop();

    }

    public void prepareClientsServer(Container container){
        String discoveryGroupName = "dg-group";
        String broadCastGroupName = "bg-group";
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
    public static WebArchive createProducerServletDeployment() {
        final WebArchive servletWar = ShrinkWrap.create(WebArchive.class, PRODUCER_FILE_NAME_PREFIX + ".war");
        JavaArchive lib = ShrinkWrap.create(JavaArchive.class, "tools.jar");
        lib.addClass(TextMessageBuilder.class);
        lib.addClass(MessageBuilder.class);
        servletWar.addClass(ServletProducerTransAck.class);
        servletWar.addAsLibraries(lib);
        servletWar.addAsManifestResource(new StringAsset(getManifest()), "MANIFEST.MF");
        log.info(servletWar.toString(true));
        File target = new File("/tmp/" + PRODUCER_FILE_NAME_PREFIX + ".war");
        if (target.exists()) {
            target.delete();
        }
        servletWar.as(ZipExporter.class).exportTo(target, true);
        return servletWar;
    }

    public static WebArchive createConsumerServletDeployment() {
        final WebArchive servletWar = ShrinkWrap.create(WebArchive.class, CONSUMER_FILE_NAME_PREFIX + ".war");
        servletWar.addClass(ServletConsumerTransAck.class);
        servletWar.addAsManifestResource(new StringAsset(getManifest()), "MANIFEST.MF");
        log.info(servletWar.toString(true));
        File target = new File("/tmp/" + CONSUMER_FILE_NAME_PREFIX + ".war");
        if (target.exists()) {
            target.delete();
        }
        servletWar.as(ZipExporter.class).exportTo(target, true);
        return servletWar;

    }

    private static String getManifest() {
        return "Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis";
    }

    protected HttpPost createHttpPostForProducerToRun() throws UnsupportedEncodingException{
        return createHttpPostForProducerToRun("http://127.0.0.1:10080/" + PRODUCER_FILE_NAME_PREFIX
                + "/ServletProducerTransAck", "127.0.0.1", 8080, queueJndiName, MAX_MESSAGES, COMMIT_AFTER);
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

    protected HttpPost createHttpPostForProducerForExceptions() throws UnsupportedEncodingException{
        return createHttpPostForConsumerForExceptions("http://127.0.0.1:10080/" + PRODUCER_FILE_NAME_PREFIX
                + "/ServletProducerTransAck");
    }

    protected HttpPost createHttpPostForConsumerToRun() throws UnsupportedEncodingException {
       return createHttpPostForConsumerToRun("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
               + "/ServletConsumerTransAck", "127.0.0.1", 8080, queueJndiName, RECEIVE_TIMEOUT, COMMIT_AFTER);
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

    protected HttpPost createHttpPostForConsumerForReceivedCount() throws UnsupportedEncodingException{
       return createHttpPostForConsumerForReceivedCount("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
                + "/ServletConsumerTransAck");
    }

    protected HttpPost createHttpPostForConsumerForReceivedCount(String servletUrl) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("method", "getCount"));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    protected HttpPost createHttpPostForConsumerForExceptions() throws UnsupportedEncodingException{
        return createHttpPostForConsumerForExceptions("http://127.0.0.1:11080/" + CONSUMER_FILE_NAME_PREFIX
                + "/ServletConsumerTransAck");
    }

    protected HttpPost createHttpPostForConsumerForExceptions(String servletUrl) throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(servletUrl);
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("method", "getExceptions"));
        httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
        return httpPost;
    }

    /**
     *
     * @param timeout in miliseconds
     * @return number of received messages
     */
    protected int waitForAllMessages(long timeout, int maxMessages) throws Exception{
        long start= System.currentTimeMillis();
        long duration=0;
        int messagesCount=0;
        HttpClient client = HttpClients.createDefault();
        HttpPost post = createHttpPostForConsumerForReceivedCount();
        while(messagesCount < maxMessages && duration<timeout){
            HttpResponse response = client.execute(post);
            String responseString =new BasicResponseHandler().handleResponse(response).replace("\n","");
            log.info("RESPONSE_COUNTER: "+ responseString);
            messagesCount = Integer.parseInt(responseString);
            log.info("receiving: " + messagesCount);
            duration=System.currentTimeMillis()-start;
            Thread.sleep(1000);
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


}
