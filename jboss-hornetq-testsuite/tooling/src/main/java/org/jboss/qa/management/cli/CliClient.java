package org.jboss.qa.management.cli;

import org.apache.log4j.Logger;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author rhatlapa (rhatlapa@redhat.com)
 */
public class CliClient {

    private static final Logger log = Logger.getLogger(CliClient.class.getName());

    private final CliConfiguration cliConfig;

    public CliClient() {
        cliConfig = new CliConfiguration();
    }

    public CliClient(CliConfiguration cliConfig) {
        this.cliConfig = cliConfig;
    }

    /**
     * Executes command and returns full output as CLI.Result
     *
     * @param command Command to be executed
     * @return CLI.Result object containing request, response and success status of the operation all in one
     */
    public CLI.Result executeCommand(String command) {

        CLI cli = CLI.newInstance();

        log.debug("Connecting to native interface (" + cliConfig.getHost() + ":" + cliConfig.getPort() + ") via CLI");
        cli.connect(cliConfig.getHost(), cliConfig.getPort(), cliConfig.getUser(), cliConfig.getPassword());

        log.debug("Running cli command: " + command);
        CLI.Result result = cli.cmd(command);

        log.info("Executed command " + result.getCliCommand() + " ended with response " + result.getResponse());
        cli.disconnect();
        log.debug("Successfully disconnected");

        return result;
    }

    /**
     * Execute the command via CLI and returns its response as ModelNode.
     *
     * @param command String based command to be executed via CLI.
     * @return Response of given command.
     */
    public ModelNode executeForResponse(String command) {
        CLI.Result result = executeCommand(command);
        return result.getResponse();
    }

    /**
     * Execute the command via CLI and returns its command execution status.
     *
     * @param command String based command to be executed via CLI.
     * @return Response <code>true</code>, if the command execution was successful, else <code>false</code>.
     */
    public boolean executeForSuccess(String command) {
        CLI.Result result = executeCommand(command);
        return result.isSuccess();
    }

    /**
     * Execute the command via CLI and returns its result as String,
     *
     * @param command String based command to be executed via CLI.
     * @return Result of given command.
     */
    public String executeForResult(String command) {
        CLI.Result result = executeCommand(command);
        return result.getResponse().get(RESULT).asString();
    }

    /**
     * Give access to connected cli
     * 
     * @return cli
     */
    public CLI getCLI() {
        CLI cli = CLI.newInstance();

        log.debug("Connecting to native interface (" + cliConfig.getHost() + ":" + cliConfig.getPort() + ") via CLI");
        cli.connect(cliConfig.getHost(), cliConfig.getPort(), cliConfig.getUser(), cliConfig.getPassword());
        return cli;
    }

    /**
     * Read attribute from given address.
     *
     * @param address Address of node we are interested in.
     * @param name Name of the attribute.
     * @return Value of given attribute.
     */
    public String readAttribute(String address, String name) {
        return readAttribute(address, name, true);
    }

    /**
     * Read attribute from given address
     *
     * @param address Address of node we are interested in.
     * @param name Name of the attribute.
     * @param includeDefaults Whether use default value or not for attribute with undefined value
     * @return Value of given attribute.
     */
    public String readAttribute(String address, String name, boolean includeDefaults) {
        String command = CliUtils.buildCommand(address, ":read-attribute", new String[] { "name=" + name,
                "include-defaults=" + String.valueOf(includeDefaults) });
        return executeForResult(command);
    }

    /**
     * Write new value to attribute.
     *
     * @param address Address of node we are interested in.
     * @param name Name of the attribute.
     * @param value New value of attribute.
     * @return Result <code>true</code>, if :write-attribute operation was successful, else <code>false</code>.
     */
    public boolean writeAttribute(String address, String name, String value) {
        String[] attributes = { "name=" + name, "value=" + value };
        String command = CliUtils.buildCommand(address, ":write-attribute", attributes);
        return executeForSuccess(command);
    }

    /**
     * Reads system property via CLI from server config
     *
     * @param name Name of the property
     * @return value of the system property as defined in the server configuration
     */
    public String getSystemProperty(String name) {
        return readAttribute("/system-property=" + name, "value");
    }

