package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.ejb.SimpleSendEJB;
import org.jboss.qa.hornetq.apps.ejb.SimpleSendEJBStatefulBean;
import org.jboss.qa.hornetq.apps.ejb.SimpleSendEJBStatelessBean;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.*;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
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
import java.util.List;
import java.util.Map;

/**
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ RESOURCE ADAPTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-lodh
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 */
@RunWith(Arquillian.class)
public class RemoteJcaTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    private final Archive mdb1 = getMdb1();
    private final Archive lodhLikemdb = getLodhLikeMdb();
    private final Archive mdbWithOnlyInbound = getMdbWithOnlyInboundConnection();
    private final Archive ejbSenderStatefulBean = getEjbSenderStatefulBean();
    private final Archive ejbSenderStatelessBean = getEjbSenderStatelessBean();
    private final Archive mdb1OnNonDurable = getMdb1OnNonDurable();
    private final Archive mdb2 = getMdb2();

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

    public Archive getMdb1() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getLodhLikeMdb() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "lodhLikemdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueWithOutQueueLookups.class, MessageUtils.class);
        if (container(2).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        } else {
            mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.apache.activemq.artemis \n"), "MANIFEST.MF");
        }
        logger.info(mdbJar.toString(true));
        return mdbJar;

    }

    public Archive getMdbWithOnlyInboundConnection() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb-inbound.jar");
        mdbJar.addClasses(MdbFromQueueNotToRemoteQueue.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getEjbSenderStatefulBean() {
        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "ejb-sender.jar");
        ejbJar.addClasses(SimpleSendEJB.class, SimpleSendEJBStatefulBean.class);
        logger.info(ejbJar.toString(true));
        File target = new File("/tmp/ejb-sender.jar");
        if (target.exists()) {
            target.delete();
        }
        ejbJar.as(ZipExporter.class).exportTo(target, true);
        return ejbJar;
    }

    public Archive getEjbSenderStatelessBean() {
        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "ejb-sender.jar");
        ejbJar.addClasses(SimpleSendEJB.class, SimpleSendEJBStatelessBean.class);
        logger.info(ejbJar.toString(true));
        File target = new File("/tmp/ejb-sender-stateless.jar");
        if (target.exists()) {
            target.delete();
        }
        ejbJar.as(ZipExporter.class).exportTo(target, true);
        return ejbJar;
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

    public Archive getMdb1OnNonDurable() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, mdb1OnNonDurable + ".jar");
        mdbJar.addClasses(MdbFromNonDurableTopicWithOutQueueToContaniner1.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getMdb2() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb2.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner2.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;
    }

    public JavaArchive getMdbWithConnectionParameters() {

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbWithConnectionParameters.jar");
        mdbJar.addClasses(MdbWithConnectionParameters.class);

        logger.info(mdbJar.toString(true));

        // Uncomment when you want to see what's in the servlet
        File target = new File("/tmp/mdb.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 4 servers(1, 2, 3, 4). Deploy InQueue and OutQueue
     * to 1,2. Configure ActiveMQ RA on sever 3,4 to connect to 1,2 server. Send
     * messages to InQueue to 1,2. Deploy MDB to 3,4 servers which reads
     * messages from InQueue and sends them to OutQueue. Read messages from
     * OutQueue from 1,2
     * @tpProcedure <ul>
     * <li>start 2 servers with deployed InQueue and OutQueue</li>
     * <li>start 2 servers which have configured HornetQ RA to connect to first 2 servers</li>
     * <li>deploy MDB to other servers which reads messages from InQueue and sends to OutQueue</li>
     * <li>start producer which sends messagese to InQueue to first 2 server</li>
     * <li>start 2 servers which have configured HornetQ RA to connect to first 2 servers</li>
     * <li>deploy MDB to other servers which reads messages from InQueue and sends to OutQueue</li>
     * <li>start producer which sends messagese to InQueue to first 2 server</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJcaInCluster() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        // cluster A
        container(1).start();
        container(3).start();
        // cluster B with mdbs
        container(2).start();
        container(4).start();

        container(2).deploy(mdb1);
        container(4).deploy(mdb2);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerTransAck producer2 = new ProducerTransAck(container(3), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 10);
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(3), outQueueJndiName, 3000, 10, 10);

        receiver1.start();
        receiver2.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size() + producer2.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());

        container(2).undeploy(mdb1);
        container(4).undeploy(mdb2);
        container(2).stop();
        container(4).stop();
        container(1).stop();
        container(3).stop();

    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testRemoteJcaWithLoad() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);

        Archive mdbToDeploy = lodhLikemdb;

        // cluster A
        container(1).start();

        // cluster B
        container(2).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, 500);
        TextMessageBuilder textMessageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = (Map<String, String>) container(1).getContext().getEnvironment();
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        textMessageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(textMessageBuilder);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
//        container(4).deploy(mdb1);

        // bind mdb EAP server to cpu core
        String cpuToBind = "0";
        final long pid = ProcessIdUtils.getProcessId(container(2));
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(pid), cpuToBind);
        ProcessIdUtils.setPriorityToProcess(String.valueOf(pid), 19);
        logger.info("Container 2 was bound to cpu: " + cpuToBind);

        Process highCpuLoader = HighCPUUtils.generateLoadInSeparateProcess();
        int highCpuLoaderPid = ProcessIdUtils.getProcessId(highCpuLoader);
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(highCpuLoaderPid), cpuToBind);
        logger.info("High Cpu loader was bound to cpu: " + cpuToBind);

        // Wait until InQueue is empty
        waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        logger.info("There are no prepared transactions on node-1.");

        highCpuLoader.destroy();
