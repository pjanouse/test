package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RemoteJCAReplicated extends ThreeNodes {

    public static final String REMOTE_LIVE_SOCKET_BINDING = "messaging-remote-live";

    public static final String REMOTE_BACKUP_SOCKET_BINDING = "messaging-remote-backup";

    public static final String REMOTE_LIVE_CONNECTOR = "connector-remote-live";

    public static final String REMOTE_BACKUP_CONNECTOR = "connector-remote-backup";

    @Override
    @PrepareMethod(value = "RemoteJCAReplicated", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);
        PrepareUtils.setIfNotSpecified(params, "3." + PrepareParams.CLUSTER_TYPE, Constants.CLUSTER_TYPE.NONE);
        PrepareUtils.setIfNotSpecified(params, "1." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        PrepareUtils.setIfNotSpecified(params, "2." + PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.REPLICATION_GROUP_NAME, "group0");
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.afterPrepare(params, ctx);

        ctx.invokeMethod("RemoteJCAReplicated-afterPrepare", params);
    }

    @PrepareMethod(value = "RemoteJCAReplicated-afterPrepare", labels = {"EAP6"})
    public void afterPrepareEAP6(Map<String, Object> params) throws Exception {
        Container container = getContainer(params, 3);
        Container remoteLive = getContainer(params, 1);
        Container remoteBackup = getContainer(params, 2);

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addRemoteSocketBinding(REMOTE_LIVE_SOCKET_BINDING, remoteLive.getHostname(), remoteLive.getHornetqPort());
        jmsOperations.createRemoteConnector(REMOTE_LIVE_CONNECTOR, REMOTE_LIVE_SOCKET_BINDING, null);
        jmsOperations.addRemoteSocketBinding(REMOTE_BACKUP_SOCKET_BINDING, remoteBackup.getHostname(), remoteBackup.getHornetqPort());
        jmsOperations.createRemoteConnector(REMOTE_BACKUP_CONNECTOR, REMOTE_BACKUP_SOCKET_BINDING, null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(REMOTE_LIVE_CONNECTOR);
        connectorList.add(REMOTE_BACKUP_CONNECTOR);

        jmsOperations.setConnectorOnPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, connectorList);
        jmsOperations.setHaForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, true);
        jmsOperations.setBlockOnAckForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, true);
        jmsOperations.setRetryIntervalForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, 1000L);
        jmsOperations.setRetryIntervalMultiplierForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, 1.0);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP6, -1);

        jmsOperations.close();
    }

    @PrepareMethod(value = "RemoteJCAReplicated-afterPrepare", labels = {"EAP7"})
    public void afterPrepareEAP7(Map<String, Object> params) throws Exception {
        Container container = getContainer(params, 3);
        Container remoteLive = getContainer(params, 1);
        Container remoteBackup = getContainer(params, 2);

        JMSOperations jmsOperations = container.getJmsOperations();

        prepareRemoteConnectors(params, jmsOperations, remoteLive, remoteBackup);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(REMOTE_LIVE_CONNECTOR);
        connectorList.add(REMOTE_BACKUP_CONNECTOR);

        jmsOperations.setConnectorOnPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, connectorList);
        jmsOperations.setHaForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, true);
        jmsOperations.setBlockOnAckForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, true);
        jmsOperations.setRetryIntervalForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, 1000L);
        jmsOperations.setRetryIntervalMultiplierForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, 1.0);
        jmsOperations.setReconnectAttemptsForPooledConnectionFactory(PrepareConstants.POOLED_CONNECTION_FACTORY_NAME_EAP7, -1);

        jmsOperations.close();
    }

    private void prepareRemoteConnectors(Map<String, Object> params, JMSOperations jmsOperations, Container remoteLive, Container remoteBackup) {
        Constants.CONNECTOR_TYPE connectorType = Constants.CONNECTOR_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CONNECTOR_TYPE, "HTTP_CONNECTOR"));

        switch (connectorType) {
            case HTTP_CONNECTOR:
                jmsOperations.addRemoteSocketBinding(REMOTE_LIVE_SOCKET_BINDING, remoteLive.getHostname(), remoteLive.getHornetqPort());
                jmsOperations.addRemoteSocketBinding(REMOTE_BACKUP_SOCKET_BINDING, remoteBackup.getHostname(), remoteBackup.getHornetqPort());
                jmsOperations.createHttpConnector(REMOTE_LIVE_CONNECTOR, REMOTE_LIVE_SOCKET_BINDING, null, PrepareConstants.ACCEPTOR_NAME);
                jmsOperations.createHttpConnector(REMOTE_BACKUP_CONNECTOR, REMOTE_BACKUP_SOCKET_BINDING, null, PrepareConstants.ACCEPTOR_NAME);
                break;
            case NETTY_BIO:
            case NETTY_NIO:
                jmsOperations.addRemoteSocketBinding(REMOTE_LIVE_SOCKET_BINDING, remoteLive.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + remoteLive.getPortOffset());
                jmsOperations.addRemoteSocketBinding(REMOTE_BACKUP_SOCKET_BINDING, remoteBackup.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + remoteBackup.getPortOffset());
                jmsOperations.createRemoteConnector(REMOTE_LIVE_CONNECTOR, REMOTE_LIVE_SOCKET_BINDING, null);
                jmsOperations.createRemoteConnector(REMOTE_BACKUP_CONNECTOR, REMOTE_BACKUP_SOCKET_BINDING, null);
                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType.name());
        }
    }
}