    /**
     * Add system property.
     *
     * @param name Name of the property.
     * @param value New value of the property.
     * @return Result <code>true</code>, if :add system property was successful, <code>false</code> otherwise.
     */
    public boolean addSystemProperty(String name, String value) {
        String command = CliUtils.buildCommand("/system-property=" + name, ":add", new String[] { "value=" + value });
        return executeForSuccess(command);
    }

    /**
     * Check whether server is in reload-required state.
     *
     * @return <code>true</code>, if server is in reload-required state, else <code>false</code>.
     */
    public boolean reloadRequired() {
        String result = readAttribute(null, "server-state");
        return result.equals("reload-required");
    }

    /**
     * Check operation response and find out whether it left server in reload-required state. Server could be in reload-required
     * state before this operation!
     *
     * @param response Response of CLI operation.
     * @return Return <code>true</code>, if operation left server in reload-required state, else <code>false</code>.
     */
    public boolean reloadRequired(ModelNode response) {
        String result = response.get(RESPONSE_HEADERS).get(PROCESS_STATE).asString();
        System.out.println(response);
        return result.equals("reload-required");
    }

    /**
     * Reloads server via CLI and waits for preset timeout for server to become available
     *
     * @return Return <code>true</code>, if server becomes available after reload operation is processed, <code>false</code>
     *         otherwise
     */
    public boolean reload() {
        executeCommand("reload");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            return CheckServerAvailableUtils.waitHornetQToAlive(cliConfig.getHost(), cliConfig.getPort(), 15000);
        } catch (InterruptedException e) {
            log.error("Problem with reload of the server.", e);
            return false;
        }
    }

    /**
     * Runs reload on the server, if forced it is executed always, if not forced it is run only if server state is
     * reload-required
     *
     * @param forced if true then reload is done, if false, it is done only if server state is reload-required
     */
    public void reload(boolean forced) {
        if (forced || reloadRequired()) {
            reload();
        }
    }

    /**
     * Get list of childnodes' names of specified type for given node.
     *
     * @param nodeAddress Address of node we are interested in
     * @param childNodeType Type of child nodes to found
     * @return Names of children of given type
     */
    public List<String> getChildNames(String nodeAddress, String childNodeType) {
        List<String> list = new ArrayList<String>();

        String cmd = CliUtils.buildCommand(nodeAddress, ":read-children-names", new String[] { "child-type=" + childNodeType });

        List<ModelNode> asList = executeForResponse(cmd).get(RESULT).asList();

        for (ModelNode modelNode : asList) {
            list.add(modelNode.asString());
        }

        return list;
    }

    /**
     * Check if any child node of given type exists for given parent node.
     *
     * @param parentAddress Address of node we are interested in
     * @param childNodeType Name of childnode type
     * @return Whether there exists direct child node of given type
     */
    public boolean hasChildNode(String parentAddress, String childNodeType) {
        return !getChildNames(parentAddress, childNodeType).isEmpty();
    }

    /**
     * Check if child node of given type and exactly the same name exists for given parent node.
     *
     * @param parentAddress Address of node we are interested in
     * @param childNodeType Name of childnode type
     * @param childNodeName Name of childnode we are looking for
     * @return Whether there exists direct child node of given type and name
     */
    public boolean childNodeExists(String parentAddress, String childNodeType, String childNodeName) {
        if (childNodeName == null) {
            throw new IllegalArgumentException("Child node name cannot be null");
        }
        List<String> childNodeList = getChildNames(parentAddress, childNodeType);
        for (String child : childNodeList) {
            if (childNodeName.equals(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve full filesystem path of path defined by /path=&lt;pathName&gt;. Path is resolved recursively until relative-to
     * attribute is specified.
     * <p/>
     * 
     * @param pathName name of path in configuration
     * @return full path
     */
    public String resolveFullPath(String pathName) {
        log.debug("Resolving full path of " + pathName);
        String path = this.readAttribute("/path=" + pathName, "path");
        log.debug("Resolved path");
        String relativeTo = this.readAttribute("/path=" + pathName, "relative-to");
        if (relativeTo == null || relativeTo.isEmpty() || relativeTo.equals("undefined")) {
            return new File(path).getAbsolutePath();
        } else {
            return new File(resolveFullPath(relativeTo), path).getPath();
        }
    }
}
