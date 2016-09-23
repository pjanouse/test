package org.jboss.qa.artemis.test.httpconnector;

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
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Test case covers test developed for purpose of RFE  @see <a href="https://issues.jboss.org/browse/EAP7-581"> Reintroduce JMS over HTTP/HTTPS capability</a>
 * Tests use HTTP upgrade feature and correct loadbalancing on front-end HTTP load balancer is tested. Main purpose of this test case is to ensure
 * that currently supported http load balancers can handle http upgrade packets, forward them to back-end workers and than tunnel communication. Client`s should be aware only
 * of front-end loadbalancer/proxy
 *
 * @author mstyk@redhat.com
 * @tpChapter Functional testing
 * @tpSubChapter HTTP CONNECTOR - TEST SCENARIOS
 * @tpTestCaseDetails Test case covers test developed for purpose of RFE  @see <a href="https://issues.jboss.org/browse/EAP7-581"> Reintroduce JMS over HTTP/HTTPS capability</a>
 * Tests use HTTP upgrade feature and correct loadbalancing on front-end HTTP load balancer is tested. Main purpose of this test case is to ensure
 * that currently supported http load balancers can handle http upgrade packets, forward them to back-end workers and than tunnel communication. Client`s should be aware only
 * of front-end loadbalancer/proxy
 * @see <a href="https://issues.jboss.org/browse/EAP7-581"> Reintroduce JMS over HTTP/HTTPS capability</a>
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Category(FunctionalTests.class)
public class FrontEndLoadBalancingTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(FrontEndLoadBalancingTestCase.class);

    enum LoadBalancerType {
        UNDERTOW_STATIC,
        UNDERTOW_MODCLUSTER
    }

    private Container loadBalancer;
    private Container workerA;
    private Container workerB;

    private final String NAME_QUEUE = "inQueue";
    private final String QUEUE_JNDI = "/queue/" + NAME_QUEUE;

    private final String NAME_CONNECTION_FACTORY = "RemoteConnectionFactory";
    private final String JNDI_CONNECTION_FACTORY = "jms/" + NAME_CONNECTION_FACTORY;

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

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void basicSendReceiveUndertowStaticBalancer() throws Exception {
        testSendReceive(LoadBalancerType.UNDERTOW_STATIC);
    }

//    @Test
//    @RunAsClient
//    @CleanUpBeforeTest
//    @RestoreConfigBeforeTest
//    public void basicSendReceiveUndertowWithModClusterBalancer() {
//        testSendReceive(LoadBalancerType.UNDERTOW_STATIC);
//    }

    private void testSendReceive(LoadBalancerType loadBalancerType) throws Exception {

        int msgsInIteration = 100;
        int iterations = 10;
        JMSTools jmsTools = new JMSTools();

        prepareServers(loadBalancerType);
        loadBalancer.start();
        workerA.start();
        workerB.start();

        for (int i = 1; i <= iterations; i++) {
            ArtemisCoreJmsProducer producer = new ArtemisCoreJmsProducer(loadBalancer, NAME_QUEUE, msgsInIteration);
            producer.start();
            producer.join();
            Assert.assertEquals("Messages not on workers in iteration number " + i, msgsInIteration * i, jmsTools.countMessages(NAME_QUEUE, workerA, workerB));
        }

        int sumReceived = 0;
        //todo can connect to one server
        for (int i = 0; i < 2; i++) {
            ArtemisCoreJmsReceiver receiver = new ArtemisCoreJmsReceiver(loadBalancer, NAME_QUEUE, 10000);
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

    private void prepareServers(LoadBalancerType loadBalancerType) {
        prepareLoadBalancer(loadBalancerType);
        prepareWorker(workerA);
        prepareWorker(workerB);
    }

    public void prepareWorker(Container container) {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createQueue(NAME_QUEUE, QUEUE_JNDI);
        jmsAdminOperations.addRemoteSocketBinding("socket-binding-to-proxy", loadBalancer.getHostname(), loadBalancer.getHornetqPort());
        jmsAdminOperations.createHttpConnector("proxy-connector", "socket-binding-to-proxy", null);
        jmsAdminOperations.setConnectorOnConnectionFactory(NAME_CONNECTION_FACTORY, "proxy-connector");
        jmsAdminOperations.removeClusteringGroup("my-cluster");
        jmsAdminOperations.removeDiscoveryGroup("dg-group1");
        jmsAdminOperations.removeBroadcastGroup("bg-group1");

        jmsAdminOperations.close();
        container.stop();
    }

    private void prepareLoadBalancer(LoadBalancerType loadBalancerType) {
        switch (loadBalancerType) {
            case UNDERTOW_STATIC:
                prepareUndertowStaticLoadBalancer();
                break;
            case UNDERTOW_MODCLUSTER:
                break;
        }
    }

    private void prepareUndertowStaticLoadBalancer() {
        final String handlerName = "my-handler";

        loadBalancer.start();
        JMSOperations jmsAdminOperations = loadBalancer.getJmsOperations();
        jmsAdminOperations.createUndertowReverseProxyHandler(handlerName);
        jmsAdminOperations.createOutBoundSocketBinding("remote-workerA", workerA.getHostname(), workerA.getHornetqPort());
        jmsAdminOperations.createOutBoundSocketBinding("remote-workerB", workerB.getHostname(), workerB.getHornetqPort());
        jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerA.getName(), "remote-workerA", "http", "myroute", "/");
        jmsAdminOperations.addHostToUndertowReverseProxyHandler(handlerName, workerB.getName(), "remote-workerB", "http", "myroute", "/");
        jmsAdminOperations.removeLocationFromUndertowServerHost("/");
        jmsAdminOperations.reload();
        jmsAdminOperations.addLocationToUndertowServerHost("/", handlerName);

        jmsAdminOperations.close();
        loadBalancer.stop();
    }


}
