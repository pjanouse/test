package org.jboss.qa.artemis.test.security;


import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.security.AddressSecuritySettings;
import org.jboss.qa.hornetq.test.security.PKCS11Utils;
import org.jboss.qa.hornetq.test.security.UsersSettings;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SocketBinding;
import org.jboss.qa.hornetq.tools.XMLManipulation;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.net.ssl.SSLEngine;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


/**
 * PKCS11 tests info - how to generate certificates:
 * # vytvorit file s novym heslem
 * echo "pass123+" > ${WORKSPACE}/newpass.txt
 * <p/>
 * modutil -force -create -dbdir ${WORKSPACE}/fipsdb
 * modutil -force -fips true -dbdir ${WORKSPACE}/fipsdb
 * modutil -force -changepw "NSS FIPS 140-2 Certificate DB" -newpwfile ${WORKSPACE}/newpass.txt -dbdir ${WORKSPACE}/fipsdb
 * <p/>
 * # vytvorit noise.txt
 * echo "dsadasdasdasdadasdasdasdasdsadfwerwerjfdksdjfksdlfhjsdk" > ${WORKSPACE}/noise.txt
 * <p/>
 * certutil -S -k rsa -n jbossweb  -t "u,u,u" -x -s "CN=localhost, OU=MYOU, O=MYORG, L=MYCITY, ST=MYSTATE, C=MY" -d ${WORKSPACE}/fipsdb -f ${WORKSPACE}/newpass.txt -z ${WORKSPACE}/noise.txt
 * certutil -L -d ${WORKSPACE}/fipsdb -n jbossweb -a > ${WORKSPACE}/cacert.asc
 * <p/>
 * IMPORTANT:
 * SunPKCS11 is not supported on 64-bit Windows platforms. [1]
 * <p/>
 * [1] http://docs.oracle.com/javase/7/docs/technotes/guides/security/p11guide.html#Requirements
 *
 * @tpChapter Security testing
 * @tpSubChapter SSL AUTHENTICATION
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Goal of the test cases is testing if the standalone JMS
 * client can connect to the EAP server using a connection over SSL.
 * 
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @author Miroslav Novak mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class SslAuthenticationTestCase extends SecurityTestBase {

    private static final Logger logger = Logger.getLogger(SslAuthenticationTestCase.class);

    private static final String TEST_USER = "user";

    private static final String TEST_USER_PASSWORD = "user.456";

    private static final String QUEUE_NAME = "test.queue";

    private static final String QUEUE_JNDI_ADDRESS = "jms/test/queue";

    private static final String TEST_MESSAGE_BODY = "test text";

    private static final long CONSUMER_TIMEOUT = 10000;

    private static final String PKCS11_DB_DIRECTORY = "fipsdb";

    private static final String PKCS11_CONFIG_FILE_ORIGINAL = "pkcs11.cfg";

    private static final String PKCS11_CONFIG_FILE_MODIFIED = "pkcs11-modified.cfg";

    private static final String TRUSTSTORE_PROVIDER_PROP_NAME = "trust-store-provider";

    private static final String KEYSTORE_PROVIDER_PROP_NAME = "key-store-provider";

    @Before
    public void cleanUpBeforeTest() {
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.keyStorePassword");
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        container(1).stop();
    }

    @After
    public void stopAllServers() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for one way SSL.
     * Server has keystore, client has truststore. Create client and connect to
     * the server using ActiveMQ Core API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL certificate installed. </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL certificate. Server has keystore, client has trust store</li>
     * <li>Standalone client connects to the server using ActiveMQ core API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverCore() throws Exception {
        container(1).start();
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        this.prepareServerSideKeystores();
        ops.close();
        container(1).restart();

        ServerLocator locator = null;
        try {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
            props.put(TransportConstants.PORT_PROP_NAME, 5445);
            props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
            props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
            props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
            TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                    props);

            locator = ActiveMQClient.createServerLocatorWithoutHA(config);
            ClientSessionFactory sf = locator.createSessionFactory();
            ClientSession session = sf.createSession(TEST_USER, TEST_USER_PASSWORD, false, true, true, false,
                    locator.getAckBatchSize());
            session.createTemporaryQueue(QUEUE_JNDI_ADDRESS, QUEUE_NAME);

            ClientProducer producer = session.createProducer(QUEUE_JNDI_ADDRESS);
            ClientMessage msg = session.createMessage(Message.TEXT_TYPE, false, 0, System.currentTimeMillis(), (byte) 4);
            msg.getBodyBuffer().writeString(TEST_MESSAGE_BODY);
            producer.send(msg);

            ClientConsumer consumer = session.createConsumer(QUEUE_NAME);
            session.start();
            Message received = consumer.receive(CONSUMER_TIMEOUT);

            assertNotNull("Cannot consume test message", received);
            assertEquals("Sent and received messages have different body",
                    TEST_MESSAGE_BODY, received.getBodyBuffer().readString());

            session.stop();
            producer.close();
            session.close();
            sf.close();
        } finally {
            if (locator != null) {
                locator.close();
            }
        }
    }

    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for one way SSL.
     * Server has keystore, client has truststore. Create client and connect to
     * the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL certificate installed. </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL certificate. Server has keystore, client has trust store</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverJms() throws Exception {
        prepareServerWithNettySslConnection(false,false,true);


        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
    }

    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for one way SSL.
     * Server has keystore, client has truststore. Force using of SSLv3. Create
     * client and try connect to the server using JMS API. Since SSLv3 is
     * deprecated, client should not be able to create connection.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL certificate installed. </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL certificate. Server has keystore, client has trust store.</li>
     * <li>Force use of SSLv3.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * </ul>
     * @tpPassCrit Client is not able to create connection to the server because SSLv3 is deprecated.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @BMRule(
            name = "rule to force sslv3",
            targetClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector$1",
            targetMethod = "initChannel",
            isAfter = true,
//            binding = "engine:SSLEngine = $0",
            targetLocation = "INVOKE createSSLEngine",
            action = "System.out.println(\"mnovak - byteman rule triggered - uuuuhhaaaa\"); org.jboss.qa.artemis.test.security.SslAuthenticationTestCase.setEnabledProtocols($!)"

    )
    public void testOneWaySslOverSSLv3Jms() throws Exception {
        container(1).start();
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        container(1).restart();

        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_CLIENT_PORT);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
        props.put(TransportConstants.PORT_PROP_NAME, 5445);
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
        Connection connection = null;
        try {
            connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);

            Assert.fail("It is possible to connect with SSLv3 protocol which is deprecated since EAP 6.3.3/6.4.0. It's possible that byteman rule" +
                    " was no triggered. Check whether test suite log contains - mnovak - byteman rule triggered - uuuuhhaaaa");

        } catch (JMSException ex)   {

            logger.info(ex);

        } finally {

            if (connection != null) {
                connection.close();
            }

            RuleInstaller.uninstallAllRules(container(1).getHostname(), BYTEMAN_CLIENT_PORT);

            container(1).stop();
        }
    }

    public static void setEnabledProtocols(SSLEngine engine) {
        engine.setEnabledProtocols(new String[]{"SSLv3"});
    }
    
    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for two way SSL (both
     * sides cross-verify the key of the other side against their own
     * truststore). Create client and connect to the server using ActiveMQ Core API.
     * Verify certificate and send and receive message to check that created
     * connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using ActiveMQ Core API and verifies server certificate. </li>
     * <li>After authentication, client sends a receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverCore() throws Exception {
        container(1).start();
        JMSOperations ops = this.prepareServer();
        this.createTwoWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        container(1).restart();

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
        props.put(TransportConstants.PORT_PROP_NAME, 5445);
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();

    }
    
    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for two way SSL (both
     * sides cross-verify the key of the other side against their own
     * truststore). Create client and connect to the server using JMS API.
     * Verify certificate and send and receive message to check that created
     * connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends a receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverJms() throws Exception {
        prepareServerWithNettySslConnection(true);


        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
    }

    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for two way SSL.
     * Create client which doesn't use any certificate to authenticate him self and connect to the server using JMS API.
     * Verify that client can't send any message
     * connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. </li>
     * <li>Connector is configured to send truststore, so client is able to verify servers keystore.
     * </li>
     * <li>ActiveMQ acceptor is configured to require client authentication via keystore
     * </li>
     * <li>Standalone client connects without using any keystore to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication failed, client can't send or receive any message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is not able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslNegativeClientAuthOverJms() throws Exception {
        prepareServerWithNettySslConnection(true, false, true);


        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNull("Message was sent and received", received);

        consumer.close();
        producer.close();
        session.close();
        connection.close();
    }


    /**
     * @tpTestDetails Start one server with keystore with the test SSL
     * certificate installed. Configure ActiveMQ acceptor for two way SSL.
     * Create client which doesn't use any certificate to authenticate him self and connect to the server using JMS API.
     * Verify that client can't send any message
     * connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. </li>
     * <li>Connector is configured to send truststore, so client is able to verify servers keystore.
     * </li>
     * <li>ActiveMQ acceptor is configured to require client authentication via keystore
     * </li>
     * <li>Standalone client connects without using any keystore to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication failed, client can't send or receive any message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is not able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslNegativeServerAuthOverJms() throws Exception {
        prepareServerWithNettySslConnection(true, true, false);


        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNull("Message was sent and received", received);

        consumer.close();
        producer.close();
        session.close();
        connection.close();
    }

    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK 1.6
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11Http() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK 1.6", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));
        container(1).start();
        System.setProperty("javax.net.ssl.trustStore", new File(TEST_KEYSTORES_DIRECTORY + File.separator + "cacerts").getAbsolutePath() ); // for server authentication
        System.setProperty("javax.net.ssl.trustStorePassword", TEST_USER_PASSWORD);


        System.setProperty("javax.net.ssl.keyStore",new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.keystore").getAbsolutePath()); //for client authentication
        System.setProperty("javax.net.ssl.keyStorePassword", TRUST_STORE_PASSWORD);

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK 1.6
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11HttpNegativeServerAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));
        container(1).start();


        System.setProperty("javax.net.ssl.keyStore",new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.keystore").getAbsolutePath()); //for client authentication
        System.setProperty("javax.net.ssl.keyStorePassword", TRUST_STORE_PASSWORD);

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }


    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK 1.6
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11HttpNegativeClientAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));
        container(1).start();
        System.setProperty("javax.net.ssl.trustStore", new File(TEST_KEYSTORES_DIRECTORY + File.separator + "cacerts").getAbsolutePath() ); // for server authentication
        System.setProperty("javax.net.ssl.trustStorePassword", TEST_USER_PASSWORD);


        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK 1.6
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11Netty() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11OnNetty(container(1),true, true, true);
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }


    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API, acceptor will not provide truststore of server to client.
     * Client can not verify certificate of server and can not vreate connection.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured not to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and tries to verify server certificate. </li>
     * <li>After authentication fails, JMS exception is thrown.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is not able to create connection to the server and verify the server certificate against its truststore.</li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11NettyNegativeServerAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11OnNetty(container(1),true, true, false);
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNull("Cannot consume test message", received);

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }
    /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore). Create client and
     * connect to the server using JMS API, acceptor will not provide keystore to the client.
     * Client tries to connect without his keystore. Server will refuse to create connection with him, because client didn't
     * authenticate him self.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured not to send clients client's SSL keystore certificate.</li>
     * <li>Standalone client connects to the server using JMS API and tries to verify server certificate and create
     * connection to the server</li>
     * <li>After authentication fails, JMS exception is thrown.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is not able to create connection to the server, because he doesn't have certificate to authenticate him self</li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11NettyNegativeClientAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11OnNetty(container(1),true, false, true);
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNull("Cannot consume test message", received);

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

     /**
     * @tpTestDetails Use PKCS11 (Oracle JDK only) keystores and truststores.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for two way SSL (both sides cross-verify the
     * key of the other side against their own truststore).Create connection
     * factory as client. Connect to the server using JMS API. Verify
     * certificate and send and receive message to check that created connection
     * is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS11 keystores/truststores </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Create connection factory as client</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11CfCreatedByClientHttp() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));

        container(1).start();

        System.setProperty("javax.net.ssl.trustStore", new File(TEST_KEYSTORES_DIRECTORY + File.separator + "cacerts").getAbsolutePath() ); // for server authentication
        System.setProperty("javax.net.ssl.trustStorePassword", TEST_USER_PASSWORD);


        System.setProperty("javax.net.ssl.keyStore",new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.keystore").getAbsolutePath()); //for client authentication
        System.setProperty("javax.net.ssl.keyStorePassword", TRUST_STORE_PASSWORD);
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);
//        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
//        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
//        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
//        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
//        props.put(TRUSTSTORE_PROVIDER_PROP_NAME, "PKCS11");
//        props.put(KEYSTORE_PROVIDER_PROP_NAME, "PKCS11");
        props.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
        props.put(TransportConstants.PORT_PROP_NAME, 8443);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);

        Connection connection = cf.createConnection(TEST_USER, TEST_USER_PASSWORD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();
        cf.close();

        container(1).stop();

    }

    /**
     * @tpTestDetails Use PKCS12  keystore.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for one way SSL. Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS12 keystore</li>
     * <li>Client has truststore of server</li>
     * <li>Standalone client connects to the server using JMS API and tries to verify server certificate. </li>
     * <li>Client will not make connection, after authentication fails.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Clients is able to send and receive message</li>
     * </ul>
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOneWaySslOverJmsWithPkcs12HttpServerAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs12(container(1));
        container(1).start();
        System.setProperty("javax.net.ssl.trustStore", new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.truststore").getAbsolutePath() ); // for server authentication
        System.setProperty("javax.net.ssl.trustStorePassword", "hornetqexample");



        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

    /**
     * @tpTestDetails Use PKCS12  keystore.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for one way SSL. Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS12 keystore</li>
     * <li>Client doesn't have truststore of server</li>
     * <li>Standalone client connects to the server using JMS API and tries to verify server certificate. </li>
     * <li>Client will not make connection, after authentication fails.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is not able to create connection to the server and verify the server certificate against its truststore.</li>
     * </ul>
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOneWaySslOverJmsWithPkcs12HttpNegativeServerAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs12(container(1));
        container(1).start();



        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }



    /**
     * @tpTestDetails Use PKCS12 keystore and JKS truststore.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for one way SSL. Create client and
     * connect to the server using JMS API. Verify certificate and send and
     * receive message to check that created connection is working properly.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS12 keystore and JKS truststore </li>
     * <li>ActiveMQ acceptor is configured to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and verifies server certificate. </li>
     * <li>After authentication, client sends and receives single message.</li>
     * </ul>
     * @tpPassCrit <ul>
     * <li>Client is able to create connection to the server and verify the server certificate against its truststore.</li>
     * <li>Client is able to successfully send and receive the test message over the created connection. </li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOneWaySslOverJmsWithPkcs12Netty() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs12OnNetty(container(1),true);
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

    /**
     * @tpTestDetails Use PKCS12 keystore.
     * Start one server with keystore with the test SSL certificate installed.
     * Configure ActiveMQ acceptor for one way SSL (client verifies server identity). Create client and
     * connect to the server using JMS API which doesn't provide truststore to the client.
     * Client can't verify identity of server and creating of connection fails.
     * @tpProcedure <ul>
     * <li>We have single EAP 7 server with keystore with the test SSL
     * certificate installed. Use PKCS12 keystore </li>
     * <li>ActiveMQ acceptor is configured not to send clients server’s SSL
     * certificate.</li>
     * <li>Standalone client connects to the server using JMS API and can't verify server certificate. </li>
     * <li>After authentication fails, JMS exception is thrown </li>
     * @tpPassCrit <ul>
     * <li>Client is not able to create connection to the server and verify the server certificate against its truststore.</li>
     * </ul>
     * @tpInfo This test can run only with Oracle JDK and OpenJDK
     */
    @Test(expected=javax.jms.JMSException.class)
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testOneWaySslOverJmsWithPkcs12NettyNegativeServerAuth() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs12OnNetty(container(1),false);
        container(1).start();

        Context context = container(1).getContext();
        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue testQueue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(testQueue);
        TextMessage msg = session.createTextMessage(TEST_MESSAGE_BODY);
        producer.send(msg);

        connection.start();
        MessageConsumer consumer = session.createConsumer(testQueue);
        TextMessage received = (TextMessage) consumer.receive(10000L);
        connection.stop();

        assertNotNull("Cannot consume test message", received);
        assertEquals("Sent and received messages have different body", TEST_MESSAGE_BODY, received.getText());

        consumer.close();
        producer.close();
        session.close();
        connection.close();

        container(1).stop();

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testHttpsConnectionClientAuth() throws InterruptedException {
        testHttpsConnection(true, false);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testHttpsConnectionServerAuth() throws InterruptedException {
        testHttpsConnection(false, true);
    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testHttpsConnectionBothAuth() throws InterruptedException {
        testHttpsConnection(true, true);
    }

    public void testHttpsConnection(boolean clientAuth, boolean serverAuth) throws InterruptedException {
        final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.keystore").getPath();
        final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/client.truststore").getPath();
        final String password = "123456";

        if (clientAuth) {
            System.setProperty("javax.net.ssl.keyStore", keyStorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
        }
        if (serverAuth) {
            System.setProperty("javax.net.ssl.trustStore", trustStorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", password);
        }

        prepareServerWithHttpsConnection(serverAuth, clientAuth);

        container(1).start();

        ProducerAutoAck producer = new ProducerAutoAck(container(1), QUEUE_JNDI_ADDRESS, 1);
        producer.start();

        ReceiverAutoAck receiver = new ReceiverAutoAck(container(1), QUEUE_JNDI_ADDRESS);
        receiver.start();

        producer.join();
        receiver.join();

        container(1).stop();

        Assert.assertNull("Producer got unexpected exception.", producer.getException());
        Assert.assertNull("Receiver got unexpected exception.", receiver.getException());
        Assert.assertEquals("Number of sent and received message are not equal.", producer.getCount(), receiver.getCount());
    }

    private void prepareSeverWithPkcs11(Container container) throws Exception {



        final String securityRealmName = "https";
        final String listenerName = "undertow-https";
        final String sockerBinding = "https";
        final String verifyClientPolitic = "REQUIRED";
        final String password = "user.456";
        final String httpAcceptorName = "https-acceptor";
        final String httpConnectorName = "https-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";

        container.start();
        JMSOperations ops = container.getJmsOperations();

        ops.createSecurityRealm(securityRealmName);
        ops.addServerIdentityWithKeyStoreProvider(securityRealmName, "PKCS11", password);
        ops.addAuthenticationWithKeyStoreProvider(securityRealmName, "PKCS11", password);
        ops.removeHttpAcceptor("http-acceptor");
        ops.removeHttpAcceptor("http-acceptor-throughput");
        ops.close();
        container.restart();
        ops = container.getJmsOperations();

        ops.addHttpsListener(listenerName, securityRealmName, sockerBinding, verifyClientPolitic);
        ops.createHttpAcceptor(httpAcceptorName, listenerName, null);
        Map<String, String> httpConnectorParams = new HashMap<String, String>();
        httpConnectorParams.put("ssl-enabled", "true");
        ops.createHttpConnector(httpConnectorName, sockerBinding, httpConnectorParams, httpAcceptorName);

        ops.close();
        container.restart();
        ops = container.getJmsOperations();

        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, httpConnectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        ops.close();
        container.stop();


    }


    private void prepareSeverWithPkcs12(Container container) throws Exception {



        final String securityRealmName = "https";
        final String listenerName = "undertow-https";
        final String sockerBinding = "https";
        final String verifyClientPolitic = "NOT_REQUESTED";
        final String password = "hornetqexample";
        final String httpAcceptorName = "https-acceptor";
        final String httpConnectorName = "https-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";
        final String keyStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "server-keystore.pkcs12").getAbsolutePath();

        container.start();
        JMSOperations ops = container.getJmsOperations();

        ops.createSecurityRealm(securityRealmName);
        ops.addServerIdentityWithKeyStoreProvider(securityRealmName, "PKCS12", keyStorePath, password);
        ops.removeHttpAcceptor("http-acceptor");
        ops.removeHttpAcceptor("http-acceptor-throughput");
        ops.close();
        container.restart();
        ops = container.getJmsOperations();

        ops.addHttpsListener(listenerName, securityRealmName, sockerBinding, verifyClientPolitic);
        ops.createHttpAcceptor(httpAcceptorName, listenerName, null);
        Map<String, String> httpConnectorParams = new HashMap<String, String>();
        httpConnectorParams.put("ssl-enabled", "true");
        ops.createHttpConnector(httpConnectorName, sockerBinding, httpConnectorParams, httpAcceptorName);

        ops.close();
        container.restart();
        ops = container.getJmsOperations();

        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, httpConnectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        ops.close();
        container.stop();


    }


    private void prepareSeverWithPkcs11OnNetty(Container container, boolean isTwoWay, boolean provideKeystoreViaConnector, boolean provideTruststoreViaConnector) throws Exception {




        final String password = "user.456";
        final String acceptorName = "netty-ssl-acceptor";
        final String connectorName = "netty-ssl-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";

        final String keyStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "fipsdb" + File.separator + "key3.db").getAbsolutePath();
        final String trustStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "fipsdb" + File.separator + "cert8.db").getAbsolutePath();

        final String socketBinding = "messaging";

        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        Map<String, String> propsAcceptor = new HashMap<String, String>();
        Map<String, String> propsConnector = new HashMap<String, String>();

        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        if(isTwoWay){
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath); //server will authenticate clients which use private key paired with this public key
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, password);
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME,"PKCS11");
        }
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, password);
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PROVIDER_PROP_NAME,"PKCS11");

        if(isTwoWay && provideKeystoreViaConnector){
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath); // client will use this keystore to prove him self to the server
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, password);
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PROVIDER_PROP_NAME,"PKCS11");
        }

        if(provideTruststoreViaConnector) {
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, password);
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, "PKCS11");
        }
        if(isTwoWay){
            propsAcceptor.put("need-client-auth", "true");
        }else{
            propsAcceptor.put("need-client-auth", "false");
        }

        ops.addSocketBinding(socketBinding, 5445);
        ops.createRemoteAcceptor(acceptorName, socketBinding, propsAcceptor);
        ops.createRemoteConnector(connectorName, socketBinding ,propsConnector);

        ops.close();
        container(1).stop();
        container(1).start();
        ops = container(1).getJmsOperations();
        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, connectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);


        ops.close();
        container(1).stop();


    }

    private void prepareSeverWithPkcs12OnNetty(Container container, boolean provideTruststoreViaConnector) throws Exception {




        final String password = "hornetqexample";
        final String acceptorName = "netty-ssl-acceptor";
        final String connectorName = "netty-ssl-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";

        final String keyStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "server-keystore.pkcs12").getAbsolutePath();
        final String trustStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.truststore").getAbsolutePath();

        final String socketBinding = "messaging";

        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        Map<String, String> propsAcceptor = new HashMap<String, String>();
        Map<String, String> propsConnector = new HashMap<String, String>();

        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, password);
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PROVIDER_PROP_NAME,"PKCS12");

        if(provideTruststoreViaConnector) {
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, password);
        }

        propsAcceptor.put("need-client-auth", "false");


        ops.addSocketBinding(socketBinding, 5445);
        ops.createRemoteAcceptor(acceptorName, socketBinding, propsAcceptor);
        ops.createRemoteConnector(connectorName, socketBinding ,propsConnector);

        ops.close();
        container(1).stop();
        container(1).start();
        ops = container(1).getJmsOperations();
        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, connectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);


        ops.close();
        container(1).stop();


    }



    private void activateLegacyJnpModule(final Container container) throws Exception {
        StringBuilder pathToStandaloneXml = new StringBuilder();
        pathToStandaloneXml = pathToStandaloneXml.append(container.getServerHome())
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
     * Creates org.jboss.as.security.providers module so PKCS11 provider from can be loaded
     */
    private void installSecurityExtension(Container container) throws Exception {

        final String securityJarFileName = "security-providers.jar";

        // create modules/system/layers/base/org/jboss/as/security/providers/main
        File moduleDir = new File(container.getServerHome(),
                "modules" + File.separator + "system" + File.separator + "layers" + File.separator + "base"
                        + File.separator + "org" + File.separator + "jboss" + File.separator + "as"
                        + File.separator + "security" + File.separator + "providers" + File.separator + "main");
        if (moduleDir.exists()) {
            moduleDir.delete();
        }
        moduleDir.mkdirs();

        // create module.xml
        File moduleXml = new File(moduleDir, "module.xml");
        if (moduleXml.exists()) {
            moduleDir.delete();
        }
        moduleXml.createNewFile();
        PrintWriter writer = new PrintWriter(moduleXml, "UTF-8");
        writer.println("<module xmlns=\"urn:jboss:module:1.0\" name=\"org.jboss.as.security.providers\">");
        writer.println("<resources>");
        writer.println("<resource-root path=\"" + securityJarFileName + "\"/>");
        writer.println("</resources>");
        writer.println("<dependencies>");
        writer.println("<module name=\"javax.api\"/>");
        writer.println("<module name=\"org.jboss.staxmapper\"/>");
        writer.println("<module name=\"org.jboss.as.controller\"/>");
        writer.println("<module name=\"org.jboss.as.server\"/>");
        writer.println("<module name=\"org.jboss.modules\"/>");
        writer.println("<module name=\"org.jboss.msc\"/>");
        writer.println("<module name=\"org.jboss.logging\"/>");
        writer.println("<module name=\"org.jboss.vfs\"/>");
        writer.println("<module name=\"sun.jdk\"/>");
        writer.println("</dependencies>");
        writer.println("</module>");
        writer.close();

        // copy there security-providers-1.0-SNAPSHOT.jar from target dir
        File secJar = new File("target", securityJarFileName);
        if (!secJar.exists()) {
            throw new Exception("File: " + secJar.getAbsolutePath() + " does not exists.");
        }
        File targetforSecjar = new File(moduleDir, securityJarFileName);
        if (targetforSecjar.exists()) {
            targetforSecjar.delete();
        }
        targetforSecjar.createNewFile();
        FileUtils.copyFile(secJar, targetforSecjar);

//        // patch $JBOSS_HOME/modules/sun/jdk/main/module.xml by sed -i 's#\(<path name="sun/security/provider"/>\)#\1<path name="sun/security/pkcs11"/>#'
//        File jdkModuleXml = new File(getJbossHome(containerName), "modules" + File.separator + "system"
//                + File.separator + "layers" + File.separator + "base" + File.separator + "sun"
//                + File.separator + "jdk" + File.separator + "main" + File.separator + "module.xml");
//        if (!jdkModuleXml.exists())  {
//            throw new Exception("File: " + jdkModuleXml.getAbsolutePath() + " does not exists");
//        }

//        String original = "<path name=\"sun/security/provider\"/>";
//        String replacement = "<path name=\"sun/security/pkcs11\"/>";
//        replaceStringInFile(jdkModuleXml, original, replacement);

    }

    private static void replaceStringInFile(File file, String original, String replacement) throws IOException {

        // we need to store all the lines
        List<String> lines = new ArrayList<String>();

        // first, read the file and store the changes
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line = in.readLine();
        while (line != null) {
            if (line.contains(original)) {
                line = line.replaceAll(original, replacement);
            }
            lines.add(line);
            line = in.readLine();
        }
        in.close();

        // now, write the file again with the changes
        PrintWriter out = new PrintWriter(file);
        for (String l : lines)
            out.println(l);
        out.close();

    }


    private JMSOperations prepareServer() throws IOException {
        JMSOperations ops = container(1).getJmsOperations();
        ops.setPersistenceEnabled(true);

        ops.createSocketBinding(SOCKET_BINDING, 5445);

        AddressSecuritySettings.forDefaultContainer(this)
                .forAddress("jms.queue.test.#")
                .giveUserAllPermissions(TEST_USER)
                .create();

        UsersSettings.forDefaultEapServer()
                .withUser(TEST_USER, TEST_USER_PASSWORD, TEST_USER)
                .create();

        return ops;
    }

    private void prepareServerWithHttpsConnection(boolean setKeyStore, boolean setTrustStore) {

        final String securityRealmName = "https";
        final String listenerName = "undertow-https";
        final String sockerBinding = "https";
        final String verifyClientPolitic = "NOT_REQUESTED";
        final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.keystore").getPath();
        final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/transportprotocols/server.truststore").getPath();
        final String password = "123456";
        final String httpAcceptorName = "https-acceptor";
        final String httpConnectorName = "https-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";

        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        ops.createSecurityRealm(securityRealmName);
        if (setKeyStore && setTrustStore) {
            ops.addServerIdentity(securityRealmName, keyStorePath, password);
            ops.addAuthentication(securityRealmName, trustStorePath, password);
        } else if (setKeyStore) {
            ops.addServerIdentity(securityRealmName, keyStorePath, password);
        } else if (setTrustStore) {
            ops.addAuthentication(securityRealmName, trustStorePath, password);
        } else {
            throw new IllegalArgumentException("At least one of setKeyStore or setTrustStore must be true");
        }

        ops.close();
        container(1).restart();
        ops = container(1).getJmsOperations();

        ops.addHttpsListener(listenerName, securityRealmName, sockerBinding, verifyClientPolitic);
        ops.createHttpAcceptor(httpAcceptorName, listenerName, null);
        Map<String, String> httpConnectorParams = new HashMap<String, String>();
        httpConnectorParams.put("ssl-enabled", "true");
        ops.createHttpConnector(httpConnectorName, sockerBinding, httpConnectorParams, httpAcceptorName);

        ops.close();
        container(1).restart();
        ops = container(1).getJmsOperations();

        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, httpConnectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        ops.close();
        container(1).stop();
    }

    private void prepareServerWithNettySslConnection( boolean isTwoWay) {
        prepareServerWithNettySslConnection(isTwoWay, true, true);
    }
    private void prepareServerWithNettySslConnection( boolean isTwoWay, boolean provideKeystoreViaConnector, boolean provideTrustStoreViaConnector) {

        final String keyStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.keystore").getAbsolutePath();
        final String trustStorePath = new File(TEST_KEYSTORES_DIRECTORY + File.separator + "hornetq.example.truststore").getAbsolutePath();
        final String password = TRUST_STORE_PASSWORD;
        final String acceptorName = "netty-ssl-acceptor";
        final String connectorName = "netty-ssl-connector";
        final String remoteConnectionFactoryName = "RemoteConnectionFactory";
        final String remoteConnectionFactoryJNDI = "java:jboss/exported/jms/RemoteConnectionFactory";
        final String socketBinding = "messaging";

        container(1).start();
        JMSOperations ops = container(1).getJmsOperations();

        Map<String, String> propsAcceptor = new HashMap<String, String>();
        Map<String, String> propsConnector = new HashMap<String, String>();
        
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        if(isTwoWay){
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath); //server will authenticate clients which use private key paired with this public key
            propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, password);
        }
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath);
        propsAcceptor.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, password);

        if(isTwoWay && provideKeystoreViaConnector){
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStorePath); // client will use this keystore to prove him self to the server
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, password);
        }

        if(provideTrustStoreViaConnector) {
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PATH_PROP_NAME, trustStorePath);
            propsConnector.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, password);
        }
        if(isTwoWay){
            propsAcceptor.put("needs-client-auth", "true");
        }else{
            propsAcceptor.put("needs-client-auth", "false");
        }

        ops.addSocketBinding(socketBinding, 5445);
        ops.createRemoteAcceptor(acceptorName, socketBinding, propsAcceptor);
        ops.createRemoteConnector(connectorName, socketBinding ,propsConnector);

        ops.close();
        container(1).stop();
        container(1).start();
        ops = container(1).getJmsOperations();
        ops.removeConnectionFactory(remoteConnectionFactoryName);
        ops.createConnectionFactory(remoteConnectionFactoryName, remoteConnectionFactoryJNDI, connectorName);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);


        ops.close();
        container(1).stop();
    }


}
