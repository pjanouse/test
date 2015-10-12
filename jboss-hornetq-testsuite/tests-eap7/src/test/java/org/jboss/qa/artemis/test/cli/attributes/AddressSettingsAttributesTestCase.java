package org.jboss.qa.artemis.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;
import org.jboss.qa.management.cli.CliClient;
import org.jboss.qa.management.cli.CliConfiguration;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.Properties;

/**
 * Test attributes on remote connection factory:
 * ATTRIBUTE           VALUE                         TYPE
 * 
 * @tpChapter Integration testing
 * @tpSubChapter Administration of HornetQ component
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-tests-matrix/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-functional-ipv6-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails Test address settings attributes: Tested attributes :
 * address-full-policy, dead-letter-address, expiry-address, last-value-queue,
 * max-delivery-attempts, max-size-bytes, message-counter-history-day-limit,
 * page-max-cache-size, page-size-bytes, redelivery-delay, redistribution-delay,
 * send-to-dla-on-no-route, slow-consumer-threshold, slow-consumer-check-period,
 * slow-consumer-policy, expiry-delay, max-redelivery-delay,
 * redelivery-multiplier.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class AddressSettingsAttributesTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(HornetQTestCaseConstants.DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(AddressSettingsAttributesTestCase.class);

    String queueCoreName = "testQueue";

    String queueJndiName = "jms/queue/" + queueCoreName;

    private final String address = "/subsystem=messaging-activemq/server=default/address-setting=#";

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword());

    @Before
    public void startServer() throws InterruptedException {
        container(1).stop();
        container(1).start();
        CliClient cliClient = new CliClient(cliConf);
        cliClient.executeForSuccess(address + ":add(durable=true,entries=[\"java:/" + queueJndiName + "\", \"java:jboss/exported/" + queueJndiName + "\"])");

    }

    @After
    public void stopServer() {
        container(1).stop();
    }

    /**
     * @tpTestDetails Try to read or write different values to Address settings
     * attributes.
     * @tpProcedure <ul>
     * <li>start one server</li>
     * <li>connect to CLI</li>
     * <li>Try to read/write attributes</li>
     * <li>stop server<li/>
     * </ul>
     * @tpPassCrit reading and writing attributes is successful
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void writeReadAttributeTest() throws Exception {
        writeReadAttributeTest("/addressSettingsAttributes.txt");
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
