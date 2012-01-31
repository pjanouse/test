package org.jboss.hornetq.test.cluster;

import org.jboss.hornetq.test.failover.*;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.hornetq.apps.clients.ProducerClientAckNonHA;
import org.jboss.hornetq.apps.servlets.HornetQTestServlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author mnovak
 */
@RunWith(Arquillian.class)
public class ClusterTest {
    
    private static final Logger logger = Logger.getLogger(ProducerClientAckNonHA.class);

    public static final String CONTAINER1 = "clustering-udp-0-unmanaged";
    
    public static final String CONTAINER2 = "clustering-udp-1-unmanaged";
    
    private static final String DEPLOYMENT1 = "dep.container1";
    
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
    
    // This test will start two servers in cluster
    // Sent some messages to first
    // Receive messages from the second one
    @Test   
    public void simpleClusterTest() throws Exception {
        //CONTAINER2 variable corresponds to container's name specified via qualifier arquillian.xml
        // ... <container qualifier="container2" mode="manual">  
        //other values for are mode="suite|class|manual" , "suite" is default, "class" not implemented yet, that will be in ARQ-236,
        // manual means - start/stop manually
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
        controller.start(CONTAINER1);
        
        deployer.deploy(DEPLOYMENT1);
        
        controller.start(CONTAINER2);        
        
        controller.stop(CONTAINER1);
        
        
        controller.start(CONTAINER1);
        
        controller.stop(CONTAINER1);
        
        controller.stop(CONTAINER2);
        
    }
}