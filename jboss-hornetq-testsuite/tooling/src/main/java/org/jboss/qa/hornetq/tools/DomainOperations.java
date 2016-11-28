package org.jboss.qa.hornetq.tools;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.threads.JBossThreadFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Administration operations for manipulating domain structure.
 *
 * EAP6 only!
 */
public final class DomainOperations {

    private static final Logger LOG = Logger.getLogger(DomainOperations.class);

    private static final String DEFAULT_HOST = "master";

    private static final String DEFAULT_PROFILE = "full-ha";

    private static final String DEFAULT_SOCKET_BINDINGS = "full-ha-sockets";

    private static final int TIMEOUT = 30000;

    private final ModelControllerClient modelControllerClient;

    private DomainOperations(final String hostname, final int managementPort) {
        try {
            AtomicInteger executorCount = new AtomicInteger(0);
            ThreadGroup group = new ThreadGroup("management-client-thread");
            ThreadFactory threadFactory = new JBossThreadFactory(group, Boolean.FALSE, null, "%G "
                    + executorCount.incrementAndGet() + "-%t", null, null, AccessController.getContext());
            ExecutorService executorService = new ThreadPoolExecutor(2, 6, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), threadFactory);

            this.modelControllerClient = ModelControllerClient.Factory.create(hostname, managementPort);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot create model controller client for host: " + hostname + " and port "
                    + managementPort, e);
        }

        if (!testDomain()) {
            throw new RuntimeException("Cannot use domain operations or non-domain server launch type");
        }
    }

    /**
     * Create and connect domain administration client for the default domain container.
     *
     * The client will check if the container runs in the domain mode and fails if that's not the case.
     *
     * @return connected client
     */
    public static DomainOperations forDefaultContainer() {
        throw new RuntimeException("This is not yet implemented");
    }

    /**
     * Create and connect domain administration client for the specified domain container.
     *
     * The client will check if the container runs in the domain mode and fails if that's not the case.
     *
     * @param container domain container information
     * @return connected client
     */
    public static DomainOperations forContainer(final Container container) {
        return new DomainOperations(container.getHostname(), container.getPort());
    }

    public DomainOperations reloadDomain() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("reload");
        model.get(ClientConstants.OP_ADDR).add("host", "master");

        try {
            LOG.info("Reloading configuration so the config restoration can take effect");
            this.applyUpdate(model);
            Thread.sleep(10000L);
        } catch (Exception e) {
            LOG.error("Cannot reload the host controller", e);
        }

        return this;
    }

    public DomainOperations startNode(final String serverDomainName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("start");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", serverDomainName);

        try {
            LOG.info("Starting server " + serverDomainName);
            this.applyUpdate(model);
            Thread.sleep(5000);
        } catch (Exception e) {
            LOG.error("Cannot start server " + serverDomainName, e);
        }

        return this;
    }

    public DomainOperations stopNode(final String serverDomainName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("stop");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", serverDomainName);

        try {
            LOG.info("Stopping server " + serverDomainName);
            this.applyUpdate(model);
            Thread.sleep(5000);
        } catch (Exception e) {
            LOG.error("Cannot stop server " + serverDomainName, e);
        }

        return this;
    }

    /**
     * Create new server group in the domain.
     *
     * All the server nodes will use 'full-ha' profile and its respective socket bindings.
     *
     * @param serverGroupName name of the server group
     */
    public DomainOperations createServerGroup(final String serverGroupName) {
        return createServerGroup(serverGroupName, DEFAULT_PROFILE, DEFAULT_SOCKET_BINDINGS);
    }

    /**
     * Create new server group in the domain.
     *
     * @param serverGroupName name of the server group
     * @param profileName configuration profile used by all the servers in the group
     * @param socketBindings socket bindings used by all the servers (note: you can specify port-offset when creating
     *                       individual server nodes.
     */
    public DomainOperations createServerGroup(final String serverGroupName, final String profileName,
            final String socketBindings) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("server-group", serverGroupName);
        model.get("profile").set(profileName);
        model.get("socket-binding-group").set(socketBindings);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            LOG.error("Cannot create server group", e);
        }

        return this;
    }

    /**
     * Delete server group from the domain.
     *
     * Note that this does not remove individual server nodes.
     *
     * @param serverGroupName name of the server group
     */
    public DomainOperations removeServerGroup(final String serverGroupName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("server-group", serverGroupName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            LOG.error("Cannot remove server group", e);
        }

        return this;
    }

    /**
     * Create new server node in the domain.
     *
     * The server will be created on the default host 'master' and it will have default socket bindings
     * for the group profile (ie server-offset 0).
     *
     * @param serverName created server name
     * @param serverGroupName name of the server group
     */
    public DomainOperations createServer(final String serverName, final String serverGroupName) {
        return createServer(DEFAULT_HOST, serverName, serverGroupName, 0);
    }

    /**
     * Create new server node in the domain.
     *
     * The server will be created on the default host 'master'.
     *
     * @param serverName created server name
     * @param serverGroupName name of the server group
     * @param portOffset port offset for this server
     */
    public DomainOperations createServer(final String serverName, final String serverGroupName, final int portOffset) {
        return createServer(DEFAULT_HOST, serverName, serverGroupName, portOffset);
    }

    /**
     * Create new server node in the domain.
     *
     * The server will have default socket bindings for the group profile (ie server-offset 0).
     *
     * @param host domain host name
     * @param serverName created server name
     * @param serverGroupName name of the server group
     */
    public DomainOperations createServer(final String host, final String serverName, final String serverGroupName) {
        return createServer(host, serverName, serverGroupName, 0);
    }

    /**
     * Create new server node in the domain.
     *
     * @param host domain host name
     * @param serverName created server name
     * @param serverGroupName name of the server group
     * @param portOffset port offset for this server
     */
    public DomainOperations createServer(final String host, final String serverName, final String serverGroupName,
            final int portOffset) {

        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("host", host);
        model.get(ClientConstants.OP_ADDR).add("server-config", serverName);
        model.get("group").set(serverGroupName);
        model.get("auto-start").set(false);
        if (portOffset != 0) {
            model.get("socket-binding-port-offset").set(portOffset);
        }

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            LOG.error("Cannot create server node", e);
        }

        return this;
    }

    /**
     * Delete server node from the domain.
     *
     * Deleted server must be on the default host 'master'.
     *
     * @param serverName deleted server name
     */
    public DomainOperations removeServer(final String serverName) {
        return removeServer(DEFAULT_HOST, serverName);
    }

    /**
     * Delete server node from the domain.
     *
     * @param host domain host name
     * @param serverName deleted server name
     */
    public DomainOperations removeServer(final String host, final String serverName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("host", host);
        model.get(ClientConstants.OP_ADDR).add("server-config", serverName);

        try {
            this.applyUpdate(model);
        } catch (Exception e) {
            LOG.error("Cannot remove server node", e);
        }

        return this;
    }

    /**
     * Close the domain administration client.
     */
    public void close() {
        try {
            modelControllerClient.close();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private boolean testDomain() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get("name").set("launch-type");

        ModelNode result = null;
        try {
            result = applyUpdate(model);
        } catch (Exception e) {
            LOG.error("Cannot read server launch type", e);
        }

        LOG.info("Server launch type: " + result.get("result").asString());
        return result != null && "domain".equals(result.get("result").asString().toLowerCase());
    }

    private ModelNode applyUpdate(final ModelNode update) throws IOException, DomainOperationException {
        ModelNode result = modelControllerClient.execute(update);
        if (result.hasDefined(ClientConstants.OUTCOME)
                && ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            LOG.info(String.format("Operation successful for update = '%s'", update.toString()));
        } else if (result.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
            final String failureDesc = result.get(ClientConstants.FAILURE_DESCRIPTION).toString();
            throw new DomainOperationException(failureDesc);
        } else {
            throw new DomainOperationException(String.format("Operation not successful; outcome = '%s'",
                    result.get(ClientConstants.OUTCOME)));
        }
        return result;
    }

    private class DomainOperationException extends Exception {

        public DomainOperationException(final String msg) {
            super(msg);
        }
    }

}
