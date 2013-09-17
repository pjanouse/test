package org.jboss.qa.hornetq.test.security;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.hornetq.api.core.Message;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
public class SslAuthenticationTestCase extends SecurityTestBase {

    private static final String TEST_USER = "user";

    private static final String TEST_USER_PASSWORD = "user.456";

    private static final String QUEUE_NAME = "test.queue";

    private static final String QUEUE_JNDI_ADDRESS = "jms/test/queue";

    private static final String TEST_MESSAGE_BODY = "test text";

    private static final long CONSUMER_TIMEOUT = 10000;


    @Before
    public void stopServerBeforeReconfiguration() {
        this.controller.stop(CONTAINER1);
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverCore() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        ServerLocator locator = null;
        try {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(TransportConstants.HOST_PROP_NAME, CONTAINER1_IP);
            props.put(TransportConstants.PORT_PROP_NAME, 5445);
            props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
            props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
            props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
            TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                    props);

            locator = HornetQClient.createServerLocatorWithoutHA(config);
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


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testOneWaySslOverJms() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createOneWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, CONTAINER1_IP);
        props.put(TransportConstants.PORT_PROP_NAME, 5445);
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
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


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverCore() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createTwoWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, CONTAINER1_IP);
        props.put(TransportConstants.PORT_PROP_NAME, 5445);
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, KEY_STORE_PATH);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
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


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testTwoWaySslOverJms() throws Exception {
        this.controller.start(CONTAINER1);
        JMSOperations ops = this.prepareServer();
        this.createTwoWaySslAcceptor(ops);
        ops.createQueue(QUEUE_NAME, QUEUE_JNDI_ADDRESS);
        this.prepareServerSideKeystores();
        ops.close();
        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(TransportConstants.HOST_PROP_NAME, CONTAINER1_IP);
        props.put(TransportConstants.PORT_PROP_NAME, 5445);
        props.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        props.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUST_STORE_PATH);
        props.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUST_STORE_PASSWORD);
        props.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, KEY_STORE_PATH);
        props.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEY_STORE_PASSWORD);
        TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getCanonicalName(),
                props);

        HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, config);
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


    private JMSOperations prepareServer() throws IOException {
        JMSOperations ops = this.getJMSOperations();
        ops.setPersistenceEnabled(true);

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
