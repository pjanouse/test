package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MdbMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithoutDelays;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueWithOutQueueLookups;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.HighCPUUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ RESOURCE ADAPTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
public class RemoteJcaLoadTestBase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaLoadTestBase.class);
    protected static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    protected final Archive mdb1 = getMdb1();
    protected final Archive lodhLikemdb = getLodhLikeMdb();

    protected String messagingGroupSocketBindingName = "messaging-group";

    // queue to send messages in
    static String dlqQueueName = "DLQ";
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    protected MdbMessageVerifier messageVerifier = new MdbMessageVerifier();

    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    public Archive getMdb1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaninerWithoutDelays.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getLodhLikeMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "lodhLikemdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueWithOutQueueLookups.class, MessageUtils.class);
        if (container(2).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        } else {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis \n"), "MANIFEST.MF");
        }
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    public void prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopologyEAP6(connectorType);
        } else {
            prepareRemoteJcaTopologyEAP7(connectorType);
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareJmsServerEAP6(container(1), connectorType, container(1), container(3));
        prepareMdbServerEAP6(container(2), connectorType, container(1), container(3));

        prepareJmsServerEAP6(container(3), connectorType, container(1), container(3));
        prepareMdbServerEAP6(container(4), connectorType, container(1), container(3));

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareJmsServerEAP7(container(1), connectorType, container(1), container(3));
        prepareMdbServerEAP7(container(2), connectorType, container(1), container(3));

        prepareJmsServerEAP7(container(3), connectorType, container(1), container(3));
        prepareMdbServerEAP7(container(4), connectorType, container(1), container(3));

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP6(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        jmsAdminOperations.disableSecurity();
        String socketBindingPrefix = "socket-binding-to-";
        String connectorPrefix = "connector-to-";
        switch (connectorType) {
            case NETTY_BIO:

                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(), remoteContainer.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                List<String> staticConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    if (!container.getName().equals(remoteContainer.getName())) {
                        String staticBIOConnectorName = connectorPrefix + remoteContainer.getName();
                        jmsAdminOperations.createRemoteConnector(staticBIOConnectorName, socketBindingPrefix + remoteContainer.getName(), null);
                        staticConnectorsNames.add(staticBIOConnectorName);
                    }
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", false, 1, 1000, true, connectorName,
                        staticConnectorsNames.toArray(new String[staticConnectorsNames.size()]));
                break;
            case NETTY_NIO:

                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(), remoteContainer.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                List<String> staticNIOConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    if (!container.getName().equals(remoteContainer.getName())) {
                        String staticConnectorName = connectorPrefix + remoteContainer.getName();
                        Map<String, String> connectorParams = new HashMap<String, String>();
                        connectorParams.put("use-nio", "true");
                        connectorParams.put("use-nio-global-worker-pool", "true");
                        jmsAdminOperations.createRemoteConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), connectorParams);
                        staticNIOConnectorsNames.add(staticConnectorName);
                    }
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", false, 1, 1000, true, connectorName,
                        staticNIOConnectorsNames.toArray(new String[staticNIOConnectorsNames.size()]));
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_DISCOVERY:
                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, udpJgroupsStackName, udpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_TCP:
                String tcpJgroupsStackName = "tcp";
                String tcpJgroupsChannelName = tcpJgroupsStackName;
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(tcpJgroupsStackName);
                jmsAdminOperations.addJGroupsStack(tcpJgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, tcpJgroupsStackName, tcpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, tcpJgroupsStackName, tcpJgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case HTTP_CONNECTOR:
                throw new RuntimeException("HTTP connector is not supported with EAP 6");
            default:
                throw new RuntimeException("Type of connector unknown for EAP 6");
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 60000, 2000, 10485760, "jms.queue.DLQ", "jms.queue.ExpiryQueue", 10);
        jmsAdminOperations.setTransactionTimeout(60000);
        jmsAdminOperations.createRemoteAcceptor("netty", "messaging", null);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);
        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String defaultNettySocketBindingName = "messaging";
        String defaultNettyAcceptorName = "netty-acceptor";
        String defaultNettyConnectorName = "netty-connector";
        String messagingGroupSocketBindingName = "messaging-group";
        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        String socketBindingPrefix = "socket-binding-to-";
        String connectorPrefix = "connector-to-";
        switch (connectorType) {
            case NETTY_BIO:
                throw new RuntimeException("BIO connectors are not supported with EAP 7");
            case NETTY_NIO:
                // create netty acceptor
                jmsAdminOperations.createSocketBinding(defaultNettySocketBindingName, Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7);
                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(),
                            Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + remoteContainer.getPortOffset());
                }
                jmsAdminOperations.close();

                container.restart();

                jmsAdminOperations = container.getJmsOperations();
                jmsAdminOperations.createRemoteAcceptor(defaultNettyAcceptorName, defaultNettySocketBindingName, null);
                jmsAdminOperations.createRemoteConnector(defaultNettyConnectorName, defaultNettySocketBindingName, null);
                List<String> staticNIOConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    String staticConnectorName = connectorPrefix + remoteContainer.getName();
                    jmsAdminOperations.createRemoteConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), null);
                    staticNIOConnectorsNames.add(staticConnectorName);
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND,
                        1, 1000, true, defaultNettyConnectorName, staticNIOConnectorsNames.toArray(new String[remoteContainers.length]));
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_DISCOVERY:
                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, udpJgroupsStackName, udpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_TCP:
                String tcpJgroupsStackName = "tcp";
                String tcpJgroupsChannelName = tcpJgroupsStackName;
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(tcpJgroupsStackName);
                jmsAdminOperations.addJGroupsStack(tcpJgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, tcpJgroupsStackName, tcpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, tcpJgroupsStackName, tcpJgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case HTTP_CONNECTOR:
                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(), remoteContainer.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                List<String> staticHttpConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    String staticConnectorName = connectorPrefix + remoteContainer.getName();
                    jmsAdminOperations.createHttpConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), null, "http-acceptor");
                    staticHttpConnectorsNames.add(staticConnectorName);
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND, 1, 1000, true, connectorName,
                        staticHttpConnectorsNames.toArray(new String[remoteContainers.length]));
                break;
            default:
                throw new RuntimeException("Type of connector unknown for EAP 6");
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 60000, 2000, 10485760, "jms.queue.DLQ", "jms.queue.ExpiryQueue", 10);
        jmsAdminOperations.setTransactionTimeout(60000);
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }
        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP6(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteSevers) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        setConnectorTypeForPooledConnectionFactoryEAP6(container, connectorType, remoteSevers);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    private void setConnectorTypeForPooledConnectionFactoryEAP6(Container container, Constants.CONNECTOR_TYPE connectorType, Container[] remoteContainers) {
        String remoteSocketBindingPrefix = "socket-binding-to-";
        String remoteConnectorNamePrefix = "connector-to-node-";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        switch (connectorType) {
            case NETTY_BIO:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), c.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                List<String> bioConnectorList = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.removeRemoteConnector(remoteConnectorNameForRemoteContainer);
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null);
                    bioConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP6, bioConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            case NETTY_NIO:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), c.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> nioConnectorList = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    Map<String, String> connectorParams = new HashMap<String, String>();
                    connectorParams.put("use-nio", "true");
                    connectorParams.put("use-nio-global-worker-pool", "true");
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), connectorParams);
                    nioConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP6, nioConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP6, discoveryGroupName);
                break;
            case JGROUPS_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP6, discoveryGroupName);
                break;
            case JGROUPS_TCP:
                String jgroupsStackName = "tcp";
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(jgroupsStackName);
                jmsAdminOperations.addJGroupsStack(jgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, jgroupsStackName, jgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP6, discoveryGroupName);
                break;
            case HTTP_CONNECTOR:
                throw new RuntimeException("HTTP connector type is not supported with EAP 6.");
            default:
                throw new RuntimeException("Type of connector unknown for EAP 6");
        }
        jmsAdminOperations.close();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteSevers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        setConnectorTypeForPooledConnectionFactoryEAP7(container, connectorType, remoteSevers);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        jmsAdminOperations.close();
        container.stop();
    }

    private void setConnectorTypeForPooledConnectionFactoryEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {
        String remoteSocketBindingPrefix = "socket-binding-to-";
        String remoteConnectorNamePrefix = "connector-to-node-";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        switch (connectorType) {
            case NETTY_BIO:
                throw new RuntimeException("NETTY_BIO connector type is not supported with EAP 7.");
            case NETTY_NIO:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + c.getPortOffset());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> nioConnectorList = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null);
                    nioConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, nioConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case JGROUPS_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);

                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case JGROUPS_TCP:
                String jgroupsStackName = "tcp";
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(jgroupsStackName);
                jmsAdminOperations.addJGroupsStack(jgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, jgroupsStackName, jgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case HTTP_CONNECTOR:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), c.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> httpConnectors = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteHttpConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.createHttpConnector(remoteHttpConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null, "http-acceptor");
                    httpConnectors.add(remoteHttpConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, httpConnectors);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            default:
                throw new RuntimeException("Type of connector unknown for EAP 7");
        }
        jmsAdminOperations.close();

    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }
    public void restartServers() {

        container(2).stop();
        container(4).stop();
        container(3).stop();
        container(1).stop();
        container(3).start();
        container(1).start();
        container(2).start();
        container(4).start();

    }

}

