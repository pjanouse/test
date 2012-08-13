package org.jboss.qa.hornetq.test.bridges;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.*;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B
 * repeatedly.
 *
 * Topology - container1 - source server container2 - target server container3 -
 * source server container4 - target server
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
    
    private String broadcastGroupAddress = "233.1.2.99";
    private int broadcastGroupPort = 9876;
    
    private String discoveryGroupAddressServer1 = "233.1.2.1";
    private int discoveryGroupPortServer1 = 9876;

    private String discoveryGroupAddressServer2 = "233.1.2.2";
    private int discoveryGroupPortServer2 = 9876;
    
    private int proxy12port = 43812;
    private int proxy21port = 43821;
    
    /**
     * Stops all servers
     */
    @Before
    public void stopAllServers() {
//        prepareServers();
        stopServer(CONTAINER1);
        stopServer(CONTAINER2);
        stopServer(CONTAINER3);
        stopServer(CONTAINER4);
    }

    @Test
    @RunAsClient
//    @RestoreConfigAfterTest
//    @CleanUpAfterTest
    public void testNetworkFailoverMixMessages() throws Exception    {
            testNetworkFailure(20000, new MixMessageBuilder(500 * 1024));
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverNormalMessages() throws Exception    {
            testNetworkFailure(20000, new MixMessageBuilder(10 * 1024));
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverExtraLargeMixMessages() throws Exception    {
            testNetworkFailure(120000,new MixMessageBuilder(100 * 1024 * 1024));
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverExtraLargeMessages() throws Exception    {
            testNetworkFailure(30000, new TextMessageBuilder(100 * 1024 * 1024));
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverMixMessagesLimitedReconnectReties() throws Exception    {
            testNetworkFailure(20000, new MixMessageBuilder(500 * 1024), 1);
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverNormalMessagesLimitedReconnectReties() throws Exception    {
            testNetworkFailure(20000, new MixMessageBuilder(10 * 1024), 1);
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverExtraLargeMixMessagesLimitedReconnectReties() throws Exception    {
            testNetworkFailure(120000,new MixMessageBuilder(100 * 1024 * 1024), 1);
    }
    
    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    @CleanUpAfterTest
    public void testNetworkFailoverExtraLargeMessagesLimitedReconnectReties() throws Exception    {
            testNetworkFailure(30000, new TextMessageBuilder(100 * 1024 * 1024), 1);
    }
    
    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     * @param timeBetweenFails time between fails
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, -1);
    }
    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts) throws Exception {

        prepareServers(reconnectAttempts);
        
        controller.start(CONTAINER1); // A1
        controller.start(CONTAINER2); // B1
       
        // A1 producer
        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, relativeJndiInQueueName + 0, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        // B1 consumer
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER2_IP, 4447, relativeJndiInQueueName + 0, 2 * timeBetweenFails, 10, 10);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(5 * 1000);

        executeNetworkFails(timeBetweenFails);
      
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
     * 
     * 1 = 5 short network failures (10s gap)
     * 2 = 5 network failures (30s gap)
     * 3 = 5 network failures (100s gap)
     * 4 = 3 network failures (300s gap)
     *
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNetworkFails(long timeBetweenFails)
            throws Exception {
        
        ControllableProxy proxy1 = new SimpleProxyServer(CONTAINER2_IP, 5445, proxy12port);
        ControllableProxy proxy2 = new SimpleProxyServer(CONTAINER1_IP, 5445, proxy21port);
        proxy1.start();
        proxy2.start();
        MulticastProxy mp1 = new MulticastProxy(broadcastGroupAddress, broadcastGroupPort,
                discoveryGroupAddressServer1, discoveryGroupPortServer1);
        MulticastProxy mp2 = new MulticastProxy(broadcastGroupAddress, broadcastGroupPort,
                discoveryGroupAddressServer1, discoveryGroupPortServer1);
        mp1.start();
        mp2.start();
        
        log.info("Start all proxies.");
        
        for (int i = 0; i < 1; i++) {
            Thread.sleep(timeBetweenFails);
            log.info("Stop all proxies.");
            proxy1.stop();
            proxy2.stop();
            
            mp1.setStop(true);
            mp2.setStop(true);
            
            Thread.sleep(timeBetweenFails);
            log.info("Start all proxies.");
            proxy1.start();
            proxy2.start();
            mp1 = new MulticastProxy(broadcastGroupAddress, broadcastGroupPort,
                discoveryGroupAddressServer1, discoveryGroupPortServer1);
            mp2 = new MulticastProxy(broadcastGroupAddress, broadcastGroupPort,
                discoveryGroupAddressServer1, discoveryGroupPortServer1);
            mp1.start();
            mp2.start();
            
        }
    }
    
    /**
     * Prepares servers.
     *
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServers(int reconnectAttempts) {
        
            prepareClusterServer(CONTAINER1, CONTAINER1_IP, proxy21port, reconnectAttempts, discoveryGroupAddressServer1, discoveryGroupPortServer1);
            prepareClusterServer(CONTAINER2, CONTAINER2_IP, proxy12port, reconnectAttempts, discoveryGroupAddressServer2, discoveryGroupPortServer2);
           
            // deploy destinations 
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1, "default", hornetqInQueueName, relativeJndiInQueueName, 1);
            stopServer(CONTAINER1);
            controller.start(CONTAINER2);
            deployDestinations(CONTAINER2, "default", hornetqInQueueName, relativeJndiInQueueName, 1);
            stopServer(CONTAINER2);
           
    }

    /**
     * Prepare servers.
     *
     * @param containerName container name
     * @param bindingAddress bind address
     * @param proxyPortIn proxy port for connector where to connect to proxy directing to this server
     * @param reconnectAttempts number of reconnects for cluster-connections
     * @param discoveryGroupAddress discovery udp address
     * @param discoveryGroupPort discovery udp port
     */
    private void prepareClusterServer(String containerName, String bindingAddress,
            int proxyPortIn, int reconnectAttempts, String discoveryGroupAddress, int discoveryGroupPort) {

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
        
        jmsAdminOperations.addRemoteSocketBinding("binding-connect-to-this-server-through-remote-proxy", "127.0.0.1", proxyPortIn);
        jmsAdminOperations.createRemoteConnector("connector-to-proxy-directing-to-this-server", "binding-connect-to-this-server-through-remote-proxy", null);
        
        jmsAdminOperations.setMulticastAddressOnSocketBinding(messagingGroupSocketBindingName, broadcastGroupAddress);
        
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, "connector-to-proxy-directing-to-this-server", "");

        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingNameForDiscovery, "public", discoveryGroupAddress, discoveryGroupPort);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, "connector-to-proxy-directing-to-this-server");
        jmsAdminOperations.setReconnectAttemptsForClusterConnection(clusterGroupName, reconnectAttempts);
//        jmsAdminOperations.setConnectionTtlOverride("default", 8000);
        
        jmsAdminOperations.close();

        controller.stop(containerName);

    }


    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName container name
     * @param serverName server name
     * @param hornetqQueueNamePrefix queue name prefix
     * @param relativeJndiQueueNamePrefix relative queue jndi name prefix
     * @param numberOfQueues number of queues
     */
    private void deployDestinations(String containerName, String serverName, String hornetqQueueNamePrefix,
            String relativeJndiQueueNamePrefix, int numberOfQueues) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        for (int queueNumber = 0; queueNumber < numberOfQueues; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, hornetqQueueNamePrefix + queueNumber, relativeJndiQueueNamePrefix + queueNumber, true);
        }

    }
}
