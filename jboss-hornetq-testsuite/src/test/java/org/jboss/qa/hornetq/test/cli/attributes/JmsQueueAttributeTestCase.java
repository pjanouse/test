package org.jboss.qa.hornetq.test.cli.attributes;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 * Test attributes on Queue:
 * ATTRIBUTE           VALUE                         TYPE
 consumer-count      0                             INT
 dead-letter-address jms.queue.DLQ                 STRING
 delivering-count    0                             INT
 durable             true                          BOOLEAN
 entries             ["java:/jms/queue/testQueue"] LIST
 expiry-address      jms.queue.ExpiryQueue         STRING
 message-count       0                             LONG
 messages-added      0                             LONG
 paused              false                         BOOLEAN
 queue-address       jms.queue.testQueue           STRING
 scheduled-count     0                             LONG
 selector            undefined                     STRING
 temporary           false                         BOOLEAN
 *
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class JmsQueueAttributeTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

//    private static final Logger log = Logger.getLogger(JmsQueueAttributeTestCase.class);

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    String queueCoreName = "testQueue";

    String queueJndiName = "jms/queue/" + queueCoreName;

    private final String address = "/subsystem=messaging/hornetq-server=default/jms-queue=" + queueCoreName;

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1));

    @Before
    public void startServer() throws InterruptedException {
        controller.start(CONTAINER1);

        // deploy queue

        CliClient cliClient = new CliClient(cliConf);
        cliClient.executeForSuccess(address + ":add(durable=true,entries=[\"java:/" + queueJndiName + "\", \"java:jboss/exported/" + queueJndiName + "\"])");

        // send some messages to it
        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, 4447, queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producer.start();
        producer.join();

    }

    @After
    public void stopServer() {

        stopServer(CONTAINER1);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeTest() throws Exception {
        writeReadAttributeTest("queueCliAttributes.txt");
    }

    public void writeReadAttributeTest(String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }

}
