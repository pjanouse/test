package org.jboss.qa.hornetq;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ModelNodeUtils;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class DomainContainerEAP6 implements DomainContainer {

    private static final Logger LOG = Logger.getLogger(DomainContainerEAP6.class);

    private ContainerDef containerDef = null;
    private ContainerController containerController = null;

    @Override
    public void init(ArquillianDescriptor arquillianDescriptor, ContainerController containerController) {
        this.containerController = containerController;

        for (ContainerDef cDef : arquillianDescriptor.getContainers()) {
            if (cDef.getContainerName().equalsIgnoreCase("cluster")) {
                containerDef = cDef;
            }
        }

        if (containerDef == null) {
            throw new RuntimeException("No domain container found in Arquillian descriptor "
                    + arquillianDescriptor.getDescriptorName());
        }
    }

    @Override
    public Constants.CONTAINER_TYPE getContainerType() {
        return Constants.CONTAINER_TYPE.EAP6_DOMAIN_CONTAINER;
    }

    @Override
    public String getHostname() {
        return containerDef.getContainerProperties().get("managementAddress");
    }

    @Override
    public int getPort() {
        return Constants.MANAGEMENT_PORT_DEFAULT_EAP6;
    }

    @Override
    public void createServerGroup(String groupName, String profileName, String socketBindings) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("add");
        model.get(ClientConstants.OP_ADDR).add("server-group", groupName);
        model.get("profile").set(profileName);
        model.get("socket-binding-group").set(socketBindings);

        LOG.info("Creating server group " + groupName + " with profile " + profileName + " and socket binding "
                + socketBindings);
        try {
            ModelNodeUtils.applyOperation(getHostname(), getPort(), model);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating server group " + groupName, e);
        }
    }

    @Override
    public void removeServerGroup(String groupName) {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("remove");
        model.get(ClientConstants.OP_ADDR).add("server-group", groupName);

        LOG.info("Removing server group " + groupName);
        try {
            ModelNodeUtils.applyOperation(getHostname(), getPort(), model);
        } catch (Exception e) {
            throw new RuntimeException("Error while deleting server group " + groupName, e);
        }
    }

    @Override
    public DomainServerGroup serverGroup(String groupName) {
        return new DomainServerGroupEAP6(groupName, getHostname(), getPort(), containerController);
    }

    @Override
    public void reloadDomain() {
        ModelNode model = new ModelNode();
        model.get(ClientConstants.OP).set("reload");
        model.get(ClientConstants.OP_ADDR).add("host", "master");

        LOG.info("Reloading domain controller");
        try {
            ModelNodeUtils.applyOperation(getHostname(), getPort(), model);
        } catch (Exception e) {
            throw new RuntimeException("Error while reloading the domain", e);
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            LOG.warn("Waiting for the domain controller reload was interrupted");
        }
    }

}
