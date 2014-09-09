package org.jboss.qa.hornetq.test.domain;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class DomainDeploymentTestCase {

    @ArquillianResource
    private ContainerController controller;

    @ArquillianResource
    private Deployer deployer;

    @Deployment(name = "test-domain-deployment", managed = false)
    @TargetsContainer("backend")
    public static WebArchive createWebArchive() {
        return ShrinkWrap.create(WebArchive.class);
    }

//    @Test
//    @InSequence(1)
//    @RunAsClient
//    public void startServer() throws Exception {
//        controller.start("backend-2");
//    }
//
//    @Test
//    @InSequence(2)
//    @TargetsContainer("backend-2")
//    public void testInContainer() throws Exception {
//        System.out.println("in container");
//    }

    @Test
    //@TargetsContainer("node-1")
    public void testDeploymentNode1() throws Exception {
        controller.start("backend-2");
        deployer.deploy("test-domain-deployment");
        controller.stop("backend-2");
    }

}
