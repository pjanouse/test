package org.jboss.qa.hornetq;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.client.helpers.domain.InitialDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.exception.ModelNodeOperationException;
import org.jboss.qa.hornetq.tools.ActiveMQAdminOperationsEAP7;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ModelNodeUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

public class DomainServerGroupEAP7 implements DomainServerGroup {

    private static final Logger LOG = Logger.getLogger(DomainServerGroupEAP7.class);

    private final String name;
    private final String hostname;
    private final int port;
    private final String profileName;
    private final String socketBindings;
    private final ContainerController containerController;

    DomainServerGroupEAP7(String groupName, String hostname, int port, ContainerController containerController) {
        this.name = groupName;
        this.hostname = hostname;
        this.port = port;
        this.containerController = containerController;
        try {
            this.profileName = getProfileName();
            this.socketBindings = getSocketBinding();
        } catch (Exception e) {
            throw new RuntimeException("Error while initializing server group " + name, e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JMSOperations getJmsOperations() {
        ActiveMQAdminOperationsEAP7 eap7AdmOps = new ActiveMQAdminOperationsEAP7();
        eap7AdmOps.setHostname(hostname);
        eap7AdmOps.setPort(port);
        eap7AdmOps.connect();

        eap7AdmOps.addAddressPrefix("profile", profileName);

        return eap7AdmOps;
    }

    @Override
    public DomainNode node(String name) {
        return new DomainNodeEAP7(name, hostname, port, containerController);
    }

    @Override
    public void createNode(String nodeName) {
        createNode(nodeName, 0);
    }

    @Override
    public void createNode(String nodeName, int portOffset) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", nodeName);
        model.get("group").set(name);
        model.get("auto-start").set(false);
        if (portOffset != 0) {
            model.get("socket-binding-port-offset").set(portOffset);
        }

        LOG.info("Creating node " + nodeName + " in server group " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating domain node " + nodeName, e);
        }
    }

    @Override
    public void deleteNode(String nodeName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("host", "master");
        model.get(ClientConstants.OP_ADDR).add("server-config", nodeName);

        LOG.info("Removing node " + nodeName + " from server group " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
        } catch (Exception e) {
            throw new RuntimeException("Error while removing domain node " + nodeName, e);
        }
    }

    @Override
    public void startAllNodes() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("start-servers");
        model.get(ClientConstants.OP_ADDR).set("server-group", name);

        LOG.info("Starting all nodes in group " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
            waitForNodesStarted();
        } catch (Exception e) {
            throw new RuntimeException("Error while starting nodes in server group " + name, e);
        }
    }

    @Override
    public void stopAllNodes() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("stop-servers");
        model.get(ClientConstants.OP_ADDR).set("server-group", name);

        LOG.info("Stopping all nodes in group " + name);
        try {
            ModelNodeUtils.applyOperation(hostname, port, model);
            waitForNodesStopped();
        } catch (Exception e) {
            throw new RuntimeException("Error while stopping nodes in server group " + name, e);
        }
    }

    @Override
    public void deploy(Archive archive) {
        DomainClient domain = null;
        InputStream archiveStream = archive.as(ZipExporter.class).exportAsInputStream();

        LOG.info("Deploying archive " + archive.getName() + " to server group " + name);
        try {
            domain = DomainClient.Factory.create(InetAddress.getByName(hostname), port);
            DomainDeploymentManager deployer = domain.getDeploymentManager();

            InitialDeploymentPlanBuilder builder = deployer.newDeploymentPlan();
            DeploymentPlan plan = builder.add(archive.getName(), archiveStream).andDeploy().toServerGroup(name).build();

            // block until the deployment's done on all server group nodes
            deployer.execute(plan).get();
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to deploy archive " + archive.getName(), e);
        } finally {
            if (domain != null) {
                try {
                    domain.close();
                } catch (IOException e) {
                    LOG.warn("Error while trying to close domain client", e);
                }
            }
        }
    }

    @Override
    public void undeploy(Archive archive) {
        undeploy(archive.getName());
    }

    @Override
    public void undeploy(String archiveName) {
        DomainClient domain = null;

        LOG.info("Removing archive " + archiveName + " from server group " + name);
        try {
            domain = DomainClient.Factory.create(InetAddress.getByName(hostname), port);
            DomainDeploymentManager deployer = domain.getDeploymentManager();

            InitialDeploymentPlanBuilder builder = deployer.newDeploymentPlan();
            DeploymentPlan plan = builder.undeploy(archiveName).andRemoveUndeployed().toServerGroup(name).build();

            // block until the undeployment's done on all server group nodes
            deployer.execute(plan).get();
        } catch (Exception e) {
            throw new RuntimeException("Error while trying to undeploy archive " + archiveName, e);
        } finally {
            if (domain != null) {
                try {
                    domain.close();
                } catch (IOException e) {
                    LOG.warn("Error while trying to close domain client", e);
                }
            }
        }
    }

    private String getProfileName() throws IOException, ModelNodeOperationException {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).set("server-group", name);
        model.get("name").set("profile");

        ModelNode result = ModelNodeUtils.applyOperation(hostname, port, model);
        return result.get("result").asString();
    }

    private String getSocketBinding() throws IOException, ModelNodeOperationException {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("read-attribute");
        model.get(ClientConstants.OP_ADDR).set("server-group", name);
        model.get("name").set("socket-binding-group");

        ModelNode result = ModelNodeUtils.applyOperation(hostname, port, model);
        return result.get("result").asString();
    }

    private void waitForNodesStarted() throws IOException {
        while (!areAllNodesStarted()) {
            LOG.info("Waiting for all nodes in server group " + name + " to start...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOG.warn("Error while waiting for checking domain node statuses", e);
            }
        }

        LOG.info("All nodes in server group " + name + " started");
    }

    private void waitForNodesStopped() throws IOException {
        while (!areAllNodesStopped()) {
            LOG.info("Waiting for all nodes in server group " + name + " to stop...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                LOG.warn("Error while waiting for checking domain node statuses", e);
            }
        }

        LOG.info("All nodes in server group " + name + " stopped");
    }

    private boolean areAllNodesStarted() throws IOException {
        for (Map.Entry<String, ServerStatus> node : getServerGroupNodesStatus().entrySet()) {
            if (!ServerStatus.STARTED.equals(node.getValue())) {
                return false;
            }
        }

        return true;
    }

    private boolean areAllNodesStopped() throws IOException {
        for (Map.Entry<String, ServerStatus> node : getServerGroupNodesStatus().entrySet()) {
            if (!ServerStatus.STOPPED.equals(node.getValue()) && !ServerStatus.DISABLED.equals(node.getValue())) {
                return false;
            }
        }

        return true;
    }

    private Map<String, ServerStatus> getServerGroupNodesStatus() throws IOException {
        DomainClient domain = null;
        try {
            domain = DomainClient.Factory.create(InetAddress.getByName(hostname), port);
            Map<String, ServerStatus> result = new HashMap<String, ServerStatus>();
            for (Map.Entry<ServerIdentity, ServerStatus> nodeStatus : domain.getServerStatuses().entrySet()) {
                // filter out only servers in this server group
                if (name.equals(nodeStatus.getKey().getServerGroupName())) {
                    result.put(nodeStatus.getKey().getServerName(), nodeStatus.getValue());
                }
            }

            return result;
        } finally {
            if (domain != null) {
                domain.close();
            }
        }
    }

}
