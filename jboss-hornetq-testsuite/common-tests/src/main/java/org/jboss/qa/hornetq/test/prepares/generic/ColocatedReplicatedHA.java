package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareContext;
import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareMethods;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class ColocatedReplicatedHA extends TwoNodes {

    public static final String STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE = "static-cluster-remote";

    public static final String STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE = "static-cluster-remote-live";

    public static final String STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP = "static-cluster-remote-backup";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_LIVE = "static-cluster-connector-live";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_BACKUP = "static-cluster-connector-backup";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE = "static-cluster-connector-remote-live";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP = "static-cluster-connector-remote-backup";

    public static final String JOURNALS_DIRECTORY_LIVE = "live";

    public static final String JOURNALS_DIRECTORY_BACKUP = "backup";

    @Override
    @PrepareMethod(value = "ColocatedReplicatedHA", labels = {"EAP6", "EAP7"})
    public void prepareMethod(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.prepareMethod(params, ctx);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        super.beforePrepare(params, ctx);

        PrepareUtils.setIfNotSpecified(params, PrepareParams.CLUSTER_TYPE, "DEFAULT");
        PrepareUtils.setIfNotSpecified(params, PrepareParams.JOURNALS_DIRECTORY, JOURNALS_DIRECTORY_LIVE);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.PREPARE_COLOCATED_BACKUP, true);
    }

    @Override
    protected void afterPrepare(Map<String, Object> params, PrepareContext ctx) throws Exception {
        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);
        JMSOperations jmsOperations1 = container1.getJmsOperations();
        JMSOperations jmsOperations2 = container2.getJmsOperations();

        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));

        if (clusterType == Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            ctx.invokeMethod("ColocatedReplicatedHA-staticCluster", params);
        }

        Map<String, Object> paramsNode1 = getParamsForContainer(params, container1, jmsOperations1, 1);
        Map<String, Object> paramsNode2 = getParamsForContainer(params, container2, jmsOperations2, 2);
        Map<String, Object> paramsNode1Backup = getParamsForServer(paramsNode1, PrepareConstants.BACKUP_SERVER_NAME);
        Map<String, Object> paramsNode2Backup = getParamsForServer(paramsNode2, PrepareConstants.BACKUP_SERVER_NAME);

        // LIVE on Node 1
        paramsNode1.put(PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        paramsNode1.put(PrepareParams.REPLICATION_GROUP_NAME, "group0");

        ctx.invokeMethod(PrepareMethods.PREPARE_HA, paramsNode1);

        // LIVE on Node 2
        paramsNode2.put(PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_MASTER);
        paramsNode2.put(PrepareParams.REPLICATION_GROUP_NAME, "group1");

        ctx.invokeMethod(PrepareMethods.PREPARE_HA, paramsNode2);

        // BACKUP on Node 1
        paramsNode1Backup.put(PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);
        paramsNode1Backup.put(PrepareParams.JOURNALS_DIRECTORY, JOURNALS_DIRECTORY_BACKUP);
        paramsNode1Backup.put(PrepareParams.REPLICATION_GROUP_NAME, "group1");

        ctx.invokeMethod(PrepareMethods.PREPARE_HA, paramsNode1Backup);
        ctx.invokeMethod(PrepareMethods.PREPARE_JOURNALS_DIRECTORY, paramsNode1Backup);

        // BACKUP on Node 2
        paramsNode2Backup.put(PrepareParams.HA_TYPE, Constants.HA_TYPE.REPLICATION_SLAVE);
        paramsNode2Backup.put(PrepareParams.JOURNALS_DIRECTORY, JOURNALS_DIRECTORY_BACKUP);
        paramsNode2Backup.put(PrepareParams.REPLICATION_GROUP_NAME, "group0");

        ctx.invokeMethod(PrepareMethods.PREPARE_HA, paramsNode2Backup);
        ctx.invokeMethod(PrepareMethods.PREPARE_JOURNALS_DIRECTORY, paramsNode2Backup);

        jmsOperations1.close();
        jmsOperations2.close();
    }

    @PrepareMethod(value = "ColocatedReplicatedHA-staticCluster", labels = {"EAP6"})
    public void prepareStaticClusterEAP6(Map<String, Object> params) {
        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);

        prepareStaticClusterNetty(container1, container2);
        prepareStaticClusterNetty(container2, container1);
    }

    @PrepareMethod(value = "ColocatedReplicatedHA-staticCluster", labels = {"EAP7"})
    public void prepareStaticClusterEAP7(Map<String, Object> params) {
        Constants.CONNECTOR_TYPE connectorType = PrepareUtils.getEnum(params, PrepareParams.CONNECTOR_TYPE, Constants.CONNECTOR_TYPE.class, Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);

        switch (connectorType) {
            case HTTP_CONNECTOR:
                prepareStaticClusterHttp(container1, container2);
                prepareStaticClusterHttp(container2, container1);
                break;
            case NETTY_BIO:
            case NETTY_NIO:
                prepareStaticClusterNetty(container1, container2);
                prepareStaticClusterNetty(container2, container1);
                break;
            default: throw new RuntimeException("Unsupported connectorType: " + connectorType);
        }
    }

    private void prepareStaticClusterNetty(Container container, Container to) {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.removeBroadcastGroup(PrepareConstants.BROADCAST_GROUP_NAME);
        jmsOperations.removeBroadcastGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.BROADCAST_GROUP_NAME);

        jmsOperations.removeDiscoveryGroup(PrepareConstants.DISCOVERY_GROUP_NAME);
        jmsOperations.removeDiscoveryGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.DISCOVERY_GROUP_NAME);

        jmsOperations.addRemoteSocketBinding(STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, to.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + to.getPortOffset());
        jmsOperations.addRemoteSocketBinding(STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, to.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7 + to.getPortOffset());

        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, PrepareConstants.MESSAGING_SOCKET_BINDING_NAME_BACKUP, null);
        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, null);
        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, null);

        jmsOperations.createRemoteConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, PrepareConstants.MESSAGING_SOCKET_BINDING_NAME, null);
        jmsOperations.createRemoteConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, null);
        jmsOperations.createRemoteConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, null);


        jmsOperations.removeClusteringGroup(PrepareConstants.CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(PrepareConstants.SERVER_NAME, PrepareConstants.CLUSTER_NAME, "jms", false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.removeClusteringGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.CLUSTER_NAME, "jms", false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.close();
    }

    private void prepareStaticClusterHttp(Container container, Container to) {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.removeBroadcastGroup(PrepareConstants.BROADCAST_GROUP_NAME);
        jmsOperations.removeBroadcastGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.BROADCAST_GROUP_NAME);

        jmsOperations.removeDiscoveryGroup(PrepareConstants.DISCOVERY_GROUP_NAME);
        jmsOperations.removeDiscoveryGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.DISCOVERY_GROUP_NAME);

        jmsOperations.addRemoteSocketBinding(STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE, to.getHostname(), to.getHttpPort());

        jmsOperations.createHttpConnector(STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, PrepareConstants.HTTP_SOCKET_BINDING, null, PrepareConstants.ACCEPTOR_NAME_BACKUP);
        jmsOperations.createHttpConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE, null, PrepareConstants.ACCEPTOR_NAME);
        jmsOperations.createHttpConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE, null, PrepareConstants.ACCEPTOR_NAME_BACKUP);

        jmsOperations.createHttpConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, PrepareConstants.HTTP_SOCKET_BINDING, null, PrepareConstants.ACCEPTOR_NAME);
        jmsOperations.createHttpConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE, null, PrepareConstants.ACCEPTOR_NAME);
        jmsOperations.createHttpConnector(PrepareConstants.BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE, null, PrepareConstants.ACCEPTOR_NAME_BACKUP);


        jmsOperations.removeClusteringGroup(PrepareConstants.CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(PrepareConstants.SERVER_NAME, PrepareConstants.CLUSTER_NAME, "jms", false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.removeClusteringGroup(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(PrepareConstants.BACKUP_SERVER_NAME, PrepareConstants.CLUSTER_NAME, "jms", false, 1, 1000, true, PrepareConstants.CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.close();
    }

}
