//TODO do check of journal files
package org.jboss.qa.hornetq.test.failover;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageVerifier;
import org.jboss.qa.hornetq.apps.clients.SimpleJMSClient;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import junit.framework.Assert;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import org.jboss.qa.hornetq.apps.clients.QueueClientsClientAck;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B reteadly.
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
        // delete all data folders
        for (int i = 1; i <=4; i++) {
            deleteFolder(new File(System.getProperty("JBOSS_HOME_" + i) + "/standalone/data"));
        }
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
    public void largeByteMessagesTest() throws Exception {
        testLogic(new ByteMessageBuilder(1024 * 1024));
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
        
        prepareServers();
        
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        controller.start(CONTAINER3);
        controller.start(CONTAINER4);
        
        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(CONTAINER1_IP, PORT_JNDI, relativeJndiInQueueName, NUMBER_OF_DESTINATIONS_BRIDGES, 1, 1, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        clientsA1.setQueueJndiNamePrefixProducers(relativeJndiInQueueName);
        clientsA1.setQueueJndiNamePrefixConsumers(relativeJndiOutQueueName);
        clientsA1.setHostnameForConsumers(CONTAINER4_IP);
        clientsA1.setMessageBuilder(messageBuilder);
        clientsA1.startClients();
        
        Thread.sleep(10000);
       
        // kill sequence
        List<Integer> killSequenceList = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            Random r = new Random();
            int nodeId = 0;
            while (nodeId <= 0 || nodeId > 5) {
                nodeId = r.nextInt(5);
            }
            log.info("##################################");
            log.info("Kill and restart server:" + nodeId);
            killSequenceList.add(nodeId);
            killAndStartContainer(nodeId);
            log.info("##################################");
        }

        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }
        
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        controller.stop(CONTAINER3);
        controller.stop(CONTAINER4);
        
        log.info("##################################");
        StringBuilder killSequence = new StringBuilder();
        killSequence.append("Print kill sequence:[");
        for (int i = 0; i < killSequenceList.size(); i++)  {
            if (i + 1 == killSequenceList.size())  {
                killSequence.append(killSequenceList.get(i));
            } else {
                killSequence.append(killSequenceList.get(i) + ", ");
            }
        }
        killSequence.append("]\n");
        log.info(killSequence);
        log.info("##################################");
        
        assertTrue("There are problems detected by clients. Check logs for more info. Look for: 'Print kill sequence', " +
                "'Kill and restart server', 'Killing server', 'Evaluate results for queue clients with client acknowledge'."
                , clientsA1.evaluateResults());
        
    }

    /**
     * Kills and starts container.
     * 
     * @param  nodeId node id of the node
     * 
     */
    private void killAndStartContainer(int nodeId) throws Exception    {
        switch (nodeId) {
            case 1:  killAndRestart(CONTAINER1, CONTAINER1_IP, BYTEMAN_CONTAINER1_PORT);
                     break;
            case 2:  killAndRestart(CONTAINER2, CONTAINER2_IP, BYTEMAN_CONTAINER2_PORT);
                     break;
            case 3:  killAndRestart(CONTAINER3, CONTAINER3_IP, BYTEMAN_CONTAINER3_PORT);
                     break;
            case 4:  killAndRestart(CONTAINER4, CONTAINER4_IP, BYTEMAN_CONTAINER4_PORT);
                     break;
            default: throw new Exception("Such a node id does not exist. Wrong value: " + nodeId);
        }
    }
    
    /**
     * Kills and starts container.
     * 
     * @param containerName name of the container from arquillian...xml
     * @param containerIpAdress containers ip address - where byteman ageng listens
     * @param bytemanPort port where byteman agent listens
     */
    private void killAndRestart(String containerName, String containerIpAdress, int bytemanPort) throws Exception   {
        RuleInstaller.installRule(this.getClass(), containerIpAdress, bytemanPort);
        controller.kill(containerName);
        Thread.sleep(10000);
        controller.start(containerName);
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
        
        String udpGroupAddress = "231.43.21.36";
        int udpGroupPort = 9875;

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, null, Integer.MIN_VALUE, udpGroupAddress, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, null, udpGroupAddress, udpGroupPort, 10000);

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
        
        controller.stop(containerName);
        controller.start(containerName);
        
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
        
        String udpGroupAddress = "231.13.21.86";
        int udpGroupPort = 9875;

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, null, Integer.MIN_VALUE, udpGroupAddress, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, null, udpGroupAddress, udpGroupPort, 10000);

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
