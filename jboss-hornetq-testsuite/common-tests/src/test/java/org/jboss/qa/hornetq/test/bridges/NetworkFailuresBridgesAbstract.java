package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.jboss.qa.hornetq.tools.*;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.rmi.RemoteException;

/**
 * Created by okalman on 1/19/15.
 */

@RunWith(Arquillian.class)
public abstract class NetworkFailuresBridgesAbstract extends HornetQTestCase {

    // this is just maximum limit for producer - producer is stopped once Failure test scenario is complete
    protected static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10000000;

    // Logger
    protected static final Logger log = Logger.getLogger(NetworkFailuresHornetQCoreBridges.class);

    protected String hornetqInQueueName = "InQueue";
    protected String relativeJndiInQueueName = "queue/InQueue";

    protected String broadcastGroupAddressClusterA = "233.1.2.1";
    protected int broadcastGroupPortClusterA = 9876;

    protected String broadcastGroupAddressClusterB = "233.1.2.2";
    protected int broadcastGroupPortClusterB = 9876;

    protected String discoveryGroupAddressClusterA = "233.1.2.3";
    protected int discoveryGroupPortServerClusterA = 9876;

    protected String discoveryGroupAddressClusterB = "233.1.2.4";
    protected int discoveryGroupPortServerClusterB = 9876;

    protected int proxy12port = 43812;
    protected int proxy21port = 43821;

    protected ControllableProxy proxy1;
    protected ControllableProxy proxy2;
    protected MulticastProxy mp12;
    protected MulticastProxy mp21;


    public abstract void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails, boolean staysDisconnected) throws Exception;

    public abstract void prepareServers(int reconnectAttempts);

    @Before
    public void stopAllServers() {
        stopServer(CONTAINER1_NAME);
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER3_NAME);
        stopServer(CONTAINER4_NAME);
        try {
            if (proxy1 != null) proxy1.stop();
        } catch (Exception ex)  {
            log.error("Proxy1 cannot be stopped: ", ex);
        }
        try {
            if (proxy2 != null) proxy2.stop();
        } catch (Exception ex)  {
            log.error("Proxy2 cannot be stopped: ", ex);
        }
        if (mp12 != null) mp12.setStop(true);
        if (mp21 != null) mp21.setStop(true);

    }



    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNetworkFailureMixMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testNetworkFailureSmallMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureLargeMessages() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureMixMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(50, 50), 2, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testNetworkFailureLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(120000, new ClientMixMessageBuilder(1024, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureMixMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureSmallMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 50), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureLargeMessages() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(1024, 1024), -1, 2, false);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureMixMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureSmallMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }

    @Test
    @RunAsClient
    @CleanUpBeforeTest @RestoreConfigBeforeTest
    public void testShortNetworkFailureLargeMessages1recAttempts() throws Exception {
        testNetworkFailure(20000, new ClientMixMessageBuilder(50, 1024), 1, 2);
    }


    /**
     * Implementation of the basic test scenario: 1. Start cluster A and B 2.
     * Start producers on A1, A2 3. Start consumers on B1, B2 4. Kill sequence -
     * it's random 5. Stop producers 6. Evaluate results
     *
     * @param messageBuilder   instance of the message builder
     * @param timeBetweenFails time between fails
     */
    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, -1, 2);
    }

    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails) throws Exception {
        testNetworkFailure(timeBetweenFails, messageBuilder, reconnectAttempts, numberOfFails, true);
    }








    protected int getNumberOfNodesInCluster(String container) {
        boolean isContainerStarted = CheckServerAvailableUtils.checkThatServerIsReallyUp(getHostname(container), getHornetqPort(container));

        int numberOfNodesInCluster = -1;
        if (isContainerStarted) {
            JMSOperations jmsAdminOperations = this.getJMSOperations(container);
            numberOfNodesInCluster = jmsAdminOperations.getNumberOfNodesInCluster();
            jmsAdminOperations.close();

        }
        return numberOfNodesInCluster;
    }


    /**
     * Executes network failures.
     * <p/>
     * 1 = 5 short network failures (10s gap)
     * 2 = 5 network failures (30s gap)
     * 3 = 5 network failures (100s gap)
     * 4 = 3 network failures (300s gap)
     *
     * @param timeBetweenFails time between subsequent kills (in milliseconds)
     */
    protected void executeNetworkFails(long timeBetweenFails, int numberOfFails)
            throws Exception {

        for (int i = 0; i < numberOfFails; i++) {



            stopProxies();

            Thread.sleep(timeBetweenFails);

            startProxies();

            Thread.sleep(timeBetweenFails);

        }
    }

    protected void startProxies() throws Exception {

        log.info("Start all proxies.");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), proxy12port);
            proxy1.start();
        }
        if (proxy2 == null) {
            proxy2 = new SimpleProxyServer(getHostname(CONTAINER1_NAME), getHornetqPort(CONTAINER1_NAME), proxy21port);
            proxy2.start();
        }

        if (mp12 == null){
            mp12 = new MulticastProxy(broadcastGroupAddressClusterA, broadcastGroupPortClusterA,
                    discoveryGroupAddressClusterB, discoveryGroupPortServerClusterB);
            mp12.setIpAddressOfInterface(getHostname(CONTAINER1_NAME));
            mp12.start();

        }
        if (mp21 == null){
            mp21 = new MulticastProxy(broadcastGroupAddressClusterB, broadcastGroupPortClusterB,
                    discoveryGroupAddressClusterA, discoveryGroupPortServerClusterA);
            mp21.setIpAddressOfInterface(getHostname(CONTAINER2_NAME));
            mp21.start();
        }
        log.info("All proxies started.");

    }

    protected void stopProxies() throws Exception {
        log.info("Stop all proxies.");
        if (proxy1 != null) {
            proxy1.stop();
            proxy1 = null;
        }
        if (proxy2 != null) {
            proxy2.stop();
            proxy2 = null;
        }

        if (mp12 != null)   {
            mp12.setStop(true);
            mp12 = null;
        }
        if (mp21 != null)   {
            mp21.setStop(true);
            mp21 = null;
        }
        log.info("All proxies stopped.");
    }

    @After
    public void after() {
        if (proxy1 != null) {
            try {
                proxy1.stop();
            } catch (RemoteException e) {
                log.error("Proxy could not be stopped.", e);
            }
        }
        if (proxy2 != null) {
            try {
                proxy2.stop();
            } catch (RemoteException e) {
                log.error("Proxy could not be stopped.", e);
            }
        }
        if (mp21 != null) {
            mp21.setStop(true);
        }
        if (mp12 != null) {
            mp12.setStop(true);
        }
    }



}
