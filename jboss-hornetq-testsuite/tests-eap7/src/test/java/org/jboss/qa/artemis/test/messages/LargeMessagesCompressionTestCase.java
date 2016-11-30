package org.jboss.qa.artemis.test.messages;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.interceptors.LargeMessagePacketInterceptor;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for checking proper large message compression.
 *
 * Large messages should be compressed down to normal size message, if their
 * compressed size goes below min-large-message-size parameter.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 * @tpChapter Functional testing
 * @tpSubChapter MESSAGE COMPRESSION - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-tests
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP 6.3
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class LargeMessagesCompressionTestCase extends HornetQTestCase {

    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz1234567890".toCharArray();

    private static final int MIN_LARGE_MESSAGE_SIZE = 100 * 1024;

    private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final Random random = new SecureRandom();

    @Before
    public void startServer() {
        container(1).stop();
        container(1).start();
    }

    @After
    public void stopServer() {
        container(1).stop();
    }

    /**
     *
     * @throws Exception
     * @tpTestDetails Normal message should be received as normal message
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send normal message to queue</li>
     *     <li>start consumer which reads message</li>
     * </ul>
     * @tpPassCrit check that message was received as normal message
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JOURNAL_FILE_SIZE, value = "10485760")
    })
    public void testUncompressedNormalMessage() throws Exception {

        container(1).restart();
       
        // EAP6: 100KiB text body results in about 102550-ish bytes message
        // EAP5: 50KiB text body results in >102400 bytes message
        ClientMessage receivedMsg = this.sendAndReceivedMessage(this.generateMessageText(51000));

        // outgoing interceptor only works in HornetQ 2.3.x
        //assertFalse("Outgoing message should've been sent as normal message",
        //        receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.SENT_AS_LARGE_MSG_PROP));
        assertFalse("Incoming message should've been received as normal message",
                receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.RECEIVED_AS_LARGE_MSG_PROP));
    }

    /**
     *
     * @throws Exception
     * @tpTestDetails Large messages should be compressed down, but not enough to be under min-large-message-size threshold.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send large message messages to queue</li>
     *     <li>start consumer which reads compressed message</li>
     * </ul>
     * @tpPassCrit check that message was received as large message
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JOURNAL_FILE_SIZE, value = "10485760")
    })
    public void testCompressedLargeMessage() throws Exception {

        container(1).restart();

        ClientMessage receivedMsg = this.sendAndReceivedMessage(this.generateMessageText(5000000));

        // outgoing interceptor only works in HornetQ 2.3.x
        //assertTrue("Outgoing message should've been sent as large message",
        //        receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.SENT_AS_LARGE_MSG_PROP));
        assertTrue("Incoming message should've been received as large message",
                receivedMsg.getBooleanProperty(LargeMessagePacketInterceptor.RECEIVED_AS_LARGE_MSG_PROP));
    }

    /**
     *
     * @throws Exception
     * @tpTestDetails Large messages should be compressed down, compressed sizer should be under min-large-message-size threshold.
     * @tpProcedure <ul>
     *     <li>start one server with deployed queue</li>
     *     <li>send large message messages to queue</li>
     *     <li>start consumer which reads compressed message</li>
     * </ul>
     * @tpPassCrit check that message was received as normal message
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.JOURNAL_FILE_SIZE, value = "10485760")
    })
    public void testLargeMessageCompressedToNormalMessage() throws Exception {

        container(1).restart();
        
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
            params.put(TransportConstants.HOST_PROP_NAME, container(1).getHostname());
            params.put(TransportConstants.PORT_PROP_NAME, container(1).getHornetqPort());
            params.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);
         
            TransportConfiguration config = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);

            locator = ActiveMQClient.createServerLocatorWithoutHA(config).setCompressLargeMessage(true)
                    .addIncomingInterceptor(getLargeMessagePacketInterceptor());           


            ClientSessionFactory sf = locator.createSessionFactory();
            session = sf.createSession();

            ClientMessage msg = session.createMessage(Message.TEXT_TYPE, true);
            msg.getBodyBuffer().writeString(messageContents);

            ClientProducer producer = session.createProducer("jms.queue." + PrepareConstants.QUEUE_NAME);
            producer.send(msg);

            ClientConsumer consumer = session.createConsumer("jms.queue." + PrepareConstants.QUEUE_NAME);
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

    private LargeMessagePacketInterceptor getLargeMessagePacketInterceptor() {
        ServiceLoader<LargeMessagePacketInterceptor> serviceLoader = ServiceLoader.load(LargeMessagePacketInterceptor.class);
        Iterator<LargeMessagePacketInterceptor> iterator = serviceLoader.iterator();

        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for JmxUtils.");
        }

        LargeMessagePacketInterceptor largeMessagePacketInterceptor = iterator.next();

        return largeMessagePacketInterceptor;
    }

}
