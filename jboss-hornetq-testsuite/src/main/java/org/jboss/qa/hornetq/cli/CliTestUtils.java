package org.jboss.qa.hornetq.cli;

import org.jboss.as.cli.scriptsupport.*;
import org.jboss.dmr.*;
import org.jboss.qa.management.cli.*;
import static org.junit.Assert.*;

/**
 * This class is used by tests in different projects. Only safe changes to API is to add new methods and keep the old ones.
 *
 * @author Petr Kremensky pkremens@redhat.com
 * @author Miroslav Novak <mnovak@redhat,com>
 */
public class CliTestUtils {

    public static final String FAILURE_DESCRIPTION = "failure-description";

    /**
     * Verify that command was executed successfully. Fail the test otherwise.
     *
     * @param result Result of command executed via org.jboss.as.cli.scriptsupport.CLI class.
     */
    public static void assertSuccess(CLI.Result result) {
        if (!result.isSuccess()) {
            String failureMessage = result.getResponse().get(FAILURE_DESCRIPTION).asString();
            String command = result.getCliCommand();
            fail("Command \'" + command + "\' failed with: \'" + failureMessage + "\'.");
        }
    }

    /**
     * Verify that command was executed successfully. Fail the test otherwise.
     *
     * @param cliClient Configured CLI client for executing CLI operations
     * @param command Cli command to be executed.
     * @return Response of given command.
     */
    public static ModelNode assertSuccess(CliClient cliClient, String command) {
        ModelNode response = cliClient.executeForResponse(command);
        if (!ModelNodeUtils.isSuccessful(response)) {
            String failureMessage = response.get(FAILURE_DESCRIPTION).asString();
            fail("Command \'" + command + "\' failed with: \'" + failureMessage + "\'.");
        }
        return response;
    }

    /**
     * Verify that execution of command failed. Fail the test otherwise.
     *
     * @param result Result of command executed via org.jboss.as.cli.scriptsupport.CLI class.
     */
    public static void assertFailure(CLI.Result result) {
        if (result.isSuccess()) {
            String command = result.getCliCommand();
            fail("Command \'" + command + "\' unexpectedly  succeeded.");
        }
    }

    /**
     * Verify that execution of command failed. Fail the test otherwise.
     *
     * @param cliClient Configured CLI client for executing CLI operations
     * @param command Cli command to be executed.
     */
    public static ModelNode assertFailure(CliClient cliClient, String command) {
        ModelNode response = cliClient.executeForResponse(command);
        if (ModelNodeUtils.isSuccessful(response)) {
            fail("Command \'" + command + "\' unexpectedly  succeeded.");
        }
        return response;
    }



    /**
     * This is method for testing :undefine-attribute operation. It will undefine attribute and verify whether its value becomes undefined.
     * The value is reverted at the end of the test.
     *
     * @param cliClient      Configured CLI client for executing CLI operations
     * @param address        Address of attribute to be undefined.
     */
    public static void assertAttributeUndefineOperation(CliClient cliClient, String address, String attribute) {
        String originalValue = cliClient.readAttribute(address, attribute);
        try {
            String undefineAttributeCommand = CliUtils.buildCommand(address, ":undefine-attribute", new String[]{"name="+attribute});
            assertTrue("Failed to undefine attribute " + attribute + " at address " + address, cliClient.executeForSuccess(undefineAttributeCommand));
            verifyAttributeValueIgnoringDefaults(cliClient, address, attribute, CliConstants.UNDEFINED);
        } finally {
            cliClient.writeAttribute(address, attribute, originalValue);
        }
    }


    /**
     * This is method for testing :write-attribute and :read-attribute operations. It will (:write-attribute) change value
     * of attribute listening on given address and verify (:read-attribute) that value was really change. The value is reverted at the end
     *
     * @param cliClient      Configured CLI client for executing CLI operations
     * @param address        Address of attribute to be overwritten.
     * @param value          Value which should be written to the attribute.
     */
    public static void attributeOperationTest(CliClient cliClient, String address, String attribute, String value) {
        String originalValue = cliClient.readAttribute(address, attribute, false);
        try {
            assertTrue("Failed to write attribute via command: " + CliUtils.buildCommand(address, ":write-attribute", new String[]{attribute + "=" + value}),
                    cliClient.writeAttribute(address, attribute, value));
            verifyAttributeValue(cliClient, address, attribute, value);
        } finally {
            assertTrue("Failed to revert attribute via command: " + CliUtils.buildCommand(address, ":write-attribute", new String[]{attribute + "=" + originalValue}),
                    cliClient.writeAttribute(address, attribute, originalValue));
            verifyAttributeValueIgnoringDefaults(cliClient, address, attribute, originalValue);
        }
    }

    /**
     * Verify, that value of attribute is set as expected (ignores default values in case the value is undefined). Fail the test otherwise.
     *
     * @param cliClient      Configured CLI client for executing CLI operations
     * @param address        Address of attribute.
     * @param name           Name of attribute.
     * @param expected       Expected value of attribute.
     */
    public static void verifyAttributeValueIgnoringDefaults(CliClient cliClient, String address, String name, String expected) {
        String actual = cliClient.readAttribute(address, name, false);
        assertEquals(expected, actual);
    }

    /**
     * Verify, that value of attribute is set as expected. Fail the test otherwise.
     *
     * @param cliClient      Configured CLI client for executing CLI operations
     * @param address        Address of attribute.
     * @param name           Name of attribute.
     * @param expected       Expected value of attribute.
     */
    public static void verifyAttributeValue(CliClient cliClient, String address, String name, String expected) {
        String actual = cliClient.readAttribute(address, name);
        assertEquals(expected, actual);
    }
}

