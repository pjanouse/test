package org.jboss.qa.hornetq.test.cli.attributes;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 *Test attributes on Topic:
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
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Try to read and write values to topic
 * attributes. Tested attributes : delivering-count, durable-message-count,
 * durable-subscription-count, entries, message-count, messages-added,
 * non-durable-message-count, non-durable-subscription-count,
 * subscription-count, temporary, topic-address
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(Functional.class)
public class JmsTopicAttributeTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger log = Logger.getLogger(JmsTopicAttributeTestCase.class);

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    String topicCoreName = "testTopic";

    String topicJndiName = "jms/topic/" + topicCoreName;

    private final String address_EAP6 = "/subsystem=messaging/hornetq-server=default/jms-topic=" + topicCoreName;
    private final String address_EAP7 = "/subsystem=messaging-activemq/server=default/jms-topic=" + topicCoreName;

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Before
    public void startServer() throws InterruptedException {
        container(1).stop();
        container(1).start();

        // deploy topic
        CliClient cliClient = new CliClient(cliConf);
        if (ContainerUtils.isEAP6(container(1))) {
            cliClient.executeForSuccess(address_EAP6 + ":add(durable=true,entries=[\"java:/" + topicJndiName + "\", \"java:jboss/exported/" + topicJndiName + "\"])");
        } else {
            cliClient.executeForSuccess(address_EAP7 + ":add(durable=true,entries=[\"java:/" + topicJndiName + "\", \"java:jboss/exported/" + topicJndiName + "\"])");
        }

        SubscriberClientAck subscriberClientAck = new SubscriberClientAck(container(1), topicJndiName, "testSubscriberClientId", "testSubscriber");
        subscriberClientAck.subscribe();

        // send some messages to it
        PublisherClientAck producer = new PublisherClientAck(container(1), topicJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER, "testPublisherClientId");
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producer.start();
        producer.join();

    }

    @After
    public void stopServer() {
        container(1).stop();
    }

    /**
     * @tpTestDetails One server is started and topic is deployed to it.
     * Publisher publishes 100 messages to topic. Once publisher finishes, try
     * to read or write values to topic attributes.
     *
     * @tpProcedure <ul>
     * <li>start one server with deployed topic</li>
     * <li>start publisher and publish messages to topic</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeTest() throws Exception {
        writeReadAttributeTest("/topicCliAttributes.txt");
    }

    public void writeReadAttributeTest(String attributeFileName) throws Exception {

        attributes = new Properties();
        attributes.load(this.getClass().getResourceAsStream(attributeFileName));

        CliClient cliClient = new CliClient(cliConf);

        String value;
        for (String attributeName : attributes.stringPropertyNames()) {

            value = attributes.getProperty(attributeName);
            log.info("Test attribute " + attributeName + " with value: " + value);
            if (ContainerUtils.isEAP6(container(1))) {
                writeReadAttributeTest(cliClient, address_EAP6, attributeName, value);
            } else {
                writeReadAttributeTest(cliClient, address_EAP7, attributeName, value);
            }

        }
    }

}
