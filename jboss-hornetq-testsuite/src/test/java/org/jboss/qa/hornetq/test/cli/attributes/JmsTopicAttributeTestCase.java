package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
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
 ATTRIBUTE                      VALUE                                       TYPE
 delivering-count               0                                           INT
 durable-message-count          0                                           INT
 durable-subscription-count     0                                           INT
 entries                        ["java:jboss/exported/jms/topic/testTopic"] LIST
 message-count                  0                                           LONG
 messages-added                 0                                           LONG
 non-durable-message-count      0                                           INT
 non-durable-subscription-count 0                                           INT
 subscription-count             0                                           INT
 temporary                      false                                       BOOLEAN
 topic-address                  jms.topic.testTopic                         STRING
 *
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class JmsTopicAttributeTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger log = Logger.getLogger(JmsTopicAttributeTestCase.class);

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    String topicCoreName = "testTopic";

    String topicJndiName = "jms/topic/" + topicCoreName;

    private final String address = "/subsystem=messaging/hornetq-server=default/jms-topic=" + topicCoreName;

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(CONTAINER1_IP, MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1));

    @Before
    public void startServer() throws InterruptedException {
        controller.start(CONTAINER1);

        // deploy queue
        CliClient cliClient = new CliClient(cliConf);
        cliClient.executeForSuccess(address + ":add(durable=true,entries=[\"java:/" + topicJndiName + "\", \"java:jboss/exported/" + topicJndiName + "\"])");

        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(CONTAINER1_IP, 4447, topicJndiName, "testSubscriberClientId", "testSubscriber");
        subscriberClientAck.subscribe();

        // send some messages to it
        PublisherClientAck producer = new PublisherClientAck(CONTAINER1_IP, 4447, topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
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
        writeReadAttributeTest("topicCliAttributes.txt");
    }

    public void writeReadAttributeTest(String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);
            log.info("Test attribute " + attributeName + " with value: " + value);

            writeReadAttributeTest(cliClient, address, attributeName, value);

        }
    }

}
