package org.jboss.qa.hornetq.test.bridges;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.MulticastProxy;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.rmi.RemoteException;
import java.util.Random;

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
    static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;

    // Logger
    private static final Logger log = Logger.getLogger(NetworkFailuresHornetQCoreBridges.class);

    protected String hornetqInQueueName = "InQueue";
    protected String relativeJndiInQueueName = "queue/InQueue";

    private String broadcastGroupAddressClusterA = "233.1.2.1";
    private int broadcastGroupPortClusterA = 9876;

    private String broadcastGroupAddressClusterB = "233.1.2.2";
    private int broadcastGroupPortClusterB = 9876;

    private String discoveryGroupAddressClusterA = "233.1.2.3";
    private int discoveryGroupPortServerClusterA = 9876;

    private String discoveryGroupAddressClusterB = "233.1.2.4";
    private int discoveryGroupPortServerClusterB = 9876;

    protected int proxy12port = 43812;
    protected int proxy21port = 43821;

    ControllableProxy proxy1;
    ControllableProxy proxy2;
    MulticastProxy mp12;
    MulticastProxy mp21;

    /**
     * Stops all servers
     */
    @Before
    public void stopAllServers() {
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);
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

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverMixMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNetworkFailoverSmallMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverLargeMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverMixMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverMixMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverSmallMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailoverLargeMessages5recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverMixMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverSmallMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverLargeMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverMixMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverMixMessages5recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailoverSmallMessages5recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
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

    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, reconnectAttempts, numberOfFails, true);
    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails, boolean staysDisconnected)
                throws Exception {

        prepareServers(reconnectAttempts);

        startProxies();

        controller.start(CONTAINER1); // A1
        controller.start(CONTAINER2); // B1

        Thread.sleep(5000);

        // A1 producer
        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), relativeJndiInQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        if (messageBuilder != null) {
            messageBuilder.setAddDuplicatedHeader(true);
            producer1.setMessageBuilder(messageBuilder);
        }
        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), CONTAINER2_IP, getJNDIPort(), relativeJndiInQueueName, (4 * timeBetweenFails) > 120000 ? (4 * timeBetweenFails):120000, 10, 10);
        receiver1.setTimeout(0);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(15 * 1000);

        executeNetworkFails(timeBetweenFails, numberOfFails);

        Thread.sleep(5 * 1000);

        producer1.stopSending();
        producer1.join();
        receiver1.setReceiveTimeOut(10000);
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        if (staysDisconnected)  {
            Assert.assertTrue("There must be more sent messages then received.",
                    producer1.getListOfSentMessages().size() > receiver1.getCount());
            stopServer(CONTAINER1);
            controller.start(CONTAINER1);
            ReceiverTransAck receiver2 = new ReceiverTransAck(getCurrentContainerForTest(), CONTAINER1_IP, getJNDIPort(), relativeJndiInQueueName, 10000, 10, 10);
            receiver2.start();
            receiver2.join();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        }

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

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

    protected void startProxies() throws Exception {

        log.info("Start all proxies.");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(CONTAINER2_IP, 5445, proxy12port);
            proxy1.start();
        }
        if (proxy2 == null) {
            proxy2 = new SimpleProxyServer(CONTAINER1_IP, 5445, proxy21port);
            proxy2.start();
        }

        if (mp12 == null) {
            mp12 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
            mp12.setIpAddressOfInterface(CONTAINER1_IP);
            mp12.start();

        }
        if (mp21 == null)   {
            mp21 = new MulticastProxy(broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
            mp21.setIpAddressOfInterface(CONTAINER2_IP);
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

    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServers(int reconnectAttempts) {

        prepareClusterServer(CONTAINER1, CONTAINER1_IP, proxy21port, reconnectAttempts, broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
        prepareClusterServer(CONTAINER2, CONTAINER2_IP, proxy12port, reconnectAttempts, broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
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
    protected void prepareClusterServer(String containerName, String bindingAddress,
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

        // Random TX ID for TM
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt());

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
