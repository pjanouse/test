package org.jboss.qa.artemis.test.bridges;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;


/**
 * @author mnovak@redhat.com
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter Network failure of core bridges - test scenarios
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/eap-60-hornetq-functional-bridge-network-failure/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails This test has the same test scenarios as NetworkFailuresHornetQCoreBridges
 * <br/>
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B
 * repeatedly.
 * <br/>
 * Topology - container1 - source server container2 - target server container3 -
 * source server container4 - target server
 * <br/>
 * IMPORTANT:
 * There is only one type of proxy : TCP
 * <br/>
 * TCP/UDP proxy listen on localhost:localport and send packets received to other specified remoteHost:remotePort.
 * (for example 233.1.2.4)
 * <br/>
 * Tests are using proxies in following way:
 * Broadcasted connectors from server A points to proxy to server A so each server in cluster connects to server A by connecting
 * it proxy resending messages to server A
 * <br/>
 * For JGroups is network failure simulated by gossip router, which can be started/stopped according to need.
 * <br/>
 * STEPS 1. AND 2. ARE THE SAME FOR ALL NODES IN ONE CLUSTER. THERE are 2^(n-1) MULTICAST PROXIES PER per n nodes.
 * STEP 3 IS SPECIFIC FOR EACH NODE IN CLUSTER. THERE IS ONE TCP/UDP PROXY PER NODE.er
 * <br/>
 * We use two configurations of failsequence: long - network stays disconnected for 2 minutes, short - network stays
 * disconnected for 20 seconds
 *
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
    @Override
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
        command.append("java -cp ").append(container(1).getServerHome()).append(File.separator).append("bin").append(File.separator).append("client")
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

    @Override
    protected void startProxies() throws Exception {

        log.info("Start all proxies.");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(container(2).getHostname(), container(2).getHornetqPort(), proxy12port);
            proxy1.start();
        }
        if (proxy2 == null) {
            proxy2 = new SimpleProxyServer(container(1).getHostname(), container(1).getHornetqPort(), proxy21port);
            proxy2.start();
        }

        startGosshipRouter(GOSSHIP_ROUTER_ADDRESS, GOSSHIP_ROUTER_PORT);

        log.info("All proxies started.");



    }
    @Override
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
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareServer(Container container, int proxyPortIn, int reconnectAttempts) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "connector-to-proxy-directing-to-this-server";
        String connectorNameForClients = "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String connectionFactoryJndiName = "java:jboss/exported/jms/" + connectionFactoryName;

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);

        // every can connect to this server through proxy on 127.0.0.1:proxyPortIn
        jmsAdminOperations.removeHttpConnector(connectorName);

        jmsAdminOperations.addRemoteSocketBinding("binding-connect-to-this-server-through-remote-proxy", "127.0.0.1", proxyPortIn);
        jmsAdminOperations.createHttpConnector(connectorName, "binding-connect-to-this-server-through-remote-proxy", null);

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
        container.stop();
    }


    /**
     * Prepares servers.
     */
    @Override
    public void prepareServers(int reconnectAttempts) {

        prepareServer(container(1), proxy21port, reconnectAttempts);
        prepareServer(container(2), proxy12port, reconnectAttempts);
    }
}
