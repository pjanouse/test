package org.jboss.qa.hornetq.test.prepares.specific;

import org.apache.commons.io.FileUtils;
import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.FourNodes;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class BytemanLodh2Prepare extends FourNodes {

    public static final String GROUP_ADDRESS = "233.6.88.5";

    @Override
    @PrepareMethod(value = "BytemanLodh2Prepare", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);

        PrepareUtils.setIfNotSpecified(params, PrepareParams.MAX_DELIVERY_ATTEMPTS, 200);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.REDISTRIBUTION_DELAY, 0);
    }

    @Override
    protected void beforePrepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        PrepareUtils.setIfNotSpecified(params, PrepareParams.PREPARE_DESTINATIONS, false);
        beforePrepareContainer(params, ctx);
    }

    @Override
    protected void beforePrepareContainer4(Map<String, Object> params, PrepareContext ctx) throws Exception {
        PrepareUtils.setIfNotSpecified(params, PrepareParams.PREPARE_DESTINATIONS, false);
        beforePrepareContainer(params, ctx);
    }

    @Override
    protected void afterPrepareContainer(Map<String, Object> params, PrepareContext ctx) {
        String messagingGroupSocketBindingName = "messaging-group";

        JMSOperations jmsOperations = getJMSOperations(params);

        jmsOperations.setClustered(true);
        jmsOperations.removeBroadcastGroup(PrepareConstants.BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(PrepareConstants.BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000, PrepareConstants.CONNECTOR_NAME, "");
        jmsOperations.removeDiscoveryGroup(PrepareConstants.DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(PrepareConstants.DISCOVERY_GROUP_NAME, messagingGroupSocketBindingName, 10000);
        jmsOperations.removeClusteringGroup(PrepareConstants.CLUSTER_NAME);
        jmsOperations.setClusterConnections(PrepareConstants.CLUSTER_NAME, "jms", PrepareConstants.DISCOVERY_GROUP_NAME, false, 1, 1000, true,
                PrepareConstants.CONNECTOR_NAME);

        jmsOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, GROUP_ADDRESS);
        jmsOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsOperations.reload();

        jmsOperations.setNodeIdentifier(getContainer(params).getProcessId());
    }

    @Override
    protected void afterPrepareContainer1(Map<String, Object> params, PrepareContext ctx) throws Exception {
        String messagingGroupSocketBindingName = "messaging-group";

        super.afterPrepareContainer1(params, ctx);
        JMSOperations jmsOperations = getJMSOperations(params);
        jmsOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55874);
    }

    @Override
    protected void afterPrepareContainer3(Map<String, Object> params, PrepareContext ctx) throws Exception {
        String messagingGroupSocketBindingName = "messaging-group";

        super.afterPrepareContainer3(params, ctx);
        JMSOperations jmsOperations = getJMSOperations(params);
        jmsOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55874);
    }

    @Override
    protected void afterPrepareContainer2(Map<String, Object> params, PrepareContext ctx) throws Exception {
        String messagingGroupSocketBindingName = "messaging-group";

        super.afterPrepareContainer2(params, ctx);
        JMSOperations jmsOperations = getJMSOperations(params);
        jmsOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55875);
    }

    @Override
    protected void afterPrepareContainer4(Map<String, Object> params, PrepareContext ctx) throws Exception {
        String messagingGroupSocketBindingName = "messaging-group";

        super.afterPrepareContainer4(params, ctx);
        JMSOperations jmsOperations = getJMSOperations(params);
        jmsOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55875);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepare(params, ctx);

        ctx.invokeMethod("BytemanLodh2Prepare-afterPrepare", params);
    }

    @PrepareMethod(value = "BytemanLodh2Prepare-afterPrepare", labels = {"EAP6"})
    public void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        prepareRemoteConnectorEAP6(container2, container1);
        prepareRemoteConnectorEAP6(container4, container3);

        copyApplicationPropertiesFiles();
    }

    @PrepareMethod(value = "BytemanLodh2Prepare-afterPrepare", labels = {"EAP7"})
    public void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        prepareRemoteConnectorEAP7(container2, container1);
        prepareRemoteConnectorEAP7(container4, container3);

        copyApplicationPropertiesFiles();
    }

    private void prepareRemoteConnectorEAP6(Container container, Container to) {
        String remoteConnectorName = "netty-remote";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addRemoteSocketBinding("messaging-remote", to.getHostname(),
                to.getHornetqPort());
        jmsOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory("hornetq-ra", -1);
        jmsOperations.close();
    }

    private void prepareRemoteConnectorEAP7(Container container, Container to) {
        String remoteConnectorName = "netty-remote";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addRemoteSocketBinding("messaging-remote", to.getHostname(),
                to.getHornetqPort());
        jmsOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null, PrepareConstants.ACCEPTOR_NAME);
        jmsOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);

        jmsOperations.close();
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone"
                    + File.separator + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }
}
