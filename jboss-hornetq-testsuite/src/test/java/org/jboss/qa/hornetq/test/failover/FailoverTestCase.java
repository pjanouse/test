package org.jboss.qa.hornetq.test.failover;

import java.util.HashMap;
import java.util.Map;
import javax.jms.Session;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.servlets.HornetQTestServlet;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
public class FailoverTestCase extends HornetQTestCase {
    
    private static final Logger logger = Logger.getLogger(ProducerClientAckNonHA.class);

    private static final String DEPLOYMENT1 = "dep.container1";
    
    private static final int NUMBER_OF_QUEUES = 3;
    
    private static final int NUMBER_OF_PRODUCERS_PER_QUEUE = 1;
    
    private static final int NUMBER_OF_RECEIVERS_PER_QUEUE = 2;
    
    @ArquillianResource
    private Deployer deployer;
    
    String hostname = "localhost";
    
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";
    
    
        
    // this is just example of preparing deployment - deployed refactored servlet from MRG
    // managed=false means that deployment is deployed manually from test using Deployer 
    // testable=true means that arquillian will add some helper tool to deployment (?) to communicate with it
    @Deployment(name = DEPLOYMENT1, managed = false, testable = true)
    public static Archive<?> createDeployment() {

        return ShrinkWrap.create(WebArchive.class).addClasses(HornetQTestServlet.class)
                .addAsWebInfResource("apps/servlets/hornetqtestservlet/web.xml", "web.xml")
                .addAsManifestResource(new StringAsset("Dependencies: org.hornetq\n"), "MANIFEST.MF");
    }

    
  
    /** This test will start two servers in dedicated topology - no cluster
    * Sent some messages to first 
    * Receive messages from the second one
    */
    @Test   
    @BMRules(
            {@BMRule(name = "Setup counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    action = "incrementCounter(\"counter\");"
                    + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
             @BMRule(name = "Kill server when a number of messages were received",
                    targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl",
                    targetMethod = "processRoute",
                    condition="readCounter(\"counter\")>100",
                    action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void simpleFailoverTest() throws Exception {
        
        prepareSimpleDedicatedTopology();
        
        controller.start(CONTAINER2);
        
        controller.start(CONTAINER1);

        // install rule to first server
        RuleInstaller.installRule(this.getClass());
        
        StartManyClientsClientAck clients = new StartManyClientsClientAck(NUMBER_OF_QUEUES, NUMBER_OF_PRODUCERS_PER_QUEUE, NUMBER_OF_RECEIVERS_PER_QUEUE);
        
        clients.startClients();
        
        controller.kill(CONTAINER1);
        
        waitForClientsToFinish(clients);
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    private void waitForClientsToFinish(StartManyClientsClientAck clients) throws InterruptedException   {
        
        while(!clients.isFinished()) Thread.sleep(1000);

    }
    
    private void evaluateResults(StartManyClientsClientAck clients)  {
        
            clients.evaluateResults();
            
    }
    
    @After
    public void stopAllServers()    {
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    public void prepareSimpleDedicatedTopology() throws Exception {
        
        prepareLiveServer(CONTAINER1);
        prepareBackupServer(CONTAINER2);
        
    }
    
    /**
     * Prepares live server for dedicated topology.
     * 
     * @param containerName Name of the container - defined in arquillian.xml
     * 
     */
    private void prepareLiveServer(String containerName) {
       
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String udpGroupAddress = "231.8.8.8";
        int udpGroupPort = 9875;
        String bindingAddress = "127.0.0.1";
        int broadcastBindingPort = 56880;
        String journalDirectory = JOURNAL_DIRECTORY_A;

        HashMap<String,String> liveConf = new HashMap<String,String>();
        liveConf.put("managementAddress", "127.0.0.1");
        liveConf.put("managementPort", "9999");
        controller.start(containerName, liveConf);
        
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
//        jmsAdminOperations.setLoopBackAddressType("public", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("unsecure", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("management", bindingAddress);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);
        
        jmsAdminOperations.setAllowFailback(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, udpGroupAddress, udpGroupPort,  2000, connectorName, "");
        
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, udpGroupAddress, udpGroupPort, 10000);
        
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");
        
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 10 * 1024);
        
        // deploy queues
        deployDestinations();
        
        controller.stop(containerName);
        
        liveConf = new HashMap<String,String>();
        liveConf.put("managementAddress", bindingAddress);
        liveConf.put("managementPort", "9999");
        controller.start(CONTAINER1, liveConf);
        controller.stop(CONTAINER1);
        
    }
    
    /**
     * Prepares backup server for dedicated topology.
     * 
     * Sets "public" interface to 127.0.0.2.
     * 
     * @param containerName Name of the container - defined in arquillian.xml
     * 
     */
    private void prepareBackupServer(String containerName) {
        
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String udpGroupAddress = "231.8.8.8";
        int udpGroupPort = 9875;
        String bindingAddress = "10.34.3.115";

        int broadcastBindingPort = 56880;
        String journalDirectory = JOURNAL_DIRECTORY_A;
                
        HashMap<String,String> liveConf = new HashMap<String,String>();
        liveConf.put("managementAddress", "127.0.0.1");
        liveConf.put("managementPort", "9999");
        controller.start(containerName, liveConf);        
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();

//        jmsAdminOperations.setLoopBackAddressType("public", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("unsecure", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("management", bindingAddress);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);
        
        jmsAdminOperations.setJmxDomainName("org.hornetq.backup");
        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);
        
        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType("NIO");
        
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, udpGroupAddress, udpGroupPort,  2000, connectorName, "");
        
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, udpGroupAddress, udpGroupPort, 10000);
        
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");
        
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 10 * 1024);
        
        // deploy queues
        deployDestinations();
        
        controller.stop(containerName);
        
        HashMap<String,String> backupConf = new HashMap<String,String>();
        backupConf.put("managementAddress", bindingAddress);
        backupConf.put("managementPort", "9999");
        controller.start(CONTAINER2, backupConf);
        controller.stop(CONTAINER2);
    }
    
    /**
     * Deploys destinations to server which is currently running on 127.0.0.1 on port 9999.
     */
    private void deployDestinations() {
        
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        
        for (int queueNumber = 0; queueNumber < NUMBER_OF_QUEUES; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber);
        }
        
        for (int topicNumber = 0; topicNumber < NUMBER_OF_QUEUES; topicNumber++) {
            jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }
        
    }
    
}