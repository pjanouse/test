package org.jboss.qa.hornetq.test.bridges;

import java.io.File;
import java.io.IOException;

import org.jboss.logging.Logger;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.test.prepares.specific.NetworkFailuresCoreBridgesPrepare;
import org.jboss.qa.hornetq.test.prepares.specific.NetworkFailuresCoreBridgesWithJGroupsPrepare;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;


/**
 * @author mnovak@redhat.com
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter Network failure of core bridges - test scenarios
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/eap-60-hornetq-functional-bridge-network-failure/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails This test has the same test scenarios as NetworkFailuresBridges
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
@Prepare(value = "NetworkFailuresCoreBridgesWithJGroupsPrepare")
public class NetworkFailuresHornetQCoreBridgesWithJGroups extends NetworkFailuresHornetQCoreBridges {

    private static final Logger log = Logger.getLogger(NetworkFailuresHornetQCoreBridgesWithJGroups.class);

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
            proxy1 = new SimpleProxyServer(container(2).getHostname(), container(2).getHornetqPort(), NetworkFailuresCoreBridgesPrepare.PROXY_12_PORT);
            proxy1.start();
        }
        if (proxy2 == null) {
            proxy2 = new SimpleProxyServer(container(1).getHostname(), container(1).getHornetqPort(), NetworkFailuresCoreBridgesPrepare.PROXY_21_PORT);
            proxy2.start();
        }

        startGosshipRouter(NetworkFailuresCoreBridgesWithJGroupsPrepare.GOSSHIP_ROUTER_ADDRESS, NetworkFailuresCoreBridgesWithJGroupsPrepare.GOSSHIP_ROUTER_PORT);

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
}
