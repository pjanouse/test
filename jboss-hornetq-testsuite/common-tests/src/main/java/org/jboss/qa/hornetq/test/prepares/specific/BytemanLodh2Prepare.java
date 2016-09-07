package org.jboss.qa.hornetq.test.prepares.specific;

import org.apache.commons.io.FileUtils;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.generic.FourNodes;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class BytemanLodh2Prepare extends FourNodes {

    public static final String GROUP_ADDRESS = "233.6.88.5";

    @Override
    @PrepareMethod(value = "BytemanLodh2Prepare", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "BytemanLodh2Prepare", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void afterPrepareContainer(Map<String, Object> params, Container container) {
        String messagingGroupSocketBindingName = "messaging-group";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setClustered(true);
        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.setBroadCastGroup(BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000, CONNECTOR_NAME_EAP6, "");
        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.setDiscoveryGroup(DISCOVERY_GROUP_NAME, messagingGroupSocketBindingName, 10000);
        jmsOperations.removeClusteringGroup(CLUSTER_NAME);
        jmsOperations.setClusterConnections(CLUSTER_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000, true,
                CONNECTOR_NAME_EAP6);

        jmsOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, GROUP_ADDRESS);
        jmsOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsOperations.createSocketBinding(messagingGroupSocketBindingName, "public", GROUP_ADDRESS, 55874);
    }

    @Override
    protected void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        Container container3 = getContainer(params, 3);
        Container container4 = getContainer(params, 4);

        prepareRemoteConnectorEAP6(container2, container1);
        prepareRemoteConnectorEAP6(container4, container3);

        copyApplicationPropertiesFiles();
    }

    @Override
    protected void afterPrepareEAP7(Map<String, Object> params) throws Exception {
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
        jmsOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, remoteConnectorName);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);

        jmsOperations.close();
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
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