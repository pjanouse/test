package org.jboss.qa.hornetq.test.cluster;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.mdb.HASingletonMdb;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class ClusteredSingletonMDBTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClusteredSingletonMDBTestCase.class);


    private final Archive HA_SINGLETON_MDB_ANNOTATED = getMdbWithAnnotations();
    private final Archive HA_SINGLETON_MDB_DESCRIPTORS = getMdbWithDescriptors();

    // InQueue and OutQueue for mdb
    protected static String inQueueNameForMdb = "InQueue";
    protected static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    protected static String outQueueNameForMdb = "OutQueue";
    protected static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;


    public Archive getMdbWithAnnotations() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, Constants.HA_SINGLETON_MDB_NAME);
        mdbJar.addClasses(HASingletonMdb.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    public Archive getMdbWithDescriptors() {
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, Constants.HA_SINGLETON_MDB_NAME);
        mdbJar.addClasses(HASingletonMdb.class);
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        log.info(mdbJar.toString(true));
        return mdbJar;
    }

    /**
     * @tpTestDetails Start two server in Artemis cluster with delivery group "group" active
     * and deploy queue InQueue and OutQueue.
     * Start sending messages to InQueue and consume from OutQueue to/from node2. Deploy MDB (by annotations configured
     * HA MDB singleton) to both of the server
     * with HA singleton enabled. Check that node 1 in singleton master and mdb active. Check that mdb on node is
     * not active. Stop node 1 and check mdb on node 2 is active.
     * @tpPassCrit MDB on node is active at the end of the test and all messages were processed.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterMDBSigletonTestAnnotation() throws Exception {
        clusterMDBSigletonTest(HA_SINGLETON_MDB_ANNOTATED);
    }

    /**
     * @tpTestDetails Start two server in Artemis cluster with delivery group "group" active
     * and deploy queue InQueue and OutQueue.
     * Start sending messages to InQueue and consume from OutQueue to/from node2. Deploy MDB (by ejb descriptors configured
     *  HA singleton) to both of the server
     * with HA singleton enabled. Check that node 1 in singleton master and mdb active. Check that mdb on node is
     * not active. Stop node 1 and check mdb on node 2 is active.
     * @tpPassCrit MDB on node is active at the end of the test and all messages were processed.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void clusterMDBSigletonTestDescriptors() throws Exception {
        clusterMDBSigletonTest(HA_SINGLETON_MDB_DESCRIPTORS);
    }

    public void clusterMDBSigletonTest(Archive mdb) throws Exception {

        prepareServer(container(1), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, true);
        prepareServer(container(2), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, true);

        container(1).start();
        container(2).start();

        ProducerTransAck queueProducer = new ProducerTransAck(container(2), inQueueJndiNameForMdb, 1000000);
        ReceiverTransAck queueConsumer = new ReceiverTransAck(container(2), outQueueJndiNameForMdb, 10000, 10, 5);

        queueProducer.start();
        queueConsumer.start();

        // deploy MDB
        container(1).deploy(mdb);
        container(2).deploy(mdb);

        // check that mdb on node 1 is active
        Assert.assertTrue("MDB on node 1 is not delivery active but it must be. This is a bug",
                checkThatMdbIsActive(mdb, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        // check that mdb on node 2 is NOT active
        Assert.assertFalse("MDB on node 2 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(mdb, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        // shutdown node 1 and check that mdb 2 is active
        container(1).stop();

        // start node 1 and check that mdb 2 is active and mdb on node 1 is inactive
        Assert.assertTrue("MDB on node 2 is not delivery active but it must be. This is a bug",
                checkThatMdbIsActive(mdb, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        queueProducer.stopSending();
        queueProducer.join();
        queueConsumer.join();

        Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(), queueConsumer.getCount());

        container(1).stop();
        container(2).stop();

    }

    /**
     * @tpTestDetails Start two server in Artemis cluster with delivery group "group" active on 2nd server (false on 1st)
     * and deploy queue InQueue and OutQueue. Start node1 and then node2.
     * Start sending messages to InQueue and consume from OutQueue to/from node2. Deploy MDB to both of the server
     * with HA singleton enabled. Check that node 1 in singleton master and mdb not active. Check that mdb on node 2 is
     * not active. Stop node 1 and check mdb on node 2 is active.
     * @tpPassCrit MDB on node is active at the end of the test and all messages were processed.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void oneNodeNotActiveClusterMDBSigletonTest() throws Exception {

        prepareServer(container(1), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, false);
        prepareServer(container(2), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, true);

        container(1).start();
        container(2).start();

        ProducerTransAck queueProducer = new ProducerTransAck(container(2), inQueueJndiNameForMdb, 1000000);
        ReceiverTransAck queueConsumer = new ReceiverTransAck(container(2), outQueueJndiNameForMdb, 10000, 10, 5);

        queueProducer.start();

        // deploy MDB
        container(1).deploy(HA_SINGLETON_MDB_ANNOTATED);
        container(2).deploy(HA_SINGLETON_MDB_ANNOTATED);

        // check that mdb on node 1 is not active
        Assert.assertFalse("MDB on node 1 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        // check that mdb on node 2 is NOT active
        Assert.assertFalse("MDB on node 2 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(2)));

         Assert.assertTrue("Number of messages in OutQueue must be 0", new JMSTools().countMessages(outQueueNameForMdb,
                 container(1), container(2)) == 0);

        // shutdown node 1 and check that mdb 2 is active
        container(1).stop();

        Thread.sleep(2000);

        // start node 1 and check that mdb 2 is active
        Assert.assertTrue("MDB on node 2 is not delivery active but it must be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        Assert.assertTrue("Number of messages in OutQueue on node 2 must be higher than 0", new JMSTools().countMessages(outQueueNameForMdb,
                container(2)) > 0);

        // start node 1 and check that mdb on node 1 is not active
        container(1).start();

        Assert.assertFalse("MDB on node 1 is delivery active but it must not. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        Assert.assertTrue("Number of messages in OutQueue must higher than 0", new JMSTools().countMessages(outQueueNameForMdb,
                container(1), container(2)) > 0);

        queueConsumer.start();
        queueProducer.stopSending();
        queueProducer.join();
        queueConsumer.join();

        container(1).undeploy(HA_SINGLETON_MDB_ANNOTATED);
        container(2).undeploy(HA_SINGLETON_MDB_ANNOTATED);
        container(1).stop();
        container(2).stop();

        Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(), queueConsumer.getCount());


    }

    /**
     * @tpTestDetails Start two server in Artemis cluster with delivery group "group" not active
     * and deploy queue InQueue and OutQueue. Start node1 and then node2.
     * Start sending messages to InQueue and consume from OutQueue to/from node2. Deploy MDB to both of the server
     * with HA singleton enabled. Check that node 1 in singleton master and mdb not active. Check that mdb on node 2 is
     * not active. Stop node 1 and check mdb on node 2 is not active. Activate mdb and delivery group on node2 and check
     * that mdb is processing messaging. Start node 1 and activate mdb delivery and group. Stop node 2. Check that mdb on node1
     * is processing messages.
     * @tpPassCrit MDB on node is active at the end of the test and all messages were processed.
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void allNodesNotActiveThenActivateClusterMDBSigletonTest() throws Exception {

        prepareServer(container(1), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, false);
        prepareServer(container(2), Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, false);

        container(1).start();
        container(2).start();

        ProducerTransAck queueProducer = new ProducerTransAck(container(2), inQueueJndiNameForMdb, 1000000);
        ReceiverTransAck queueConsumer = new ReceiverTransAck(container(2), outQueueJndiNameForMdb, 10000, 10, 5);

        queueProducer.start();

        // deploy MDB
        container(1).deploy(HA_SINGLETON_MDB_ANNOTATED);
        container(2).deploy(HA_SINGLETON_MDB_ANNOTATED);

        // check that mdb on node 1 is not active
        Assert.assertFalse("MDB on node 1 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        // check that mdb on node 2 is NOT active
        Assert.assertFalse("MDB on node 2 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        Assert.assertTrue("Number of messages in OutQueue must be 0", new JMSTools().countMessages(outQueueNameForMdb,
                container(1), container(2)) == 0);

        // shutdown node 1 and check that mdb 2 must not be active
        container(1).stop();

        Thread.sleep(2000);

        // start node 1 and check that mdb 2 is active
        Assert.assertFalse("MDB on node 2 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        // activate delivery group on node 2
        activateDeliveryGroup(container(2));
        startDelivery(container(2), HA_SINGLETON_MDB_ANNOTATED);

        Thread.sleep(2000);

        Assert.assertTrue("MDB on node 2 is not delivery active but it must be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(2)));

        Assert.assertTrue("Number of messages in OutQueue on node 2 must be higher than 0", new JMSTools().countMessages(outQueueNameForMdb,
                container(2)) > 0);

        // start node 1 and check that mdb on node 1 is not active
        container(1).start();

        activateDeliveryGroup(container(1));
        startDelivery(container(1), HA_SINGLETON_MDB_ANNOTATED);

        Assert.assertFalse("MDB on node 1 is delivery active but it must not be. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        queueConsumer.start();
        queueProducer.stopSending();
        queueProducer.join();
        queueConsumer.join();

        container(2).stop();

        Assert.assertTrue("MDB on node 1 is not delivery active but it must not. This is a bug",
                checkThatMdbIsActive(HA_SINGLETON_MDB_ANNOTATED, Constants.HA_SINGLETON_MDB_NAME, container(1)));

        container(1).undeploy(HA_SINGLETON_MDB_ANNOTATED);
        container(1).stop();

        Assert.assertEquals("Number of received messages from queue does not match: ", queueProducer.getCount(), queueConsumer.getCount());


    }

    private void startDelivery(Container container, Archive mdb) {
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.startDeliveryToMdb(mdb.getName());
        jmsOperations.close();
    }

    private void activateDeliveryGroup(Container container) {
        JMSOperations jmsOperations = container.getJmsOperations();
        jmsOperations.setDeliveryGroupActive(Constants.HA_SINGLETON_MDB_DELIVERY_GROUP_NAME, true);
        jmsOperations.close();
    }


    private boolean checkThatMdbIsActive(Archive mdb, String mdbName, Container container) {
        JMSOperations jmsOperations = container.getJmsOperations();
        // /deployment=mdb-1.0-SNAPSHOT.jar/subsystem=ejb3/message-driven-bean=LocalResendingMdbFromQueueToQueue:read-attribute(name=delivery-active)
        boolean isActive = jmsOperations.isDeliveryActive(mdb, mdbName);
        jmsOperations.close();

        log.info("Is delivery of MDB " + mdbName + " active? " + isActive);

        return isActive;
    }

    /**
     * Prepares server for topology.
     *
     * @param container             The container - defined in arquillian.xml
     * @param deliveryGroup         name of delivery group
     * @param isDeliveryGroupActive whether delivery group is active
     */
    protected void prepareServer(Container container, String deliveryGroup, boolean isDeliveryGroupActive) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        try {

            if (container.getContainerType() == Constants.CONTAINER_TYPE.EAP6_CONTAINER) {
                jmsAdminOperations.setClustered(true);

            }
            jmsAdminOperations.setPersistenceEnabled(true);

            jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
            jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

            jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
            jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

            jmsAdminOperations.removeClusteringGroup(clusterGroupName);
            jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                    connectorName);

            jmsAdminOperations.addDeliveryGroup(deliveryGroup, isDeliveryGroupActive);

            jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
            jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
            jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
            jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

            jmsAdminOperations.setNodeIdentifier(new Random().nextInt());
            jmsAdminOperations.disableSecurity();
            // jmsAdminOperations.setLoggingLevelForConsole("INFO");
            // jmsAdminOperations.addLoggerCategory("org.hornetq", "DEBUG");

            jmsAdminOperations.removeAddressSettings("#");
            jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

            jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
            jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);

        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            jmsAdminOperations.close();
            container.stop();

        }

    }

    /**
     * Stop nodes.
     */
    @Before
    @After
    public void stopServers() {
        container(1).stop();
        container(2).stop();
    }

}
