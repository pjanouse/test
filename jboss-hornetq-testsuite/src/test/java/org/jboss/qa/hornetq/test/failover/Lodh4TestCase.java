//TODO do check of journal files
package org.jboss.qa.hornetq.test.failover;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertTrue;
import org.hornetq.jms.client.HornetQQueue;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.SoakReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.tools.arquillina.extension.annotation.CleanUpAfterTest;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B repeatedly.
 * 
 * Topology - 
 * container1 - source server
 * container2 - target server
 * container3 - source server
 * container4 - target server
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class Lodh4TestCase extends HornetQTestCase {

    // number of bridges/destinations
    private static final int NUMBER_OF_DESTINATIONS_BRIDGES = 5;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    
    // Logger
    private static final Logger log = Logger.getLogger(Lodh4TestCase.class);
    
    private String hornetqInQueueName = "InQueue";
    private String relativeJndiInQueueName = "queue/InQueue";
    private String hornetqOutQueueName = "OutQueue";
    private String relativeJndiOutQueueName = "queue/OutQueue";
    private boolean topologyCreated = false;

    /**
     * Stops all servers
     */
    @Before
    public void stopAllServers() {
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER3);
        controller.stop(CONTAINER4);
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @BMRules({
        @BMRule(name = "Kill server",
        targetClass = "java.util.concurrent.ThreadPoolExecutor",
        targetMethod = "getTask",
        condition = "TRUE",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    @RunAsClient
    @Test
//    @CleanUpAfterTest
    public void normalMessagesTest() throws Exception {
        testLogic(new ByteMessageBuilder(30));
    }

    /**
     * Large message, byte message
     *
     * @throws InterruptedException if something is wrong
     */
    @BMRules({
        @BMRule(name = "Kill server",
        targetClass = "java.util.concurrent.ThreadPoolExecutor",
        targetMethod = "getTask",
        condition = "TRUE",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")
    })
    @RunAsClient
    @Test
//    @CleanUpAfterTest
    public void largeByteMessagesTest() throws Exception {
        List<String> killSequence = new ArrayList<String>();
        killSequence.add(CONTAINER2);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        testLogicLargeMessages(new ByteMessageBuilder(300 * 1024), killSequence);
    }
    
    @Test
//    @CleanUpAfterTest
    public void mixMessagesTest() throws Exception {
        List<String> killSequence = new ArrayList<String>();
        killSequence.add(CONTAINER2);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        testLogicLargeMessages(new MixMessageBuilder(300 * 1024), killSequence);
    }
    
    /**
     * Implementation of the basic test scenario:
     * 1. Start cluster A and B
     * 2. Start producers on A1, A2
     * 3. Start consumers on B1, B2
     * 4. Kill sequence - it's random
     * 5. Stop producers
     * 6. Evaluate results
     *
     * @param messages        number of messages used for the test
     * @param messageBuilder  instance of the message builder
     * @param messageVerifier instance of the messages verifier
     */
    private void testLogic(MessageBuilder messageBuilder) throws Exception {
        List<String> killSequence = new ArrayList<String>();
        killSequence.add(CONTAINER2);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        killSequence.add(CONTAINER3);
        testLogic(messageBuilder, killSequence);
    }
    
    /**
     * Implementation of the basic test scenario:
     * 1. Start cluster A and B
     * 2. Start producers on A1, A2
     * 3. Start consumers on B1, B2
     * 4. Kill sequence - it's random
     * 5. Stop producers
     * 6. Evaluate results
     *
     * @param messages        number of messages used for the test
     * @param messageBuilder  instance of the message builder
     * @param messageVerifier instance of the messages verifier
     */
    private void testLogic(MessageBuilder messageBuilder, List<String> killSequence) throws Exception {
        
        prepareServers();
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        
        // give some time to server4 to really start
        Thread.sleep(3000);
        
        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(CONTAINER1_IP, PORT_JNDI, relativeJndiInQueueName, NUMBER_OF_DESTINATIONS_BRIDGES, 1, 1, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        clientsA1.setQueueJndiNamePrefixProducers(relativeJndiInQueueName);
        clientsA1.setQueueJndiNamePrefixConsumers(relativeJndiOutQueueName);
        clientsA1.setHostnameForConsumers(CONTAINER4_IP);
        clientsA1.setMessageBuilder(messageBuilder);
        clientsA1.startClients();
        
        Thread.sleep(10000);
       
        executeKillSequence(killSequence, 20000);
        
        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }
        
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER3);
        controller.stop(CONTAINER4);
               
        assertTrue("There are problems detected by clients. Check logs for more info. Look for: 'Print kill sequence', " +
                "'Kill and restart server', 'Killing server', 'Evaluate results for queue clients with client acknowledge'."
                , clientsA1.evaluateResults());
        
    }
    
    /**
     * Implementation of the basic test scenario:
     * 1. Start cluster A and B
     * 2. Start producers on A1, A2
     * 3. Start consumers on B1, B2
     * 4. Kill sequence - it's random
     * 5. Stop producers
     * 6. Evaluate results
     *
     * @param messages        number of messages used for the test
     * @param messageBuilder  instance of the message builder
     * @param messageVerifier instance of the messages verifier
     */
    private void testLogicLargeMessages(MessageBuilder messageBuilder, List<String> killSequence) throws Exception {
        
        prepareServers();
        controller.start(CONTAINER2);
        controller.start(CONTAINER4);
        controller.start(CONTAINER1);
        controller.start(CONTAINER3);
        
        // give some time to server4 to really start
        Thread.sleep(3000);
        
        SoakProducerClientAck producer1 = new SoakProducerClientAck(CONTAINER1_IP, 4447, relativeJndiInQueueName+0, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        SoakReceiverClientAck receiver1 = new SoakReceiverClientAck(CONTAINER4_IP, 4447, relativeJndiOutQueueName+0, 10000, 10, 10);
        
        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();
        
        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        executeKillSequence(killSequence, 20000);
        
        producer1.stopSending();
        producer1.join();
        receiver1.join();
        
        log.info("Number of sent messages: " + producer1.getCounter());
        log.info("Number of received messages: " + receiver1.getCount());
                
        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getCounter(),
                receiver1.getCount());
       
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER3);
        controller.stop(CONTAINER4);
        
    }
    
    /**
     * Executes kill sequence.
     * 
     * @param killSequence map Contanier -> ContainerIP
     * @param timeBetweenKills time between subsequent kills (in milliseconds)
     */
    private void executeKillSequence(List<String> killSequence, long timeBetweenKills) throws InterruptedException {
        
        for (String containerName : killSequence) {
            Thread.sleep(timeBetweenKills);
            killServer(containerName);
            Thread.sleep(3000);
            controller.kill(containerName);
            log.info("Start server: " + containerName);
            controller.start(containerName);
            log.info("Server: " + containerName + " -- STARTED");
        }
    }

    /**
     * Prepares servers.
     * 
     * Container1,3 - source servers in cluster A.
     * Container2,4 - source servers in cluster B.
     */    
    public void prepareServers() {
        if (!topologyCreated) {
            prepareSourceServer(CONTAINER1, CONTAINER1_IP, CONTAINER2_IP);
            prepareSourceServer(CONTAINER3, CONTAINER3_IP, CONTAINER4_IP);
            prepareTargetServer(CONTAINER2, CONTAINER2_IP);
            prepareTargetServer(CONTAINER4, CONTAINER4_IP);

            // deploy destinations 
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1_IP, 9999, "default", hornetqInQueueName, relativeJndiInQueueName, NUMBER_OF_DESTINATIONS_BRIDGES);
            controller.stop(CONTAINER1);
            controller.start(CONTAINER3);
            deployDestinations(CONTAINER3_IP, 9999, "default", hornetqInQueueName, relativeJndiInQueueName, NUMBER_OF_DESTINATIONS_BRIDGES);
            controller.stop(CONTAINER3);
            controller.start(CONTAINER2);
            deployDestinations(CONTAINER2_IP, 9999, "default", hornetqOutQueueName, relativeJndiOutQueueName, NUMBER_OF_DESTINATIONS_BRIDGES);
            controller.stop(CONTAINER2);
            controller.start(CONTAINER4);
            deployDestinations(CONTAINER4_IP, 9999, "default", hornetqOutQueueName, relativeJndiOutQueueName, NUMBER_OF_DESTINATIONS_BRIDGES);
            controller.stop(CONTAINER4);
            topologyCreated = true;
        }
    }
    
    /**
     * Prepares source server for bridge.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param targetServerIpAddress ip address of target server for bridge
     */
    private void prepareSourceServer(String containerName, String bindingAddress,
            String targetServerIpAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";
        
        String udpGroupAddress = "231.43.21.36";
        
        controller.start(containerName);
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        
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

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", targetServerIpAddress, 5445);
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        jmsAdminOperations.close();
        
        controller.stop(containerName);
        controller.start(containerName);
        
        jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", udpGroupAddress, 55874);
        for (int i = 0; i < NUMBER_OF_DESTINATIONS_BRIDGES; i++) {
            jmsAdminOperations.createBridge("myBridge" + i, "jms.queue." + hornetqInQueueName + i, "jms.queue." + hornetqOutQueueName + i, -1, "bridge-connector");
        }
        
        controller.stop(containerName);

    }
    
    /**
     * Prepare target server for bridge
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareTargetServer(String containerName, String bindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        
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
        
        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        
        controller.stop(containerName);

    }
    
    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to management interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     * @param hornetqQueueName name of hornetq queue e.g. 'testQueue'
     * @param relativeJndiQueueName relativeJndiName e.g. 'queue/testQueueX' -> will create "java:jboss/exported/queue/testQueueX"
     *  and "java:/queue/testQueue" jndi bindings
     * @param numberOfQueues number of queue with the given queue name prefix
     *
     */
    private void deployDestinations(String hostname, int port, String serverName, String hornetqQueueNamePrefix, 
            String relativeJndiQueueNamePrefix, int numberOfQueues) {

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(hostname, port);

        for (int queueNumber = 0; queueNumber < numberOfQueues; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, hornetqQueueNamePrefix + queueNumber, relativeJndiQueueNamePrefix + queueNumber, true);
        }
      
    }
}
