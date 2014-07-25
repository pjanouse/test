package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;

import java.io.File;
import java.io.IOException;

/**
 *
 * Tests network failures of HornetQ core bridges which are using JGroups to broadcast and accept connectors.
 *
 * Both of the servers in cluster are configured to use JGroups to create HornetQ cluster.
 * Then there is started gosship router which is used to connect and disconnect cluster. This is not enough because
 * once there must be destroyed also HQ core cluster connection. For this purpose all connectors points to TCP proxy
 * which are used to destroy those connections as well.
 *
 * @author Miroslav Novak (mnovak@redhat.com)
 */
public class NetworkFailuresHornetQCoreBridgesWithJGroups extends NetworkFailuresHornetQCoreBridges{

    private static final Logger log = Logger.getLogger(NetworkFailuresHornetQCoreBridgesWithJGroups.class);

    public static String GOSSHIP_ROUTER_ADDRESS = "0.0.0.0";
    public static int GOSSHIP_ROUTER_PORT = 12001;

    Process gosshipRouterProcess = null;


    /**
     * Executes network failures.
     *
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     * @param numberOfFails number of fails
     */
    protected void executeNetworkFails(long timeBetweenFails, int numberOfFails)
            throws Exception {

        startProxies();

        for (int i = 0; i < numberOfFails; i++) {

            Thread.sleep(timeBetweenFails);

            stopProxies();

            Thread.sleep(timeBetweenFails);

            startProxies();

        }
    }

    /**
     * Starts gosship router
     *
     * @param gosshipAddress address
     * @param gosshipPort port
     */
    private void startGosshipRouter(String gosshipAddress, int gosshipPort) throws IOException {

        if (gosshipRouterProcess != null)    {
            log.info("Gosship router already started.");
            return;
        }

        StringBuilder command = new StringBuilder();
        command.append("java -cp ").append(JBOSS_HOME_1).append(File.separator).append("bin").append(File.separator).append("client")
                .append(File.separator).append("jboss-client.jar").append("  org.jgroups.stack.GossipRouter ").append("-port ").append(gosshipPort)
                .append(" -bindaddress ").append(gosshipAddress);

        gosshipRouterProcess = Runtime.getRuntime().exec(command.toString());
        log.info("Gosship router started. Command: " + command);

    }

    /**
     * Starts gosship router
     *
     */
    private void stopGosshipRouter() throws IOException {

        if (gosshipRouterProcess == null)   {
            log.warn("Gosship router is null. It must be started first!");
        }
        if (gosshipRouterProcess != null) {
            gosshipRouterProcess.destroy();
        }
        gosshipRouterProcess = null;

    }

    protected void startProxies() throws Exception {

        log.info("Start all proxies.");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), proxy12port);
            proxy1.start();
        }
        if (proxy2 == null) {
            proxy2 = new SimpleProxyServer(getHostname(CONTAINER1), getHornetqPort(CONTAINER1), proxy21port);
            proxy2.start();
        }

        startGosshipRouter(GOSSHIP_ROUTER_ADDRESS, GOSSHIP_ROUTER_PORT);

        log.info("All proxies started.");



    }

    protected void stopProxies() throws Exception {
        log.info("Stop all proxies.");
        if (proxy1 != null) {
            proxy1.stop();
            proxy1 = null;
        }
        if (proxy2 != null) {
            proxy2.stop();
            proxy2 = null;
        }

        stopGosshipRouter();

        log.info("All proxies stopped.");
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    protected void prepareServer(String containerName, int proxyPortIn, int reconnectAttempts) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectorNameForClients = "netty-throughput";
        String connectionFactoryName = "RemoteConnectionFactory";
        String connectionFactoryJndiName = "java:jboss/exported/jms/" + connectionFactoryName;

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);

        // every can connect to this server through proxy on 127.0.0.1:proxyPortIn
        jmsAdminOperations.removeRemoteConnector(connectorName);
        jmsAdminOperations.addRemoteSocketBinding("binding-connect-to-this-server-through-remote-proxy", "127.0.0.1", proxyPortIn);
        jmsAdminOperations.createRemoteConnector(connectorName, "binding-connect-to-this-server-through-remote-proxy", null);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "udp", "udp", 2000, connectorName);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, "udp", "udp");

        // update UDP stack to use
//        <transport type="TUNNEL" shared="false">
//        <property name="enable_bundling">false</property>
//        <property name="gossip_router_hosts">0.0.0.0[12001]</property>
//        </transport>
        jmsAdminOperations.addTransportToJGroupsStack("udp", "TUNNEL", GOSSHIP_ROUTER_ADDRESS, GOSSHIP_ROUTER_PORT, false);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.setReconnectAttemptsForClusterConnection(clusterGroupName, reconnectAttempts);

        jmsAdminOperations.removeConnectionFactory(connectionFactoryName);
        jmsAdminOperations.createConnectionFactory(connectionFactoryName, connectionFactoryJndiName, connectorNameForClients);
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);


        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);

        jmsAdminOperations.createQueue(hornetqInQueueName, relativeJndiInQueueName, true);

        jmsAdminOperations.close();
        controller.stop(containerName);

    }


    /**
     * Prepares servers.
     */
    public void prepareServers(int reconnectAttempts) {

        prepareServer(CONTAINER1, proxy21port, reconnectAttempts);
        prepareServer(CONTAINER2, proxy12port, reconnectAttempts);
    }
}
