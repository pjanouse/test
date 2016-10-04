package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.PublisherTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.ejb.SimpleSendEJB;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.verifiers.configurable.MessageVerifierFactory;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicToQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbOnlyInbound;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueue;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithSecurity;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.TransactionUtils;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter ARTEMIS RESOURCE ADAPTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
public class RemoteJcaRebalancingTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaRebalancingTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;

    private final Archive mdbNoRebalancing = getMdbNoRebalancing();
    private final Archive mdbWithOnlyInbound = getMdbWithOnlyInboundConnection();
    private final Archive mdbFromTopic = getMdbFromTopic();

    private String messagingGroupSocketBindingName = "messaging-group";

    // queue to send messages in 
    static String inQueueName = "InQueue";
    static String inQueueJndiName = "jms/queue/" + inQueueName;

    static String inTopicName = "InTopic";
    static String inTopicJndiName = "jms/topic/" + inTopicName;

    // queue for receive messages out
    static String outQueueName = "OutQueue";
    static String outQueueJndiName = "jms/queue/" + outQueueName;

    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    public Archive getMdbNoRebalancing() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbNoRebalancing.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueue.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getMdbWithOnlyInboundConnection() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-inbound.jar");
        mdbJar.addClasses(MdbOnlyInbound.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getMdbFromTopic() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "lodhLikemdbFromTopic.jar");
        mdbJar.addClasses(LocalMdbFromTopicToQueue.class, MessageUtils.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis \n"), "MANIFEST.MF");
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public static String createEjbXml(String mdbName) {

        StringBuilder ejbXml = new StringBuilder();

        ejbXml.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        ejbXml.append("<jboss:ejb-jar xmlns:jboss=\"http://www.jboss.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns=\"http://java.sun.com/xml/ns/javaee\"\n");
        ejbXml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        ejbXml.append("xmlns:c=\"urn:clustering:1.0\"\n");
        ejbXml.append("xsi:schemaLocation=\"http://www.jboss.com/xml/ns/javaee http://www.jboss.org/j2ee/schema/jboss-ejb3-2_0.xsd http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/ejb-jar_3_1.xsd\"\n");
        ejbXml.append("version=\"3.1\"\n");
        ejbXml.append("impl-version=\"2.0\">\n");
        ejbXml.append("<enterprise-beans>\n");
        ejbXml.append("<message-driven>\n");
        ejbXml.append("<ejb-name>").append(mdbName).append("</ejb-name>\n");
        ejbXml.append("<ejb-class>org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaninerWithSecurity</ejb-class>\n");
        ejbXml.append("</message-driven>\n");
        ejbXml.append("</enterprise-beans>\n");
        ejbXml.append("</jboss:ejb-jar>\n");
        ejbXml.append("\n");

        return ejbXml.toString();
    }

    public Archive getMdb2() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }


    /**
     * @throws Exception
     * @tpTestDetails Start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using static Netty connectors.
     * Queue InQueue is deployed to server 1,3,
     * Configure RA inbound connection on sever 2 to connect to 1 and 3 server using static Netty connectors.
     * Deploy MDB to server 2 which reads messages from InQueue. Check that all inbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using static Netty connectors.</li>
     * <li>Queue InQueue is deployed to server 1,3. Queue InQueue is deployed to server 1,3,</li>
     * <li>configure RA inbound connection on sever 2 to connect to 1 and 3 server using static Netty connectors.</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue.</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are load-balanced to servers 1,3.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterStaticNettyConnectors() throws Exception {
        testLoadBalancingOfInboundConnectionsToCluster(Constants.CONNECTOR_TYPE.NETTY_NIO);
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using Netty discovery.
     * Queue InQueue is deployed to server 1,3,
     * Configure RA inbound connection on sever 2 to connect to 1 and 3 server using Netty discovery.
     * Deploy MDB to server 2 which reads messages from InQueue. Check that all inbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using static Netty connectors.</li>
     * <li>Queue InQueue is deployed to server 1,3. Queue InQueue is deployed to server 1,3,</li>
     * <li>configure RA inbound connection on sever 2 to connect to 1 and 3 server using static Netty connectors.</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue.</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are load-balanced to servers 1,3.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterNettyDiscovery() throws Exception {
        testLoadBalancingOfInboundConnectionsToCluster(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY);
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using JGroups "udp" discovery.
     * Queue InQueue is deployed to server 1,3,
     * Configure RA inbound connection on sever 2 to connect to 1 and 3 server using JGroups "udp" discovery.
     * Deploy MDB to server 2 which reads messages from InQueue. Check that all inbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using static Netty connectors.</li>
     * <li>Queue InQueue is deployed to server 1,3. Queue InQueue is deployed to server 1,3,</li>
     * <li>configure RA inbound connection on sever 2 to connect to 1 and 3 server using static Netty connectors.</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue.</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are load-balanced to servers 1,3.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterJGroupsDiscovery() throws Exception {
        testLoadBalancingOfInboundConnectionsToCluster(Constants.CONNECTOR_TYPE.JGROUPS_DISCOVERY);
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using JGroups "tcp".
     * Queue InQueue is deployed to server 1,3,
     * Configure RA inbound connection on sever 2 to connect to 1 and 3 server using JGroups "tcp"
     * Deploy MDB to server 2 which reads messages from InQueue. Check that all inbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 3 EAP servers 1, 2 and 3. Severs 1, 3 are in cluster configured using static Netty connectors.</li>
     * <li>Queue InQueue is deployed to server 1,3. Queue InQueue is deployed to server 1,3,</li>
     * <li>configure RA inbound connection on sever 2 to connect to 1 and 3 server using static Netty connectors.</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue.</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are load-balanced to servers 1,3.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterJGroupsTCP() throws Exception {
        testLoadBalancingOfInboundConnectionsToCluster(Constants.CONNECTOR_TYPE.JGROUPS_TCP);
    }


    public void testLoadBalancingOfInboundConnectionsToCluster(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        int numberOfMessagesPerServer = 500;

        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();
        container(3).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessagesPerServer);
        ProducerTransAck producer2 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMessagesPerServer);

        producer1.start();
        producer2.start();
        producer1.join();
        producer2.join();

        // cluster B with mdbs
        container(2).start();
        container(2).deploy(mdbWithOnlyInbound);

        long startTime = System.currentTimeMillis();
        long timeout = 60000;
        while (new JMSTools().countMessages(inQueueName, container(1), container(3)) > 0 && System.currentTimeMillis() - startTime < timeout) {
            logger.info("Waiting for all messages to be read from " + inQueueName);
            Thread.sleep(1000);
        }
        Assert.assertEquals("There are still messages in " + inQueueName + " after timeout " + timeout + "ms.",
                0, new JMSTools().countMessages(inQueueName, container(1), container(3)));

//        int numberOfNewConnections1 = countConnectionOnContainer(container(1)) - initialNumberOfConnections1;
//        int numberOfNewConnections3 = countConnectionOnContainer(container(3)) - initialNumberOfConnections3;
        // get number of consumer from server 3 and 1
        int numberOfConsumer1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        int numberOfConsumer3 = countNumberOfConsumersOnQueue(container(3), inQueueName);
        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer3);

        container(2).undeploy(mdbWithOnlyInbound);
        container(2).stop();
        container(1).stop();
        container(3).stop();

        // check that number of connections is almost equal
//        Assert.assertTrue("Number of connections should be almost equal. Number of new connections on node " + container(1).getName()
//                        + " is " + numberOfNewConnections1 + " and node " + container(3).getName() + " is " + numberOfNewConnections3,
//                Math.abs(numberOfNewConnections1 - numberOfNewConnections3) < 3);
        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                Math.abs(numberOfConsumer1 - numberOfConsumer3) < 3);
        Assert.assertTrue("Number of consumers must be higher than 0, number of consumer on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                numberOfConsumer1 > 0 && numberOfConsumer3 > 0);
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using static Netty connectors.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA (inbound) configured to connect to servers 1 and 3 server using static Netty connectors.
     * Start server 1 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue
     * Wait until MDBs process 1/10 of messages from InQueue and start server 3
     * In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue</li>
     * <li>Wait until MDBs process 1/10 of messages from InQueue and start server 3</li>
     * <li>In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterScaleUpStaticNetty() throws Exception {
        testLoadBalancingOfInboundConnectionsToClusterScaleUp(Constants.CONNECTOR_TYPE.NETTY_NIO);
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using JGroups "tcp" stack.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA (inbound) configured to connect to servers 1 and 3 server using JGroups "tcp" stack.
     * Start server 1 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue
     * Wait until MDBs process 1/10 of messages from InQueue and start server 3
     * In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue</li>
     * <li>Wait until MDBs process 1/10 of messages from InQueue and start server 3</li>
     * <li>In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterScaleUpJgroupsTcp() throws Exception {

        testLoadBalancingOfInboundConnectionsToClusterScaleUp(Constants.CONNECTOR_TYPE.JGROUPS_TCP);

    }

    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using Netty UDP discovery.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA (inbound) configured to connect to servers 1 and 3 server Netty UDP discovery.
     * Start server 1 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue
     * Wait until MDBs process 1/10 of messages from InQueue and start server 3
     * In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue</li>
     * <li>Wait until MDBs process 1/10 of messages from InQueue and start server 3</li>
     * <li>In the moment when MDBs processed 4/5 of messages measure number of consumers on InQueue on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterScaleUpNettyDiscovery() throws Exception {

        testLoadBalancingOfInboundConnectionsToClusterScaleUp(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY);

    }

    public void testLoadBalancingOfInboundConnectionsToClusterScaleUp(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        int numberOfMessages = 10000;
        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(new TextMessageBuilder(1000));
        producer1.start();
        producer1.join();

        container(2).start();
        container(2).deploy(mdbWithOnlyInbound);
        container(4).start();
        container(4).deploy(mdbWithOnlyInbound);

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, numberOfMessages * 9 / 10, 120000);
        // get number of connections and consumers from server 1 and connections
        int initialNumberOfConnections1 = countConnectionOnContainer(container(1));
        int initialNumberOfConsumer1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + initialNumberOfConsumer1 + " and connections " + initialNumberOfConnections1);

        // start 3rd server
        logger.info("Start container node-3");
        container(3).start();
        logger.info("Container node-3 started");

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, numberOfMessages / 5, 120000);

        // get number of consumer from server 3 and 1
        int numberOfConsumer1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        int numberOfConsumer3 = countNumberOfConsumersOnQueue(container(3), inQueueName);

        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));

        // get number of connections from server 3 and 1
        int numberOfNewConnections1 = countConnectionOnContainer(container(1)) - initialNumberOfConnections1;
        int numberOfConnections3 = countConnectionOnContainer(container(3));

        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer1 + " and connections " + numberOfNewConnections1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer3 + " and connections " + numberOfConnections3);

        container(2).undeploy(mdbWithOnlyInbound);
        container(4).undeploy(mdbWithOnlyInbound);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                Math.abs(numberOfConsumer1 - numberOfConsumer3) < 3);
        Assert.assertTrue("Number of consumers must be higher than 0, number of consumer on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                numberOfConsumer1 > 0 && numberOfConsumer3 > 0);

    }

    /**
     * @throws Exception
     * @tpTestDetails There are 4 servers 1, 2, 3 and 4. Deploy InQueue and OutQueue servers
     * to 1 and 3. Servers 1 and 3 are in cluster configured using static Netty connectors.
     * Configure RA on severs 2,4 to connect to server 1,3 using static Netty connectors.
     * Start server 1 and 3 and send 10000 messages to InQueue to 1.
     * Deploy MDB to 2nd, 4 server which reads
     * messages from InQueue. When MDBs are processing messages, stop 3rd server and check that all inbound connections are rebalanced to 1st server.
     * @tpProcedure <ul>
     * <li>start servers 1,3 in cluster with deployed queue InQueue</li>
     * <li>start server 2.4 with deployed MDB which reads messages from InQueue from cluster of servers 1,3</li>
     * <li>stop server 3 and check that consumers/connections are rebalanced to server 1</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are rebalanced to 1st server
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterScaleDownStaticNetty() throws Exception {

        testLoadBalancingOfInboundConnectionsToClusterScaleDown(Constants.CONNECTOR_TYPE.NETTY_NIO);

    }

    public void testLoadBalancingOfInboundConnectionsToClusterScaleDown(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        int numberOfMessages = 10000;
        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();
        container(3).start();

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setTimeout(0);
        producer1.setMessageBuilder(new TextMessageBuilder(1));
        producer1.start();
        producer1.join();

        container(2).start();
        container(2).deploy(mdbWithOnlyInbound);
        container(4).start();
        container(4).deploy(mdbWithOnlyInbound);

        new JMSTools().waitUntilNumberOfMessagesInQueueIsBelow(container(1), inQueueName, numberOfMessages / 20, 120000);

        // get number of consumer from server 3 and 1
        int numberOfConsumerBeforeScaleDown1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        int numberOfConsumerBeforeScaleDown3 = countNumberOfConsumersOnQueue(container(3), inQueueName);
        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumerBeforeScaleDown1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumerBeforeScaleDown3);

        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumerBeforeScaleDown1 + " and on node-3 is: " + numberOfConsumerBeforeScaleDown3,
                Math.abs(numberOfConsumerBeforeScaleDown1 - numberOfConsumerBeforeScaleDown3) < 3);

        // start 3rd server
        logger.info("Stopping container node-3");
        container(3).stop();
        logger.info("Container node-3 stopped");

        Thread.sleep(120000);
        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 30000, container(1));
        // get number of consumers from server 1
        int numberOfConsumerAfterScaleDown1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumerAfterScaleDown1);

        Assert.assertTrue("Number of consumers after scale down should be " + (numberOfConsumerBeforeScaleDown3 + numberOfConsumerBeforeScaleDown1)
                        + ". Number of consumers on node-1 is: " + numberOfConsumerAfterScaleDown1,
                numberOfConsumerBeforeScaleDown3 + numberOfConsumerBeforeScaleDown1 == numberOfConsumerAfterScaleDown1);
        container(2).undeploy(mdbWithOnlyInbound);
        container(2).stop();
        container(4).undeploy(mdbWithOnlyInbound);
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    /**
     * @throws Exception
     * @tpTestDetails There are 3 EAP servers. Severs 1, 3 are in cluster configured using JGroups "tcp" stack.
     * Topic InTopic is deployed on servers 1 and 3,
     * Server 2 has RA (inbound, outbound connections) configured to connect to servers 1 and 3 using JGroups "tcp" stack.
     * Deploy MDB which is consuming from InTopic and sending to OutQueue to server 2. MDB does JNDI lookup for OutQueue for every message. (LODH does that.)
     * Start server 1 and server 2 so MDB creates subscription on InTopic.
     * Stop server 2.
     * Send 10000 (~1b) messages to InTopic
     * Start server 2 with MDB consuming messages from InTopic
     * Wait until MDB process 1/10 of messages from InTopic and start server 3
     * In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and server 2 with MDB to create subscription on InTopic deployed to server 1.</li>
     * <li>Stop server 2</li>
     * <li>Send 10000 (~1Kb) messages to InTopic</li>
     * <li>Start server 2 with MDB consuming messages from InTopic and sending to OutQueue</li>
     * <li>Wait until MDB process 1/10 of messages from InTopic and start server 3</li>
     * <li>In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsScaleUpTopicLodhMdb_JGROUPS_TCP() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        testLoadBalancingOfInboundConnectionsTopic(messageBuilder, Constants.CONNECTOR_TYPE.JGROUPS_TCP);
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 3 EAP servers. Severs 1, 3 are in cluster configured using JGroups "tcp" stack.
     * Topic InTopic is deployed on servers 1 and 3,
     * Server 2 has RA (inbound, outbound connections) configured to connect to servers 1 and 3 using JGroups "tcp" stack.
     * Deploy MDB which is consuming from InTopic and sending to OutQueue to server 2. MDB calls session.createQueue(OutQueue) for every message.
     * Start server 1 and server 2 so MDB creates subscription on InTopic.
     * Stop server 2.
     * Send 10000 (~1b) messages to InTopic
     * Start server 2 with MDB consuming messages from InTopic
     * Wait until MDB process 1/10 of messages from InTopic and start server 3
     * In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and server 2 with MDB to create subscription on InTopic deployed to server 1.</li>
     * <li>Stop server 2</li>
     * <li>Send 10000 (~1Kb) messages to InTopic</li>
     * <li>Start server 2 with MDB consuming messages from InTopic and sending to OutQueue</li>
     * <li>Wait until MDB process 1/10 of messages from InTopic and start server 3</li>
     * <li>In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsScaleUpTopicNormalMdb_JGROUPS_TCP() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(false);
        testLoadBalancingOfInboundConnectionsTopic(messageBuilder, Constants.CONNECTOR_TYPE.JGROUPS_TCP);
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 3 EAP servers. Severs 1, 3 are in cluster configured using static Netty connectors.
     * Topic InTopic is deployed on servers 1 and 3,
     * Server 2 has RA (inbound, outbound connections) configured to connect to servers 1 and 3 using static Netty connectors.
     * Deploy MDB which is consuming from InTopic and sending to OutQueue to server 2. MDB does JNDI lookup for OutQueue for every message. (LODH does that.)
     * Start server 1 and server 2 so MDB creates subscription on InTopic.
     * Stop server 2.
     * Send 10000 (~1b) messages to InTopic
     * Start server 2 with MDB consuming messages from InTopic
     * Wait until MDB process 1/10 of messages from InTopic and start server 3
     * In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and server 2 with MDB to create subscription on InTopic deployed to server 1.</li>
     * <li>Stop server 2</li>
     * <li>Send 10000 (~1Kb) messages to InTopic</li>
     * <li>Start server 2 with MDB consuming messages from InTopic and sending to OutQueue</li>
     * <li>Wait until MDB process 1/10 of messages from InTopic and start server 3</li>
     * <li>In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsScaleUpTopicLodhMdb_STATIC_NETTY() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = new JMSTools().getJndiPropertiesToContainers(container(1), container(3));
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        messageBuilder.setAddDuplicatedHeader(false);
        messageBuilder.setJndiProperties(jndiProperties);
        testLoadBalancingOfInboundConnectionsTopic(messageBuilder, Constants.CONNECTOR_TYPE.NETTY_NIO);
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 3 EAP servers. Severs 1, 3 are in cluster configured using Netty discovery.
     * Topic InTopic is deployed on servers 1 and 3,
     * Server 2 has RA (inbound, outbound connections) configured to connect to servers 1 and 3 using Netty discovery.
     * Deploy MDB which is consuming from InTopic and sending to OutQueue to server 2. MDB calls session.createQueue(OutQueue) for every message.
     * Start server 1 and server 2 so MDB creates subscription on InTopic.
     * Stop server 2.
     * Send 10000 (~1b) messages to InTopic
     * Start server 2 with MDB consuming messages from InTopic
     * Wait until MDB process 1/10 of messages from InTopic and start server 3
     * In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.
     * Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpProcedure <ul>
     * <li>Start server 1 and server 2 with MDB to create subscription on InTopic deployed to server 1.</li>
     * <li>Stop server 2</li>
     * <li>Send 10000 (~1Kb) messages to InTopic</li>
     * <li>Start server 2 with MDB consuming messages from InTopic and sending to OutQueue</li>
     * <li>Wait until MDB process 1/10 of messages from InTopic and start server 3</li>
     * <li>In the moment when MDBs processes all messages measure number of consumers on InTopic on server 1 and 3.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be < 2.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsScaleUpTopicNormalMdb_NETTY_DISCOVERY() throws Exception {
        TextMessageBuilder messageBuilder = new TextMessageBuilder(1);
        messageBuilder.setAddDuplicatedHeader(false);
        testLoadBalancingOfInboundConnectionsTopic(messageBuilder, Constants.CONNECTOR_TYPE.NETTY_DISCOVERY);
    }


    private void testLoadBalancingOfInboundConnectionsTopic(MessageBuilder messageBuilder, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        int numberOfMessages = 10000;
        String clientId = "myClientId";
        String subscriptionName = "mySubscription";
        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();
        container(2).start();
        container(2).deploy(mdbFromTopic);// change here
        // just wait here a while to create subscription
        Thread.sleep(5000);
        container(2).undeploy(mdbFromTopic);// change here
        container(2).stop();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));
        PublisherTransAck producer1 = new PublisherTransAck(container(1), inTopicJndiName, numberOfMessages, "publisher");
        producer1.setTimeout(0);
        producer1.setMessageBuilder(messageBuilder);
        List<FinalTestMessageVerifier> verifiers = new ArrayList<FinalTestMessageVerifier>();
        verifiers.add(messageVerifier);
        producer1.setMessageVerifiers(verifiers);
        producer1.start();
        producer1.join();


        container(2).start();
        container(2).deploy(mdbFromTopic);// change here

        new JMSTools().waitForMessages(outQueueName, numberOfMessages / 10, 300000, container(1));
        // start 3rd server
        logger.info("Start container node-3");
        container(3).start();
        logger.info("Container node-3 started");

        new JMSTools().waitForMessages(outQueueName, numberOfMessages, 300000, container(1), container(3));
        // get number of consumer from server 3 and 1
        int numberOfConsumer1 = countNumberOfConsumersOnTopic(container(1), clientId, subscriptionName);
        int numberOfConsumer3 = countNumberOfConsumersOnTopic(container(3), clientId, subscriptionName);

        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));

        logger.info(container(1).getName() + " - Number of consumers on queue " + inTopicName + " is " + numberOfConsumer1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inTopicName + " is " + numberOfConsumer3);


        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 30000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        container(2).undeploy(mdbFromTopic);
        container(2).stop();
        container(1).stop();
        container(3).stop();

        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                Math.abs(numberOfConsumer1 - numberOfConsumer3) < 2);
        Assert.assertTrue("Number of consumers must be higher than 0, number of consumer on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                numberOfConsumer1 > 0 && numberOfConsumer3 > 0);
        Assert.assertTrue("Message verified found duplicate/lost messages: ", messageVerifier.verifyMessages());
    }


    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using Netty static connectors.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA configured to connect to servers 1 and 3 server using Netty static connectors.
     * Start servers 1,3 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue in XA transaction. MDB calls session.createQueue(OutQueue) for 1st message it processes.
     * When MDBs are processing messages, restart (clean shutdown and start) servers in this order: 1,2,4,3
     * Wait until all messages are processed.
     * Difference between number of consumers on InQueue on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpProcedure <ul>
     * <li>Start server 1,3 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue</li>
     * <li>When MDBs are processing messages, restart (clean shutdown and start) servers in this order: 1,2,4,3</li>
     * <li>Wait until all messages are processed.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterOneServerRestart() throws Exception {

        int numberOfMessages = 10000;

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO);

        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setTimeout(0);
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
        producer1.setMessageBuilder(messageBuilder);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();
        producer1.join();

        container(2).start();
        container(3).start();
        container(4).start();
        container(2).deploy(mdbNoRebalancing);
        container(4).deploy(mdbNoRebalancing);

        container(1).restart();
        container(2).restart();
        container(4).restart();
        container(3).restart();

        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        // get number of consumer from server 3 and 1
        int numberOfConsumer1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        int numberOfConsumer3 = countNumberOfConsumersOnQueue(container(3), inQueueName);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1), 0, true);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3), 0, true);

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 10000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();

        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer3);

        Assert.assertTrue("Message verifier detected lost/duplicated messages.", messageVerifier.verifyMessages());
        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                Math.abs(numberOfConsumer1 - numberOfConsumer3) < 3);
        Assert.assertTrue("Number of consumers must be higher than 0, number of consumer on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                numberOfConsumer1 > 0 && numberOfConsumer3 > 0);

        container(2).undeploy(mdbNoRebalancing);
        container(4).undeploy(mdbNoRebalancing);
        container(2).stop();
        container(1).stop();
        container(3).stop();
        container(4).stop();
    }

    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using Netty static connectors.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA configured to connect to servers 1 and 3 server using Netty static connectors.
     * Start servers 1,3 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue in XA transaction
     * When MDBs are processing messages, clean shutdown servers 1 and 2 then start again. MDB calls session.createQueue(OutQueue) for 1st message it processes.
     * Wait until all messages are processed.
     * Difference between number of consumers on InQueue on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpProcedure <ul>
     * <li>Start server 1,3 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue</li>
     * <li>When MDBs are processing messages, clean shutdown servers 1 and 2 then start again</li>
     * <li>Wait until all messages are processed.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterJmsMdbServerStopStart() throws Exception {
        testLoadBalancingOfInboundConnectionsToClusterTwoServerStopStart(container(2), container(1));
    }


    /**
     * @throws Exception
     * @tpTestDetails There are 4 EAP servers. Severs 1, 3 are in cluster configured using Netty static connectors.
     * Queue InQueue is deployed to servers 1,3,
     * Servers 2 and 4 have RA configured to connect to servers 1 and 3 server using Netty static connectors.
     * Start servers 1,3 and send 10000 (~1Kb) messages to InQueue
     * Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue in XA transaction
     * When MDBs are processing messages, clean shutdown servers 1 and 3 then start again. MDB calls session.createQueue(OutQueue) for 1st message it processes.
     * Wait until all messages are processed.
     * Difference between number of consumers on InQueue on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpProcedure <ul>
     * <li>Start server 1,3 and send 10000 (~1Kb) messages to InQueue</li>
     * <li>Start servers 2,4 with MDB consuming messages from InQueue and sending to OutQueue</li>
     * <li>When MDBs are processing messages, clean shutdown servers 1 and 3 then start again</li>
     * <li>Wait until all messages are processed.</li>
     * </ul>
     * @tpPassCrit >Difference between number of consumers on server 1 and 3 must be <= 2. There is no lost or duplicated message.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToClusterTwoJmsServerStopStart() throws Exception {
        testLoadBalancingOfInboundConnectionsToClusterTwoServerStopStart(container(1), container(3));
    }

    private void testLoadBalancingOfInboundConnectionsToClusterTwoServerStopStart(Container container1, Container container2) throws Exception {

        int numberOfMessages = 10000;

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_NIO);

        container(1).start();

        FinalTestMessageVerifier messageVerifier = MessageVerifierFactory.getMdbVerifier(ContainerUtils.getJMSImplementation(container(1)));
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producer1.setTimeout(0);
        MessageBuilder messageBuilder = new ClientMixMessageBuilder(10, 100);
        producer1.setMessageBuilder(messageBuilder);
        producer1.addMessageVerifier(messageVerifier);
        producer1.start();
        producer1.join();

        container(2).start();
        container(3).start();
        container(2).deploy(mdbNoRebalancing);
        container(4).start();
        container(4).deploy(mdbNoRebalancing);

        // stop start jms and mdb server
        container1.stop();
        container2.stop();
        container1.start();
        container2.start();


        new JMSTools().waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        // get number of consumer from server 3 and 1
        int numberOfConsumer1 = countNumberOfConsumersOnQueue(container(1), inQueueName);
        int numberOfConsumer3 = countNumberOfConsumersOnQueue(container(3), inQueueName);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1), 0, true);
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3), 0, true);

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 10000, 10, 10);
        receiver1.addMessageVerifier(messageVerifier);
        receiver1.setTimeout(0);
        receiver1.start();
        receiver1.join();
        logger.info(container(1).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer1);
        logger.info(container(3).getName() + " - Number of consumers on queue " + inQueueName + " is " + numberOfConsumer3);

        printThreadDumpsOfAllServers();

        Assert.assertTrue("Message verifier detected lost/duplicated messages.", messageVerifier.verifyMessages());
        // assert that number of consumers on both server is almost equal
        Assert.assertTrue("Number of consumers should be almost equal. Number of consumers on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                Math.abs(numberOfConsumer1 - numberOfConsumer3) < 3);
        Assert.assertTrue("Number of consumers must be higher than 0, number of consumer on node-1 is: " + numberOfConsumer1 + " and on node-3 is: " + numberOfConsumer3,
                numberOfConsumer1 > 0 && numberOfConsumer3 > 0);


        container(2).undeploy(mdbNoRebalancing);
        container(4).undeploy(mdbNoRebalancing);
        container(2).stop();
        container(1).stop();
        container(3).stop();
        container(4).stop();
    }

    private void printThreadDumpsOfAllServers() throws IOException {
        ContainerUtils.printThreadDump(container(1));
        ContainerUtils.printThreadDump(container(2));
        ContainerUtils.printThreadDump(container(3));
        ContainerUtils.printThreadDump(container(4));
    }

    private Map<SimpleSendEJB, Context> lookupEjbs(Container container, int numberOfEjbs) throws Exception {
        Map<SimpleSendEJB, Context> ejbs = new HashMap<SimpleSendEJB, Context>();
        for (int i = 0; i < numberOfEjbs; i++) {
            Context ctx = container.getContext(Constants.JNDI_CONTEXT_TYPE.EJB_CONTEXT);
            SimpleSendEJB simpleSendBean = (SimpleSendEJB) ctx.lookup("ejb-sender/SimpleSendEJBStatefulBean!org.jboss.qa.hornetq.apps.ejb.SimpleSendEJB");
            ejbs.put(simpleSendBean, ctx);
        }
        return ejbs;
    }

    private void createConnectionsInEjbsAndSend(Map<SimpleSendEJB, Context> ejbs) {
        for (SimpleSendEJB ejb : ejbs.keySet()) {
            ejb.createConnection();
            ejb.sendMessage();
        }
    }

    private void closeConnectionsInEjb(Map<SimpleSendEJB, Context> ejbs) throws Exception {
        for (SimpleSendEJB ejb : ejbs.keySet()) {
            ejb.closeConnection();
            ejbs.get(ejb).close();
        }
    }

    private int countConnectionOnContainer(Container container) {
        int count;
        JMSOperations jmsOperations = container.getJmsOperations();
        count = jmsOperations.countConnections();
        jmsOperations.close();
        logger.info("Number of connections in container: " + container.getName() + " is " + count);
        return count;
    }

    private int countNumberOfConsumersOnQueue(Container container, String coreQueueName) {

        JMSOperations jmsOperations = container.getJmsOperations();
        int count = jmsOperations.getNumberOfConsumersOnQueue(coreQueueName);
        jmsOperations.close();
        logger.info("Number of consumers on queue: " + coreQueueName + " on container: " + container.getName() + " is " + count);
        return count;
    }

    private int countNumberOfConsumersOnTopic(Container container, String clientId, String subscriptionName) {

        JMSOperations jmsOperations = container.getJmsOperations();
        int count = jmsOperations.getNumberOfConsumersOnTopic(clientId, subscriptionName);
        jmsOperations.close();
        logger.info("Number of consumers on subscription: " + clientId + "." + subscriptionName
                + " on container: " + container.getName() + " is " + count);
        return count;
    }

    public static JavaArchive createDeploymentForLimitedPoolSize(int id) {

        String deploymentName = "mdb-" + id;

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, deploymentName);

        mdbJar.addClass(MdbWithRemoteOutQueueToContaninerWithSecurity.class);

        mdbJar.addAsManifestResource(new StringAsset(createEjbXml(deploymentName)), "jboss-ejb3.xml");

        logger.info(mdbJar.toString(true));
        // Uncomment when you want to see what's in the servlet
        // File target = new File("/tmp/mdb.jar");
        // if (target.exists()) {
        // target.delete();
        // }
        // mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;

    }

    /**
     * Be sure that both of the servers are stopped before and after the test.
     * Delete also the journal directory.
     */
    @Before
    @After
    public void stopAllServers() {

        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    public void prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareRemoteJcaTopologyEAP7(connectorType);
    }


    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareJmsServerEAP7(container(1), connectorType, container(1), container(3));
        prepareMdbServerEAP7(container(2), connectorType, container(1), container(3));

        prepareJmsServerEAP7(container(3), connectorType, container(1), container(3));
        prepareMdbServerEAP7(container(4), connectorType, container(1), container(3));

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String defaultNettySocketBindingName = "messaging";
        String defaultNettyAcceptorName = "netty-acceptor";
        String defaultNettyConnectorName = "netty-connector";
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        String socketBindingPrefix = "socket-binding-to-";
        String connectorPrefix = "connector-to-";
        switch (connectorType) {
            case NETTY_BIO:
                throw new RuntimeException("BIO connectors are not supported with EAP 7");
            case NETTY_NIO:
                // create netty acceptor
                jmsAdminOperations.createSocketBinding(defaultNettySocketBindingName, Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7);
                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(),
                            Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + remoteContainer.getPortOffset());
                }
                jmsAdminOperations.close();

                container.restart();

                jmsAdminOperations = container.getJmsOperations();
                jmsAdminOperations.createRemoteAcceptor(defaultNettyAcceptorName, defaultNettySocketBindingName, null);
                jmsAdminOperations.createRemoteConnector(defaultNettyConnectorName, defaultNettySocketBindingName, null);
                List<String> staticNIOConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    String staticConnectorName = connectorPrefix + remoteContainer.getName();
                    jmsAdminOperations.createRemoteConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), null);
                    staticNIOConnectorsNames.add(staticConnectorName);
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND,
                        1, 1000, true, defaultNettyConnectorName, staticNIOConnectorsNames.toArray(new String[remoteContainers.length]));
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_DISCOVERY:
                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, udpJgroupsStackName, udpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case JGROUPS_TCP:
                String tcpJgroupsStackName = "tcp";
                String tcpJgroupsChannelName = tcpJgroupsStackName;
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(tcpJgroupsStackName);
                jmsAdminOperations.addJGroupsStack(tcpJgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadCastGroupName, tcpJgroupsStackName, tcpJgroupsChannelName, 2000, connectorName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, tcpJgroupsStackName, tcpJgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);
                break;
            case HTTP_CONNECTOR:
                for (Container remoteContainer : remoteContainers) {
                    // create outbound socket bindings
                    jmsAdminOperations.addRemoteSocketBinding(socketBindingPrefix + remoteContainer.getName(), remoteContainer.getHostname(), remoteContainer.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                List<String> staticHttpConnectorsNames = new ArrayList<String>();
                for (Container remoteContainer : remoteContainers) {
                    // create static connector
                    String staticConnectorName = connectorPrefix + remoteContainer.getName();
                    jmsAdminOperations.createHttpConnector(staticConnectorName, socketBindingPrefix + remoteContainer.getName(), null, "http-acceptor");
                    staticHttpConnectorsNames.add(staticConnectorName);
                }
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setStaticClusterConnections("default", clusterGroupName, "jms", Constants.MESSAGE_LOAD_BALANCING_POLICY.ON_DEMAND, 1, 1000, true, connectorName,
                        staticHttpConnectorsNames.toArray(new String[remoteContainers.length]));
                break;
            default:
                throw new RuntimeException("Type of connector unknown for EAP 6");
        }


        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue");
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        jmsAdminOperations.createQueue(inQueueName, inQueueJndiName, true);
        jmsAdminOperations.createQueue(outQueueName, outQueueJndiName, true);

        jmsAdminOperations.createTopic(inTopicName, inTopicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String remoteConnectorName = "http-remote-connector";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setNodeIdentifier(new Random().nextInt(10000));

        setConnectorTypeForPooledConnectionFactoryEAP7(container, connectorType, remoteContainers);

        // set security persmissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        File applicationUsersModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-users.properties");
        try {
            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
        } catch (IOException e) {
            logger.error(e);
        }

        File applicationRolesModified = new File(
                "src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(container(1).getServerHome() + File.separator + "standalone"
                + File.separator + "configuration" + File.separator + "application-roles.properties");
        try {
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        } catch (IOException e) {
            logger.error(e);
        }
        jmsAdminOperations.setHaForPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.close();
        container.stop();
    }

    private void setConnectorTypeForPooledConnectionFactoryEAP7(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteContainers) {
        String remoteSocketBindingPrefix = "socket-binding-to-";
        String remoteConnectorNamePrefix = "connector-to-node-";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        switch (connectorType) {
            case NETTY_BIO:
                throw new RuntimeException("NETTY_BIO connector type is not supported with EAP 7.");
            case NETTY_NIO:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), Constants.PORT_ARTEMIS_NETTY_DEFAULT_EAP7 + c.getPortOffset());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> nioConnectorList = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null);
                    nioConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, nioConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case JGROUPS_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);

                String udpJgroupsStackName = "udp";
                String udpJgroupsChannelName = udpJgroupsStackName;
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, udpJgroupsStackName, udpJgroupsChannelName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case JGROUPS_TCP:
                String jgroupsStackName = "tcp";
                LinkedHashMap<String, Properties> protocols = new LinkedHashMap<String, Properties>();
                Properties tcpPingProperties = new Properties();
                StringBuilder initialHosts = new StringBuilder();
                for (Container c : remoteContainers) {
                    initialHosts.append(c.getHostname()).append("[").append(c.getJGroupsTcpPort()).append("]");
                    initialHosts.append(",");
                }
                initialHosts.deleteCharAt(initialHosts.lastIndexOf(","));
                tcpPingProperties.put("initial_hosts", initialHosts.toString());
                tcpPingProperties.put("port_range", "10");
                tcpPingProperties.put("timeout", "3000");
                tcpPingProperties.put("num_initial_members", String.valueOf(remoteContainers.length));
                protocols.put("TCPPING", tcpPingProperties);
                protocols.put("MERGE2", null);
                protocols.put("FD_SOCK", null);
                protocols.put("FD", null);
                protocols.put("VERIFY_SUSPECT", null);
                protocols.put("pbcast.NAKACK", null);
                protocols.put("UNICAST2", null);
                protocols.put("pbcast.STABLE", null);
                protocols.put("pbcast.GMS", null);
                protocols.put("UFC", null);
                protocols.put("MFC", null);
                protocols.put("FRAG2", null);
                protocols.put("RSVP", null);
                Properties transportProperties = new Properties();
                transportProperties.put("socket-binding", "jgroups-tcp");
                transportProperties.put("type", "TCP");
                jmsAdminOperations.removeJGroupsStack(jgroupsStackName);
                jmsAdminOperations.addJGroupsStack(jgroupsStackName, protocols, transportProperties);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, jgroupsStackName, jgroupsStackName);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP7, discoveryGroupName);
                break;
            case HTTP_CONNECTOR:
                for (Container c : remoteContainers) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), c.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.restart();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                List<String> httpConnectors = new ArrayList<String>();
                for (Container c : remoteContainers) {
                    String remoteHttpConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.createHttpConnector(remoteHttpConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null, "http-acceptor");
                    httpConnectors.add(remoteHttpConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, httpConnectors);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            default:
                throw new RuntimeException("Type of connector unknown for EAP 7");
        }

        // set rebalancing on pooled connection factory
        jmsAdminOperations.setRebalanceConnectionsOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP7, true);
        jmsAdminOperations.close();

    }
}
