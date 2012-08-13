package org.jboss.qa.hornetq.test.transportreliability;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * Purpose of this test case is to test how jms client will handle server shutdown
 * and server kill. No message should be lost or duplicated.
 * 
 * @author mnovak
 */
@RunWith(Arquillian.class)
@RestoreConfigAfterTest
public class ServerNetworkUnavailableTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ServerNetworkUnavailableTestCase.class);
    
    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES = 100000;
   
    private static boolean topologyCreated = false;
    
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";
    
    @ArquillianResource
    ContainerController controller;

    @Test
    @RunAsClient
    public void testShutdownWithProducerNormalMessages1KB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1000), false);
    }
    
    @Test
    @RunAsClient
    public void testShutdownWithProducerLargeMessages1MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024), false);
    }
    
    @Test
    @RunAsClient
    public void testShutdownWithProducerLargeMessages20MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }
    
    @Test
    @RunAsClient
    public void testShutdownWithConsumerNormalMessages1KB() throws Exception    {
        testWithConsumer(new TextMessageBuilder(1000), false);
    }
    
    @Test
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages1MB() throws Exception    {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), false);
    }
    
    @Test
    @RunAsClient
    public void testShutdownWithConsumerLargeMessages20MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithProducerNormalMessages1KB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1000), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithProducerLargeMessages1MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithProducerLargeMessages20MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithConsumerNormalMessages1KB() throws Exception    {
        testWithConsumer(new TextMessageBuilder(1000), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithConsumerLargeMessages1MB() throws Exception    {
        testWithConsumer(new TextMessageBuilder(1024 * 1024), false);
    }
    
    @Test
    @RunAsClient
    public void testKillWithConsumerLargeMessages20MB() throws Exception    {
        testWithProducer(new TextMessageBuilder(1024 * 1024 * 20), false);
    }
    
    /**
     * 
     * Server A is stopped/killed.
     * 
     */
    @BMRules({
        @BMRule(name = "Setup counter for PostOfficeImpl",
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
        condition = "readCounter(\"counter\")>124",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testWithConsumer(MessageBuilder messageBuilder, boolean testKill) throws Exception {

        prepareServers();
        
        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + "0", 500);
        
        producer.setMessageBuilder(messageBuilder);
        
        producer.start();
        
        producer.join();
        
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + "0");
        
        receiver.start();
        
        if (testKill)   {
            
            RuleInstaller.installRule(ServerNetworkUnavailableTestCase.class);
            Thread.sleep(5000);
            log.info("############# Kill server 1.");
            controller.kill(CONTAINER1);
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            controller.start(CONTAINER1);
            log.info("############# Server 1 started.");

        } else {
            
            Thread.sleep(10000);
            log.info("############# Stopping server 1.");
            stopServer(CONTAINER1);
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            controller.start(CONTAINER1);
            log.info("############# Server 1 started.");
        }
            
        receiver.join();
        
        Assert.assertEquals("There is differen number sent and recieved messages. Sent messages" + producer.getListOfSentMessages().size() +
                "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        
        
        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }
    
    
    @BMRules({
        @BMRule(name = "Setup counter for PostOfficeImpl",
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
        condition = "readCounter(\"counter\")>124",
        action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();")})
    public void testWithProducer(MessageBuilder messageBuilder, boolean testKill) throws Exception {

        prepareServers();
        
        controller.start(CONTAINER2);

        controller.start(CONTAINER1);

        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + "0", NUMBER_OF_MESSAGES);
        
        producer.setMessageBuilder(messageBuilder);
        
        producer.start();
        
        if (testKill)   {
            
            RuleInstaller.installRule(ServerNetworkUnavailableTestCase.class);
            Thread.sleep(10000);
            log.info("############# Kill server 1.");
            controller.kill(CONTAINER1);
            log.info("############# Server 1 killed.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            controller.start(CONTAINER1);
            log.info("############# Server 1 started.");

        } else {
            
            Thread.sleep(10000);
            log.info("############# Stopping server 1.");
            stopServer(CONTAINER1);
            log.info("############# Server 1 stopped.");
            Thread.sleep(5000);
            log.info("############# Starting server 1.");
            controller.start(CONTAINER1);
            log.info("############# Server 1 started.");
        }
        
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER1_IP, getJNDIPort(), queueJndiNamePrefix + "0");
        
        receiver.start();
        
        receiver.join();
        
        Assert.assertEquals("There is differen number sent and recieved messages. Sent messages" + producer.getListOfSentMessages().size() +
                "Received: " + receiver.getListOfReceivedMessages().size(),
                producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        
        
        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }
    
    @Before
    public void prepareServers() {
        if (!topologyCreated) {
            prepareServer(CONTAINER1, CONTAINER1_IP);
            prepareServer(CONTAINER2, CONTAINER2_IP);

            // deploy destinations 
            controller.start(CONTAINER1);
            deployDestinations(CONTAINER1);
            stopServer(CONTAINER1);
            controller.start(CONTAINER2);
            deployDestinations(CONTAINER2);
            stopServer(CONTAINER2);
            topologyCreated = true;
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareServer(String containerName, String bindingAddress) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String udpGroupAddress = "231.8.8.8";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
//        jmsAdminOperations.setLoopBackAddressType("public", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("unsecure", bindingAddress);
//        jmsAdminOperations.setLoopBackAddressType("management", bindingAddress);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setClustered(true);
       
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(false);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, udpGroupAddress, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, udpGroupAddress, udpGroupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
//        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
//        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
//        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
//        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        controller.stop(containerName);

    }
    
    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }
}