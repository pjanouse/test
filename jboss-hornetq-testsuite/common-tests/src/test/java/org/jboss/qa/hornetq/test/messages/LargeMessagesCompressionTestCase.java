package org.jboss.qa.hornetq.test.messages;


import org.hornetq.api.core.Message;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.*;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.interceptors.LargeMessagePacketInterceptor;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;


/**
 * Tests for checking proper large message compression.
 *
 * Large messages should be compressed down to normal size message, if their compressed size goes
 * below min-large-message-size parameter.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class LargeMessagesCompressionTestCase extends HornetQTestCase {

    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz1234567890".toCharArray();

    private static final int MIN_LARGE_MESSAGE_SIZE = 100 * 1024;

    private static final String QUEUE_NAME = "TestQueue";

    private static final String QUEUE_CORE_NAME = "jms.queue.TestQueue";

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final Random random = new SecureRandom();


    @Before
    public void startServer() {
        this.controller.start(CONTAINER1);
    }


    @After
    public void stopServer() {
        this.controller.stop(CONTAINER1);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testUncompressedNormalMessage() throws Exception {
        this.prepare();

        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        // EAP6: 100KiB text body results in about 102550-ish bytes message
        // EAP5: 50KiB text body results in >102400 bytes message
        ClientMessage receivedMsg = this.sendAndReceivedMessage(this.generateMessageText(51000));

        // outgoing interceptor only works in HornetQ 2.3.x
        //assertFalse("Outgoing message should've been sent as normal message",
        //        receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.SENT_AS_LARGE_MSG_PROP));
        assertFalse("Incoming message should've been received as normal message",
                receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.RECEIVED_AS_LARGE_MSG_PROP));
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testCompressedLargeMessage() throws Exception {
        this.prepare();

        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        ClientMessage receivedMsg = this.sendAndReceivedMessage(this.generateMessageText(5000000));

        // outgoing interceptor only works in HornetQ 2.3.x
        //assertTrue("Outgoing message should've been sent as large message",
        //        receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.SENT_AS_LARGE_MSG_PROP));
        assertTrue("Incoming message should've been received as large message",
                receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.RECEIVED_AS_LARGE_MSG_PROP));
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLargeMessageCompressedToNormalMessage() throws Exception {
        this.prepare();

        this.controller.stop(CONTAINER1);
        this.controller.start(CONTAINER1);

        ClientMessage receivedMsg = this.sendAndReceivedMessage(this.generateMessageText(MIN_LARGE_MESSAGE_SIZE + 200));

        // outgoing interceptor only works in HornetQ 2.3.x
        //assertFalse("Outgoing message should've been sent as normal message",
        //        receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.SENT_AS_LARGE_MSG_PROP));
        assertFalse("Incoming message should've been received as normal message",
                receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.RECEIVED_AS_LARGE_MSG_PROP));
    }


    private ClientMessage sendAndReceivedMessage(final String messageContents) throws Exception {
        ServerLocator locator = null;
        ClientSession session = null;

        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put(TransportConstants.HOST_PROP_NAME, getHostname(CONTAINER1));
            params.put(TransportConstants.PORT_PROP_NAME, getHornetqPort(CONTAINER1));
            TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);

            locator = HornetQClient.createServerLocatorWithoutHA(config);
            locator.setCompressLargeMessage(true);

            // need to use deprecated method to work with EAP 5 / HQ 2.2.x
            locator.addInterceptor(getLargeMessagePacketInterceptor());

            ClientSessionFactory sf = locator.createSessionFactory();
            session = sf.createSession();

            ClientMessage msg = session.createMessage(Message.TEXT_TYPE, true);
            msg.getBodyBuffer().writeString(messageContents);

            ClientProducer producer = session.createProducer(QUEUE_CORE_NAME);
            producer.send(msg);

            ClientConsumer consumer = session.createConsumer(QUEUE_CORE_NAME);
            session.start();
            ClientMessage received = consumer.receive(RECEIVE_TIMEOUT);

            // check whether the received message has same body as sent message
            assertNotNull("Message wasn't received by the consumer", received);
            assertEquals("Received message should be text message", Message.TEXT_TYPE, received.getType());

            String receivedText = received.getBodyBuffer().readString();
            assertNotNull("Received message doesn't contain any text", receivedText);
            assertEquals("Message body content should be same", messageContents, receivedText);
            received.getBodyBuffer().resetReaderIndex();

            producer.close();
            consumer.close();

            return received;
        } finally {
            if (session != null) {
                session.stop();
                session.close();
            }

            if (locator != null) {
                locator.close();
            }
        }
    }


    private String generateMessageText(final int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = CHARS[this.random.nextInt(CHARS.length)];
            builder.append(c);
        }
        return builder.toString();
    }


    private void prepare() {
        JMSOperations ops = this.getJMSOperations(CONTAINER1);
        ops.createQueue(QUEUE_NAME, QUEUE_NAME);
        ops.close();
    }

}
