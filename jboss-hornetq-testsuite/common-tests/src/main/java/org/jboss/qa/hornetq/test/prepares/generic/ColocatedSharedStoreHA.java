package org.jboss.qa.hornetq.test.prepares.generic;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class ColocatedSharedStoreHA extends TwoNodes {

    public static final String STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE = "static-cluster-remote-live";

    public static final String STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP = "static-cluster-remote-backup";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_LIVE = "static-cluster-connector-live";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_BACKUP = "static-cluster-connector-backup";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE = "static-cluster-connector-remote-live";

    public static final String STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP = "static-cluster-connector-remote-backup";

    @Override
    @PrepareMethod(value = "ColocatedSharedStoreHA", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "ColocatedSharedStoreHA", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params) throws Exception {
        PrepareUtils.setIfNotSpecified(params, PrepareParams.CLUSTER_TYPE, "DEFAULT");
    }

    @Override
    protected void afterPrepareContainer1EAP6(Map<String, Object> params, Container container) throws Exception {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));

        super.afterPrepareContainer1EAP6(params, container);
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(true);
        jmsOperations.setFailoverOnShutdown(true);

        setJournalDirectories(jmsOperations, HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
        container.restart();
        Thread.sleep(2000);
        jmsOperations = container.getJmsOperations();

        prepareBackupEAP6(params, container);

        setJournalDirectories(jmsOperations, BACKUP_SERVER_NAME, HornetQTestCase.JOURNAL_DIRECTORY_B);

        if (clusterType != Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            prepareCluster(params, jmsOperations, BACKUP_SERVER_NAME);
        }

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP6(Map<String, Object> params, Container container) throws Exception {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));

        super.afterPrepareContainer2EAP6(params, container);
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.setFailoverOnShutdown(true);
        jmsOperations.setSharedStore(true);
        jmsOperations.setFailoverOnShutdown(true);

        setJournalDirectories(jmsOperations, HornetQTestCase.JOURNAL_DIRECTORY_B);

        jmsOperations.close();
        container.restart();
        Thread.sleep(2000);
        jmsOperations = container.getJmsOperations();

        prepareBackupEAP6(params, container);

        setJournalDirectories(jmsOperations, BACKUP_SERVER_NAME, HornetQTestCase.JOURNAL_DIRECTORY_A);

        if (clusterType != Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            prepareCluster(params, jmsOperations, BACKUP_SERVER_NAME);
        }

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainerEAP7(Map<String, Object> params, Container container) throws Exception {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addHAPolicySharedStoreMaster(5000, true);
        prepareBackupEAP7(params, container);

        if (clusterType != Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            prepareCluster(params, jmsOperations);
        }

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer1EAP7(Map<String, Object> params, Container container) throws Exception {
        super.afterPrepareContainer1EAP7(params, container);

        JMSOperations jmsOperations = container.getJmsOperations();

        setJournalDirectories(jmsOperations, HornetQTestCase.JOURNAL_DIRECTORY_A);
        setJournalDirectories(jmsOperations, BACKUP_SERVER_NAME, HornetQTestCase.JOURNAL_DIRECTORY_B);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepareContainer2EAP7(Map<String, Object> params, Container container) throws Exception {
        super.afterPrepareContainer2EAP7(params, container);
        JMSOperations jmsOperations = container.getJmsOperations();

        setJournalDirectories(jmsOperations, HornetQTestCase.JOURNAL_DIRECTORY_B);
        setJournalDirectories(jmsOperations, BACKUP_SERVER_NAME, HornetQTestCase.JOURNAL_DIRECTORY_A);

        jmsOperations.close();
    }

    @Override
    protected void afterPrepare(Map<String, Object> params) throws Exception {
        Constants.CLUSTER_TYPE clusterType = Constants.CLUSTER_TYPE.valueOf(PrepareUtils.getString(params, PrepareParams.CLUSTER_TYPE));

        if (clusterType != Constants.CLUSTER_TYPE.STATIC_CONNECTORS) {
            return;
        }

        Container container1 = getContainer(params, 1);
        Container container2 = getContainer(params, 2);

        prepareStaticConnectors(params, container1, container2);
        prepareStaticConnectors(params, container2, container1);
    }

    private void setJournalDirectories(JMSOperations jmsOperations, String journalDirectory) {
        setJournalDirectories(jmsOperations, "default", journalDirectory);
    }

    private void setJournalDirectories(JMSOperations jmsOperations, String serverName, String journalDirectory) {
        jmsOperations.setBindingsDirectory(serverName, journalDirectory);
        jmsOperations.setPagingDirectory(serverName, journalDirectory);
        jmsOperations.setLargeMessagesDirectory(serverName, journalDirectory);
        jmsOperations.setJournalDirectory(serverName, journalDirectory);
    }

    protected void prepareBackupEAP7(Map<String, Object> params, Container container) throws Exception {
        int socketBindingPort = Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7;

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addMessagingSubsystem(BACKUP_SERVER_NAME);

        jmsOperations.createSocketBinding(MESSAGING_SOCKET_BINDING_NAME_BACKUP, socketBindingPort);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, CONNECTOR_NAME, MESSAGING_SOCKET_BINDING_NAME_BACKUP, null);
        jmsOperations.createInVmConnector(BACKUP_SERVER_NAME, INVM_CONNECTOR_NAME, 0, null);
        jmsOperations.createRemoteAcceptor(BACKUP_SERVER_NAME, ACCEPTOR_NAME, MESSAGING_SOCKET_BINDING_NAME_BACKUP, null);

        jmsOperations.setBroadCastGroup(BACKUP_SERVER_NAME, BROADCAST_GROUP_NAME, null, JGROUPS_CHANNEL, 1000, CONNECTOR_NAME);
        jmsOperations.setDiscoveryGroup(BACKUP_SERVER_NAME, DISCOVERY_GROUP_NAME, 1000, null, JGROUPS_CHANNEL);
        jmsOperations.setClusterConnections(BACKUP_SERVER_NAME, CLUSTER_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000, true, CONNECTOR_NAME);
        jmsOperations.disableSecurity(BACKUP_SERVER_NAME);

        jmsOperations.setPersistenceEnabled(BACKUP_SERVER_NAME, true);

        prepareMisc(params, jmsOperations, BACKUP_SERVER_NAME);

        prepareAddressSettings(params, jmsOperations, BACKUP_SERVER_NAME);

        prepareSecurity(params, container, BACKUP_SERVER_NAME);

        jmsOperations.addHAPolicySharedStoreSlave(BACKUP_SERVER_NAME, true, 5000, true, true, false, null, null, null, null);

        // set ha also for hornetq-ra
        jmsOperations.setNodeIdentifier(String.valueOf(System.currentTimeMillis()).hashCode());

        jmsOperations.close();
    }

    protected void prepareBackupEAP6(Map<String, Object> params, Container container) throws Exception {
        String socketBindingName = "messaging-backup";
        int socketBindingPort = Constants.PORT_HORNETQ_BACKUP_DEFAULT_EAP6;
        String messagingGroupSocketBindingName = "messaging-group";

        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.addMessagingSubsystem(BACKUP_SERVER_NAME);

        jmsOperations.setClustered(BACKUP_SERVER_NAME, true);
        jmsOperations.setPersistenceEnabled(BACKUP_SERVER_NAME, true);
        jmsOperations.setBackup(BACKUP_SERVER_NAME, true);

        prepareSecurity(params, container, BACKUP_SERVER_NAME);

        prepareMisc(params, jmsOperations, BACKUP_SERVER_NAME);

        prepareAddressSettings(params, jmsOperations, BACKUP_SERVER_NAME);

        jmsOperations.setSharedStore(BACKUP_SERVER_NAME, true);
        jmsOperations.setFailoverOnShutdown(true, BACKUP_SERVER_NAME);
        jmsOperations.setAllowFailback(BACKUP_SERVER_NAME, true);

        jmsOperations.createSocketBinding(socketBindingName, socketBindingPort);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, CONNECTOR_NAME, socketBindingName, null);
        jmsOperations.createInVmConnector(BACKUP_SERVER_NAME, INVM_CONNECTOR_NAME, 0, null);
        jmsOperations.createRemoteAcceptor(BACKUP_SERVER_NAME, ACCEPTOR_NAME, socketBindingName, null);

        jmsOperations.setBroadCastGroup(BACKUP_SERVER_NAME, BROADCAST_GROUP_NAME, messagingGroupSocketBindingName, 2000, CONNECTOR_NAME, "");
        jmsOperations.setDiscoveryGroup(BACKUP_SERVER_NAME, DISCOVERY_GROUP_NAME, messagingGroupSocketBindingName, 10000);
        jmsOperations.setClusterConnections(BACKUP_SERVER_NAME, CLUSTER_NAME, "jms", DISCOVERY_GROUP_NAME, false, 1, 1000, true, CONNECTOR_NAME);

        jmsOperations.close();
    }

    private void prepareStaticConnectors(Map<String, Object> params, Container container, Container to) {
        JMSOperations jmsOperations = container.getJmsOperations();

        jmsOperations.removeBroadcastGroup(BROADCAST_GROUP_NAME);
        jmsOperations.removeBroadcastGroup(BACKUP_SERVER_NAME, BROADCAST_GROUP_NAME);

        jmsOperations.removeDiscoveryGroup(DISCOVERY_GROUP_NAME);
        jmsOperations.removeDiscoveryGroup(BACKUP_SERVER_NAME, DISCOVERY_GROUP_NAME);

        jmsOperations.addRemoteSocketBinding(STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, to.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + to.getPortOffset());
        jmsOperations.addRemoteSocketBinding(STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, to.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_BACKUP_EAP7 + to.getPortOffset());

        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, MESSAGING_SOCKET_BINDING_NAME_BACKUP, null);
        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, null);
        jmsOperations.createRemoteConnector(STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, null);

        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, MESSAGING_SOCKET_BINDING_NAME, null);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_LIVE, null);
        jmsOperations.createRemoteConnector(BACKUP_SERVER_NAME, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP, STATIC_CLUSTER_SOCKET_BINDING_NAME_REMOTE_BACKUP, null);


        jmsOperations.removeClusteringGroup(CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(SERVER_NAME, CLUSTER_NAME, "jms", false, 1, 1000, true, CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_BACKUP, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.removeClusteringGroup(BACKUP_SERVER_NAME, CLUSTER_NAME);
        jmsOperations.setStaticClusterConnections(BACKUP_SERVER_NAME, CLUSTER_NAME, "jms", false, 1, 1000, true, CONNECTOR_NAME, STATIC_CLUSTER_CONNECTOR_NAME_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_LIVE, STATIC_CLUSTER_CONNECTOR_NAME_REMOTE_BACKUP);

        jmsOperations.close();
    }
}
