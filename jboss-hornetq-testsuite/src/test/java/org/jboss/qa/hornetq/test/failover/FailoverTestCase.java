package org.jboss.qa.hornetq.test.failover;

import java.util.HashMap;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAckNonHA;
import org.jboss.qa.hornetq.apps.servlets.HornetQTestServlet;
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
public class FailoverTestCase {
    
    private static final Logger logger = Logger.getLogger(ProducerClientAckNonHA.class);

    public static final String CONTAINER1 = "clustering-udp-0-unmanaged";
    
    public static final String CONTAINER2 = "clustering-udp-1-unmanaged";
    
    private static final String DEPLOYMENT1 = "dep.container1";
    
    private static HashMap<String,String> liveServerProperties = null;
            
    private static HashMap<String,String> backupServerProperties = null;
    
    @ArquillianResource
    ContainerController controller;
    
    @ArquillianResource
    private Deployer deployer;
    
    String hostname = "localhost";
    
    String queueName = "testQueue";
        
    // this is just example of preparing deployment - deployed refactored servlet from MRG
    // managed=false means that deployment is deployed manually from test using Deployer 
    // testable=true means that arquillian will add some helper tool to deployment (?) to communicate with it
    @Deployment(name = DEPLOYMENT1, managed = false, testable = true)
    public static Archive<?> createDeployment() {

        return ShrinkWrap.create(WebArchive.class).addClasses(HornetQTestServlet.class)
                .addAsWebInfResource("apps/servlets/hornetqtestservlet/web.xml", "web.xml")
                .addAsManifestResource(new StringAsset("Dependencies: org.hornetq\n"), "MANIFEST.MF");
    }
    
    // This test will start two servers in dedicated topology
    // Sent some messages to first 
    // Receive messages from the second one
    @Test   
    public void simpleFailoverTest() throws Exception {
        //CONTAINER2 variable corresponds to container's name specified via qualifier arquillian.xml
        // ... <container qualifier="container2" mode="manual">  
        //other values for are mode="suite|class|manual" , "suite" is default, "class" not implemented yet, that will be in ARQ-236,
        // manual means - start/stop manually
        
        controller.start(CONTAINER1, liveServerProperties);

        controller.start(CONTAINER2, backupServerProperties);
        
        logger.info("mnovak: it works :-)");
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    @Before @After
    public void stopAllServers()    {
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
    
    @BeforeClass
    public static void setupPropertiesForContainers()    {
        
        liveServerProperties = new HashMap<String, String>();
        
        liveServerProperties.put("serverConfig", "standalone-ha-simple-live.xml");
        
        backupServerProperties = new HashMap<String, String>();
        
        backupServerProperties.put("serverConfig", "standalone-ha-simple-backup.xml");
        
    }
    
}