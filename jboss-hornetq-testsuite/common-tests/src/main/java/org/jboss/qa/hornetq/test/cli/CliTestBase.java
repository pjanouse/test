package org.jboss.qa.hornetq.test.cli;

import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.DebugTools;
import org.jboss.qa.management.cli.CliClient;
import org.junit.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Parent class for all Cli tests.
 */
public class CliTestBase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(CliTestBase.class);

    public void writeReadAttributeTest(CliClient cliClient, String address, String attributeName, String value) throws Exception {

        boolean isWritable = isWritable(address, attributeName);
        log.info("Test attribute: " + attributeName + ", writable: " + isWritable);

        if (isWritable) {
            CliTestUtils.attributeOperationTest(cliClient, address, attributeName, value);
            if (cliClient.reloadRequired()) {
                if (!reload(cliClient, 3)){
                    DebugTools.printThreadDump();
                    ContainerUtils.printThreadDump(container(1));
                    Assert.fail("Reload operation failed after 3 attempts.");
                }
                long startTime = System.currentTimeMillis();
                while (true) {
                    try {
                        if (cliClient.executeCommand("/:read-attribute(name=server-state)").isSuccess()) {
                            break;
                        }
                    } catch (Exception e) {
                        // it can happen that the server is not active so try again
                    }
                    Thread.sleep(500);
                    if (startTime - System.currentTimeMillis() > 15000) {
                        log.error("Problem with reload of the server. Server did not start in 15 seconds.");
                        break;
                    }
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
        cli.connect(container(1).getHostname(), container(1).getPort(), container(1).getUsername(), container(1).getPassword().toCharArray());
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

    private boolean reload(final CliClient cliClient, final int attempts) throws InterruptedException {
        final AtomicBoolean result = new AtomicBoolean(false);

        Thread reload = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < attempts; i++) {
                    try {
                        log.info("Calling reload command");
                        result.set(cliClient.reload());
                        return;
                    } catch (Exception e) {
                        log.warn("Error during reload command", e);
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e2) {
                            log.warn(e2);
                        }
                    }
                }
            }
        }, "reload-operation");

        reload.start();
        // Wait 5 minutes
        reload.join(300000);
        if (reload.isAlive()) {
            DebugTools.printThreadDump(reload);
            ContainerUtils.printThreadDump(container(1));

            reload.interrupt();
            reload.join();
            Assert.fail("Reload operation timed out after 5 minutes.");
        }
        return result.get();
    }


}
