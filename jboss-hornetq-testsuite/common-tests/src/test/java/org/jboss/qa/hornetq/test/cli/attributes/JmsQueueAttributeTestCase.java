package org.jboss.qa.hornetq.test.cli.attributes;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
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
 * 
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Try to read and write values to queue attributes. Tested attributes : consumer-count, dead-letter-address, delivering-count, durable, entries, expiry-address, message-count, messages-added,
 * paused, queue-address, scheduled-count, selector, temporary
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class JmsQueueAttributeTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);


    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 100;

    String queueCoreName = "testQueue";

    String queueJndiName = "jms/queue/" + queueCoreName;

    private String address;

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Before
    public void startServer() throws InterruptedException {
        container(1).stop();
        container(1).start();

        if (ContainerUtils.isEAP7(container(1))) {
            address = "/subsystem=messaging-activemq/server=default/jms-queue=" + queueCoreName;
        } else {
            address = "/subsystem=messaging/hornetq-server=default/jms-queue=" + queueCoreName;
        }

        // deploy queue

        CliClient cliClient = new CliClient(cliConf);
        cliClient.executeForSuccess(address + ":add(durable=true,entries=[\"java:/" + queueJndiName + "\", \"java:jboss/exported/" + queueJndiName + "\"])");

        // send some messages to it
        ProducerClientAck producer = new ProducerClientAck(container(1).getContainerType().toString(),container(1).getHostname(), container(1).getJNDIPort(), queueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        producer.start();
        producer.join();

    }

    @After
    public void stopServer() {
        container(1).stop();
    }
    
    /** 
     * @tpTestDetails Server is started. Try to read or write values to queue attributes.
     * @tpProcedure <ul>
     * <li>start one server with deployed queue</li>
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
        writeReadAttributeTest("/queueCliAttributes.txt");
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
