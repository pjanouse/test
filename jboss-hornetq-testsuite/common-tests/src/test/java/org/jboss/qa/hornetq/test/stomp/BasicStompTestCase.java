package org.jboss.qa.hornetq.test.stomp;

import org.apache.log4j.Logger;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.HighLoadStompProducerWithSemaphores;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fusesource.hawtbuf.AsciiBuffer.ascii;
import static org.fusesource.stomp.client.Constants.*;
import static org.junit.Assert.*;

/**
 * Tests covers basic operations with STOMP protocol send/receives messages
 * <p/>
 * Links
 * http://code.google.com/p/stompj/wiki/GettingStarted
 * http://jmesnil.net/weblog/2010/01/14/using-stomp-with-hornetq/
 * http://stomp.github.io/stomp-specification-1.1.html
 *
 * @author pslavice@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Ignore
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
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Simple basic tests which sends and receives messages to/from server
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveTest() {
        final String QUEUE_NAME = "queueStomp";
        final String TEST = "test";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MSG_APPENDS = 10 * 1024;

        BlockingConnection connection = null;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations, true);
        try {
            Stomp stomp = new Stomp(getHostname(CONTAINER1_NAME), STOMP_PORT);
            connection = stomp.connectBlocking();

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

            // Try to send large message
            // Send message
            final String CHAIN = "0123456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MSG_APPENDS; i++) {
                sb.append(CHAIN);
            }
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            frame.content(new Buffer(sb.toString().getBytes("ISO-8859-1")));
            connection.send(frame);

            // Try to get the received message.
            received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(CHAIN.length() * MSG_APPENDS, received.content().getLength());
            String content = received.contentAsString();
            assertNotNull(content);
            assertEquals(CHAIN.length() * MSG_APPENDS, content.getBytes().length);

        } catch (Exception e) {
            log.error(e);
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Simple basic tests which sends message via STOMP and receives it via JMS API
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveViaJMSTest() {
        final String QUEUE_NAME = "queueStomp";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MESSAGES = 10;
        final String HEADER_COUNTER = "internal_counter";

        BlockingConnection connection = null;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations, true);
        try {
            // Send messages via STOMP protocol
            Stomp stomp = new Stomp(getHostname(CONTAINER1_NAME), STOMP_PORT);
            connection = stomp.connectBlocking();
            for (int i = 0; i < MESSAGES; i++) {
                // Send message
                StompFrame frame = new StompFrame(SEND);
                frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
                frame.addHeader(ascii(HEADER_COUNTER), Buffer.ascii(Integer.toString(i)));
                frame.addHeader(ACK_MODE, AUTO);
                frame.addHeader(PERSISTENT, TRUE);
                frame.content(new Buffer("Hello".getBytes()));
                connection.send(frame);
            }

            // Receive messages via JMS API
            ReceiverAutoAck receiverAutoAck = new ReceiverAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), QUEUE_JNDI);
            receiverAutoAck.run();
            assertEquals(MESSAGES, receiverAutoAck.getCount());
        } catch (Exception e) {
            log.error(e);
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Simple basic tests which sends message via JMS and receives it via STOMP
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendViaJMSAndReceive() {
        final String QUEUE_NAME = "queueStomp";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MESSAGES = 10;

        BlockingConnection connection = null;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations, true);
        try {
            // Receives messages via STOMP protocol
            Stomp stomp = new Stomp(getHostname(CONTAINER1_NAME), STOMP_PORT);
            connection = stomp.connectBlocking();

            // Subscribe
            StompFrame frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            StompFrame response = connection.request(frame);
            assertNotNull(response);

            // Send messages via JMS API
            ProducerAutoAck producerAutoAck = new ProducerAutoAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), QUEUE_JNDI, MESSAGES);
            producerAutoAck.setMessageBuilder(new ByteMessageBuilder(512));
            producerAutoAck.run();

            // Receive messages
            int counter = 0;
            try {
                StompFrame received = connection.receive();
                while (received != null) {
                    counter++;
                    assertTrue(received.action().equals(MESSAGE));
                    received = connection.receive();
                }
            } catch (EOFException e) {
                // Ignore it
            }
            assertEquals(MESSAGES, counter);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage() + e.getClass().getName());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        jmsAdminOperations.close();
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Test sends lot of messages to server via different org.jboss.qa.hornetq.apps.clients
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendStressTest() {
        final String QUEUE_NAME = "queueStomp";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MESSAGE_SIZE = 512 * 1024;
        final int CLIENTS = 10;
        final int MESSAGES_PER_CLIENT = 1000;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations, true);
        assertEquals(0, jmsAdminOperations.getCountOfMessagesOnQueue(QUEUE_NAME));
        try {
            Stomp stomp = new Stomp(getHostname(CONTAINER1_NAME), STOMP_PORT);

            HighLoadStompProducerWithSemaphores[] producers = new HighLoadStompProducerWithSemaphores[CLIENTS];
            for (int i = 0; i < CLIENTS; i++) {
                producers[i] = new HighLoadStompProducerWithSemaphores("StompClient" + i, QUEUE_ADDRESS, stomp, null,
                        Integer.MAX_VALUE, MESSAGES_PER_CLIENT, MESSAGE_SIZE);
                producers[i].start();
            }
            for (int i = 0; i < CLIENTS; i++) {
                producers[i].join();
            }

            ReceiverClientAck receiverClientAck = new ReceiverClientAck(getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), QUEUE_JNDI);
            receiverClientAck.start();
            receiverClientAck.join();
            assertEquals(MESSAGES_PER_CLIENT * CLIENTS, receiverClientAck.getCount());

            jmsAdminOperations.removeQueue(QUEUE_NAME);
            stopServer(CONTAINER1_NAME);
        } catch (Exception e) {
            log.error(e);
            fail(e.getMessage());
        }
    }

    /**
     * Simple basic tests which sends and receives large message to/from server
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testSizeLimits() {
        /**
         http://stomp.github.io/stomp-specification-1.1.html
         the number of frame headers allowed in a single frame
         the maximum length of header lines
         the maximum size of a frame body
         **/

        final String QUEUE_NAME = "queueStomp";
        final String TEST = "test";
        final String QUEUE_JNDI = "/queue/" + QUEUE_NAME;
        final String QUEUE_ADDRESS = "jms.queue." + QUEUE_NAME;
        final int MSG_APPENDS = 1 * 1024 * 1024;

        BlockingConnection connection = null;

        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        startAndPrepareServerForStompTest(QUEUE_NAME, QUEUE_JNDI, jmsAdminOperations, true);
        try {
            Stomp stomp = new Stomp(getHostname(CONTAINER1_NAME), STOMP_PORT);
            connection = stomp.connectBlocking();

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

            // Try to send large message
            // Send message
            final String CHAIN = "0123456789";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MSG_APPENDS; i++) {
                sb.append(CHAIN);
            }
            log.info("Body of messages wil have: " + sb.toString().length() + " characters.");
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(QUEUE_ADDRESS));
            frame.content(new Buffer(sb.toString().getBytes("ISO-8859-1")));
            frame.addContentLengthHeader();
            for (StompFrame.HeaderEntry e : frame.headerList()) {
                log.info("Header: " + e.getKey().toString() + " Value: " + e.getValue().toString());
            }

            connection.send(frame);

            // Try to get the received message.
            received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(CHAIN.length() * MSG_APPENDS, received.content().getLength());
            String content = received.contentAsString();
            assertNotNull(content);
            assertEquals(CHAIN.length() * MSG_APPENDS, content.getBytes().length);

        } catch (Exception e) {
            log.error("Exception was thrown: ", e);
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        jmsAdminOperations.removeQueue(QUEUE_NAME);
        jmsAdminOperations.close();
        stopServer(CONTAINER1_NAME);
    }

    /**
     * Starts and prepares server for Stomp tests
     *
     * @param queueName       name of used queue
     * @param jmsOperations   instance of JMS Admin Operations
     * @param disableSecurity disable security on HornetQ?
     */

    protected void startAndPrepareServerForStompTest(String queueName, String queueJNDI, JMSOperations jmsOperations, boolean disableSecurity) {
        controller.start(CONTAINER1_NAME);

        // Create queue
        jmsOperations.addSocketBinding("messaging-stomp", STOMP_PORT);
        jmsOperations.createQueue(queueName, queueJNDI);

        // Create HQ acceptor for Stomp
        Map<String, String> params = new HashMap<String, String>();
        params.put("protocol", "stomp");
        jmsOperations.createRemoteAcceptor("stomp-acceptor", "messaging-stomp", params);

        // Disable security on HQ
        if (disableSecurity) {
            jmsOperations.setSecurityEnabled(false);
        }

        controller.stop(CONTAINER1_NAME);
        controller.start(CONTAINER1_NAME);
    }

}
