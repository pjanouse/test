package org.jboss.qa.hornetq.test.transportreliability;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.PublisherClientAck;
import org.jboss.qa.hornetq.apps.clients.SubscriberClientAck;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p/>
 * This test also serves
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class ClientNetworkDisconnectionTestCase extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClientNetworkDisconnectionTestCase.class);


    String queueName = "testQueue";
    String topicName = "testTopic";
    String queueJndiName = "jms/queue/testQueue";
    String topicJndiName = "jms/topic/testTopic";

    ControllableProxy proxy1;
    protected int proxyPort = 43812;


    /**
     * Test whether client is disconnected after network fail.
     */
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @RunAsClient
    public void causeNetworkFailureAndCheckThatClientGotDisconnected() throws Exception {

        prepareServer(container(1), 0);

        container(1).start();
        // start proxy
        startProxies();

        // subscribe to topic
        String connectionId = "testConnectionIdSubscriber";
        String subscriberName = "testSubscriber";
        SubscriberClientAck subscriber = new SubscriberClientAck(container(1).getHostname(), container(1).getJNDIPort(), topicJndiName, connectionId, subscriberName);
        subscriber.setMaxRetries(1);
        subscriber.subscribe();

        // publish some messages
        PublisherClientAck publisher = new PublisherClientAck(container(1).getHostname(), container(1).getJNDIPort(), topicJndiName, 2000000, "testConnectionIdPublisher");
        publisher.start();
        subscriber.start();

        // wait for subscriber to receive some messages
        List<Client> subscribers = new ArrayList<Client>();
        subscribers.add(subscriber);
        waitForReceiversUntil(subscribers, 30, 60000);

        // stop proxies
        stopProxies();

        // check that client was disconnected
        // list durable active subscribers on topic
        // check there are none after 60s
        JMSOperations jmsOperations = container(1).getJmsOperations();
        int numberOfSubscribers = 0;
        long startTime = System.currentTimeMillis();
        do {
            if (System.currentTimeMillis() - startTime > 60000) {
                Assert.fail("There is still active durable subscription after more than 60s. ");
            }
            Thread.sleep(1000);
            numberOfSubscribers = jmsOperations.getNumberOfActiveClientConnections();
        } while (numberOfSubscribers > 0);
        jmsOperations.close();

        subscriber.join(60000);
        Assert.assertNotNull("Subscriber must get exception when disconnected.", subscriber.getException());

        // try to stop server
        container(1).stop();

    }

    /**
     * Test whether client is disconnected after network fail.
     */
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @RunAsClient
    public void checkServerShutdownsImmediatelyWithOpenJNDIContext() throws Exception {

        prepareServer(container(1), 0);

        container(1).start();
        // start proxy
        startProxies();

        // subscribe to topic
        String connectionId = "testConnectionIdSubscriber";
        String subscriberName = "testSubscriber";
        SubscriberClientAck subscriber = new SubscriberClientAck(container(1).getHostname(), container(1).getJNDIPort(), topicJndiName, 30000, 1, 1, connectionId, subscriberName);
        subscriber.subscribe();
        subscriber.start();

        // stop proxies
        stopProxies();

        // check that client was disconnected
        // list durable active subscribers on topic
        // check there are none after 60s
        JMSOperations jmsOperations = container(1).getJmsOperations();
        int numberOfSubscribers = 0;
        long startTime = System.currentTimeMillis();
        do {
            if (System.currentTimeMillis() - startTime > 60000) {
                Assert.fail("There is still active durable subscription after more than 60s. ");
            }
            Thread.sleep(1000);
            numberOfSubscribers = jmsOperations.getNumberOfActiveClientConnections();
        } while (numberOfSubscribers > 0);
        jmsOperations.close();

        subscriber.join(30000);
        if (subscriber.isAlive())   {
            subscriber.interrupt();
            Assert.fail("Subscriber did not stop. Check why when reconnect attempts was set to 0.");
        }

        // try to stop server
        container(1).stop();

    }


    protected void startProxies() throws Exception {

        log.info("Start all proxies.");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(container(1).getHostname(), container(1).getHornetqPort(), proxyPort);
            proxy1.start();
        }
        log.info("All proxies started.");

    }

    protected void stopProxies() throws Exception {
        log.info("Stop all proxies.");
        if (proxy1 != null) {
            proxy1.stop();
            proxy1 = null;
        }
        log.info("All proxies stopped.");
    }


    /**
     * Prepares server for topology.
     *
     * @param container Test container - defined in arquillian.xml
     */
    private void prepareServer(Container container, int reconnectAttempts) {

        String connectorName = "netty";
        String socketBindingToProxyName = "messaging-proxy";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);


        // every can connect to this server through proxy on 127.0.0.1:proxyPortIn
        jmsAdminOperations.removeRemoteConnector(connectorName);
        jmsAdminOperations.addRemoteSocketBinding(socketBindingToProxyName, "127.0.0.1", proxyPort);
        jmsAdminOperations.createRemoteConnector(connectorName, socketBindingToProxyName, null);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, reconnectAttempts);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "BLOCK", 10 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setConnectionTtlOverride("default", 5000);

        jmsAdminOperations.createQueue(queueName, queueJndiName, true);
        jmsAdminOperations.createTopic(topicName, topicJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    @After
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
    }

}