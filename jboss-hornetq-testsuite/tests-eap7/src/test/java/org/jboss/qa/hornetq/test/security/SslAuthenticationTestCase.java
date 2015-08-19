package org.jboss.qa.hornetq.test.security;


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
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SocketBinding;
import org.jboss.qa.hornetq.tools.XMLManipulation;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
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
import javax.net.ssl.SSLEngine;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


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
 * @tpJobLink tbd
 * @tpTcmsLink tbd
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
    public void stopServerBeforeReconfiguration() {
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
        container(1).start();
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
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
            action = "System.out.println(\"mnovak - byteman rule triggered - uuuuhhaaaa\"); org.jboss.qa.hornetq.test.security.SslAuthenticationTestCase.setEnabledProtocols($!)"

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
    public void testTwoWaySslOverJmsWithPkcs11() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK 1.6", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));

        container(1).start();

        Context context = container(1).getContext();

        ConnectionFactory cf = (ConnectionFactory) context.lookup(container(1).getConnectionFactoryName());
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
     * @tpInfo This test can run only with Oracle JDK and OpenJDK 1.6
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testTwoWaySslOverJmsWithPkcs11CfCreatedByClient() throws Exception {

        Assume.assumeTrue("This test can run only with Oracle JDK and OpenJDK 1.6", System.getProperty("java.vm.name").contains("Java HotSpot"));

        prepareSeverWithPkcs11(container(1));

        container(1).start();

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        props.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        props.put(TRUSTSTORE_PROVIDER_PROP_NAME, "PKCS11");
        props.put(KEYSTORE_PROVIDER_PROP_NAME, "PKCS11");
        props.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
        props.put(TransportConstants.PORT_PROP_NAME, container(1).getHornetqPort());
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

    private void prepareSeverWithPkcs11(Container container) throws Exception {

        installSecurityExtension(container);

        container.start();
        JMSOperations ops = this.prepareServer();

        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);

        // enable logging
        ops.addLoggerCategory("org.jboss.security", "TRACE");

        // enable SunPKCS11 security provider EAP 6 server
        ops.addExtension("org.jboss.as.security.providers");
        ops.addSubsystem("security-providers");
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("nssLibraryDirectory", System.getProperty("sun.arch.data.model").equals("64") ?
                "/usr/lib64" : "/usr/lib");
        attributes.put("nssSecmodDirectory", new File(TEST_KEYSTORES_DIRECTORY, "fipsdb").getAbsolutePath());
        attributes.put("nssModule", "fips");
        ops.addSecurityProvider("sunpkcs11", "nss-fips", attributes);

        // enable it for this arquillian test
//        name=PKCS11
//        nssLibraryDirectory=/usr/lib64
//        nssSecmodDirectory=/home/mnovak/tmp/pkcs11/fipsdb
//        nssModule=fips
        FileUtils.copyFile(new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_ORIGINAL), new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_MODIFIED));
        File pkcs11ConfigFile = new File(TEST_KEYSTORES_DIRECTORY, PKCS11_CONFIG_FILE_MODIFIED);
        replaceStringInFile(pkcs11ConfigFile, "nssLibraryDirectory=", System.getProperty("sun.arch.data.model").equals("64") ?
                "nssLibraryDirectory=/usr/lib64" : "nssLibraryDirectory=/usr/lib");
        replaceStringInFile(pkcs11ConfigFile, "nssSecmodDirectory=",
                "nssSecmodDirectory=" + new File(TEST_KEYSTORES_DIRECTORY, "fipsdb").getAbsolutePath());
        PKCS11Utils.registerProvider(pkcs11ConfigFile.getAbsolutePath());

        String acceptorName = "http-acceptor";
        String connectorName = "http-connector";
        String messagingGroupSocketBindingName = "messaging";
        String httpListener = "default";

        ops.removeHttpConnector(connectorName);
        ops.removeHttpAcceptor(acceptorName);

        // create connector and acceptor with ssl certificates
        Map<String, String> acceptorProps = new HashMap<String, String>();
        acceptorProps.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        acceptorProps.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        acceptorProps.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        acceptorProps.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        acceptorProps.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        acceptorProps.put(TRUSTSTORE_PROVIDER_PROP_NAME, "PKCS11");
        acceptorProps.put(KEYSTORE_PROVIDER_PROP_NAME, "PKCS11");
        acceptorProps.put("need-client-auth", "true");
        ops.createHttpAcceptor(acceptorName, httpListener, acceptorProps);

        Map<String, String> connectorProps = new HashMap<String, String>();
        connectorProps.put(TransportConstants.SSL_ENABLED_PROP_NAME, "true");
        connectorProps.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "cert8.db").getAbsolutePath());
        connectorProps.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        connectorProps.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, new File(TEST_KEYSTORES_DIRECTORY, "fipsdb" + File.separator + "key3.db").getAbsolutePath());
        connectorProps.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TEST_USER_PASSWORD);
        connectorProps.put(TRUSTSTORE_PROVIDER_PROP_NAME, "PKCS11");
        connectorProps.put(KEYSTORE_PROVIDER_PROP_NAME, "PKCS11");
        ops.createHttpConnector(connectorName, messagingGroupSocketBindingName, connectorProps);

        ops.setConnectorOnConnectionFactory("RemoteConnectionFactory", connectorName);
        ops.setSecurityEnabled(true);

        if (container.getContainerType().equals(HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_LEGACY_CONTAINER)) {
            ops.addExtension("org.jboss.legacy.jnp");
            ops.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());
            ops.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());
            activateLegacyJnpModule(container);
        }

        ops.close();

        container.stop();

        if (container.getContainerType().equals(HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_LEGACY_CONTAINER)) {
            activateLegacyJnpModule(container);
        }

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

}
