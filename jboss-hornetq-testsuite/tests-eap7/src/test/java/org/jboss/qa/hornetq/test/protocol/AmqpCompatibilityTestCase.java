package org.jboss.qa.hornetq.test.protocol;


import org.apache.log4j.Logger;
import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Test;

import org.apache.qpid.amqp_1_0.client.Connection;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.client.Sender;
import org.apache.qpid.amqp_1_0.client.Session;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Created by okalman on 8/27/15.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class AmqpCompatibilityTestCase extends ProtocolCompatibilityTestCase {
    private static final Logger log = Logger.getLogger(AmqpCompatibilityTestCase.class);

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveTestStandaloneBroker() throws Exception{
        prepareServerForStandaloneBroker();
        Connection connection = null;

        try
        {
            container(1).start();
            container(1).deploy(mdb);
            String payload = "I am an amqp message";
            // Step 1. Create an amqp qpid 1.0 connection
            connection= new Connection(BROKER_REMOTE_ADDRESS, REMOTE_PORT_BROKER, null, null);

            // Step 2. Create a session
            Session session = connection.createSession();

            // Step 3. Create a sender
            Sender sender = session.createSender(IN_QUEUE_ADDRESS);

            // Step 4. send a simple message
            Message messageToSend = new Message(payload);
            sender.send(messageToSend);

            // Step 5. create a moving receiver, this means the message will be removed from the queue
            Receiver rec = session.createMovingReceiver(OUT_QUEUE_ADDRESS);

            // Step 6. set some credit so we can receive
            rec.setCredit(UnsignedInteger.valueOf(1), false);

            // Step 7. receive the simple message
            Message m = rec.receive(5000);
            assertNotNull("Receiver didn't received any message", m);
            assertTrue("Payload of received message doesn't match ", m.getPayload().contains(payload));

            // Step 8. acknowledge the message
            rec.acknowledge(m);
        }
        finally
        {
            if(connection != null)
            {
                // Step 9. close the connection
                connection.close();
            }
        }
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void simpleSendAndReceiveTestEAP() {
        prepareServer();
        Connection connection = null;

        try
        {
            container(1).start();
            String payload = "I am an amqp message";
            // Step 1. Create an amqp qpid 1.0 connection
            connection= new Connection(container(1).getHostname(), REMOTE_PORT_EAP, null, null);

            // Step 2. Create a session
            Session session = connection.createSession();

            // Step 3. Create a sender
            Sender sender = session.createSender(IN_QUEUE_ADDRESS);

            // Step 4. send a simple message
            Message messageToSend = new Message(payload);
            sender.send(messageToSend);

            // Step 5. create a moving receiver, this means the message will be removed from the queue
            Receiver rec = session.createMovingReceiver(IN_QUEUE_ADDRESS);

            // Step 6. set some credit so we can receive
            rec.setCredit(UnsignedInteger.valueOf(1), false);

            // Step 7. receive the simple message
            Message m = rec.receive(5000);
            assertNotNull("Receiver didn't received any message", m);
            assertTrue("Payload of received message doesn't match ", m.getPayload().toString().contains(payload));

            // Step 8. acknowledge the message
            rec.acknowledge(m);
            fail("AMQP is not supported in EAP7");
        }catch (Exception e){
            //exception is good we don't want AMQP protocol support in EAP
            e.printStackTrace();
        }
        finally
        {
            if(connection != null)
            {
                // Step 9. close the connection
                try {
                    connection.close();
                }catch (ConnectionErrorException e){
                    log.warn(e);
                }
            }
        }
    }


}
