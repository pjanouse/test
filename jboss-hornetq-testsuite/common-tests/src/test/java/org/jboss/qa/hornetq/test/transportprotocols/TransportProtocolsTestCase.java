package org.jboss.qa.hornetq.test.transportprotocols;

import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.SocketBinding;
import org.jboss.qa.hornetq.tools.XMLManipulation;
import org.junit.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.test.administration.AdministrationTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * @author nziakova
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class TransportProtocolsTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(TransportProtocolsTestCase.class);
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 200;
    private static final long RECEIVE_TIMEOUT = 10000;
    private static final int RECEIVER_MAX_RETRIES = 10;
    private static final String IN_QUEUE_NAME_FOR_MDB = "InQueue";
    private static final String IN_QUEUE_JNDI_NAME_FOR_MDB = "jms/queue/" + IN_QUEUE_NAME_FOR_MDB;

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        container(1).stop();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOTCPTransportTest() throws Exception {
        prepareServerForTCPTransport(container(1), "NIO");
        transportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOTCPTransportTest() throws Exception {
        prepareServerForTCPTransport(container(1), "ASYNCIO");
        transportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOHTTPTransportTest() throws Exception {
        prepareServerForHTTPTransport(container(1), "NIO");
        transportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOHTTPTransportTest() throws Exception {
        prepareServerForHTTPTransport(container(1), "ASYNCIO");
        transportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void NIOSSLTransportTest() throws Exception {
        prepareServerForSSLTransport(container(1), "NIO");
        transportProtocolTest();
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void AIOSSLTransportTest() throws Exception {
        prepareServerForSSLTransport(container(1), "ASYNCIO");
        transportProtocolTest();
    }

    private void activateLegacyJnpModule(final ContainerInfo container) throws Exception {
        StringBuilder pathToStandaloneXml = new StringBuilder();
        pathToStandaloneXml = pathToStandaloneXml.append(container.getJbossHome())
                .append(File.separator).append("standalone")
                .append(File.separator).append("configuration")
                .append(File.separator).append("standalone-full-ha.xml");
        Document doc = XMLManipulation.getDOMModel(pathToStandaloneXml.toString());

        Element e = doc.createElement("subsystem");
        e.setAttribute("xmlns", "urn:jboss:domain:legacy-jnp:1.0");

        Element entry = doc.createElement("jnp-connector");
        entry.setAttribute("socket-binding", "jnp");
        entry.setAttribute("rmi-socket-binding", "rmi-jnp");
        e.appendChild(entry);

        /*Element entry2 = doc.createElement("remoting");
         entry2.setAttribute("socket-binding", "legacy-remoting");
         e.appendChild(entry2);*/
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate("//profile", doc, XPathConstants.NODE);
        node.appendChild(e);

        XMLManipulation.saveDOMModel(doc, pathToStandaloneXml.toString());
    }

    /**
     * Test: starts 1 server, creates producer and consumer, producer sends messages to queue and consumer receives them
     *
     * @throws Exception
     */
    public void transportProtocolTest() throws Exception {

        if (getContainerType(CONTAINER1_NAME).equals(CONTAINER_TYPE.EAP6_LEGACY_CONTAINER))  {
            // configure legacy extension

            container(1).start();

            JMSOperations jmsAdminOperations = container(1).getJmsOperations();

            jmsAdminOperations.addExtension("org.jboss.legacy.jnp");

            jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());

            jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());

            jmsAdminOperations.close();

            container(1).stop();

            activateLegacyJnpModule(getContainerInfo(CONTAINER1_NAME));
        }

        container(1).start();

        log.info("Start producer and consumer.");
        ProducerTransAck producer = new ProducerTransAck(getContainerType(CONTAINER1_NAME).toString() ,container(1).getHostname(), container(1).getJNDIPort(), IN_QUEUE_JNDI_NAME_FOR_MDB, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverTransAck receiver = new ReceiverTransAck(getContainerType(CONTAINER1_NAME).toString(), container(1).getHostname(), container(1).getJNDIPort(), IN_QUEUE_JNDI_NAME_FOR_MDB, RECEIVE_TIMEOUT, 50, RECEIVER_MAX_RETRIES);

        producer.start();
        producer.join();
        receiver.start();
        receiver.join();

        Assert.assertEquals("Numbers of sent and received messages differ.", producer.getListOfSentMessages().size(), receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not send any messages. Sent: " + producer.getListOfSentMessages().size(), producer.getListOfSentMessages().isEmpty());
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().isEmpty());
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size(), NUMBER_OF_MESSAGES_PER_PRODUCER);

        container(1).stop();

    }


    /**
     * Configuration of server for TCP transport
     *
     * @param container     Test container - defined in arquillian.xml
     * @param journalType   Type of journal
     */
    private void prepareServerForTCPTransport(Container container, String journalType) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        container.stop();
    }

    /**
     * Configuration of server for HTTP transport
     *
     * @param container     Test container - defined in arquillian.xml
     * @param journalType   Type of journal
     */
    private void prepareServerForHTTPTransport(Container container, String journalType) {
        container.start();
        String socketBindingName = "messaging-http";
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("http-enabled", "true");

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.createSocketBinding(socketBindingName, 7080);
        jmsAdminOperations.removeRemoteConnector("netty");
        jmsAdminOperations.createRemoteConnector("netty", socketBindingName, params);
        jmsAdminOperations.removeRemoteAcceptor("netty");
        jmsAdminOperations.createRemoteAcceptor("netty", socketBindingName, params);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        container.stop();
    }

    /**
     * Configuration of server for SSL transport
     *
     * @param container     Test container - defined in arquillian.xml
     * @param journalType   Type of journal
     * @throws IOException
     */
    private void prepareServerForSSLTransport(Container container, String journalType) throws IOException {
        container.start();

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
        connectorParams.put("trust-store-path", trustStoreNew.getAbsolutePath());
        connectorParams.put("trust-store-password", "hornetqexample");
        HashMap<String, String> acceptorParams = new HashMap<String, String>();
        acceptorParams.put("ssl-enabled", "true");
        acceptorParams.put("key-store-path", keyStoreNew.getAbsolutePath());
        acceptorParams.put("key-store-password", "hornetqexample");
        acceptorParams.put("trust-store-path", trustStoreNew.getAbsolutePath());
        acceptorParams.put("trust-store-password", "hornetqexample");

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.removeRemoteConnector("netty");
        jmsAdminOperations.createRemoteConnector("netty", socketBindingName, connectorParams);
        jmsAdminOperations.removeRemoteAcceptor("netty");
        jmsAdminOperations.createRemoteAcceptor("netty", socketBindingName, acceptorParams);
        jmsAdminOperations.createQueue("default", IN_QUEUE_NAME_FOR_MDB, IN_QUEUE_JNDI_NAME_FOR_MDB, true);

        container.stop();
    }
}
