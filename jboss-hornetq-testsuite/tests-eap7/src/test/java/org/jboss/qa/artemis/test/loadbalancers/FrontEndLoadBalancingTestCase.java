package org.jboss.qa.artemis.test.loadbalancers;

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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
public class FrontEndLoadBalancingTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FrontEndLoadBalancingTestCase.class);

    private static Container loadBalancer;
    private static Container workerA;
    private static Container workerB;

    private final String NAME_QUEUE = "inQueue";
    private final String QUEUE_JNDI = "/queue/" + NAME_QUEUE;

    private final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
    private final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;

    public static final String MODCLUSTER_FILTER_NAME = "modclusterFilter";
    public static final String ADVERTISE_KEY = "mypassword";
    public static final String MCAST_SOCKET_BINDING = "modcluster";
    public static final int MCAST_PORT = 23364;
    public static final String MCAST_ADDRESS = loadBalancer.MCAST_ADDRESS;

    @Before
    public void init() throws IOException {
        loadBalancer = container(1);
        workerA = container(2);
        workerB = container(3);
    }

    @Before
    public void setProfileOnBalancer() {
        container(1).setServerProfile("standalone.xml");
    }

    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with static loadbalancing features, reverse proxy. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a static load balancer using reverse proxy</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowStaticBalancer() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_STATIC, SecurityType.HTTP);
    }

    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with static loadbalancing features, reverse proxy. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * Use SSL between balancer and clients. Communication between balancer and worker is without SSL.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a static load balancer using reverse proxy</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowStaticBalancerHttpsClientBalancer() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_STATIC, SecurityType.HTTPS_CLIENT_BALANCER);
    }

    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with static loadbalancing features, reverse proxy. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * Use SSL between balancer and clients and also between balancer and workers.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a static load balancer using reverse proxy</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowStaticBalancerHttpsAll() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_STATIC, SecurityType.HTTPS_ALL);
    }


    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with with mod cluster dynamic load balancing. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a dynamic load balancer using modcluster</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Ignore //TODO this is configured, but feature doesn't work so far
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancer() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_MODCLUSTER, SecurityType.HTTP);
    }

    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with with mod cluster dynamic load balancing. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * Use SSL between balancer and clients. Communication between balancer and worker is without SSL.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a dynamic load balancer using modcluster</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Ignore //TODO this is configured, but feature doesn't work so far
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancerHttpsClientBalancer() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_MODCLUSTER, SecurityType.HTTPS_CLIENT_BALANCER);
    }

    /**
     * @tpTestDetails Start one JBoss EAP server configured as a loadbalancer and two backend EAP workers. Loadbalancer uses
     * undertow with with mod cluster dynamic load balancing. Send and receive messages with clients knowing only about
     * frontend loadbalancer. Create multiple connections during send and receive. Backend workers serve messaging tasks.
     * Use SSL between balancer and clients and also between balancer and workers.
     * @tpProcedure <ul>
     * <li>Start EAP configured as a dynamic load balancer using modcluster</li>
     * <li>Start 2 EAP backend servers with messaging</li>
     * <li>Producer starts to send messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Producer repeats sending with creating of new connection - connections should be balanced between workers</li>
     * <li>Receiver receives messages, it uses connection factory pointing to loadbalancer, not knowing about backed workers</li>
     * <li>Receiver repeats recevive with creating of new connection - connections should be balanced between workers and receive messages from all of them</li>
     * <li>Verify messages count</li>
     * </ul>
     * @tpPassCrit Receiver reads same amount of messages as was sent
     */
    @Ignore //TODO this is configured, but feature doesn't work so far
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowWithModClusterBalancerHttpsAll() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_MODCLUSTER, SecurityType.HTTPS_ALL);
    }


    private void testSendReceive(LoadBalancerType loadBalancerType, SecurityType securityType) throws Exception {

        int msgsInIteration = 100;
        int iterations = 10;
        JMSTools jmsTools = new JMSTools();

        prepareServers(loadBalancerType, securityType);
        loadBalancer.start();
        workerA.start();
        workerB.start();

        //init client keystore and truststore if required
        securityType.initClient();

        //wait some time to allow undertow find workers
        Thread.sleep(TimeUnit.SECONDS.toMillis(15));

        for (int i = 1; i <= iterations; i++) {
            ArtemisCoreJmsProducer producer = new ArtemisCoreJmsProducer(loadBalancer, NAME_QUEUE, msgsInIteration, !SecurityType.HTTP.equals(securityType));
            producer.start();
            producer.join();
            Assert.assertEquals("Messages not on workers in iteration number " + i, msgsInIteration * i, jmsTools.countMessages(NAME_QUEUE, workerA, workerB));
        }

        int sumReceived = 0;
        //todo remove this workaround when client topology updates are disabled
        for (int i = 0; i < 2; i++) {
            ArtemisCoreJmsReceiver receiver = new ArtemisCoreJmsReceiver(loadBalancer, NAME_QUEUE, 10000, !SecurityType.HTTP.equals(securityType));
            receiver.start();
            receiver.join();
            sumReceived += receiver.getReceivedMessageCount();
        }

        Assert.assertEquals("Messages not recevied", msgsInIteration * iterations, sumReceived);

        loadBalancer.stop();
        workerA.stop();
        workerB.stop();
    }

    //////////////////////////////////////////////////////
    ///////////   PREPARE PHASE METHODS   ////////////////
    //////////////////////////////////////////////////////

    private void prepareServers(LoadBalancerType loadBalancerType, SecurityType securityType) {
        prepareLoadBalancer(loadBalancer, loadBalancerType, securityType);
        prepareWorker(workerA, loadBalancerType, securityType);
        prepareWorker(workerB, loadBalancerType, securityType);
    }

    private void prepareLoadBalancer(Container loadBalancer, LoadBalancerType loadBalancerType, SecurityType securityType) {
        if (LoadBalancerType.UNDERTOW_STATIC.equals(loadBalancerType)) {
            prepareLoadBalancerUndertowStatic(loadBalancer, securityType);
        }

        if (LoadBalancerType.UNDERTOW_MODCLUSTER.equals(loadBalancerType)) {
            prepareLoadBalancerUndertowModCluster(loadBalancer, securityType);
        }
    }

    private void prepareLoadBalancerUndertowModCluster(Container loadBalancer, SecurityType securityType) {
        loadBalancer.start();

        // prepare security realm and https listener if required
        // for plain http tests this does nothing
        securityType.setUpBalancerSecurity(loadBalancer,
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/balancer.keystore.jks").getPath(),
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/balancer.truststore.jks").getPath(),
                "secret"
        );

        JMSOperations jmsAdminOperations = loadBalancer.getJmsOperations();
        jmsAdminOperations.addSocketBinding(MCAST_SOCKET_BINDING, MCAST_ADDRESS, MCAST_PORT);

        //only setup https security realm between backed servers for HTTPS_ALL tests
        if (SecurityType.HTTPS_ALL.equals(securityType)) {
            jmsAdminOperations.addModClusterFilterToUndertow(MODCLUSTER_FILTER_NAME, "http", MCAST_SOCKET_BINDING, ADVERTISE_KEY, "https");
        } else {
            jmsAdminOperations.addModClusterFilterToUndertow(MODCLUSTER_FILTER_NAME, "http", MCAST_SOCKET_BINDING, ADVERTISE_KEY, null);
        }
        jmsAdminOperations.addFilterToUndertowServerHost(MODCLUSTER_FILTER_NAME);

        jmsAdminOperations.close();
        loadBalancer.stop();
    }


    private void prepareLoadBalancerUndertowStatic(Container loadBalancer, SecurityType securityType) {
        final String handlerName = "my-handler";

        loadBalancer.start();

        // prepare security realm and https listener if required
        securityType.setUpBalancerSecurity(loadBalancer,
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/balancer.keystore.jks").getPath(),
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/balancer.truststore.jks").getPath(),
                "secret"
        );

        //setup loadbalancing
        JMSOperations jmsAdminOperations = loadBalancer.getJmsOperations();
        jmsAdminOperations.createUndertowReverseProxyHandler(handlerName);

        //setup secured connection behind loadbalancer only if required
        if (SecurityType.HTTPS_ALL.equals(securityType)) {
            jmsAdminOperations.createOutBoundSocketBinding("remote-workerA", workerA.getHostname(), workerA.getHttpsPort());
            jmsAdminOperations.createOutBoundSocketBinding("remote-workerB", workerB.getHostname(), workerB.getHttpsPort());
            jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerA.getName(), "remote-workerA", "https", "myroute", "/", "https");
            jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerB.getName(), "remote-workerB", "https", "myroute", "/", "https");
        } else {
            jmsAdminOperations.createOutBoundSocketBinding("remote-workerA", workerA.getHostname(), workerA.getHornetqPort());
            jmsAdminOperations.createOutBoundSocketBinding("remote-workerB", workerB.getHostname(), workerB.getHornetqPort());
            jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerA.getName(), "remote-workerA", "http", "myroute", "/", null);
            jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerB.getName(), "remote-workerB", "http", "myroute", "/", null);
        }
        jmsAdminOperations.removeLocationFromUndertowServerHost("/");
        jmsAdminOperations.reload();
        jmsAdminOperations.addLocationToUndertowServerHost("/", handlerName);

        jmsAdminOperations.close();
        loadBalancer.stop();
    }

    /**
     * Worker preparation does following:
     * - deploy destinations
     * - set connector on connection factory to point on proxy/laodbalancer because clients should be aware only of load balancer/proxy
     * - disable Artemis cluster to avoid clients to get topology behind proxy/load balancer
     * - specific setup to work with load balancer if needed
     *
     * @param container
     * @param loadBalancerType
     * @param securityType
     */
    public void prepareWorker(Container container, LoadBalancerType loadBalancerType, SecurityType securityType) {
        final String keyStorePath = container.getName().equals("node-2") ?
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.a.keystore.jks").getPath() :
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.b.keystore.jks").getPath();
        final String trustStorePath = container.getName().equals("node-2") ?
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.a.truststore.jks").getPath() :
                getClass().getResource("/org/jboss/qa/artemis/test/httpconnector/worker.b.truststore.jks").getPath();

        container.start();

        securityType.setUpWorkerSecurity(container, keyStorePath, trustStorePath, "secret");

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createQueue(NAME_QUEUE, QUEUE_JNDI);
        jmsAdminOperations.addRemoteSocketBinding("socket-binding-to-proxy", loadBalancer.getHostname(), loadBalancer.getHornetqPort());

        if (securityType.equals(SecurityType.HTTP)) {
            jmsAdminOperations.createHttpConnector("proxy-connector", "socket-binding-to-proxy", null);

        } else {
            jmsAdminOperations.createHttpAcceptor("https-acceptor", "https", null);

            Map<String, String> httpConnectorParams = new HashMap<String, String>();
            httpConnectorParams.put("ssl-enabled", "true");
            jmsAdminOperations.createHttpConnector("proxy-connector", "socket-binding-to-proxy", httpConnectorParams, "https-acceptor");
        }
        jmsAdminOperations.setConnectorOnConnectionFactory(NAME_CONNECTION_FACTORY, "proxy-connector");
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");

        if (loadBalancerType.equals(LoadBalancerType.UNDERTOW_MODCLUSTER)) {
            jmsAdminOperations.setModClusterAdvertiseKey(ADVERTISE_KEY);
            jmsAdminOperations.removeSocketBinding(MCAST_SOCKET_BINDING);
            jmsAdminOperations.reload();
            jmsAdminOperations.addSocketBinding(MCAST_SOCKET_BINDING, MCAST_ADDRESS, MCAST_PORT);
            if (securityType.equals(SecurityType.HTTPS_ALL)) {
                jmsAdminOperations.setModClusterConnector("https");
            } else {
                jmsAdminOperations.setModClusterConnector("default");
            }
            jmsAdminOperations.setUndertowInstanceId(container.getName());
        }

        jmsAdminOperations.close();

        container.stop();
    }


    enum LoadBalancerType {
        UNDERTOW_STATIC,
        UNDERTOW_MODCLUSTER
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
