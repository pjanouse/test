package org.jboss.qa.hornetq.test.failover;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests failover of remote JCA and replicated journal.
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter FAILOVER OF HORNETQ RESOURCE ADAPTER WITH SHARED STORE AND REPLICATED JOURNAL IN DEDICATED TOPOLOGY - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-ha-failover-dedicated-mdb-replicated-journal/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19048/activemq-artemis-high-availability#testcases
 * @tpTestCaseDetails Tests failover of remote JCA and replicated journal. There
 * are two servers in dedicated HA topology and MDB deployed on another server.
 * Live server is shutdown/killed and correct failover/failback is tested. Live
 * and backup servers use replicated journal.
 * This test case implements the same tests as DedicatedFailoverTestCaseWithMdb
 */
@RunWith(Arquillian.class)
public class ReplicatedDedicatedFailoverTestWithMdb extends DedicatedFailoverTestCaseWithMdb {

    private static final Logger logger = Logger.getLogger(ReplicatedDedicatedFailoverTestWithMdb.class);

    String clusterConnectionName = "my-cluster";
    String replicationGroupName = "replication-group-name-1";


    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopology() throws Exception {
        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareLiveServer(container(1));
            prepareBackupServer(container(2));
            prepareMdbServerEAP6(container(3), container(1), container(2));
            copyApplicationPropertiesFiles();

        } else {
            prepareLiveServerEAP7(container(1));
            prepareBackupServerEAP7(container(2));
            prepareMdbServerEAP7(container(3), container(1), container(2));
            copyApplicationPropertiesFiles();
        }
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareLiveServer(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.kill();
        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setFailoverOnShutdown(true);

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setBackup(false);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        jmsAdminOperations.setClusterUserPassword("heslo");
        jmsAdminOperations.removeAddressSettings("#");
        setAddressSettings(jmsAdminOperations);


        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareBackupServer(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setBackupGroupName("firstPair");
        jmsAdminOperations.setCheckForLiveServer(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(false);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.setSecurityEnabled(true);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        jmsAdminOperations.setClusterUserPassword("heslo");

        jmsAdminOperations.removeAddressSettings("#");
        setAddressSettings(jmsAdminOperations);

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container.getServerHome() + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error("Error during copy.", e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container        The container - defined in arquillian.xml
     */
    protected void prepareLiveServerEAP7(Container container) {

        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.addHAPolicyReplicationMaster(true, clusterConnectionName, replicationGroupName);

        jmsAdminOperations.close();

        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareMdbServerEAP7(Container container, Container containerLive, Container containerBackup) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String remoteConnectorName = "remote-http-connector";
        String remoteConnectorNameBackup = "remote-http-connector-backup";
        String pooledConnectionFactoryName = "ra-connection-factory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
//        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
//        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
//        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", containerLive.getHostname(), containerLive.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote-backup", containerBackup.getHostname(), containerBackup.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorNameBackup, "messaging-remote-backup", null);

        List<String> connectorList = new ArrayList<String>();
        connectorList.add(remoteConnectorName);
//        connectorList.add(remoteConnectorNameBackup);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, connectorList);
        jmsAdminOperations.setHaForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.setBlockOnAckForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.setRetryIntervalForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, 1.0);
        jmsAdminOperations.setReconnectAttemptsForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, -1);
        jmsAdminOperations.createPooledConnectionFactory(pooledConnectionFactoryName, "java:/jmsXALocal", connectorName);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory(pooledConnectionFactoryName, connectorName);
        jmsAdminOperations.setNodeIdentifier(1231);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareBackupServerEAP7(Container container) {

        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.createQueue("default", inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue("default", outQueueName, outQueueJndiName, true);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 10 * 1024);
        jmsAdminOperations.addHAPolicyReplicationSlave(true, clusterConnectionName, 5000, replicationGroupName, 60, true, false, null, null, null, null);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
    }
}
