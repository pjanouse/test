package org.jboss.qa.hornetq.test.compatibility;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SocketBinding;
import org.jboss.qa.hornetq.tools.XMLManipulation;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRule;
import org.jboss.qa.hornetq.tools.byteman.annotation.BMRules;
import org.jboss.qa.hornetq.tools.byteman.rule.RuleInstaller;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forward compatibility tests for EAP5 HornetQ org.jboss.qa.hornetq.apps.clients connecting to EAP6 server.
 * <p/>
 * For this test working properly, you need to use arqullian-eap7-legacy.xml descriptor. Your JBOSS_HOME_X properties need to
 * point to EAP7 servers with org.jboss.legacy.jnp module installed. When running this test, use eap5x-backward-compatibility
 * maven profile and set netty.version and hornetq.version maven properties to client libraries versions you want to test with
 * (HornetQ needs to be 2.2.x).
 *
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS CLIENTS - TEST SCENARIOS
 * @tpJobLink tbd
 * @tpTcmsLink tbd
 * @tpTestCaseDetails Test older EAP5 JMS client against latest EAP 7.x server, this test case also implements tests from
 * ClientCompatibilityTestBase
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class Eap5ClientCompatibilityTestCase extends ClientCompatibilityTestBase {

    private static final Logger LOG = Logger.getLogger(Eap5ClientCompatibilityTestCase.class);

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";

    private static int NUMBER_OF_MESSAGES_PER_PRODUCER = 1000000;

    @Override
    protected void prepareContainer(final Container container) throws Exception {
        final String discoveryGroupName = "dg-group1";
        final String broadCastGroupName = "bg-group1";
        final String messagingGroupSocketBindingName = "messaging-group";
        final String clusterGroupName = "my-cluster";
        final String connectorName = "netty";

        JMSOperations ops = container.getJmsOperations();

        ops.setBindingsDirectory(JOURNAL_DIR);
        ops.setPagingDirectory(JOURNAL_DIR);
        ops.setJournalDirectory(JOURNAL_DIR);
        ops.setLargeMessagesDirectory(JOURNAL_DIR);

        ops.setClustered(false);
        ops.setPersistenceEnabled(true);
        ops.setSharedStore(true);

        ops.removeBroadcastGroup(broadCastGroupName);
        ops.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        ops.removeDiscoveryGroup(discoveryGroupName);
        ops.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        ops.removeClusteringGroup(clusterGroupName);
        ops.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        ops.disableSecurity();
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        ops.addExtension("org.jboss.legacy.jnp");

        ops.close();
        container.restart();
        ops = container.getJmsOperations();

        ops.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());
        ops.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());
        // ops.createSocketBinding(SocketBinding.LEGACY_REMOTING.getName(), SocketBinding.LEGACY_REMOTING.getPort());

        this.deployDestinations(ops);
        ops.close();

        this.activateLegacyJnpModule(container);
        container.stop();
    }

    private void deployDestinations(final JMSOperations ops) {
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            ops.createQueue(QUEUE_NAME_PREFIX + destinationNumber, QUEUE_JNDI_NAME_PREFIX + destinationNumber, true);
            ops.createTopic(TOPIC_NAME_PREFIX + destinationNumber, TOPIC_JNDI_NAME_PREFIX + destinationNumber);
        }
    }

    private void activateLegacyJnpModule(final Container container) throws Exception {
        StringBuilder pathToStandaloneXml = new StringBuilder();
        pathToStandaloneXml = pathToStandaloneXml.append(container.getServerHome()).append(File.separator).append("standalone")
                .append(File.separator).append("configuration").append(File.separator).append("standalone-full-ha.xml");
        Document doc = XMLManipulation.getDOMModel(pathToStandaloneXml.toString());

        Element e = doc.createElement("subsystem");
        e.setAttribute("xmlns", "urn:jboss:domain:legacy-jnp:1.0");

        Element entry = doc.createElement("jnp-connector");
        entry.setAttribute("socket-binding", "jnp");
        entry.setAttribute("rmi-socket-binding", "rmi-jnp");
        e.appendChild(entry);

        /*
         * Element entry2 = doc.createElement("remoting"); entry2.setAttribute("socket-binding", "legacy-remoting");
         * e.appendChild(entry2);
         */
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate("//profile", doc, XPathConstants.NODE);
        node.appendChild(e);

        XMLManipulation.saveDOMModel(doc, pathToStandaloneXml.toString());
    }

    @After
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }

    /**
     * @tpTestDetails This test scenario tests whether is possible to make JNDI lookup with EAP 5 client on EAP 7 server.
     * @tpProcedure <ul>
     *     <li>Start EAP 7 server with legacy extention and deployed destinations</li>
     *     <li>do JNDI lookup with EAP 5 client libraries to EAP 7 server</li>
     * </ul>
     * @tpPassCrit Verify that JNDI lookup was successful
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testJNDILookupTroughLegacyExtension() throws Exception {

        prepareContainer(container(1));

        container(1).start();

        Context ctx = null;

        try {

            // get eap 5 context even when you're connecting to eap 6 server
            ctx = container(1).getContext();

            List<String> jndiNameToLookup = new ArrayList<String>();

            jndiNameToLookup.add(Constants.CONNECTION_FACTORY_JNDI_EAP6);
            jndiNameToLookup.add(Constants.CONNECTION_FACTORY_JNDI_FULL_NAME_EAP6);
            jndiNameToLookup.add("jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jboss/exported/jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("jms/topic/" + TOPIC_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jms/topic/" + TOPIC_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jboss/exported/jms/topic/" + TOPIC_NAME_PREFIX + "0");

            for (String jndiName : jndiNameToLookup) {
                Object o = ctx.lookup(jndiName);
                if (o == null) {
                    Assert.fail("jndiName: " + jndiName + " could not be found.");
                } else {
                    if (o instanceof ConnectionFactory) {

                        ConnectionFactory cf = (ConnectionFactory) o;

                        LOG.info("jndiName: " + jndiName + " was found and cast to connection factory.");

                    } else if (o instanceof Destination) {

                        Destination cf = (Destination) o;

                        LOG.info("jndiName: " + jndiName + " was found and cast to destination.");

                    } else {
                        Assert.fail("jndiName: " + jndiName
                                + " could not be cast to connection factory of destination which is an error.");
                    }

                }
            }

        } catch (Exception ex) {
            LOG.error("Error during jndi lookup.", ex);
            throw new Exception(ex);
        } finally {

            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.error("Error while closing the naming context", ex);
                }
            }
        }
    }

    /**
     * Prepare two servers in simple dedicated topology.
     *
     * @throws Exception
     */
    public void prepareSimpleDedicatedTopology() throws Exception {

        prepareLiveServer(container(1), container(1).getHostname(), JOURNAL_DIRECTORY_A);
        prepareBackupServer(container(2), container(2).getHostname(), JOURNAL_DIRECTORY_A);

        container(1).start();
        deployDestinations(container(1));
        container(1).stop();

        container(2).start();
        deployDestinations(container(2));
        container(2).stop();
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container test container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    protected void prepareLiveServer(Container container, String bindingAddress, String journalDirectory)
            throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);
        jmsAdminOperations.setJournalType("ASYNCIO");
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
//        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.addExtension("org.jboss.legacy.jnp");
        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());
        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());
        activateLegacyJnpModule(container);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Prepares backup server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareBackupServer(Container container, String bindingAddress, String journalDirectory)
            throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setBackup(true);
        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setFailoverOnShutdown(true);
        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setAllowFailback(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFailoverOnShutdown(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();
        // jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.addExtension("org.jboss.legacy.jnp");

        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());

        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());

        activateLegacyJnpModule(container);

        jmsAdminOperations.close();
        container.stop();
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container test container
     */
    protected void deployDestinations(Container container) {
        deployDestinations(container, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param container test container
     * @param serverName server name of the hornetq server
     */
    protected void deployDestinations(Container container, String serverName) {

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
        }

        jmsAdminOperations.close();
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent some messages to first Receive messages from
     * the second one
     *
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     * @throws Exception
     */
    @BMRules({
            @BMRule(name = "Setup counter for PostOfficeImpl", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute", action = "createCounter(\"counter\")"),
            @BMRule(name = "Info messages and counter for PostOfficeImpl", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute", action = "incrementCounter(\"counter\");"
                    + "System.out.println(\"Called org.hornetq.core.postoffice.impl.PostOfficeImpl.processRoute  - \" + readCounter(\"counter\"));"),
            @BMRule(name = "Kill server when a number of messages were received", targetClass = "org.hornetq.core.postoffice.impl.PostOfficeImpl", targetMethod = "processRoute", condition = "readCounter(\"counter\")>15", action = "System.out.println(\"Byteman - Killing server!!!\"); killJVM();") })
    public void testFailover(int acknowledge, boolean failback, boolean topic, boolean shutdown) throws Exception {

        prepareSimpleDedicatedTopology();

        container(1).start();

        container(2).start();

        Thread.sleep(10000);

        Clients clients = createClients(container(1), acknowledge, topic);
        clients.setProducedMessagesCommitAfter(10);
        clients.setReceivedMessagesAckCommitAfter(9);
        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 50, 300000);
        ClientUtils.waitForProducersUntil(clients.getProducers(), 50, 300000);

        if (!shutdown) {
            LOG.warn("########################################");
            LOG.warn("Kill live server");
            LOG.warn("########################################");
            RuleInstaller.installRule(this.getClass(), container(1).getHostname(), container(1).getBytemanPort());
            container(1).kill();
        } else {
            LOG.warn("########################################");
            LOG.warn("Shutdown live server");
            LOG.warn("########################################");
            container(1).stop();
        }

        LOG.warn("Wait some time to give chance backup to come alive and org.jboss.qa.hornetq.apps.clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", CheckServerAvailableUtils
                .waitHornetQToAlive(container(2).getHostname(), container(2).getHornetqPort(), 300000));
        waitForClientsToFailover(clients);
        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 200, 300000);

        if (failback) {
            LOG.warn("########################################");
            LOG.warn("failback - Start live server again ");
            LOG.warn("########################################");
            container(1).start();
            Assert.assertTrue("Live did not start again - failback failed.", CheckServerAvailableUtils.waitHornetQToAlive(
                    container(1).getHostname(), container(1).getHornetqPort(), 300000));
            LOG.warn("########################################");
            LOG.warn("failback - Live started again ");
            LOG.warn("########################################");
            CheckServerAvailableUtils.waitHornetQToAlive(container(1).getHostname(), container(1).getHornetqPort(), 600000);
            // check that backup is really down
            waitHornetQBackupToBecomePassive(container(2), container(2).getHornetqPort(), 60000);
            waitForClientsToFailover(clients);
            Thread.sleep(5000); // give it some time
            // LOG.warn("########################################");
            // LOG.warn("failback - Stop backup server");
            // LOG.warn("########################################");
            // stopServer(CONTAINER2_NAME);
            // LOG.warn("########################################");
            // LOG.warn("failback - Backup server stopped");
            // LOG.warn("########################################");
        }

        Thread.sleep(5000);

        waitForClientsToFailover(clients);

        clients.stopClients();
        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.",
                clients.evaluateResults());

        container(1).stop();

        container(2).stop();

    }

    protected void waitForClientsToFailover(Clients clients) {

        long timeout = 180000;
        // wait for 2 min for producers to receive more messages
        long startTime = System.currentTimeMillis();

        int startValue = 0;
        for (Client c : clients.getProducers()) {

            startValue = c.getCount();

            while (c.getCount() <= startValue) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Assert.fail("Clients - producers - did not failover/failback in: " + timeout + " ms. Print bad producer: "
                            + c);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        // wait for 2 min for consumers to send more messages
        startTime = System.currentTimeMillis();

        Map<Client, Integer> consumersCounts = new HashMap<Client, Integer>();
        for (Client c : clients.getConsumers()) {
            consumersCounts.put(c, c.getCount());
        }

        do {
            for (Client c : clients.getConsumers()) {
                if (c.getCount() > consumersCounts.get(c)) {
                    consumersCounts.remove(c);
                }
            }
            if (System.currentTimeMillis() - startTime > timeout) {
                Assert.fail("Clients - consumers - did not failover/failback in: " + timeout + " ms");
            }
        } while (consumersCounts.size() > 0);

    }

    protected void waitHornetQBackupToBecomePassive(Container container, int port, long timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        while (CheckServerAvailableUtils.checkThatServerIsReallyUp(container.getHostname(), container.getPort())) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTime < timeout) {
                Assert.fail("Server " + container + " should be down. Timeout was " + timeout);
            }
        }
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent some messages to first Receive messages from
     * the second one
     *
     * @param acknowledge acknowledge type
     * @param failback whether to test failback
     * @param topic whether to test with topics
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback, boolean topic) throws Exception {
        testFailover(acknowledge, failback, topic, false);
    }

    /**
     * This test will start two servers in dedicated topology - no cluster. Sent some messages to first Receive messages from
     * the second one
     *
     * @param acknowledge acknowledge type
     * @param failback whether to test fail back
     * @throws Exception
     */
    public void testFailover(int acknowledge, boolean failback) throws Exception {

        testFailover(acknowledge, failback, false);

    }


    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on dedicated topology with shared-store and kill.
     * EAP 5 clients are using CLIENT_ACKNOWLEDGE sessions to sending and receiving messages from testTopic on EAP 7 server.
     *
     * @tpProcedure <ul>
     *     <li>start two EAP7 nodes in dedicated topology (live and backup)</li>
     *     <li>start EAP5 clients (with CLIENT_ACKNOWLEDGE) sessions  sending messages to testTopic on EAP7 server and
     *     receiving them from testTopic on EAP7</li>
     *     <li>kill live server</li>
     *     <li>clients make failover on EAP7 backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackClientAckTopic() throws Exception {
        testFailover(Session.CLIENT_ACKNOWLEDGE, true, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on dedicated topology with shared-store and kill.
     * EAP 5 clients are using SESSION_TRANSACTED sessions to sending and receiving messages from testTopic on EAP 7 server.
     *
     * @tpProcedure <ul>
     *     <li>start two EAP7 nodes in dedicated topology (live and backup)</li>
     *     <li>start EAP5 clients (with SESSION_TRANSACTED) sessions  sending messages to testTopic on EAP7 server and
     *     receiving them from testTopic on EAP7</li>
     *     <li>kill live server</li>
     *     <li>clients make failover on EAP7 backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckTopic() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, true);
    }


    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on dedicated topology with shared-store and shutdown.
     * EAP 5 clients are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue on EAP 7 server.
     * @tpProcedure <ul>
     *     <li>start two EAP7 nodes in dedicated topology (live and backup)</li>
     *     <li>start EAP5 clients (with SESSION_TRANSACTED) sessions  sending messages to testQueue on EAP7 server and
     *     receiving them from testQueue on EAP7</li>
     *     <li>shut down live server</li>
     *     <li>clients make failover on EAP7 backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailbackTransAckQueueOnShutdown() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, true, false, true);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests simple failover and failback on dedicated topology with shared-store and kill.
     * EAP 5 clients are using SESSION_TRANSACTED sessions to sending and receiving messages from testQueue on EAP 7 server.
     * @tpProcedure <ul>
     *     <li>start two EAP7 nodes in dedicated topology (live and backup)</li>
     *     <li>start EAP5 clients (with SESSION_TRANSACTED) sessions  sending messages to testQueue on EAP7 server and
     *     receiving them from testQueue on EAP7</li>
     *     <li>kill live server</li>
     *     <li>clients make failover on EAP7 backup and continue in sending and receiving messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testFailoverTransAckQueue() throws Exception {
        testFailover(Session.SESSION_TRANSACTED, false);
    }

    /**
     * @throws Exception
     * @tpTestDetails This scenario tests whether is EAP5 client capable to send large messages to EAP7 server with activated
     * large message compression on it.
     * @tpProcedure <ul>
     *     <li>start two EAP7 server with deployed destinations and large message compression </li>
     *     <li>start EAP5 clients sending messages to testQueue on EAP7 server and receiving them from the same queue</li>
     *     <li>wait for clients to send and receive some messages</li>
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and  receiver  successfully made failover and didn't get any exception
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testCompressLargeMessages() throws Exception {

        prepareServerForCompressLargeMessages(container(1));

        container(1).start();

        Thread.sleep(5000);

        Clients clients = createClients(container(1), Session.AUTO_ACKNOWLEDGE, false);

        clients.setProducedMessagesCommitAfter(10);

        clients.setReceivedMessagesAckCommitAfter(9);

        clients.setMessageBuilder(new ClientMixMessageBuilder(10, 200));

        clients.startClients();

        ClientUtils.waitForReceiversUntil(clients.getConsumers(), 100, 300000);

        ClientUtils.waitForProducersUntil(clients.getProducers(), 100, 300000);

        clients.stopClients();

        // blocking call checking whether all consumers finished
        JMSTools.waitForClientsToFinish(clients);

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.",
                clients.evaluateResults());

        container(1).stop();

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    protected void prepareServerForCompressLargeMessages(Container container) throws Exception {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setJournalType("ASYNCIO");

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);

        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);

        jmsAdminOperations.setCompressionOnConnectionFactory(connectionFactoryName, true);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");

        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);

        jmsAdminOperations.addExtension("org.jboss.legacy.jnp");

        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());

        jmsAdminOperations.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());

        deployDestinations(jmsAdminOperations);

        jmsAdminOperations.close();

        container.stop();

        activateLegacyJnpModule(container);

    }

    protected Clients createClients(Container container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        Clients clients;

        if (isTopic) {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsAutoAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new TopicClientsClientAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new TopicClientsTransAck(container, TOPIC_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
                clients.setProducedMessagesCommitAfter(10);

            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for topic not known");
            }
        } else {
            if (Session.AUTO_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsAutoAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
                clients = new QueueClientsClientAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
            } else if (Session.SESSION_TRANSACTED == acknowledgeMode) {
                clients = new QueueClientsTransAck(container, QUEUE_JNDI_NAME_PREFIX, NUMBER_OF_DESTINATIONS,
                        NUMBER_OF_PRODUCERS_PER_DESTINATION, NUMBER_OF_RECEIVERS_PER_DESTINATION,
                        NUMBER_OF_MESSAGES_PER_PRODUCER);
                clients.setProducedMessagesCommitAfter(10);
            } else {
                throw new Exception("Acknowledge type: " + acknowledgeMode + " for queue not known");
            }
        }
        clients.setMessageBuilder(new ClientMixMessageBuilder(10, 200));
        return clients;
    }
}
