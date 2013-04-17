package org.jboss.qa.hornetq.test.transportprotocols;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.administration.AdministrationTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * @author nziakova
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class TransportProtocolsTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(TransportProtocolsTestCase.class);
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;
    private static final long RECEIVE_TIMEOUT = 10000;
    private static final int RECEIVER_MAX_RETRIES = 10;
    private static final String IN_QUEUE_NAME_FOR_MDB = "InQueue";
    private static final String IN_QUEUE_JNDI_NAME_FOR_MDB = "jms/queue/" + IN_QUEUE_NAME_FOR_MDB;

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOTCPTransportTest() throws Exception {
        prepareServerForTCPTransport(CONTAINER1, "NIO");
        TransportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOTCPTransportTest() throws Exception {
        prepareServerForTCPTransport(CONTAINER1, "ASYNCIO");
        TransportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOHTTPTransportTest() throws Exception {
        prepareServerForHTTPTransport(CONTAINER1, "NIO");
        TransportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOHTTPTransportTest() throws Exception {
        prepareServerForHTTPTransport(CONTAINER1, "ASYNCIO");
        TransportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOSSLTransportTest() throws Exception {
        prepareServerForSSLTransport(CONTAINER1, "NIO");
        TransportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOSSLTransportTest() throws Exception {
        prepareServerForSSLTransport(CONTAINER1, "ASYNCIO");
        TransportProtocolTest();
    }

    /**
     * Test: starts 1 server, creates producer and consumer, producer sends messages to queue and consumer receives them
     *
     * @throws Exception
     */
    public void TransportProtocolTest() throws Exception {

        controller.start(CONTAINER1);

        log.info("Start producer and consumer.");
        ProducerAutoAck producer = new ProducerAutoAck(CONTAINER1_IP, getJNDIPort(), IN_QUEUE_JNDI_NAME_FOR_MDB, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverAutoAck receiver = new ReceiverAutoAck(CONTAINER1_IP, getJNDIPort(), IN_QUEUE_JNDI_NAME_FOR_MDB, RECEIVE_TIMEOUT, RECEIVER_MAX_RETRIES);

        producer.start();
        producer.join();
        receiver.start();
        receiver.join();

        Assert.assertEquals("Numbers of sent and received messages differ.", producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not send any messages. Sent: " + producer.getListOfSentMessages().size(), producer.getListOfSentMessages().isEmpty());
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().isEmpty());
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(), NUMBER_OF_MESSAGES_PER_PRODUCER);

        stopServer(CONTAINER1);

    }


    /**
     * Configuration of server for TCP transport
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param journalType   Type of journal
     */
    private void prepareServerForTCPTransport(String containerName, String journalType) {
        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        controller.stop(containerName);
    }

    /**
     * Configuration of server for HTTP transport
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param journalType   Type of journal
     */
    private void prepareServerForHTTPTransport(String containerName, String journalType) {
        controller.start(containerName);
        String socketBindingName = "messaging-http";
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("http-enabled", "true");

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.createSocketBinding(socketBindingName, 7080);
        jmsAdminOperations.removeRemoteConnector("netty");
        jmsAdminOperations.createRemoteConnector("netty", socketBindingName, params);
        jmsAdminOperations.removeRemoteAcceptor("netty");
        jmsAdminOperations.createRemoteAcceptor("netty", socketBindingName, params);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        controller.stop(containerName);
    }

    /**
     * Configuration of server for SSL transport
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param journalType   Type of journal
     * @throws IOException
     */
    private void prepareServerForSSLTransport(String containerName, String journalType) throws IOException {

        controller.start(containerName);

        AdministrationTestCase fileOperation = new AdministrationTestCase();
        File keyStore = new File("src/test/resources/org/jboss/qa/hornetq/test/transportprotocols/hornetq.example.keystore");
        File trustStore = new File("src/test/resources/org/jboss/qa/hornetq/test/transportprotocols/hornetq.example.truststore");
        File keyStoreNew = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator + "deployments" + File.separator + "hornetq.example.keystore");
        File trustStoreNew = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator + "deployments" + File.separator + "hornetq.example.truststore");
        if (!keyStoreNew.exists()) {
            boolean result = keyStoreNew.createNewFile();
            log.info("New key store file was created - " + Boolean.toString(result));
        }
        if (!trustStoreNew.exists()) {
            boolean result = trustStoreNew.createNewFile();
            log.info("New Trust store file was created - " + Boolean.toString(result));
        }
        fileOperation.copyFile(keyStore, keyStoreNew);
        fileOperation.copyFile(trustStore, trustStoreNew);

        String socketBindingName = "messaging";
        HashMap<String, String> connectorParams = new HashMap<String, String>();
        connectorParams.put("ssl-enabled", "true");
        connectorParams.put("key-store-path", keyStoreNew.getAbsolutePath());
        connectorParams.put("key-store-password", "hornetqexample");
        HashMap<String, String> acceptorParams = new HashMap<String, String>();
        acceptorParams.put("ssl-enabled", "true");
        acceptorParams.put("key-store-path", keyStoreNew.getAbsolutePath());
        acceptorParams.put("key-store-password", "hornetqexample");
        acceptorParams.put("trust-store-path", trustStoreNew.getAbsolutePath());
        acceptorParams.put("trust-store-password", "hornetqexample");

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.removeRemoteConnector("netty");
        jmsAdminOperations.createRemoteConnector("netty", socketBindingName, connectorParams);
        jmsAdminOperations.removeRemoteAcceptor("netty");
        jmsAdminOperations.createRemoteAcceptor("netty", socketBindingName, acceptorParams);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        controller.stop(containerName);
    }
}
