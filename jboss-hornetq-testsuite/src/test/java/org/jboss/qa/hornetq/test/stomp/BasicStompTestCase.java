package org.jboss.qa.hornetq.test.stomp;

import org.apache.log4j.Logger;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.fusesource.stomp.client.Constants.*;
import static org.junit.Assert.*;

/**
 * Tests covers basic operations with STOMP protocol send/receives messages
 * <p/>
 * Links
 * http://code.google.com/p/stompj/wiki/GettingStarted
 * http://jmesnil.net/weblog/2010/01/14/using-stomp-with-hornetq/
 *
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class BasicStompTestCase extends HornetQTestCase {

    // Logger
    private static final Logger log = Logger.getLogger(BasicStompTestCase.class);

    // Default port for STOMP
    protected static final int STOMP_PORT = 61613;

    /**
     * Stops all servers
     */
    @Before
    @After
    public void stopAllServers() {
        stopServer(CONTAINER1);
    }

    /**
     * Simple basic tests which sends and receives messages to/from server
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleBlockingSendAndReceiveTest() {
        final String QUEUE_NAME = "queueStomp";
        final String TEST = "test";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;

        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations);
        try {
            Stomp stomp = new Stomp(CONTAINER1_IP, STOMP_PORT);
            BlockingConnection connection = stomp.connectBlocking();

            // Subscribe
            StompFrame frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            StompFrame response = connection.request(frame);
            assertNotNull(response);

            // Send message
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            frame.content(new Buffer(TEST.getBytes()));
            connection.send(frame);

            // Try to get the received message.
            StompFrame received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(TEST, received.contentAsString());
        } catch (Exception e) {
            log.error(e);
            fail(e.getMessage());
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        stopServer(CONTAINER1);
    }

    /**
     * Simple basic tests which sends and receives large message to/from server
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void sendAndReceiveLargeMessage() {
        final String QUEUE_NAME = "queueStomp";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MSG_APPENDS = 10 * 1024;

        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations);
        try {
            Stomp stomp = new Stomp(CONTAINER1_IP, STOMP_PORT);
            BlockingConnection connection = stomp.connectBlocking();

            // Subscribe
            StompFrame frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            StompFrame response = connection.request(frame);
            assertNotNull(response);

            // Send message
            final String CHAIN = "0123456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MSG_APPENDS; i++) {
                sb.append(CHAIN);
            }
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            frame.content(new Buffer(sb.toString().getBytes("ISO-8859-1")));
            frame.addHeader(MESSAGE_ID, StompFrame.encodeHeader("test"));
            connection.send(frame);

            // Try to get the received message.
            StompFrame received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(CHAIN.length() * MSG_APPENDS, received.content().getLength());
            String content = received.contentAsString();
            log.error(content);
        } catch (Exception e) {
            log.error(e);
            fail(e.getMessage());
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        stopServer(CONTAINER1);
    }

    /**
     * Starts and prepares server for Stomp tests
     *
     * @param queueName     name of used queue
     * @param jmsOperations instance of JMS Admin Operations
     */
    protected void startAndPrepareServerForStompTest(String queueName, String queueJNDI, JMSOperations jmsOperations) {
        controller.start(CONTAINER1);

        // Create queue
        jmsOperations.addSocketBinding("messaging-stomp", STOMP_PORT);
        jmsOperations.createQueue(queueName, queueJNDI);

        // Create HQ acceptor for Stomp
        Map<String, String> params = new HashMap<String, String>();
        params.put("protocol", "stomp");
        jmsOperations.createRemoteAcceptor("stomp-acceptor", "messaging-stomp", params);

        // Disable security on HQ
        jmsOperations.setSecurityEnabled(false);

        controller.stop(CONTAINER1);
        controller.start(CONTAINER1);
    }

}
