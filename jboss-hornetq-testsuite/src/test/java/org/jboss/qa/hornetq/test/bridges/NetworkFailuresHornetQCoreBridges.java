package org.jboss.qa.hornetq.test.bridges;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.ControllableProxy;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.MulticastProxy;
import org.jboss.qa.tools.SimpleProxyServer;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B
 * repeatedly.
 * <p/>
 * Topology - container1 - source server container2 - target server container3 -
 * source server container4 - target server
 * <p/>
 * IMPORTANT:
 * There are two types of proxies. Multicast proxy and TCP/UDP proxy.
 * <p/>
 * TCP/UDP proxy listen on localhost:localport and send packets received to other specified remoteHost:remotePort.
 * <p/>
 * Multicast proxy receives multicast on multicast group (for example 233.1.2.3) and sends to other multicast group
 * (for example 233.1.2.4)
 * <p/>
 * Tests are using proxies in following way:
 * 1. Set broadcast group to mulsticast connector to sourceMulticastAddress where multicast proxy listens
 * 2. Set discovery group to listen multicast on destinationMulticastAddress to where multicast proxy sends connectors
 * from broadcast groups
 * 3. Broadcasted connectors from server A points to proxy to server A so each server in cluster connects to server A by connecting
 * it proxy resending messages to server A
 * <p/>
 * STEPS 1. AND 2. ARE THE SAME FOR ALL NODES IN ONE CLUSTER. THERE IS ONE MULTICAST PROXY PER CLUSTER.
 * STEP 3 IS SPECIFIC FOR EACH NODE IN CLUSTER. THERE IS ONE TCP/UDP PROXY PER NODE.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class NetworkFailuresHornetQCoreBridges extends HornetQTestCase {

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    // Logger
    private static final Logger log = Logger.getLogger(NetworkFailuresHornetQCoreBridges.class);
    private String hornetqInQueueName = "InQueue";
    private String relativeJndiInQueueName = "queue/InQueue";

    private String broadcastGroupAddressClusterA = "233.1.2.1";
    private int broadcastGroupPortClusterA = 9876;

    private String broadcastGroupAddressClusterB = "233.1.2.2";
    private int broadcastGroupPortClusterB = 9876;

    private String discoveryGroupAddressClusterA = "233.1.2.3";
    private int discoveryGroupPortServerClusterA = 9876;

    private String discoveryGroupAddressClusterB = "233.1.2.4";
    private int discoveryGroupPortServerClusterB = 9876;

    private int proxy12port = 43812;
    private int proxy21port = 43821;

    ControllableProxy proxy1;
    ControllableProxy proxy2;
    MulticastProxy mp1;
    MulticastProxy mp2;

    /**
     * Stops all servers
     */
    @Before
    public void stopAllServers() throws Exception {
//        prepareServers();
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);
        if (proxy1 != null) proxy1.stop();
        if (proxy2 != null) proxy2.stop();
        if (mp1 != null) mp1.setStop(true);
        if (mp2 != null) mp2.setStop(true);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverMixMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverSmallMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverLargeMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverMixMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverMixMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverSmallMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testNetworkFailoverLargeMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverMixMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverSmallMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverLargeMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(1024, 1024), -1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverMixMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverMixMessages5recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverSmallMessages5recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpAfterTest
    public void testShortNetworkFailoverLargeMessages5recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(1024, 1024), 5, 2);
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

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails) throws Exception {

        prepareServers(reconnectAttempts);

        controller.start(CONTAINER1); // A1
        controller.start(CONTAINER2); // B1
        Thread.sleep(60000);
        // A1 producer
        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, relativeJndiInQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        // B1 consumer
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER2_IP, 4447, relativeJndiInQueueName, 2 * timeBetweenFails, 10, 10);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(5 * 1000);

        executeNetworkFails(timeBetweenFails, numberOfFails);

        Thread.sleep(5 * 1000);

        producer1.stopSending();
        producer1.join();
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getCounter());
        log.info("Number of received messages: " + receiver1.getCount());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

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
    private void executeNetworkFails(long timeBetweenFails, int numberOfFails)
            throws Exception {

        log.info("Start all proxies.");
        proxy1 = new SimpleProxyServer(CONTAINER2_IP, 5445, proxy12port);
        proxy2 = new SimpleProxyServer(CONTAINER1_IP, 5445, proxy21port);
        proxy1.start();
        proxy2.start();
        mp1 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
        mp2 = new MulticastProxy(broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
        mp1.start();
        mp2.start();

        log.info("All proxies started.");

        for (int i = 0; i < numberOfFails; i++) {
            Thread.sleep(timeBetweenFails);
            log.info("Stop all proxies.");
            proxy1.stop();
            proxy2.stop();

            mp1.setStop(true);
            mp2.setStop(true);
            log.info("All proxies stopped.");

            Thread.sleep(timeBetweenFails);
            log.info("Start all proxies.");
            proxy1.start();
            proxy2.start();
            mp1 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                    discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
            mp2 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                    discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
            mp1.start();
            mp2.start();
            log.info("All proxies started.");

        }
    }

    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServers(int reconnectAttempts) {

        prepareClusterServer(CONTAINER1, CONTAINER1_IP, proxy21port, reconnectAttempts, broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
        prepareClusterServer(CONTAINER2, CONTAINER2_IP, proxy12port, reconnectAttempts, broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);


    }

    /**
     * Prepare servers.
     *
     * @param containerName         container name
     * @param bindingAddress        bind address
     * @param proxyPortIn           proxy port for connector where to connect to proxy directing to this server,every can connect to this server through proxy on 127.0.0.1:proxyPortIn
     * @param reconnectAttempts     number of reconnects for cluster-connections
     * @param discoveryGroupAddress discovery udp address
     * @param discoveryGroupPort    discovery udp port
     */
    private void prepareClusterServer(String containerName, String bindingAddress,
                                      int proxyPortIn, int reconnectAttempts, String broadcastGroupAddress,
                                      int broadcastGroupPort, String discoveryGroupAddress, int discoveryGroupPort) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectionFactoryName = "RemoteConnectionFactory";

        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingNameForDiscovery = messagingGroupSocketBindingName + "-" + containerName;

        controller.start(containerName);
        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        // every can connect to this server through proxy on 127.0.0.1:proxyPortIn
        jmsAdminOperations.addRemoteSocketBinding("binding-connect-to-this-server-through-remote-proxy", "127.0.0.1", proxyPortIn);
        jmsAdminOperations.createRemoteConnector("connector-to-proxy-directing-to-this-server", "binding-connect-to-this-server-through-remote-proxy", null);

        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, broadcastGroupAddress);
        jmsAdminOperations.setMulticastPortOnSocketBinding(messagingGroupSocketBindingName, broadcastGroupPort);

        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, "connector-to-proxy-directing-to-this-server", "");

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingNameForDiscovery, "public", discoveryGroupAddress, discoveryGroupPort);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingNameForDiscovery, 10000);
        jmsAdminOperations.setIdCacheSize(20000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, "connector-to-proxy-directing-to-this-server");
        jmsAdminOperations.setReconnectAttemptsForClusterConnection(clusterGroupName, reconnectAttempts);

        jmsAdminOperations.createQueue(hornetqInQueueName, relativeJndiInQueueName, true);

//        jmsAdminOperations.setConnectionTtlOverride("default", 8000);

        jmsAdminOperations.close();

        controller.stop(containerName);

    }


}
