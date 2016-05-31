package org.jboss.qa.artemis.test.protocol;

import org.apache.log4j.Logger;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.fusesource.hawtbuf.AsciiBuffer.ascii;
import static org.fusesource.stomp.client.Constants.*;

/**
 * Created by okalman on 8/24/15.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class StompCompatibilityTestCase extends ProtocolCompatibilityTestCase  {
    private static final Logger log = Logger.getLogger(StompCompatibilityTestCase.class);


    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveTestStandaloneBroker() {
        prepareServerForStandaloneBroker();

        BlockingConnection connection = null;

        try {
            container(1).start();
            container(1).deploy(mdb);
            Stomp stomp = new Stomp(BROKER_REMOTE_ADDRESS, REMOTE_PORT_BROKER);
            connection = stomp.connectBlocking();

            // Subscribe
            StompFrame frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(OUT_QUEUE_ADDRESS));
            StompFrame response = connection.request(frame);
            assertNotNull(response);

            // Send message
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(IN_QUEUE_ADDRESS));
            frame.addHeader(REPLY_TO,StompFrame.encodeHeader(OUT_QUEUE_ADDRESS));
            frame.addContentLengthHeader();
            frame.content(new Buffer(TEST.getBytes()));
            connection.send(frame);

            // Try to get the received message.
            StompFrame received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(TEST, received.contentAsString());
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
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveTestEAP(){
        prepareServer();
        BlockingConnection connection = null;
        container(1).start();
        try {
            Stomp stomp = new Stomp(container(1).getHostname(), REMOTE_PORT_EAP);
            connection = stomp.connectBlocking();

            // Subscribe
            StompFrame frame = new StompFrame(SUBSCRIBE);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(IN_QUEUE_ADDRESS));
            StompFrame response = connection.request(frame);

            // Send message
            frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader(IN_QUEUE_ADDRESS));
            frame.content(new Buffer(TEST.getBytes()));
            connection.send(frame);
            // Try to get the received message.
            StompFrame received = connection.receive();
            assertTrue(received.action().equals(MESSAGE));
            assertEquals(TEST, received.contentAsString());

            fail("STOMP is not supported in EAP7");
        } catch (Exception e) {

        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

    }


}
