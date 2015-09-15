package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.rmi.RemoteException;


/**
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter NETWORK FAILURE OF HORNETQ CORE BRIDGES - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/eap-60-hornetq-functional-bridge-network-failure/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 * @tpSince EAP6
 * @tpTestCaseDetails This test case is implemented by NetworkFailuresHornetQCoreBridges and
 * NetworkFailuresHornetQCoreBridgesWithJGroups implementation details are specified there.
 */
@RunWith(Arquillian.class)
public abstract class NetworkFailuresBridgesAbstract extends HornetQTestCase {

    // this is just maximum limit for producer - producer is stopped once Failure test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;

    // Logger
    protected static final Logger log = Logger.getLogger(NetworkFailuresBridgesAbstract.class);

    protected String hornetqInQueueName = "InQueue";
    protected String relativeJndiInQueueName = "queue/InQueue";

    protected String broadcastGroupAddressClusterA = "233.1.2.1";
    protected int broadcastGroupPortClusterA = 9876;

    protected String broadcastGroupAddressClusterB = "233.1.2.2";
    protected int broadcastGroupPortClusterB = 9876;

    protected String discoveryGroupAddressClusterA = "233.1.2.3";
    protected int discoveryGroupPortServerClusterA = 9876;

    protected String discoveryGroupAddressClusterB = "233.1.2.4";
    protected int discoveryGroupPortServerClusterB = 9876;

    protected int proxy12port = 43812;
    protected int proxy21port = 43821;

    protected ControllableProxy proxy1;
    protected ControllableProxy proxy2;
    protected MulticastProxy mp12;
    protected MulticastProxy mp21;


    public abstract void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails, boolean staysDisconnected) throws Exception;

    public abstract void prepareServers(int reconnectAttempts);

    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
        try {
            if (proxy1 != null) proxy1.stop();
        } catch (Exception ex)  {
            log.error("Proxy1 cannot be stopped: ", ex);
        }
        try {
            if (proxy2 != null) proxy2.stop();
        } catch (Exception ex)  {
            log.error("Proxy2 cannot be stopped: ", ex);
        }
        if (mp12 != null) mp12.setStop(true);
        if (mp21 != null) mp21.setStop(true);

    }


    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is unlimited.
     * Message grouping is disabled. Producer starts sending normal and large messages on node A, consumer consumes
     * these messages on node B. During this time is executed twice network failure sequence (network goes down
     * and then up). After that, producer stops and receiver receives all rest messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNetworkFailureMixMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is unlimited.
     * Message grouping is disabled. Producer starts sending normal messages on node A, consumer consumes
     * these messages on node B. During this time is executed twice network failure sequence (network goes down
     * and then up). After that, producer stops and receiver receives all rest messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNetworkFailureSmallMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is unlimited.
     * Message grouping is disabled. Producer starts sending large messages on node A, consumer consumes
     * these messages on node B. During this time is executed twice network failure sequence (network goes down
     * and then up). After that, producer stops and receiver receives all rest messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureLargeMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }


    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is 1.
     * Message grouping is disabled. Producer starts sending normal and large
     * messages on node A, consumer consumes these messages on node B. During this time is executed twice network
     * failure sequence (network goes down and then up). After that, producer stops and receiver receives all rest
     * messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureMixMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }


    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is 1.
     * Message grouping is disabled. Producer starts sending normal
     * messages on node A, consumer consumes these messages on node B. During this time is executed twice network
     * failure sequence (network goes down and then up). After that, producer stops and receiver receives all rest
     * messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 2, 2);
    }

    /**
     * @tpTestDetails Cluster with node A and B is started. Number of reconnect attempts for cluster connection is 1.
     * Message grouping is disabled. Producer starts sending large
     * messages on node A, consumer consumes these messages on node B. During this time is executed twice network
     * failure sequence (network goes down and then up). After that, producer stops and receiver receives all rest
     * messages.
     * @tpPassCrit number of sent messages and received messages have to match
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 1, 2);
    }


    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureMixMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureSmallMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureLargeMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureMixMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }


    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder   instance of the message builder
     * @param timeBetweenFails time between fails
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, -1, 2);
    }

    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, reconnectAttempts, numberOfFails, true);
    }








    protected int getNumberOfNodesInCluster(Container container) {
        boolean isContainerStarted = CheckServerAvailableUtils.checkThatServerIsReallyUp(
                container.getHostname(), container.getHornetqPort());

        int numberOfNodesInCluster = -1;
        if (isContainerStarted) {
            JMSOperations jmsAdminOperations = container.getJmsOperations();
            numberOfNodesInCluster = jmsAdminOperations.getNumberOfNodesInCluster();
            jmsAdminOperations.close();

        }
        return numberOfNodesInCluster;
    }


    /**
     * Executes network failures.
     * <p/>
     * 1 = 5 short network failures (10s gap)
     * 2 = 5 network failures (30s gap)
     * 3 = 5 network failures (100s gap)
     * 4 = 3 network failures (300s gap)
     *
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    protected void executeNetworkFails(long timeBetweenFails, int numberOfFails)
            throws Exception {

        for (int i = 0; i < numberOfFails; i++) {



            stopProxies();

            Thread.sleep(timeBetweenFails);

            startProxies();

            Thread.sleep(timeBetweenFails);

        }
    }

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

        if (mp12 == null){
            mp12 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                    discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
            mp12.setIpAddressOfInterface(container(1).getHostname());
            mp12.start();

        }
        if (mp21 == null){
            mp21 = new MulticastProxy(broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                    discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
            mp21.setIpAddressOfInterface(container(2).getHostname());
            mp21.start();
        }
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

        if (mp12 != null)   {
            mp12.setStop(true);
            mp12 = null;
        }
        if (mp21 != null)   {
            mp21.setStop(true);
            mp21 = null;
        }
        log.info("All proxies stopped.");
    }

    @After
    public void after() {
        if (proxy1 != null) {
            try {
                proxy1.stop();
            } catch (RemoteException e) {
                log.error("Proxy could not be stopped.", e);
            }
        }
        if (proxy2 != null) {
            try {
                proxy2.stop();
            } catch (RemoteException e) {
                log.error("Proxy could not be stopped.", e);
            }
        }
        if (mp21 != null) {
            mp21.setStop(true);
        }
        if (mp12 != null) {
            mp12.setStop(true);
        }
    }



}
