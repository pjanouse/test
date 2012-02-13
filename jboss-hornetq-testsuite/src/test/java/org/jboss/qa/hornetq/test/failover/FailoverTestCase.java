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
    
    private static HashMap<String,String> liveServerProperties = null;
            
    private static HashMap<String,String> backupServerProperties = null;
    
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
    
    
    @Before
    public void prepareServer() throws Exception {
        //CONTAINER2 variable corresponds to container's name specified via qualifier arquillian.xml
        // ... <container qualifier="container2" mode="manual">  
        //other values for are mode="suite|class|manual" , "suite" is default, "class" not implemented yet, that will be in ARQ-236,
        // manual means - start/stop manually
        liveServerProperties = new HashMap<String, String>();
        
        liveServerProperties.put("serverConfig", "standalone-ha-2.xml");
        
        controller.start(CONTAINER1, liveServerProperties);
     
        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations();
        
        jmsAdminOperations.setAllowFailback(true);
        jmsAdminOperations.setBindingsDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setJournalDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setLargeMessagesDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setPagingDirectory("/tmp/hornetq-journal/");
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setBroadCastGroup("bg-group", null, -1, "231.8.8.8", 8765,  2000, "netty", "");
        jmsAdminOperations.setDiscoveryGroup("dg-group", null, "231.8.8.8", 8765, 2000);
        jmsAdminOperations.setClusterConnections("jms", "dg-group", false, 2, 10000, true, "netty");
        jmsAdminOperations.setRedistributionDelay(0L);
        jmsAdminOperations.addJndiBindingForConnectionFactory("RemoteConnectionFactory","java:jboss/exported/RemoteConnectionFactory");
        jmsAdminOperations.setHaForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory("RemoteConnectionFactory", true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory("RemoteConnectionFactory", 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory("RemoteConnectionFactory", 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory("RemoteConnectionFactory", -1);
        
        controller.stop(CONTAINER1);
        
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
    
    @Before @After
    public void stopAllServers()    {
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    @BeforeClass
    public static void setupPropertiesForContainers()    {
        
        liveServerProperties = new HashMap<String, String>();
        
        liveServerProperties.put("serverConfig", "standalone-ha-2.xml");
        
        backupServerProperties = new HashMap<String, String>();
        
        backupServerProperties.put("serverConfig", "standalone-ha-simple-backup.xml");
        
    }
    
}