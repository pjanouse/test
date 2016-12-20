package org.jboss.qa.artemis.test.loadbalancers;

import noe.server.Httpd;
import noe.server.ServerController;
import noe.workspace.IWorkspace;
import noe.workspace.WorkspaceHttpd;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.apps.clients20.ArtemisCoreJmsProducer;
import org.jboss.qa.hornetq.apps.clients20.ArtemisCoreJmsReceiver;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * IPV6
 * This test case can be run with IPv6 - just replace those environment
 * variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p>
 * <p>
 * SECURITY
 * Tests cover three scenarios
 * * plain HTTP without SSL
 * * HTTPS secured communication between client and loadbalancer
 * * HTTPS secured communication between client and loadbalancer, and also between loadbalancer and workers
 * Truststores are configured as follows
 * * client trust store    : balancer
 * * balancer trust store  : client, workerA, workerB
 * * workerA trust store   : balancer
 * * workerB trust store   : balancer
 *
 * @author mstyk@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter HTTP CONNECTOR - TEST SCENARIOS
 * @tpTestCaseDetails Test case covers test developed for purpose of RFE <a href="https://issues.jboss.org/browse/EAP7-581">
 * Reintroduce JMS over HTTP/HTTPS_CLIENT_BALANCER capability</a>.
 * Tests use HTTP upgrade feature and correct loadbalancing on front-end HTTP load balancer is tested. Main purpose of
 * this test case is to ensure that currently supported HTTP loadbalancers can handle http upgrade packets, forward
 * them to back-end workers and than tunnel communication. Client`s should be aware only of front-end loadbalancer/proxy
 * <p>
 * @see <a href="https://issues.jboss.org/browse/EAP7-581"> Reintroduce JMS over HTTP/HTTPS_CLIENT_BALANCER capability</a>
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class ApacheFrontEndLoadBalancingTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FrontEndLoadBalancingTestCase.class);

    //noe stuff
    private static final ServerController serverController = ServerController.getInstance();
    protected static Httpd apacheServer = null;

    //hornetq-ts containers - eap workers
    private static Container workerA;
    private static Container workerB;

    // must be created by child testsuites
    protected static IWorkspace workspace;

    private final String NAME_QUEUE = "inQueue";
    private final String QUEUE_JNDI = "/queue/" + NAME_QUEUE;

    private final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
    private final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;

    @Before
    public void init() throws IOException {
        workerA = container(1);
        workerB = container(2);
    }

    @BeforeClass
    public static void installApache() throws InterruptedException {
        Properties props = System.getProperties();
        props.setProperty("ews.version", "3.0.3");
        props.setProperty("apache.core.version", "2.4.23-CR5");
        props.setProperty("workspace.basedir", new File(".." + File.separator + ".." + File.separator + "apache").getAbsolutePath());


        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.debug("Shutdown hook started");
                ServerController serverController = ServerController.getInstance();
                serverController.killAllInSystem();
                logger.debug("Shutdown hook finished");
            }
        });
        logger.debug("Preparing workspace");
        workspace = new WorkspaceHttpd();
        // start servers, change configurations etc.
        workspace.prepare();
        apacheServer = (Httpd) serverController.getServerById(serverController.getHttpdServerId());
        apacheServer.shiftPorts(2000);
    }

    @AfterClass
    public static void clean() {
        logger.debug("Destroying workspace");
        if (workspace != null) {
            workspace.destroy();
            workspace = null;
        }
    }

    @Ignore //TODO this is configured, but feature doesn't work so far
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancer() throws Exception {
        testSendReceive(SecurityType.HTTP);
    }

    @Ignore //TODO
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancerHttpsClientBalancer() throws Exception {
        testSendReceive(SecurityType.HTTPS_CLIENT_BALANCER);
    }

    @Ignore //TODO
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancerHttpsAll() throws Exception {
        testSendReceive(SecurityType.HTTPS_ALL);
    }


    private void testSendReceive(SecurityType securityType) throws Exception {

        int msgsInIteration = 100;
        int iterations = 10;
        JMSTools jmsTools = new JMSTools();

        prepareServers(securityType);
        apacheServer.start();
        workerA.start();
        workerB.start();

        //init client keystore and truststore if required
        securityType.initClient();

        //wait some time to allow undertow find workers
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        for (int i = 1; i <= iterations; i++) {
            ArtemisCoreJmsProducer producer = new ArtemisCoreJmsProducer(apacheServer.getHost(), 2080, 8443, NAME_QUEUE, msgsInIteration, !SecurityType.HTTP.equals(securityType));
            producer.start();
            producer.join();
            Assert.assertEquals("Messages not on workers in iteration number " + i, msgsInIteration * i, jmsTools.countMessages(NAME_QUEUE, workerA, workerB));
        }

        int sumReceived = 0;
        //todo remove this workaround when client topology updates are disabled
        for (int i = 0; i < 2; i++) {
            ArtemisCoreJmsReceiver receiver = new ArtemisCoreJmsReceiver(apacheServer.getHost(), 2080, 8443, NAME_QUEUE, 10000, !SecurityType.HTTP.equals(securityType));
            receiver.start();
            receiver.join();
            sumReceived += receiver.getReceivedMessageCount();
        }

        Assert.assertEquals("Messages not recevied", msgsInIteration * iterations, sumReceived);

        apacheServer.stop();
        workerA.stop();
        workerB.stop();
    }

    //////////////////////////////////////////////////////
    ///////////   PREPARE PHASE METHODS   ////////////////
    //////////////////////////////////////////////////////

    private void prepareServers(SecurityType securityType) {
        prepareWorker(workerA, securityType);
        prepareWorker(workerB, securityType);
    }

    /**
     * Worker preparation does following:
     * - deploy destinations
     * - set connector on connection factory to point on proxy/laodbalancer because clients should be aware only of load balancer/proxy
     * - disable Artemis cluster to avoid clients to get topology behind proxy/load balancer
     * - specific setup to work with load balancer if needed
     * <p>
     * <p>
     * Verify discovery works here : http://127.0.0.1:6666/mod_cluster_manager
     *
     * @param container
     * @param securityType
     */
    public void prepareWorker(Container container, SecurityType securityType) {
        final String keyStorePath = container.getName().equals("node-2") ?
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.a.keystore.jks").getPath() :
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.b.keystore.jks").getPath();
        final String trustStorePath = container.getName().equals("node-2") ?
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.a.truststore.jks").getPath() :
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.b.truststore.jks").getPath();

        final String proxyBinding = "socket-binding-to-proxy";
        final String proxyConnector = "proxy-connector";

        container.start();

        securityType.setUpWorkerSecurity(container, keyStorePath, trustStorePath, "secret");

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createQueue(NAME_QUEUE, QUEUE_JNDI);

        jmsAdminOperations.setEapServerName(container.getName());
        jmsAdminOperations.setModClusterAdvertise(false);
        jmsAdminOperations.addRemoteSocketBinding(proxyBinding, apacheServer.getHost(), 6666);
        jmsAdminOperations.addModClusterProxy(proxyBinding);
        jmsAdminOperations.setModClusterConnector("default");

        if (securityType.equals(SecurityType.HTTP)) {
            jmsAdminOperations.createHttpConnector(proxyConnector, proxyBinding, null);

        } else {
            jmsAdminOperations.createHttpAcceptor("https-acceptor", "https", null);

            Map<String, String> httpConnectorParams = new HashMap<String, String>();
            httpConnectorParams.put("ssl-enabled", "true");
            jmsAdminOperations.createHttpConnector(proxyConnector, proxyBinding, httpConnectorParams, "https-acceptor");
        }
        jmsAdminOperations.setConnectorOnConnectionFactory(NAME_CONNECTION_FACTORY, proxyConnector);
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");

        jmsAdminOperations.close();

        container.stop();
    }

    enum SecurityType {
        HTTP {
            @Override
            protected void initClient() {
            }

            @Override
            protected void setUpBalancerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            }

            @Override
            protected void setUpWorkerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            }
        },

        //two way
        //ssl between client <-> balancer
        HTTPS_CLIENT_BALANCER {
            @Override
            protected void setUpWorkerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            }
        },

        //two way
        //ssl between client <-> balancer, balancer <-> workers
        HTTPS_ALL;

        protected void initClient() {
            final String keyStorePath = getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/client.keystore.jks").getPath();
            final String trustStorePath = getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/client.truststore.jks").getPath();
            final String password = "secret";

            System.setProperty("javax.net.ssl.keyStore", keyStorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
            System.setProperty("javax.net.ssl.trustStore", trustStorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", password);
        }

        protected void setUpBalancerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            setUpServerSecurity(container, keyStorePath, trustStorePath, password);
        }

        protected void setUpWorkerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            setUpServerSecurity(container, keyStorePath, trustStorePath, password);
        }

        private void setUpServerSecurity(Container container, String keyStorePath, String trustStorePath, String password) {
            final String securityRealmName = "https";
            final String listenerName = "https";
            final String socketBinding = "https";
            final String verifyClientPolitic = "REQUESTED";

            JMSOperations jmsAdminOperations = container.getJmsOperations();

            jmsAdminOperations.createSecurityRealm(securityRealmName);
            jmsAdminOperations.addServerIdentity(securityRealmName, keyStorePath, password);
            jmsAdminOperations.addAuthentication(securityRealmName, trustStorePath, password);

            jmsAdminOperations.removeHttpsListener("https");
            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            jmsAdminOperations.addHttpsListener(listenerName, securityRealmName, socketBinding, verifyClientPolitic);
            jmsAdminOperations.reload();
            jmsAdminOperations.close();
        }

    }


}
