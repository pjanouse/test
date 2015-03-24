package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.test.cli.CliTestBase;
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
 */

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class AddressSettingsAttributesTestCase extends CliTestBase {

    @Rule
    public Timeout timeout = new Timeout(DEFAULT_TEST_TIMEOUT);

    private static final Logger logger = Logger.getLogger(AddressSettingsAttributesTestCase.class);

    String queueCoreName = "testQueue";

    String queueJndiName = "jms/queue/" + queueCoreName;

    private final String address = "/subsystem=messaging/hornetq-server=default/address-setting=#";

    private Properties attributes;

    CliConfiguration cliConf = new CliConfiguration(container(1).getHostname(), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1_NAME), getPassword(CONTAINER1_NAME));

    @Before
    public void startServer() throws InterruptedException {
        container(1).start();
        CliClient cliClient = new CliClient(cliConf);
        cliClient.executeForSuccess(address + ":add(durable=true,entries=[\"java:/" + queueJndiName + "\", \"java:jboss/exported/" + queueJndiName + "\"])");

    }

    @After
    public void stopServer() {
        container(1).stop();
    }

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
