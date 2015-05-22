package org.jboss.qa.hornetq.test.remote.jca;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.MdbFromNonDurableTopicWithOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithConnectionParameters;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner1;
import org.jboss.qa.hornetq.apps.mdb.MdbWithRemoteOutQueueToContaniner2;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This is modified lodh 2 test case which is testing remote jca in cluster and
 * have remote inqueue and outqueue.
 *
 * @author mnovak@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ RESOURCE ADAPTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5534/hornetq-integration#testcases
 */
@RunWith(Arquillian.class)
public class RemoteJcaTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(RemoteJcaTestCase.class);
    private static final int NUMBER_OF_DESTINATIONS = 2;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    private final Archive mdb1 = getMdb1();
    private final Archive mdb1OnNonDurable = getMdb1OnNonDurable();
    private final Archive mdb2 = getMdb2();

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

        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdb1.jar");
        mdbJar.addClasses(MdbWithRemoteOutQueueToContaniner1.class);
        logger.info(mdbJar.toString(true));
        return mdbJar;

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
     * @tpTestDetails Start  4 servers(1, 2, 3, 4). Deploy InQueue and OutQueue to 1,2. Configure ActiveMQ RA on
     * sever 3,4 to connect to 1,2 server. Send messages to InQueue to 1,2. Deploy MDB to 3,4 servers which reads
     * messages from InQueue and sends them to OutQueue. Read messages from OutQueue from 1,2
     * @tpProcedure <ul>
     *     <li>start 2 servers with deployed InQueue and OutQueue</li>
     *     <li>start 2 servers which have configured HornetQ RA to connect to first 2 servers</li>
     *     <li>deploy MDB to other servers which reads messages from InQueue and sends to OutQueue</li>
     *     <li>start producer which sends messagese to InQueue to first 2 server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJcaInCluster() throws Exception {

        prepareRemoteJcaTopology();
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

    /**
     * @throws Exception
     * @tpTestDetails Start two servers. Deploy InQueue and OutQueue to first. Configure HornetQ RA on second sever to
     * connect to first server. Send messages to InQueue. Deploy MDB do second server which reads messages from InQueue
     * and sends them to OutQueue. Read messages from OutQueue
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start second server which has configured HornetQ RA to connect to first server</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */

    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJca() throws Exception {

        prepareRemoteJcaTopology();
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
     * @tpTestDetails Start two servers. Deploy InQueue OutQueue and InTopic to first. Configure HornetQ RA on second
     * sever to connect to first server. Send messages to InQueue. Deploy MDB do second server which creates non durable
     * subscription on InTopic and sends them to OutQueue. Restart first server. Check log for errors.
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start second server which has configured HornetQ RA to connect to first server</li>
     *     <li>deploy MDB do 2nd server which creates non durable subscription on InTopic</li>
     *     <li>restart 1st server and wait for complete boot</li>
     *     <li>check 1st server logs for error "errorType=QUEUE_EXISTS message=HQ119019: Queue already exists"</li>
     * </ul>
     * @tpPassCrit "errorType=QUEUE_EXISTS message=HQ119019: Queue already exists" is not in log
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRemoteJcaWithNonDurableMdbs() throws Exception {

        prepareRemoteJcaTopology();
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
     * @tpTestDetails tart two servers. Deploy InQueue and OutQueue to first. Configure HornetQ RA on second sever to
     * connect to first server. Send messages to InQueue. Deploy MDB do second server which reads messages from InQueue
     * and sends them to OutQueue. Undeploy MDB and restart the servers. Deploy MDB again. Read messages from OutQueue
     * @tpProcedure <ul>
     *     <li>start first server with deployed InQueue and OutQueue</li>
     *     <li>start second server which has configured HornetQ RA to connect to first server</li>
     *     <li>start producer which sends messages to InQueue</li>
     *     <li>deploy MDB do 2nd server which reads messages from InQueue and sends to OutQueue</li>
     *     <li>undeploy MDB</li>
     *     <li>stop both of the servers and restart them</li>
     *     <li>deploy MDB to 2nd server</li>
     *     <li>receive messages from OutQueue</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testUndeployStopStartDeployMdb() throws Exception {

        int numberOfMessages = 500;

        prepareRemoteJcaTopology();

        container(1).start();//jms server
        container(2).start();// mdb server

        container(2).undeploy(mdb1);

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
     * @tpTestDetails Start three servers in cluster.Deploy InQueue and OutQueue to first. Server 2 is started with
     * container properties including connection parameters for MDB. Deploy MDB which reads messages from InQueue and sends
     * them to OutQueue to server 2. Start producer which sends messages to InQueue to server 1 and receiver which reads
     * them from OutQueue on server2.
     * messages to inQueue
     * @tpProcedure <ul>
     *     <li>start 3 servers in cluster with deployed InQueue and OutQueue</li>
     *     <li>kill server 2 and start it again with container properties including connection parameters for MDB</li>
     *     <li>deploy MDB to server 2 which reads messages from InQueue on server 1 and sends to OutQueue</li>
     *     <li>start producer which sends messages to InQueue to server 1</li>
     *     <li>receive messages from OutQueue on server 3</li>
     * </ul>
     * @tpPassCrit receiver consumes all messages
     * @tpInfo For more information see related test case described in the beginning of this section.
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testRAConfiguredByMdbInRemoteJcaTopology() throws Exception {

        prepareRemoteJcaTopology();

        // cluster A
        container(1).start();
        container(3).start();

        // get container properties for node 2 and modify them
        String s = null;
        ContainerDef containerDef = container(2).getContainerDefinition();

        if (containerDef.getContainerProperties().containsKey("javaVmArguments")) {
            s = containerDef.getContainerProperties().get("javaVmArguments");
            s = s.concat(" -Dconnection.parameters=port=" + container(1).getHornetqPort() + ";host=" + container(1).getHostname());
            if (container(2).getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER)) {
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

    public void prepareRemoteJcaTopology()  throws Exception {

        if (container(1).getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER))  {
            prepareRemoteJcaTopologyEAP6();
        } else {
            prepareRemoteJcaTopologyEAP7();
        }
    }

    /**
     * Prepare two servers in simple dedecated topology.
     *
     * @throws Exception
     */
    public void prepareRemoteJcaTopologyEAP6() throws Exception {

        prepareJmsServerEAP6(container(1));
        prepareMdbServerEAP6(container(2), container(1));

        prepareJmsServerEAP6(container(3));
        prepareMdbServerEAP6(container(4), container(1));


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
        String messagingGroupSocketBindingName = "messaging-group";

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
        Map<String, String> map = new HashMap<String, String>();
        map.put("use-nio", "true");
        jmsAdminOperations.createRemoteAcceptor("netty", "messaging", map);

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
        Map<String, String> map = new HashMap<String, String>();
        map.put("use-nio", "true");
        jmsAdminOperations.createHttpAcceptor("http-acceptor", null, map);

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
    private void prepareMdbServerEAP6(Container container, Container remoteSever) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String remoteConnectorName = "netty-remote";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
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
        jmsAdminOperations.createRemoteConnector(remoteConnectorName, "messaging-remote", null);
        jmsAdminOperations.setConnectorOnPooledConnectionFactory("hornetq-ra", remoteConnectorName);
        jmsAdminOperations.close();
        container.stop();
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

}