package org.jboss.qa.hornetq.test.failover;

import java.util.HashMap;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAckHA;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAckNonHA;
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
    
    private static HashMap<String,String> liveServerProperties = new HashMap<String, String>();
            
    private static HashMap<String,String> backupServerProperties = new HashMap<String, String>();
    
    @ArquillianResource
    private Deployer deployer;
    
    String hostname = "localhost";
    
    String queueName = "jms/queue/testQueue1";
        
    // this is just example of preparing deployment - deployed refactored servlet from MRG
    // managed=false means that deployment is deployed manually from test using Deployer 
    // testable=true means that arquillian will add some helper tool to deployment (?) to communicate with it
    @Deployment(name = DEPLOYMENT1, managed = false, testable = true)
    public static Archive<?> createDeployment() {

        return ShrinkWrap.create(WebArchive.class).addClasses(HornetQTestServlet.class)
                .addAsWebInfResource("apps/servlets/hornetqtestservlet/web.xml", "web.xml")
                .addAsManifestResource(new StringAsset("Dependencies: org.hornetq\n"), "MANIFEST.MF");
    }
    
    
    
    
    @Test
    public void test()  {
        
    }

//    // This test will start two servers in dedicated topology
//    // Sent some messages to first 
//    // Receive messages from the second one
//    @Test   
//    @BMRules(
//            {@BMRule(name = "setup counter for JournalImpl",
//                    targetClass = "org.hornetq.core.journal.impl.JournalImpl",
//                    targetMethod = "<init>",
//                    action = "createCounter(\"counter\")"),
//            @BMRule(name = "Info messages and counter for JournalImpl.appendUpdateRecord",
//                    targetClass = "org.hornetq.core.journal.impl.JournalImpl",
//                    targetMethod = "appendUpdateRecord",
//                    action = "incrementCounter(\"counter\");"
//                    + "System.out.println(\"Called org.hornetq.core.journal.impl.JournalImpl.appendUpdateRecord  - \" + readCounter(\"counter\"));"),
//             @BMRule(name = "Clean shutdown on JournalImpl.doInternalWrite.appendUpdateRecord",
//                    targetClass = "org.hornetq.core.journal.impl.JournalImpl",
//                    targetMethod = "appendUpdateRecord",
//                    condition="readCounter(\"counter\")>100",
//                    action = "System.out.println(\"Byteman invoked\"); killJVM();")}
//    )
//    public void simpleFailoverTest() throws Exception {
//        //CONTAINER2 variable corresponds to container's name specified via qualifier arquillian.xml
//        // ... <container qualifier="container2" mode="manual">  
//        //other values for are mode="suite|class|manual" , "suite" is default, "class" not implemented yet, that will be in ARQ-236,
//        // manual means - start/stop manually
//        
//        controller.start(CONTAINER1, liveServerProperties);
//        
//        RuleInstaller.installRule(this.getClass());
//
//        controller.start(CONTAINER2, backupServerProperties);
//    
//        ProducerClientAckHA producer = new ProducerClientAckHA(queueName);
//        
//        producer.start();
//        
//        controller.kill(CONTAINER1);
//        
//        
//        producer.join();
//        
//        logger.info("mnovak: it works :-)");
//        
//        controller.stop(CONTAINER1);
//        
//        controller.stop(CONTAINER2);
//        
//    }
    
    @After
    public void stopAllServers()    {
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    @Before
    public void prepareServer() throws Exception {
        
        prepareLiveServer(CONTAINER1);
        prepareBackupServer(CONTAINER2);
        controller.start(CONTAINER1);
        controller.start(CONTAINER2);
        Thread.sleep(10000);
        controller.stop(CONTAINER1);
        controller.stop(CONTAINER2);
        
    }
    
    
    /**
     * Prepares live server for dedicated topology.
     * 
     * @param containerName Name of the container - defined in arquillian.xml
     * 
     */
    public void prepareLiveServer(String containerName) {
       
        controller.start(containerName);
     
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        
        // set "public" interface to 127.0.0.1 and to "loopback-adreess" type
        jmsAdminOperations.setLoopBackAddressType("public", "127.0.0.1");
        
        jmsAdminOperations.setAllowFailback(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setPagingDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setJournalDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setLargeMessagesDirectory("/tmp/hornetq-journal/");
        
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setBroadCastGroup("bg-group", null, -1, "231.8.8.8", 9875,  2000, "netty", "");
        jmsAdminOperations.setDiscoveryGroup("dg-group", null, "231.8.8.8", 9875, 60000);
        jmsAdminOperations.setClusterConnections("jms", "dg-group", false, 1, 10000, true, "netty");
        jmsAdminOperations.setRedistributionDelay(0L);
        jmsAdminOperations.setHaForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory("RemoteConnectionFactory", 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory("RemoteConnectionFactory", 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory("RemoteConnectionFactory", -1);
        
        controller.stop(containerName);
        
    }
    
    /**
     * Prepares backup server for dedicated topology.
     * 
     * Sets "public" interface to 127.0.0.2.
     * 
     * @param containerName Name of the container - defined in arquillian.xml
     * 
     */
    public void prepareBackupServer(String containerName) {
        
        // set public IP address to 127.0.0.2
        // start it on 127.0.0.1 and then modify standalone-ha...xml to start on 127.0.0.2
        controller.start(containerName);
        
        // backup server has management ports + 100 because of a stupid bug in arquillian
        // it's not possible to set managementAddress to 127.0.0.2, arquillian won't get it
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations("127.0.0.1",10099);

        jmsAdminOperations.setLoopBackAddressType("public", "127.0.0.2");
        
        jmsAdminOperations.setJmxDomainName("org.hornetq.backup");
        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setBindingsDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setJournalDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setLargeMessagesDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setPagingDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setBroadCastGroup("bg-group", null, -1, "231.8.8.8", 9875,  2000, "netty", "");
        jmsAdminOperations.setDiscoveryGroup("dg-group", null, "231.8.8.8", 9875, 60000);
        jmsAdminOperations.setClusterConnections("jms", "dg-group", false, 1, 10000, true, "netty");
        
        jmsAdminOperations.setRedistributionDelay(0L);
        jmsAdminOperations.setHaForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory("RemoteConnectionFactory", 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory("RemoteConnectionFactory", 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory("RemoteConnectionFactory", -1);
        
        controller.stop(containerName);
        
    }
    
}