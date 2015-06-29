package org.jboss.qa.hornetq.test.domain;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.DomainContainer;
import org.jboss.qa.hornetq.DomainHornetQTestCase;
import org.jboss.qa.hornetq.DomainServerGroup;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.categories.DomainTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@Category(DomainTests.class)
public class DomainDeploymentTestCase extends DomainHornetQTestCase {

    private static final Logger LOG = Logger.getLogger(DomainDeploymentTestCase.class);

    // @ArquillianResource
    // private ContainerController controller;

    // @ArquillianResource
    // private Deployer deployer;

    private final WebArchive SERVER_GROUP_DEPLOYMENT = createServerGroupWebArchive();

    // @Deployment(name = "server-group-deployment", managed = false)
    // @TargetsContainer("server-group-1")
    private WebArchive createServerGroupWebArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(LocalMdbFromQueue.class);
    }

    // @Deployment(name = "server-deployment", managed = false)
    // @TargetsContainer("master:server-1")
    // public static WebArchive createServerWebArchive() {
    // return ShrinkWrap.create(WebArchive.class);
    // }

    // @Deployment(name = "node-deployment", managed = false)
    // @TargetsContainer("node-2")
    public WebArchive createNodeWebArchive() {
        return ShrinkWrap.create(WebArchive.class);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDeployment() throws Exception {
        DomainContainer host = domainContainer();

        // delete server 1/2
        host.serverGroup("server-group-2").deleteNode("server-2");
        host.serverGroup("server-group-1").deleteNode("server-1");

        // recreate both under server-group-1
        host.serverGroup("server-group-1").createNode("server-1");
        host.serverGroup("server-group-1").createNode("server-2", 1000);
        host.reloadDomain();

        DomainServerGroup serverGroup1 = host.serverGroup("server-group-1");
        JMSOperations ops = serverGroup1.getJmsOperations();
        ops.createQueue("InQueue", "jms/queue/InQueue");
        ops.createQueue("OutQueue", "jms/queue/OutQueue");
        ops.close();

        serverGroup1.startAllNodes();
        serverGroup1.deploy(SERVER_GROUP_DEPLOYMENT);
        Thread.sleep(5000);
        serverGroup1.undeploy(SERVER_GROUP_DEPLOYMENT);
        Thread.sleep(5000);
        serverGroup1.stopAllNodes();
    }

}
