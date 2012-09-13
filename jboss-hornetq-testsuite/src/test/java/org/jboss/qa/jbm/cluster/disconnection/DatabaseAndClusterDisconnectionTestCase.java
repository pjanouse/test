package org.jboss.qa.jbm.cluster.disconnection;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerClientAck;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicDurable;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromTopicDurable2;
import org.jboss.qa.hornetq.apps.mdb.MdbAllHornetQActivationConfigQueue;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.ControllableProxy;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.SimpleProxyServer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * Purpose of this test is to verify behavior of JBM during disconnection a node from cluster or from database.
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class DatabaseAndClusterDisconnectionTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(DatabaseAndClusterDisconnectionTestCase.class);

    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 200;

    private String gosshipAddress = CONTAINER1_IP;
    private int gosshipPort = 12001;
    // where tcp proxy to database listen
    int proxyPort = 3307;
    // where is database
    int databasePort = 3306;
    String databaseHostname = "localhost";

    // InQueue and OutQueue for mdb
    String inQueueNameForMdb = "InQueue";
    String inQueueJndiNameForMdb = "queue/" + inQueueNameForMdb;

    String outQueueNameForMdb = "OutQueue";
    String outQueueJndiNameForMdb = "queue/" + outQueueNameForMdb;

    // InTopic and OutTopic for mdb
    String inTopicNameForMdb = "InTopic";
    String inTopicJndiNameForMdb = "topic/" + inTopicNameForMdb;

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
//    @CleanUpAfterTest
    public void clusterTest() throws Exception {

        long networkFailure = 60000;

        prepareServer(CONTAINER1, gosshipAddress, gosshipPort, true);
        prepareServer(CONTAINER2, gosshipAddress, gosshipPort, false);

        // java -cp jgroups.jar:/home/jbossqa/tmp/jboss-eap-5.2/jboss-as/client/* org.jgroups.stack.GossipRouter -port 12001 -bindaddress 192.168.40.1
        Process router = null;
        ControllableProxy proxyToDb = null;

        try {

            router = startGosshipRouter(gosshipAddress, gosshipPort);
            // start tcp proxy here
            proxyToDb = new SimpleProxyServer(databaseHostname, databasePort, proxyPort);
            proxyToDb.start();

            controller.start(CONTAINER1);
            controller.start(CONTAINER2);
            log.info("servers started");

            deployer.undeploy("mdbOnTopic1");
            deployer.undeploy("mdbOnTopic2");
            deployer.deploy("mdbOnTopic1");
            deployer.deploy("mdbOnTopic2");

            PublisherClientAck publisher1 = new PublisherClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER1_IP, getJNDIPort(), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
            PublisherClientAck publisher2 = new PublisherClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER2_IP, getJNDIPort(), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
            publisher1.setMessageBuilder(new ClientMixMessageBuilder(10, 10));
            publisher2.setMessageBuilder(new ClientMixMessageBuilder(10, 10));

            publisher1.start();
            publisher2.start();
            publisher1.join();
            publisher2.join();
            ReceiverClientAck receiver2 = new ReceiverClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER2_IP, getJNDIPort(), outQueueJndiNameForMdb, networkFailure + 60000, 10, 10);
            receiver2.start();
            while (receiver2.getListOfReceivedMessages().size() < NUMBER_OF_MESSAGES_PER_PRODUCER/4) {
                Thread.sleep(1000);
            }

            router.destroy();
            proxyToDb.stop();
            log.info("Gosship router stopped.");

            Thread.sleep(30000);
            PublisherClientAck publisher3 = new PublisherClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER2_IP, getJNDIPort(), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
            publisher3.setMessageBuilder(new ClientMixMessageBuilder(10, 10));
            publisher3.start();

            Thread.sleep(networkFailure);

            router = startGosshipRouter(gosshipAddress, gosshipPort);
            proxyToDb.start();// start proxy here

            Thread.sleep(30000);

            PublisherClientAck publisher4 = new PublisherClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER2_IP, getJNDIPort(), inTopicJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER, "topicId");
            publisher4.setMessageBuilder(new ClientMixMessageBuilder(10, 10));
            publisher4.start();
            ReceiverClientAck receiver1 = new ReceiverClientAck(EAP5_WITH_JBM_CONTAINER, CONTAINER1_IP, getJNDIPort(), outQueueJndiNameForMdb, 30000, 10, 10);
            receiver1.start();
            receiver1.join();
            receiver2.join();
            Thread.sleep(600000);

            Assert.assertEquals("Receiver did not get expected number of messages. Received receiver1: " + receiver1.getListOfReceivedMessages().size() + " receiver2: " + receiver2.getListOfReceivedMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size()
                    , 8 * NUMBER_OF_MESSAGES_PER_PRODUCER);

        } catch (Exception ex)  {
            log.error("ERROR - see stack trace", ex);
        } finally {

//            deployer.undeploy("mdbOnTopic1");
//            deployer.undeploy("mdbOnTopic2");
            stopServer(CONTAINER1);
            stopServer(CONTAINER2);

            router.destroy();
            log.info("Gosship router stopped.");
            proxyToDb.stop();

            printOutputLog(CONTAINER1);

        }


    }

    private void printOutputLog(String container) {
        StringBuilder pathToOutputLog = new StringBuilder();
        pathToOutputLog.append(getJbossHome(container)).append(File.separator).append("server").append(File.separator)
        .append("default").append(File.separator).append("log").append(File.separator).append("output.log");

        try {
            BufferedReader in = new BufferedReader(new FileReader(pathToOutputLog.toString()));
            String str;
            while ((str = in.readLine()) != null) {
                log.info(str);
            }
            in.close();
        } catch (IOException e) {
            log.error("Error during printing output log: ", e);
        }
    }


    /**
     * Starts gosship router
     *
     * @param gosshipAddress address
     * @param gosshipPort port
     */
    private Process startGosshipRouter(String gosshipAddress, int gosshipPort) throws IOException {
        StringBuilder command = new StringBuilder();
        command.append("java -cp ").append(JBOSS_HOME_1).append(File.separator).append("server").append(File.separator)
                .append("default").append(File.separator).append("lib").append(File.separator).append("jgroups.jar").append(File.pathSeparator)
                .append(JBOSS_HOME_1).append(File.separator).append("client").append(File.separator)
                .append("*").append("  org.jgroups.stack.GossipRouter ").append("-port ").append(gosshipPort)
                .append(" -bindaddress ").append(gosshipAddress);

        Process router = Runtime.getRuntime().exec(command.toString());
        log.info("Gosship router started. Command: " + command);

        return router;
    }

    /**
     * This test will start two servers A and B in cluster.
     * Start producers/publishers connected to A with client/transaction acknowledge on queue/topic.
     * Start consumers/subscribers connected to B with client/transaction acknowledge on queue/topic.
     */
    @Test
    @RunAsClient
