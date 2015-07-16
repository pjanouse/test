//TODO do check of journal files
package org.jboss.qa.hornetq.test.failover;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ByteMessageBuilder;
import org.jboss.qa.hornetq.apps.impl.MixMessageBuilder;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Lodh4 - cluster A -> bridge (core) -> cluster B. Kill server from A or B repeatedly
 * Topology - 1,3 - source containers, 2,4 - target containers
 *
 * @tpChapter RECOVERY/FAILOVER TESTING
 * @tpSubChapter XA TRANSACTION RECOVERY TESTING WITH RESOURCE ADAPTER - TEST SCENARIOS (LODH SCENARIOS)
 * @tpJobLink
 *            https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP6/view/EAP6-HornetQ/job/_eap-6-hornetq-qe-internal-ts-lodh
 *            /
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/5536/hornetq-functional#testcases
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class Lodh4TestCase extends HornetQTestCase {

    // number of bridges/destinations
    private static final int NUMBER_OF_DESTINATIONS_BRIDGES = 5;
    // this is just maximum limit for producer - producer is stopped once failover test scenario is complete
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;
    // Logger
    private static final Logger log = Logger.getLogger(Lodh4TestCase.class);
    private String hornetqInQueueName = "InQueue";
    private String relativeJndiInQueueName = "queue/InQueue";
    private String hornetqOutQueueName = "OutQueue";
    private String relativeJndiOutQueueName = "queue/OutQueue";

    /**
     * Stops all servers
     */
    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
    }

    /**
     * Stops all servers
     */
    @After
    public void stopAllServerAfterTest() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
    }

    /**
     * Normal message (not large message), byte message
     *
     * @throws InterruptedException if something is wrong
     */

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void normalMessagesKillTest() throws Exception {
        testLogic(new ByteMessageBuilder(30));
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void normalMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogic(new ByteMessageBuilder(30), killSequence, true);
    }

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesKillTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new ByteMessageBuilder(300 * 1024), killSequence, false);
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large byte messages to InQueue to container 1 and consumer which
     *                  reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void largeByteMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new ByteMessageBuilder(300 * 1024), killSequence, true);
    }

    /**
     * @tpTestDetails Test whether the kill of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large messages of various types to InQueue to container 1 and
     *                  consumer which reads messages from OutQueue from container 4</li>
     *              <li>kill and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mixMessagesKillTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new MixMessageBuilder(300 * 1024), killSequence, false);
    }

    /**
     * @tpTestDetails Test whether the shutdown of the server in a cluster with a core bridge results
     *                in a message loss or duplication.
     * @tpInfo For more information see related test case described in the beginning of this section.
     * @tpProcedure <ul>
     *              <li>start 2 server containers 1 and 2 with deployed InQueue in a cluster A</li>
     *              <li>start other 2 server containers 3 and 4 with deployed OutQueue in a cluster B</li>
     *              <li>set up 2 Core bridges from InQueue to OutQueue (from container 1 to container 3, and
     *                  from 2 to 4)</li>
     *              <li>start producer which sends large messages of various types to InQueue to container 1 and
     *                  consumer which reads messages from OutQueue from container 4</li>
     *              <li>shutdown and restart server containers in this order - 2, 3, 3, 3</li>
     *              </ul>
     * @tpPassCrit receiver will receive all messages which where sent with no duplicates
     */
    @RunAsClient
    @Test
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void mixMessagesShutdownTest() throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogicLargeMessages(new MixMessageBuilder(300 * 1024), killSequence, true);
    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     */
    private void testLogic(MessageBuilder messageBuilder) throws Exception {
        List<Container> killSequence = new ArrayList<Container>();
        killSequence.add(container(2));
        killSequence.add(container(3));
        killSequence.add(container(3));
        killSequence.add(container(3));
        testLogic(messageBuilder, killSequence, false);
    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     */
    private void testLogic(MessageBuilder messageBuilder, List<Container> killSequence, boolean shutdown) throws Exception {

        prepareServers();

        container(2).start();
        container(4).start();
        container(1).start();
        container(3).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        QueueClientsClientAck clientsA1 = new QueueClientsClientAck(
                container(1),
                relativeJndiInQueueName,
                NUMBER_OF_DESTINATIONS_BRIDGES,
                1,
                1,
                NUMBER_OF_MESSAGES_PER_PRODUCER);

        clientsA1.setQueueJndiNamePrefixProducers(relativeJndiInQueueName);
        clientsA1.setQueueJndiNamePrefixConsumers(relativeJndiOutQueueName);
        clientsA1.setHostnameForConsumers(container(4).getHostname());
        clientsA1.setPortForConsumers(container(4).getJNDIPort());
        clientsA1.setMessageBuilder(messageBuilder);
        clientsA1.startClients();

        executeNodeFailSequence(killSequence, 10000, shutdown);

        clientsA1.stopClients();

        while (!clientsA1.isFinished()) {
            Thread.sleep(1000);
        }
        
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

        assertTrue("There are problems detected by org.jboss.qa.hornetq.apps.clients. Check logs for more info. Look for: 'Print kill sequence', "
                + "'Kill and restart server', 'Killing server', 'Evaluate results for queue org.jboss.qa.hornetq.apps.clients with client acknowledge'.", clientsA1.evaluateResults());

    }

    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder instance of the message builder
     * @param killSequence   kill sequence for servers
     * @param shutdown       clean shutdown?
     */
    private void testLogicLargeMessages(MessageBuilder messageBuilder, List<Container> killSequence, boolean shutdown) throws Exception {

        prepareServers();

        container(2).start();
        container(4).start();
        container(1).start();
        container(3).start();

        // give some time to server4 to really start
        Thread.sleep(3000);

        ProducerTransAck producer1 = new ProducerTransAck(container(1), relativeJndiInQueueName + 0, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageBuilder(messageBuilder);
        ReceiverTransAck receiver1 = new ReceiverTransAck(container(4), relativeJndiOutQueueName + 0, 100000, 10, 10);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        executeNodeFailSequence(killSequence, 20000, shutdown);

        producer1.stopSending();
        producer1.join();
        receiver1.join();


        log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
        log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());

        Assert.assertEquals("There is different number of sent and received messages.",
                producer1.getListOfSentMessages().size(),
                receiver1.getListOfReceivedMessages().size());

        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();

    }

    /**
     * Executes kill sequence.
     *
     * @param failSequence     map Contanier -> ContainerIP
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    private void executeNodeFailSequence(List<Container> failSequence, long timeBetweenFails, boolean shutdown) throws InterruptedException {

        if (shutdown) {
            for (Container container : failSequence) {
                Thread.sleep(timeBetweenFails);
                log.info("Shutdown server: " + container.getName());
                container.stop();
                Thread.sleep(3000);
                log.info("Start server: " + container.getName());
                container.start();
                log.info("Server: " + container.getName() + " -- STARTED");
            }
        } else {
            for (Container container : failSequence) {
                Thread.sleep(timeBetweenFails);
                container.kill();
                Thread.sleep(3000);
                log.info("Start server: " + container.getName());
                container.start();
                log.info("Server: " + container.getName() + " -- STARTED");
            }
        }
    }

    public void prepareServers()    {
        if (container(1).getContainerType().equals(CONTAINER_TYPE.EAP6_CONTAINER))  {
            prepareServersEAP6();
        } else {
            prepareServersEAP7();
        }
    }
    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServersEAP7() {

        prepareSourceServerEAP7(container(1), container(2));
        prepareSourceServerEAP7(container(3), container(4));
        prepareTargetServerEAP7(container(2));
        prepareTargetServerEAP7(container(4));

    }

    /**
     * Prepares servers.
     * <p/>
     * Container1,3 - source servers in cluster A. Container2,4 - source servers
     * in cluster B.
     */
    public void prepareServersEAP6() {

        prepareSourceServerEAP6(container(1), container(2));
        prepareSourceServerEAP6(container(3), container(4));
        prepareTargetServerEAP6(container(2));
        prepareTargetServerEAP6(container(4));

    }

    /**
     * Prepares source server for bridge.
     *  @param container             test container - defined in arquillian.xml
     * @param targetServer target container
     */
    private void prepareSourceServerEAP6(Container container,
                                         Container targetServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = Constants.CONNECTION_FACTORY_EAP6;
        String messagingGroupSocketBindingName = "messaging-group";

        String udpGroupAddress = "231.43.21.36";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        try {
            jmsAdminOperations.removeRemoteSocketBinding("messaging-bridge");
        } catch (Exception ex)    {
            // ignore
        }
        jmsAdminOperations.close();
        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", targetServer.getHostname(), targetServer.getHornetqPort());
        jmsAdminOperations.createHttpConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS_BRIDGES; queueNumber++) {
            jmsAdminOperations.createQueue("default", hornetqInQueueName + queueNumber, relativeJndiInQueueName + queueNumber, true);                 }
        jmsAdminOperations.close();

        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", udpGroupAddress, 55874);
        for (int i = 0; i < NUMBER_OF_DESTINATIONS_BRIDGES; i++) {
            jmsAdminOperations.createCoreBridge("myBridge" + i, "jms.queue." + hornetqInQueueName + i, "jms.queue." + hornetqOutQueueName + i, -1, "bridge-connector");
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepare target server for bridge
     *
     * @param container      test container - defined in arquillian.xml
     */
    private void prepareTargetServerEAP6(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = Constants.CONNECTION_FACTORY_EAP6;
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.disableSecurity();
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS_BRIDGES; queueNumber++) {
            jmsAdminOperations.createQueue("default", hornetqOutQueueName + queueNumber, relativeJndiOutQueueName + queueNumber, true);
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares source server for bridge.
     *  @param container             test container - defined in arquillian.xml
     * @param targetServer target container
     */
    private void prepareSourceServerEAP7(Container container,
                                         Container targetServer) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String connectionFactoryName = Constants.CONNECTION_FACTORY_EAP7;
        String messagingGroupSocketBindingName = "messaging-group";

        String udpGroupAddress = "231.43.21.36";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        try {
            jmsAdminOperations.removeRemoteSocketBinding("messaging-bridge");
        } catch (Exception ex)    {
            // ignore
        }
        jmsAdminOperations.close();
        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", targetServer.getHostname(), targetServer.getHornetqPort());
        jmsAdminOperations.createHttpConnector("bridge-connector", "messaging-bridge", null);
        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.removeSocketBinding(messagingGroupSocketBindingName);
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS_BRIDGES; queueNumber++) {
            jmsAdminOperations.createQueue("default", hornetqInQueueName + queueNumber, relativeJndiInQueueName + queueNumber, true);                 }
        jmsAdminOperations.close();

        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingName, "public", udpGroupAddress, 55874);
        for (int i = 0; i < NUMBER_OF_DESTINATIONS_BRIDGES; i++) {
            jmsAdminOperations.createCoreBridge("myBridge" + i, "jms.queue." + hornetqInQueueName + i, "jms.queue." + hornetqOutQueueName + i, -1, "bridge-connector");
        }

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepare target server for bridge
     *
     * @param container      test container - defined in arquillian.xml
     */
    private void prepareTargetServerEAP7(Container container) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "http-connector";
        String connectionFactoryName = Constants.CONNECTION_FACTORY_EAP7;
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.setIdCacheSize(500000);
        jmsAdminOperations.disableSecurity();
        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS_BRIDGES; queueNumber++) {
            jmsAdminOperations.createQueue("default", hornetqOutQueueName + queueNumber, relativeJndiOutQueueName + queueNumber, true);
        }

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.close();
        container.stop();
    }


}
