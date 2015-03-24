package org.jboss.qa.hornetq.test.integration;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Only EAP 5 test.
 * This test is base on HORNETQ-640.
 */
@RunWith(Arquillian.class)
//@RestoreConfigAfterTest
public class XARecoveryConfigTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ConnectionFactoryTestCase.class);


    @BMRules({
            @BMRule(name = "get xa recovery configs during getXAResources",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    targetLocation = "EXIT",
                    action = "org.jboss.qa.hornetq.test.integration.XARecoveryConfigHelper.checkResult($!);"),
            @BMRule(name = "test",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    action = "System.out.print(\"test: getXAResources called.\");")
    })
    /**
     * This test case verifies that just one xa recovery config is registered with InVMConnector.
     */
    @Test
    @RunAsClient
    public void testOnlySimpleInVMJca() throws Exception {

        container(1).start();

        // deploy helper and rule
        deployer.undeploy("xaRecoverConfigHelper");
        deployer.deploy("xaRecoverConfigHelper");
        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_CONTAINER1_PORT);

        // wait until tx manager call getXAResources
        Thread.sleep(120000);

        BufferedReader in = new BufferedReader(new FileReader(container(1).getServerHome()
                + File.separator + "xa-resources.txt"));

        // there is one live per config
        String line;
        StringBuilder st = new StringBuilder();
        int numberOfLines = 0;
        while ((line = in.readLine()) != null) {
//            logger.info(line);
            numberOfLines++;
            st.append(line);
        }

        // check them
        String xaResources = st.toString();

        Assert.assertNotSame("File xa-resources.txt cannot be empty.", "", xaResources);
        Assert.assertEquals("Only one xa resource should be registered.", numberOfLines, 1);

        if (xaResources.contains("InVMConnectorFactory")) {
            logger.info("InVMConnectorFactory found");
        } else {
            Assert.fail("InVMConnectorFactory not found but is expected.");
        }

        // stop server
        container(1).stop();

    }

    @BMRules({
            @BMRule(name = "get xa recovery configs during getXAResources",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    targetLocation = "EXIT",
                    action = "org.jboss.qa.hornetq.test.integration.XARecoveryConfigHelper.checkResult($!);"),
            @BMRule(name = "test",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    action = "System.out.print(\"test: getXAResources called.\");")
    })
    /**
     * This test case verifies that just one xa recovery config is registered with InVMConnector.
     */
    @Test
    @RunAsClient
    public void testOnlySimpleInVMJcaInCluster() throws Exception {

        prepareMdbServer(container(1), container(1).getHostname(), CONTAINER2_NAME);
        prepareJmsServer(container(2), container(2).getHostname());

        container(1).start();
        container(2).start();

        // deploy helper and rule
        deployer.undeploy("xaRecoverConfigHelper");
        deployer.deploy("xaRecoverConfigHelper");
        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_CONTAINER1_PORT);

        // wait until tx manager call getXAResources
        Thread.sleep(200000);

        BufferedReader in = new BufferedReader(new FileReader(container(1).getServerHome()
                + File.separator + "xa-resources.txt"));

        // there is one live per config
        String line;
        StringBuilder st = new StringBuilder();
        int numberOfLines = 0;
        while ((line = in.readLine()) != null) {
//            logger.info(line);
            numberOfLines++;
            st.append(line);
        }

        // check them
        String xaResources = st.toString();

        Assert.assertNotSame("File xa-resources.txt cannot be empty.", "", xaResources);
        Assert.assertEquals("Only one xa resource should be registered.", numberOfLines, 1);

        if (xaResources.contains("InVMConnectorFactory")) {
            logger.info("InVMConnectorFactory found");
        } else {
            Assert.fail("InVMConnectorFactory not found but is expected.");
        }

        // stop server
        container(1).stop();
        container(2).stop();

    }
//    @Test
//    @RunAsClient
////    @RestoreConfigAfterTest
//    public void testByteman() throws Exception {
//    // deploy helper and rule
//    //deployer.undeploy("xaRecoverConfigHelper");
//    //deployer.deploy("xaRecoverConfigHelper");
//    RuleInstaller.installRule(this.getClass(), CONTAINER1_NAME_IP, BYTEMAN_CONTAINER1_NAME_PORT);
//
//    // wait until tx manager call getXAResources
//    Thread.sleep(120000);
//
//    BufferedReader in = new BufferedReader(new FileReader(ConfigurationLoader.getJbossHome(CONTAINER1_NAME_NAME)
//            + File.separator + "xa-resources.txt"));
//
//    // there is one live per config
//    String line;
//    StringBuilder st = new StringBuilder();
//    int numberOfLines = 0;
//    while ((line = in.readLine()) != null) {
//        logger.info(line);
//        numberOfLines++;
//        st.append(line);
//    }
//    }

    @BMRules({
            @BMRule(name = "get xa recovery configs during getXAResources",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    targetLocation = "EXIT",
                    action = "org.jboss.qa.hornetq.test.integration.XARecoveryConfigHelper.checkResult($!);"),
            @BMRule(name = "test",
                    targetClass = "org.hornetq.jms.server.recovery.HornetQRecoveryRegistry",
                    targetMethod = "getXAResources",
                    action = "System.out.print(\"test: getXAResources called.\");")
    })
    @Test
    @RunAsClient
