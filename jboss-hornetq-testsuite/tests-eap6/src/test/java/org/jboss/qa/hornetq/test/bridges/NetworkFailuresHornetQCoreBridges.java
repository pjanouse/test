package org.jboss.qa.hornetq.test.bridges;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.GroupMessageVerifier;
import org.jboss.qa.hornetq.apps.impl.MixMessageGroupMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

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
 * STEPS 1. AND 2. ARE THE SAME FOR ALL NODES IN ONE CLUSTER. THERE are 2^(n-1) MULTICAST PROXIES PER per n nodes.
 * STEP 3 IS SPECIFIC FOR EACH NODE IN CLUSTER. THERE IS ONE TCP/UDP PROXY PER NODE.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class NetworkFailuresHornetQCoreBridges extends NetworkFailuresBridgesAbstract{


    ///////////////////// NETWORK FAILURE WITH MESSAGE GROUP TESTS //////////////////////

    // TODO tests are ignored until this is fixed: https://bugzilla.redhat.com/show_bug.cgi?id=1168937
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupNetworkFailureSmallMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), -1, 2, false);
        //testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), -1, 2, false);

    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupMixMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupSmallMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupLargeMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(1024, 1024, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupMixMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupSmallMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupLargeMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(1024, 1024, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupMixMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupSmallMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupLargeMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(120000, new MixMessageGroupMessageBuilder(1024, 1024, "messageGroupId1"), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testMessageGroupFailureMixMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureSmallMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureLargeMessages() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(1024, 1024, "messageGroupId1"), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureMixMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureSmallMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureLargeMessages1recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureMixMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 1024, "messageGroupId1"), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureSmallMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(50, 50, "messageGroupId1"), 5, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    @Ignore
    public void testShortMessageGroupFailureLargeMessages5recAttempts() throws Exception {
        testNetworkFailureWithMessageGrouping(20000, new MixMessageGroupMessageBuilder(1024, 1024, "messageGroupId1"), 5, 2);
    }

    public void testNetworkFailureWithMessageGrouping(long timeBetweenFails, MixMessageGroupMessageBuilder messageBuilder, int reconnectAttempts,
                                                      int numberOfFails) throws Exception {
        testNetworkFailureWithMessageGrouping(timeBetweenFails, messageBuilder, reconnectAttempts, numberOfFails, true);
    }


    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     */
    @Override
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails, boolean staysDisconnected)
            throws Exception {

        prepareServers(reconnectAttempts);

        startProxies();
        container(2).start(); // B1
        container(1).start(); // A1

        Thread.sleep(5000);
        // message verifier which detects duplicated or lost messages
        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getBasicVerifier(ContainerUtils.getJMSImplementation(container(1)));

        // A1 producer
        ProducerTransAck producer1 = new ProducerTransAck(container(1),relativeJndiInQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.addMessageVerifier(messageVerifier);
        if (messageBuilder != null) {
            messageBuilder.setAddDuplicatedHeader(true);
            producer1.setMessageBuilder(messageBuilder);
        }
        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2),relativeJndiInQueueName, (4 * timeBetweenFails) > 120000 ? (4 * timeBetweenFails) : 120000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(messageVerifier);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(15 * 1000);

        executeNetworkFails(timeBetweenFails, numberOfFails);

        producer1.stopSending();
        producer1.join();
        receiver1.setReceiveTimeout(120000);
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        // Just prints lost or duplicated messages if there are any. This does not fail the test.
        messageVerifier.verifyMessages();

        Thread.sleep(5*60000);
        if (staysDisconnected)  {
            Assert.assertTrue("There must be more sent messages then received.",
                    producer1.getListOfSentMessages().size() > receiver1.getCount());
            container(1).restart();
            ReceiverTransAck receiver2 = new ReceiverTransAck(container(1), relativeJndiInQueueName, 10000, 10, 10);
            receiver2.start();
            receiver2.join();
            container(1).stop();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        } else {
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        }
        container(2).stop();
        container(1).stop();
    }




    /**
     * Implementation of the basic test scenario:
     * 1. Start cluster - nodes A and B
     * 2. Start producer on A
     * 3. Start consumer on B
     * 4. Disconnect sequence
     * 5. Stop producer
     * 6. Evaluate results
     */
    public void testNetworkFailureWithMessageGrouping(long timeBetweenFails, MixMessageGroupMessageBuilder messageBuilder, int reconnectAttempts,
                                                      int numberOfFails, boolean staysDisconnected) throws Exception {

        prepareServersWihtMessageGrouping(reconnectAttempts);

        startProxies();
        container(1).start(); // A1
        container(2).start(); // B1

        Thread.sleep(5000);

        GroupMessageVerifier groupMessageVerifier = new GroupMessageVerifier();

        // A1 producer
        ProducerTransAck producer1 = new ProducerTransAck(container(1), relativeJndiInQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        if (messageBuilder != null) {
            messageBuilder.setAddDuplicatedHeader(true);
            producer1.setMessageBuilder(messageBuilder);
        }
        producer1.addMessageVerifier(groupMessageVerifier);

        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(2),  relativeJndiInQueueName, (4 * timeBetweenFails) > 120000 ? (4 * timeBetweenFails) : 120000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.addMessageVerifier(groupMessageVerifier);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(15 * 1000);


        executeNetworkFails(timeBetweenFails, numberOfFails);

        Thread.sleep(5 * 1000);

        producer1.stopSending();
        producer1.join();
        receiver1.setReceiveTimeout(10000);
        receiver1.join();

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        if (staysDisconnected)  {
            Assert.assertTrue("There must be more sent messages then received.",
                    producer1.getListOfSentMessages().size() > receiver1.getCount());
            log.info("Stop server 1.");
            container(1).stop();
            log.info("Server 1 stopped.");
            stopProxies();
            log.info("Stop server 2.");
            container(2).stop();
            log.info("Server 2 stopped.");

            log.info("Start server 1.");
            container(1).start();
            log.info("Server 1 started.");

            log.info("Start server 2.");
            container(2).start();
            log.info("Server 2 started.");

            startProxies();

            Thread.sleep(10000);

            log.info("Container 1 have " + getNumberOfNodesInCluster(container(1)) + " other node in cluster.");
            log.info("Container 2 have " + getNumberOfNodesInCluster(container(2)) + " other node in cluster.");
            Assert.assertEquals("There shoulld be 2 nodes in cluster for container 1.",
                    1, getNumberOfNodesInCluster(container(1)));
            Assert.assertEquals("There shoulld be 2 nodes in cluster for container 2.",
                    1, getNumberOfNodesInCluster(container(2)));

            ReceiverTransAck receiver2 = new ReceiverTransAck(container(1), relativeJndiInQueueName, 10000, 10, 10);
            receiver2.addMessageVerifier(groupMessageVerifier);
            receiver2.start();
            receiver2.join();
            // check send and received messages
            groupMessageVerifier.verifyMessages();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        } else {
            // check send and received messages
            groupMessageVerifier.verifyMessages();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        }

        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();
    }




    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    @Override
    public void prepareServers(int reconnectAttempts) {

        prepareClusterServer(container(1), proxy21port, reconnectAttempts, broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA, false);
        prepareClusterServer(container(2), proxy12port, reconnectAttempts, broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB, false);
    }

    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServersWihtMessageGrouping(int reconnectAttempts) {

        prepareClusterServer(container(1), proxy21port, reconnectAttempts, broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA, true);
        prepareClusterServer(container(2), proxy12port, reconnectAttempts, broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB, true);
    }

    /**
     * Prepare servers.
     *
     * @param container             container
     * @param proxyPortIn           proxy port for connector where to connect to proxy directing to this server,every can connect to this server through proxy on 127.0.0.1:proxyPortIn
     * @param reconnectAttempts     number of reconnects for cluster-connections
     * @param discoveryGroupAddress discovery udp address
     * @param discoveryGroupPort    discovery udp port
     */
    protected void prepareClusterServer(Container container,
                                      int proxyPortIn, int reconnectAttempts, String broadcastGroupAddress,
                                      int broadcastGroupPort, String discoveryGroupAddress, int discoveryGroupPort, boolean isMessageWithGrouping) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectionFactoryName = "RemoteConnectionFactory";

        String messagingGroupSocketBindingName = "messaging-group";
        String messagingGroupSocketBindingNameForDiscovery = messagingGroupSocketBindingName + "-" + container.getName();

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", container.getHostname());
        jmsAdminOperations.setInetAddress("unsecure", container.getHostname());
        jmsAdminOperations.setInetAddress("management", container.getHostname());


        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        // set logging
//        jmsAdminOperations.addLoggerCategory("org.hornetq", "TRACE");

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

        String name = "my-grouping-handler";
        String address = "jms";
        long timeout = 5000;
        long groupTimeout = 500;
        long reaperPeriod = 750;

        if (isMessageWithGrouping)  {
            if (CONTAINER1_NAME.equals(container.getName())) {
                jmsAdminOperations.addMessageGrouping("default", name, "LOCAL", address, timeout, groupTimeout, reaperPeriod);
            } else if (CONTAINER2_NAME.equals(container.getName()))    {
                jmsAdminOperations.addMessageGrouping("default", name, "REMOTE", address, timeout, groupTimeout, reaperPeriod);
            }
        }

        jmsAdminOperations.close();

        container.stop();
    }


}