//        producer1.stopSending();
//        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 10000, 10, 10);
        receiver1.start();
        receiver1.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(2).undeploy(mdbToDeploy);
        container(2).stop();
        container(1).stop();
    }

    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    @RunAsClient
    public void testRemoteJcaWithLoadInCluster() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);

        Archive mdbToDeploy = lodhLikemdb;

        // cluster A
        container(1).start();
        container(3).start();

        // cluster B
        container(2).start();
        container(4).start();

        // send messages to queue
        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, 500);
        TextMessageBuilder textMessageBuilder = new TextMessageBuilder(1);
        Map<String, String> jndiProperties = (Map<String, String>) container(1).getContext().getEnvironment();
        for (String key : jndiProperties.keySet()) {
            logger.warn("key: " + key + " value: " + jndiProperties.get(key));
        }
        textMessageBuilder.setJndiProperties(jndiProperties);
        producer1.setMessageBuilder(textMessageBuilder);
        producer1.setCommitAfter(100);
        producer1.start();
        producer1.join();

        // deploy mdb
        container(2).deploy(mdbToDeploy);
        container(4).deploy(mdbToDeploy);

        // bind mdb EAP server to cpu core
        String cpuToBind = "0";
        final long pid = ProcessIdUtils.getProcessId(container(2));
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(pid), cpuToBind);
        ProcessIdUtils.setPriorityToProcess(String.valueOf(pid), 19);
        logger.info("Container 2 was bound to cpu: " + cpuToBind);

        Process highCpuLoader = HighCPUUtils.generateLoadInSeparateProcess();
        int highCpuLoaderPid = ProcessIdUtils.getProcessId(highCpuLoader);
        BindProcessToCpuUtils.bindProcessToCPU(String.valueOf(highCpuLoaderPid), cpuToBind);
        logger.info("High Cpu loader was bound to cpu: " + cpuToBind);

        // Wait until some messages are consumes from InQueue
        waitUntilMessagesAreStillConsumed(inQueueName, 300000, container(1), container(3));
        logger.info("No messages can be consumed from InQueue. Stop Cpu loader and receive all messages.");
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(1));
        new TransactionUtils().waitUntilThereAreNoPreparedHornetQTransactions(300000, container(3));
        logger.info("There are no prepared transactions on node-1 and node-3.");
        highCpuLoader.destroy();
        producer1.stopSending();
        producer1.join();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 10000, 10, 10);
        receiver1.start();
        receiver1.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(2).undeploy(mdbToDeploy);
        container(4).undeploy(mdbToDeploy);
        container(2).stop();
        container(4).stop();
        container(3).stop();
        container(1).stop();
    }

    /**
     * It will check whether messages are still consumed from this queue. It will return after timeout or there is 0 messages
     * in queue.
     * @param queueName
     * @param timeout
     * @param containers
     */
    private void waitUntilMessagesAreStillConsumed(String queueName, long timeout, Container... containers) throws Exception {
        long startTime = System.currentTimeMillis();
        long lastCount = new JMSTools().countMessages(inQueueName, container(1), container(3));
        long newCount = new JMSTools().countMessages(inQueueName, container(1), container(3));
        while ((newCount = new JMSTools().countMessages(inQueueName, container(1), container(3))) > 0)  {
            // check there is a change
                // if yes then change lastCount and start time
                // else check time out and if timed out then return
            if (lastCount - newCount > 0)   {
                lastCount = newCount;
                startTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - startTime > timeout)    {
                return;
            }
            Thread.sleep(5000);
        }
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 servers(1, 2, 3). Deploy InQueue
     * to 1,2. Configure ActiveMQ RA on sever 3 to connect to 1,2 server. Send
     * messages to InQueue to 1,2. Deploy MDB to 3rd server which reads
     * messages from InQueue. Check that all inbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 2 servers with deployed InQueue</li>
     * <li>deploy MDB to other server which reads messages from InQueue</li>
     * <li>start producer which sends messagese to InQueue to first 2 server</li>
     * </ul>
     * @tpPassCrit Check that all inbound connections are load-balanced.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfInboundConnectionsToCluster() throws Exception {

        int numberOfMessagesPerServer = 500;
        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        // cluster A
        container(1).start();
        container(3).start();

        // remember number of connection on server 1 and 3
        int initialNumberOfConnections1 = countConnectionOnContainer(container(1));
        int initialNumberOfConnections3 = countConnectionOnContainer(container(3));


        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessagesPerServer);
        ProducerTransAck producer2 = new ProducerTransAck(container(3), inQueueJndiName, numberOfMessagesPerServer);

        producer1.start();
        producer2.start();

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        // cluster B with mdbs
        container(2).start();
        container(2).deploy(mdbWithOnlyInbound);

        long startTime = System.currentTimeMillis();
        long timeout = 60000;
        while (countMessagesAcrossCluster(inQueueName, container(1), container(3)) > 0 && System.currentTimeMillis() - startTime < timeout) {
            logger.info("Waiting for all messages to be read from " + inQueueName);
            Thread.sleep(1000);
        }
        Assert.assertEquals("There are still messages in " + inQueueName + " after timeout " + timeout + "ms.",
                0, countMessagesAcrossCluster(inQueueName, container(1), container(3)));

        int numberOfNewConnections1 = countConnectionOnContainer(container(1)) - initialNumberOfConnections1;
        int numberOfNewConnections3 = countConnectionOnContainer(container(3)) - initialNumberOfConnections3;

        container(2).undeploy(mdbWithOnlyInbound);
        container(2).stop();
        container(1).stop();
        container(3).stop();

        // check that number of connections is almost equal
        Assert.assertTrue("Number of connections should be almost equal. Number of new connections on node " + container(1).getName()
                        + " is " + numberOfNewConnections1 + " and node " + container(3).getName() + " is " + numberOfNewConnections3,
                Math.abs(numberOfNewConnections1 - numberOfNewConnections3) < 3);
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 servers(1, 2, 3). Deploy OutQueue
     * to 1,2. Configure ActiveMQ RA on sever 3 to connect to 1,2 server. Deploy EJB
     * to 3rd server which sends
     * messages to OutQueue. Check that all outbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 2 servers with deployed OutQueue</li>
     * <li>deploy EJB to other server which sends messages to OutQueue</li>
     * </ul>
     * @tpPassCrit Check that all outbound connections are load-balanced.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfOutboundConnectionsToClusterBIO() throws Exception {
        testLoadBalancingOfOutboundConnectionsToCluster(Constants.CONNECTOR_TYPE.NETTY_BIO);
    }

    /**
     * @throws Exception
     * @tpTestDetails Start 3 servers(1, 2, 3). Deploy OutQueue
     * to 1,2. Configure ActiveMQ RA on sever 3 to connect to 1,2 server. Deploy EJB
     * to 3rd server which sends
     * messages to OutQueue. RA is using discovery group to find servers 1,2 which are in cluster.
     * Check that all outbound connections are load-balanced.
     * @tpProcedure <ul>
     * <li>start 2 servers with deployed OutQueue</li>
     * <li>deploy EJB to other server which sends messages to OutQueue</li>
     * </ul>
     * @tpPassCrit Check that all outbound connections are load-balanced.
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfOutboundConnectionsToClusterNettyDiscovery() throws Exception {
        testLoadBalancingOfOutboundConnectionsToCluster(Constants.CONNECTOR_TYPE.NETTY_DISCOVERY);
    }

    public void testLoadBalancingOfOutboundConnectionsToCluster(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        int numberOfEjbs = 20;
        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();
        container(3).start();

        // remember number of connection on server 1 and 3
        int initialNumberOfConnections1 = countConnectionOnContainer(container(1));
        int initialNumberOfConnections3 = countConnectionOnContainer(container(3));

        // cluster B with mdbs
        container(2).start();
        container(2).deploy(ejbSenderStatefulBean);

        // lookup 100 ejbs
        Map<SimpleSendEJB, Context> ejbs = lookupEjbs(container(2), numberOfEjbs);

        // call create connection on all and send message
        createConnectionsInEjbsAndSend(ejbs);

//        Thread.sleep(100000);

        // measure connections
        int numberOfNewConnections1 = countConnectionOnContainer(container(1)) - initialNumberOfConnections1;
        int numberOfNewConnections3 = countConnectionOnContainer(container(3)) - initialNumberOfConnections3;

        // close all ejbs
        closeConnectionsInEjb(ejbs);

        Assert.assertEquals("There is wrong number of messages in " + outQueueName + ". Expected number of messages is: " + numberOfEjbs,
                numberOfEjbs, countMessagesAcrossCluster(outQueueName, container(1), container(3)));


        container(2).undeploy(ejbSenderStatefulBean);

        container(2).stop();
        container(1).stop();
        container(3).stop();

        // check that number of connections is almost equal
        Assert.assertTrue("Number of connections should be almost equal. Number of new connections on node " + container(1).getName()
                        + " is " + numberOfNewConnections1 + " and node " + container(3).getName() + " is " + numberOfNewConnections3,
                Math.abs(numberOfNewConnections1 - numberOfNewConnections3) < 3);
    }

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testLoadBalancingOfOutboundConnectionsToClusterWithFail() throws Exception {
        Constants.CONNECTOR_TYPE connectorType = Constants.CONNECTOR_TYPE.NETTY_BIO;
        int numberOfEjbs = 20;
        prepareRemoteJcaTopology(connectorType);
        // cluster A
        container(1).start();
        container(3).start();

        // cluster B with mdbs
        container(2).start();
        container(2).deploy(ejbSenderStatefulBean);

        // remember number of connection on server 1 and 3
        int initialNumberOfConnections1 = countConnectionOnContainer(container(1));
        int initialNumberOfConnections3 = countConnectionOnContainer(container(3));

        // lookup ejbs
        Map<SimpleSendEJB, Context> ejbs = lookupEjbs(container(2), numberOfEjbs);

        // call create connection on all and send message
        createConnectionsInEjbsAndSend(ejbs);

        // close all ejbs
        closeConnectionsInEjb(ejbs);

        // execute fail on node-1
        container(1).fail(Constants.FAILURE_TYPE.KILL);

        // start node 1 and measure connections again
        container(1).start();

        logger.info("Sending new messages after restarting " + container(1).getName());

        // lookup new ejbs
        ejbs = lookupEjbs(container(2), numberOfEjbs);
        createConnectionsInEjbsAndSend(ejbs);

        // measure connections
        int numberOfNewConnections1 = countConnectionOnContainer(container(1)) - initialNumberOfConnections1;
        int numberOfNewConnections3 = countConnectionOnContainer(container(3)) - initialNumberOfConnections3;

        closeConnectionsInEjb(ejbs);

        Assert.assertEquals("There is wrong number of messages in " + outQueueName + ". Expected number of messages is: " + 2 * numberOfEjbs,
                2 * numberOfEjbs, countMessagesAcrossCluster(outQueueName, container(1), container(3)));

        container(2).undeploy(ejbSenderStatefulBean);

        container(2).stop();
        container(1).stop();
        container(3).stop();

        // check that number of connections is almost equal
        Assert.assertTrue("Number of connections should be almost equal. Number of new connections on node " + container(1).getName()
                        + " is " + numberOfNewConnections1 + " and node " + container(3).getName() + " is " + numberOfNewConnections3,
                Math.abs(numberOfNewConnections1 - numberOfNewConnections3) < 3);
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

    private long countMessagesAcrossCluster(String queue, Container... containers) {
        long count = 0;
        for (Container container : containers) {
            JMSOperations jmsOperations = container.getJmsOperations();
            count = count + jmsOperations.getCountOfMessagesOnQueue(queue);
            jmsOperations.close();
        }
        return count;
    }


    private int countConnectionOnContainer(Container container) {
        int count = 0;
        JMSOperations jmsOperations = container.getJmsOperations();
        count = jmsOperations.countConnections();
        jmsOperations.close();
        logger.info("Number of connections in container: " + container.getName() + " is " + count);
        return count;
    }

    /**
     * @throws Exception
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first.
     * Configure HornetQ RA on second sever to connect to first server. Send
     * messages to InQueue. Deploy MDB do second server which reads messages
     * from InQueue and sends them to OutQueue. Read messages from OutQueue
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJca() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        // cluster A
        container(1).start();

        // cluster B
        container(2).start();

        container(2).deploy(mdb1);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 10);

        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer1.join();

        receiver1.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(2).undeploy(mdb1);
        container(2).stop();
        container(1).stop();

    }

    /**
     * @throws Exception
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first. Configure RA on second sever to
     * connect to first server. Send messages to InQueue. Deploy 60+ MDBs to second server which reads messages from InQueue
     * and sends them to OutQueue. Read messages from OutQueue
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy 60+ MDBs do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJcaWithManyMDB() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        // cluster A
        container(1).start();

        // cluster B
        container(2).start();

        for (int j = 1; j < 30; j++) {
            container(2).deploy(createDeploymentForLimitedPoolSize(j));
        }

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 10);

        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer1.join();

        receiver1.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());

        container(2).stop();
        container(1).stop();

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
     * @throws Exception
     * @tpTestDetails Start two servers. Deploy InQueue OutQueue and InTopic to
     * first. Configure HornetQ RA on second sever to connect to first server.
     * Send messages to InQueue. Deploy MDB do second server which creates non
     * durable subscription on InTopic and sends them to OutQueue. Restart first
     * server. Check log for errors.
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>deploy MDB do 2nd server which creates non durable subscription on InTopic</li>
     * <li>restart 1st server and wait for complete boot</li>
     * <li>check 1st server logs for error "errorType=QUEUE_EXISTS message=HQ119019: Queue already exists"</li>
     * </ul>
     * @tpPassCrit "errorType=QUEUE_EXISTS message=HQ119019: Queue already
     * exists" is not in log
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJcaWithNonDurableMdbs() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);
        // cluster A
        container(1).start();

        // cluster B
        container(2).start();

        container(2).deploy(mdb1OnNonDurable);

        container(1).stop();

        container(1).start();

        while (!CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1).getHostname(), container(1).getHornetqPort())) {
            Thread.sleep(3000);
        }

        Thread.sleep(10000);

        // parse server.log with mdb for "HornetQException[errorType=QUEUE_EXISTS message=HQ119019: Queue already exists"
        StringBuilder pathToServerLogFile = new StringBuilder(container(1).getServerHome());

        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        logger.info("Check server.log: " + pathToServerLogFile);

        File serverLog = new File(pathToServerLogFile.toString());

        String stringToFind = "errorType=QUEUE_EXISTS message=HQ119019: Queue already exists";

        Assert.assertFalse("Server log cannot contain string: " + stringToFind + ". This is fail - see https://bugzilla.redhat.com/show_bug.cgi?id=1167193.",
                CheckFileContentUtils.checkThatFileContainsGivenString(serverLog, stringToFind));

        container(2).undeploy(mdb1OnNonDurable);

        container(2).stop();

        container(1).stop();

    }

    /**
     * @throws Exception
     * @tpTestDetails tart two servers. Deploy InQueue and OutQueue to first.
     * Configure HornetQ RA on second sever to connect to first server. Send
     * messages to InQueue. Deploy MDB do second server which reads messages
     * from InQueue and sends them to OutQueue. Undeploy MDB and restart the
     * servers. Deploy MDB again. Read messages from OutQueue
     * @tpProcedure <ul>
     * <li>start first server with deployed InQueue and OutQueue</li>
     * <li>start second server which has configured HornetQ RA to connect to first server</li>
     * <li>start producer which sends messages to InQueue</li>
     * <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     * <li>undeploy MDB</li>
     * <li>stop both of the servers and restart them</li>
     * <li>deploy MDB to 2nd server</li>
     * <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testUndeployStopStartDeployMdb() throws Exception {

        int numberOfMessages = 500;

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);

        container(1).start();//jms server
        container(2).start();// mdb server

        container(2).undeploy(mdb1);

        CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 60000);
        ProducerTransAck producerToInQueue1 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producerToInQueue1.setMessageBuilder(new ClientMixMessageBuilder(50, 300));
        producerToInQueue1.start();
        producerToInQueue1.join();
        container(2).deploy(mdb1);

        new JMSTools().waitForNumberOfMessagesInQueue(container(1), inQueueName, numberOfMessages / 10, 120000);

        container(2).undeploy(mdb1);
        container(2).stop();
        container(1).stop();

        // Start newer version of EAP and client with older version of EAP
        container(1).start();
        container(2).start();

        container(2).deploy(mdb1);
        ProducerTransAck producerToInQueue2 = new ProducerTransAck(container(1), inQueueJndiName, numberOfMessages);
        producerToInQueue2.setMessageBuilder(new ClientMixMessageBuilder(50, 300));
        producerToInQueue2.start();
        producerToInQueue2.join();

        ReceiverTransAck receiverClientAck = new ReceiverTransAck(container(1), outQueueJndiName, 3000, 10, 5);
        receiverClientAck.start();
        receiverClientAck.join();
        logger.info("Receiver got: " + receiverClientAck.getCount() + " messages from queue: " + receiverClientAck.getQueueNameJndi());
        Assert.assertEquals("Number of sent and received messages should be equal.", 2 * numberOfMessages, receiverClientAck.getCount());

        container(2).undeploy(mdb1);

        container(2).stop();
        container(1).stop();

    }

    /**
     * @throws Exception
     * @tpTestDetails Start three servers in cluster.Deploy InQueue and OutQueue
     * to first. Server 2 is started with container properties including
     * connection parameters for MDB. Deploy MDB which reads messages from
     * InQueue and sends them to OutQueue to server 2. Start producer which
     * sends messages to InQueue to server 1 and receiver which reads them from
     * OutQueue on server2. messages to inQueue
     * @tpProcedure <ul>
     * <li>start 3 servers in cluster with deployed InQueue and OutQueue</li>
     * <li>kill server 2 and start it again with container properties including connection parameters for MDB</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue on server 1 and sends to OutQueue</li>
     * <li>kill server 2 and start it again with container properties including connection parameters for MDB</li>
     * <li>deploy MDB to server 2 which reads messages from InQueue on server 1 and sends to OutQueue</li>
     * <li>start producer which sends messages to InQueue to server 1</li>
     * <li>receive messages from OutQueue on server 3</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRAConfiguredByMdbInRemoteJcaTopology() throws Exception {

        prepareRemoteJcaTopology(Constants.CONNECTOR_TYPE.NETTY_BIO);

        // cluster A
        container(1).start();
        container(3).start();

        // get container properties for node 2 and modify them
        String s = null;
        ContainerDef containerDef = container(2).getContainerDefinition();

        if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
            s = containerDef.getContainerProperties().get("javaVmArguments");

            if (container(2).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
                s = s.concat(" -Dconnection.parameters=port=" + container(1).getHornetqPort() + ";host=" + container(1).getHostname());
            } else {
                s = s.concat(" -Dconnection.parameters=port=" + container(1).getHornetqPort() + ";host=" + container(1).getHostname() + ";httpUpgradeEnabled=true");
            }

            if (container(2).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
                s = s.concat(" -Dconnector.factory.class=org.hornetq.core.remoting.impl.netty.NettyConnectorFactory");
            } else {
                s = s.concat(" -Dconnector.factory.class=org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory");
            }
            containerDef.getContainerProperties().put("javaVmArguments", s);
        }
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javaVmArguments", s);
        container(2).start(properties);

        JavaArchive mdbWithConnectionParameters = getMdbWithConnectionParameters();
        container(2).deploy(mdbWithConnectionParameters);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ProducerTransAck producer2 = new ProducerTransAck(container(3), inQueueJndiName, NUMBER_OF_MESSAGES_PER_PRODUCER);

        producer1.start();
        producer2.start();

        ReceiverTransAck receiver1 = new ReceiverTransAck(container(1), outQueueJndiName, 10000, 10, 10);
        ReceiverTransAck receiver2 = new ReceiverTransAck(container(3), outQueueJndiName, 10000, 10, 10);

        receiver1.start();
        receiver2.start();

        // Wait to send and receive some messages
        Thread.sleep(30 * 1000);

        producer1.stopSending();
        producer2.stopSending();
        producer1.join();
        producer2.join();

        receiver1.join();
        receiver2.join();

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size() + producer2.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());

        container(2).undeploy(mdbWithConnectionParameters);
        container(2).stop();
        container(1).stop();
        container(3).stop();

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

        if (container(1).getContainerType().equals(Constants.CONTAINER_TYPE.EAP6_CONTAINER)) {
            prepareRemoteJcaTopologyEAP6(connectorType);
        } else {
            prepareRemoteJcaTopologyEAP7();
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6(Constants.CONNECTOR_TYPE connectorType) throws Exception {

        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), connectorType, container(1), container(3));

        prepareJmsServerEAP6(container(3));
        prepareMdbServerEAP6(container(4), connectorType, container(1), container(3));

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP7() throws Exception {

        prepareJmsServerEAP7(container(1));
        prepareMdbServerEAP7(container(2), container(1));

        prepareJmsServerEAP7(container(3));
        prepareMdbServerEAP7(container(4), container(1));

        copyApplicationPropertiesFiles();

    }

    /**
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024, "jms.queue.DLQ", "jms.queue.ExpiryQueue");
//        Map<String, String> map = new HashMap<String, String>();
//        map.put("use-nio", "true");
        jmsAdminOperations.createRemoteAcceptor("netty", "messaging", null);

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
     * Prepares jms server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareJmsServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String messagingGroupSocketBindingName = "messaging-group";
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);


        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

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
    private void prepareMdbServerEAP6(Container container, Constants.CONNECTOR_TYPE connectorType, Container... remoteSever) {

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPropertyReplacement("annotation-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("jboss-descriptor-property-replacement", true);
        jmsAdminOperations.setPropertyReplacement("spec-descriptor-property-replacement", true);
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        setConnectorTypeForPooledConnectionFactoryEAP6(container, connectorType, remoteSever);

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

        jmsAdminOperations.close();
        container.stop();
    }

    private void setConnectorTypeForPooledConnectionFactoryEAP6(Container container, Constants.CONNECTOR_TYPE connectorType, Container[] remoteSever) {
        String remoteSocketBindingPrefix = "socket-binding-to-";
        String remoteConnectorNamePrefix = "connector-to-node-";
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        switch (connectorType) {
            case NETTY_BIO:
                for (Container c : remoteSever) {
                    jmsAdminOperations.addRemoteSocketBinding(remoteSocketBindingPrefix + c.getName(), c.getHostname(), c.getHornetqPort());
                }
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                List<String> remoteConnectorList = new ArrayList<String>();
                for (Container c : remoteSever) {
                    String remoteConnectorNameForRemoteContainer = remoteConnectorNamePrefix + c.getName();
                    jmsAdminOperations.removeRemoteConnector(remoteConnectorNameForRemoteContainer);
                    jmsAdminOperations.createRemoteConnector(remoteConnectorNameForRemoteContainer,
                            remoteSocketBindingPrefix + c.getName(), null);
                    remoteConnectorList.add(remoteConnectorNameForRemoteContainer);
                }
                jmsAdminOperations.setConnectorOnPooledConnectionFactory(Constants.RESOURCE_ADAPTER_NAME_EAP6, remoteConnectorList);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                break;
            case NETTY_DISCOVERY:
                jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
                jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
                jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);
                jmsAdminOperations.removeClusteringGroup(clusterGroupName);
                jmsAdminOperations.setPooledConnectionFactoryToDiscovery(Constants.RESOURCE_ADAPTER_NAME_EAP6, discoveryGroupName);
                break;
            default:
                break;
        }
        jmsAdminOperations.close();

    }

    /**
     * Prepares mdb server for remote jca topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareMdbServerEAP7(Container container, Container remoteSever) {

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
        jmsAdminOperations.addRemoteSocketBinding("messaging-remote", remoteSever.getHostname(),
                remoteSever.getHornetqPort());
        jmsAdminOperations.createHttpConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("activemq-ra", remoteConnectorName);

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

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Copy application-users/roles.properties to all standalone/configurations
     * <p/>
     * TODO - change config by cli console
     */
    private void copyApplicationPropertiesFiles() throws IOException {

        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");

        File applicationUsersOriginal;
        File applicationRolesOriginal;
        for (int i = 1; i < 5; i++) {

            // copy application-users.properties
            applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-users.properties");
            // copy application-roles.properties
            applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_" + i) + File.separator + "standalone" + File.separator
                    + "configuration" + File.separator + "application-roles.properties");

            FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
            FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        }
    }

    public static void main(String[] args) throws Exception {


    }
}