//    @RestoreConfigAfterTest
    public void testRemoteJcaInCluster() throws Exception {
        // jms server are 2 and 3
        // mdb server is 1 = > 2
        prepareMdbServer(container(1), container(1).getHostname(), CONTAINER2_NAME);

        prepareJmsServer(container(2), container(2).getHostname());
        prepareJmsServer(container(3), container(3).getHostname());

        container(1).start();
        container(2).start();
        container(3).start();

        // deploy helper and rule
        deployer.undeploy("xaRecoverConfigHelper");
        deployer.deploy("xaRecoverConfigHelper");
        RuleInstaller.installRule(this.getClass(), container(1).getHostname(), BYTEMAN_CONTAINER1_PORT);

        // wait until tx manager call getXAResources
        Thread.sleep(300000);

        File xaRecoveryConfigFile = new File(container(1).getServerHome()
                + File.separator + "xa-resources.txt");
        BufferedReader in = null;
        int numberOfLines = 0;
        StringBuilder st1 = new StringBuilder();
        try {

            in = new BufferedReader(new FileReader(xaRecoveryConfigFile));
            // there is one live per config
            String line;
            while ((line = in.readLine()) != null) {
                logger.info(line);
                numberOfLines++;
                st1.append(line);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        // check them
        String xaResources1 = st1.toString();

        Assert.assertNotSame("File xa-resources.txt cannot be empty.", "", xaResources1);

        if (xaResources1.contains("NettyConnectorFactory")) {
            logger.info("NettyConnectorFactory found. This is expected.");
        } else {
            Assert.fail("NettyConnectorFactory not found but is expected.");
        }

        // if ips of jms servers are not present then fail the test
        if (!xaResources1.replaceAll("-", ".").contains(container(2).getHostname()) || !xaResources1.replaceAll("-", ".").contains(getHostname(


                CONTAINER3_NAME))) {

            Assert.fail(container(2).getHostname() + " or " + container(3).getHostname() + " not found but are expected in: " + xaResources1);

        }

        // remove xa-resources.txt (just for sure)
        if (xaRecoveryConfigFile.exists()) {
            xaRecoveryConfigFile.delete();
        }

        // stop 3. server to see whether xa recovery config for this node disappears
        container(3).stop();

        // wait until tx manager call getXAResources
        Thread.sleep(300000);

        // expecting only
        StringBuilder st2 = new StringBuilder();
        try {

            in = new BufferedReader(new FileReader(xaRecoveryConfigFile));
            // there is one live per config
            String line;
            while ((line = in.readLine()) != null) {
                logger.info(line);
                numberOfLines++;
                st2.append(line);
            }
        } finally {
            in.close();
        }

        // check them
        String xaResources2 = st2.toString();

        Assert.assertNotSame("File xa-resources.txt cannot be empty.", "", xaResources2);
        Assert.assertEquals("Only one xa resource should be registered: " + xaResources2, 1, numberOfLines);

        if (xaResources2.contains("NettyConnectorFactory")) {
            logger.info("NettyConnectorFactory found. This is expected.");
        } else {
            Assert.fail("NettyConnectorFactory not found but is expected.");
        }

        // if ips of jms 2 server is not present then fail the test
        if (!xaResources2.replaceAll("-", ".").contains(container(2).getHostname())) {

            Assert.fail(container(2).getHostname() + " not found but is expected in: " + xaResources2);

        }

        // if ip of jms 3 server is present then fail the test
        if (xaResources2.replaceAll("-", ".").contains(container(3).getHostname())) {

            Assert.fail(container(3).getHostname() + " found but is not expected in: " + xaResources2);

        }

        container(1).stop();
        container(2).stop();
        container(3).stop();
    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container      test container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     */
    private void prepareJmsServer(Container container, String bindingAddress) {

        String broadCastGroupName = "bg-group1";
        String discoveryGroupName = "dg-group1";
        String clusterGroupName = "my-cluster";
        int port = 9876;
        String groupAddress = "233.6.88.3";
        int groupPort = 9876;
        long broadcastPeriod = 500;
        String connectorName = "netty";

//        controller.start(containerName);

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);


        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
        jmsAdminOperations.close();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServer(Container container, String bindingAddress, String jmsServerName) {

        String broadCastGroupName = "bg-group1";
        String discoveryGroupName = "dg-group1";
        String clusterGroupName = "my-cluster";
        int port = 9876;
        String groupAddress = "233.6.88.5";
        int groupPort = 9876;
        long broadcastPeriod = 500;
        String connectorName = "netty";

        String connectorClassName = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory";
        Map<String, String> connectionParameters = new HashMap<String, String>();
        connectionParameters.put(getHostname(jmsServerName), String.valueOf(getHornetqPort(jmsServerName)));
        boolean ha = false;

//        controller.start(containerName);

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, port, groupAddress, groupPort, broadcastPeriod, connectorName, null);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, groupAddress, groupPort, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

//        Map<String, String> params = new HashMap<String, String>();
//        params.put("host", jmsServerBindingAddress);
//        params.put("port", "5445");
//        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "", params);

        jmsAdminOperations.setRA(connectorClassName, connectionParameters, ha);
        jmsAdminOperations.close();
        container.stop();
    }


    @Deployment(testable = false, name = "xaRecoverConfigHelper", managed = false)
    @TargetsContainer(CONTAINER1_NAME)
    public static JavaArchive createXaRecoverConfigHelper() {

        JavaArchive helper = ShrinkWrap.create(JavaArchive.class, "xaRecoverConfigHelper.jar");
        helper.addClass(XARecoveryConfigHelper.class);
        helper.addManifest();
        //  Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/helper.jar");
        if (target.exists()) {
            target.delete();
        }
        helper.as(ZipExporter.class).exportTo(target, true);
        return helper;

    }
}
