package org.jboss.qa.hornetq.test.domain;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.DomainOperations;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class DomainDeploymentTestCase { // extends HornetQTestCase {

    @ArquillianResource
    private ContainerController controller;

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = "server-group-deployment", managed = false)
    @TargetsContainer("server-group-1")
    public static WebArchive createServerGroupWebArchive() {
        return ShrinkWrap.create(WebArchive.class);
    }

//    @Deployment(name = "server-deployment", managed = false)
//    @TargetsContainer("master:server-1")
//    public static WebArchive createServerWebArchive() {
//        return ShrinkWrap.create(WebArchive.class);
//    }

    @Deployment(name = "node-deployment", managed = false)
    @TargetsContainer("node-2")
    public static WebArchive createNodeWebArchive() {
        return ShrinkWrap.create(WebArchive.class);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDeploymentNode1() throws Exception {
        assert controller.isStarted("server-group-1");

        DomainOperations domainOps = DomainOperations.forDefaultContainer();
        // remove the default nodes/server groups
        domainOps.removeServer("server-1");
        domainOps.removeServer("server-2");

        domainOps.createServer("server-1", "server-group-1");
        domainOps.createServer("server-2", "server-group-1", 20000);
        domainOps.close();

        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        eap6AdmOps.setHostname("localhost");
        eap6AdmOps.setPort(9999);
        eap6AdmOps.connect();

        JMSOperations ops = eap6AdmOps;
        ops.addAddressPrefix("profile", "full-ha-1");
        ops.createQueue("TestQueue", "jms/queue/TestQUeue");
        ops.close();

        controller.start("node-1");
        controller.start("node-2");

        deployer.deploy("server-group-deployment");
//        deployer.deploy("server-deployment");
//        deployer.deploy("node-deployment");

//        deployer.undeploy("node-deployment");
//        deployer.undeploy("server-deployment");
        //deployer.undeploy("server-group-deployment");

        controller.stop("node-2");
        controller.stop("node-1");
    }

}