//    @CleanUpAfterTest
    public void clusterTestWithMdbOnQueue() throws Exception {

//        prepareServers();

        controller.start(CONTAINER2);
        controller.start(CONTAINER1);

        deployer.deploy("mdbOnQueue1");

        deployer.deploy("mdbOnQueue2");

        // Send messages into input node and read from output node
        ProducerClientAck producer = new ProducerClientAck(CONTAINER1_IP, getJNDIPort(), inQueueJndiNameForMdb, NUMBER_OF_MESSAGES_PER_PRODUCER);
        ReceiverClientAck receiver = new ReceiverClientAck(CONTAINER2_IP, getJNDIPort(), outQueueJndiNameForMdb, 10000, 10, 10);

        log.info("Start producer and consumer.");
        producer.start();
        receiver.start();

        producer.join();
        receiver.join();

        Assert.assertEquals("Number of sent and received messages is different. Sent: " + producer.getListOfSentMessages().size()
                + "Received: " + receiver.getListOfReceivedMessages().size(), producer.getListOfSentMessages().size(),
                receiver.getListOfReceivedMessages().size());
        Assert.assertFalse("Producer did not sent any messages. Sent: " + producer.getListOfSentMessages().size()
                , producer.getListOfSentMessages().size() == 0);
        Assert.assertFalse("Receiver did not receive any messages. Sent: " + receiver.getListOfReceivedMessages().size()
                , receiver.getListOfReceivedMessages().size() == 0);
        Assert.assertEquals("Receiver did not get expected number of messages. Expected: " + NUMBER_OF_MESSAGES_PER_PRODUCER
                + " Received: " + receiver.getListOfReceivedMessages().size(), receiver.getListOfReceivedMessages().size()
                , NUMBER_OF_MESSAGES_PER_PRODUCER);

        stopServer(CONTAINER1);
        stopServer(CONTAINER2);

    }


    /**
     * Prepares server for topology.
     *
     * Initial state:
     * EAP 5 with production profile prepared to work with mysql database -> mysql-ds.xml, mysql-persistence-service.xml and
     * remove hsqldb-ds.xml and hsqldb-persistence-service.xml.
     *
     * What needs to be configured:
     * clustered true
     * new failover true -
     *  <attribute name="MaxRetry">-1</attribute>
     *  <attribute name="RetryInterval">2000</attribute>
     *  <attribute name="RetryOnConnectionFailure">true</attribute>
     *
     *
     *  <attribute name="KeepOldFailoverModel">false</attribute>  // whether or not the "old" fail-over model should be used; value is boolean; defaults to true
     *  <attribute name="NodeStateRefreshInterval">5000</attribute>   //how often to refresh the cluster information from the database; value is in milliseconds; defaults to 30000
     *
     * deploy test queues/topics
     * set jgroups to use tunnel and ping for jbm to gosship router
     * change to tcp to start servers
     *
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareServer(String containerName, String gossipRouterHostname, int gossipRouterPort, boolean directToDbThroughProxy) {

        boolean keepOldFailover = false;
        long nodeStateRefreshInterval = 20000;


//        boolean retryOnConnectionFailure = true;
//        long retryInterval = 1000;
//        int maxRetry = -1;

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setKeepOldFailoverModel(keepOldFailover, nodeStateRefreshInterval);
//        jmsAdminOperations.setRetryForDb(retryOnConnectionFailure, retryInterval, maxRetry);
//        jmsAdminOperations.createQueue("testQueue", "queue/testQueue");
        jmsAdminOperations.createTopic(inTopicNameForMdb, inTopicJndiNameForMdb);
        jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, false);
        if (directToDbThroughProxy) {
            jmsAdminOperations.setDatabase("localhost", proxyPort);
        }

        jmsAdminOperations.setTunnelForJGroups(gossipRouterHostname, gossipRouterPort);

        jmsAdminOperations.close();


    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = "mdbOnQueue1")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeploymentMdbOnQueue1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = "mdbOnQueue2")
    @TargetsContainer(CONTAINER2)
    public static JavaArchive createDeploymentMdbOnQueue2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbQueue.jar");
        mdbJar.addClass(MdbAllHornetQActivationConfigQueue.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.remote-naming, org.hornetq \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = "mdbOnTopic1")
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeploymentMdbOnTopic1() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic1.jar");
        mdbJar.addClass(LocalMdbFromTopicDurable.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));

        File target = new File("/tmp/mdbOnTopic1.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }


    /**
     * This mdb reads messages from jms/queue/InQueue and sends to jms/queue/OutQueue
     *
     * @return mdb
     */
    @Deployment(managed = false, testable = false, name = "mdbOnTopic2")
    @TargetsContainer(CONTAINER2)
    public static JavaArchive createDeploymentMdbOnTopic2() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "mdbTopic2.jar");
        mdbJar.addClass(LocalMdbFromTopicDurable2.class);
        mdbJar.addAsManifestResource(new StringAsset("Dependencies: \n"), "MANIFEST.MF");
        log.info(mdbJar.toString(true));

        File target = new File("/tmp/mdbOnTopic2.jar");
        if (target.exists()) {
            target.delete();
        }
        mdbJar.as(ZipExporter.class).exportTo(target, true);

        return mdbJar;
    }

}