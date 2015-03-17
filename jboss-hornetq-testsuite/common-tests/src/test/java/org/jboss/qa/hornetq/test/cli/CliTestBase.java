package org.jboss.qa.hornetq.test.cli;

import org.apache.log4j.Logger;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.cli.attributes.AddressSettingsAttributesTestCase;
import org.jboss.qa.management.cli.CliClient;

/**
 * Parent class for all Cli tests.
 */
public class CliTestBase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(AddressSettingsAttributesTestCase.class);

    public void writeReadAttributeTest(CliClient cliClient, String address, String attributeName, String value) throws Exception {

        boolean isWritable = isWritable(address, attributeName);
        log.info("Test attribute: " + attributeName + ", writable: " + isWritable);

        if (isWritable) {
            CliTestUtils.attributeOperationTest(cliClient, address, attributeName, value);
            if (cliClient.reloadRequired()) {
                try {
                    cliClient.reload();
                } catch (Exception ex)  {
                    log.error(ex);
                    // it can happen that this fails so try again
                    Thread.sleep(1000);
                    cliClient.reload();
                }
            }
        } else {
            cliClient.readAttribute(address, attributeName);
        }
    }

    /**
     * @param address   like messaging subsystem
     * @param attribute name of the attribute
     * @return true if attribute is writable, false if not
     */
    public boolean isWritable(String address, String attribute) {

        boolean isWritable = false;

        CLI cli = CLI.newInstance();
        cli.connect(getHostname(CONTAINER1), MANAGEMENT_PORT_EAP6, getUsername(CONTAINER1), getPassword(CONTAINER1).toCharArray());
        CLI.Result result = cli.cmd(address + ":read-resource-description()");

        // grep it for attribute and access-typ
        String resultAsString = result.getResponse().get("result").get("attributes").asString();
        if (resultAsString.contains(attribute)) {
            // get index where attribute starts
            resultAsString = resultAsString.substring(resultAsString.indexOf(attribute));
            // grep access type
            // find first access-type behind it - "access-type" => "read-write",
            String accessType = resultAsString.substring(resultAsString.indexOf("access-type"), resultAsString.indexOf("access-type") + "\"access-type\" => \"read-write\"".length());

            if (accessType.contains("read-write")) {
                isWritable = true;
            } else if (accessType.contains("read-only")) {
                isWritable = false;
            }

        } else {
            throw new IllegalArgumentException("Attribute " + attribute + " is not in address " + address + ". Result: " + resultAsString);
        }

        cli.disconnect();

        return isWritable;

    }
}
