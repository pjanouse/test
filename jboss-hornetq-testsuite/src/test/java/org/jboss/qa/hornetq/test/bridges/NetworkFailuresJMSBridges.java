package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Miroslav Novak (mnovak@redhat.com)
 */
@RunWith(Arquillian.class)
public class NetworkFailuresJMSBridges extends NetworkFailuresHornetQCoreBridges {

    private static final Logger log = Logger.getLogger(NetworkFailuresJMSBridges.class);

    /**
     * Prepare servers.
     * <p/>
     */
    public void prepareServers(int reconnectAttempts) {

        prepareClusterServer(CONTAINER1, proxy12port, reconnectAttempts, true);
        prepareClusterServer(CONTAINER2, proxy12port, reconnectAttempts, false);

    }

    /**
     * Prepare servers.
     *
     * @param containerName         container name
     * @param proxyPortIn           proxy port for connector where to connect to proxy directing to this server,every can connect to this server through proxy on 127.0.0.1:proxyPortIn
     * @param reconnectAttempts     number of reconnects for cluster-connections
     */
    protected void prepareClusterServer(String containerName, int proxyPortIn, int reconnectAttempts, boolean deployJmsBridge) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "BridgeConnectionFactory";

        controller.start(containerName);
        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(false);
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);

        // every one can connect to remote server through proxy
        String connectorToProxy = "connector-to-proxy-to-target-server";
        String socketBindingToProxy = "binding-connect-to-proxy-to-target-server";
        jmsAdminOperations.addRemoteSocketBinding(socketBindingToProxy, "127.0.0.1", proxyPortIn);
        jmsAdminOperations.createRemoteConnector(connectorToProxy, socketBindingToProxy, null);

        jmsAdminOperations.close();

        stopServer(containerName);
        controller.start(containerName);

        jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.createConnectionFactory(connectionFactoryName, "java:jboss/exported/jms/" + connectionFactoryName, connectorToProxy);
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");

        jmsAdminOperations.setFactoryType(inVmConnectionFactory, "XA_GENERIC");

        jmsAdminOperations.createQueue(hornetqInQueueName, relativeJndiInQueueName, true);

        jmsAdminOperations.close();

        if (deployJmsBridge)    {
            deployBridge(containerName, reconnectAttempts, "jms/" + connectionFactoryName);
        }

        controller.stop(containerName);

    }

    protected void deployBridge(String containerName, int reconnetAttempts, String bridgeConnectionFactoryJndiName) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = relativeJndiInQueueName;
//        Map<String,String> sourceContext = new HashMap<String, String>();
//        sourceContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
//        sourceContext.put("java.naming.provider.url", "remote://" + getHostname(containerName) + ":4447");

        String targetDestination = relativeJndiInQueueName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + CONTAINER2_IP + ":4447");
        String qualityOfService = "ONCE_AND_ONLY_ONCE";
        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                bridgeConnectionFactoryJndiName, targetDestination, targetContext, qualityOfService, failureRetryInterval, reconnetAttempts,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
    }

    protected void startProxies() throws Exception {

        log.info("Start proxy...");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(CONTAINER2_IP, 5445, proxy12port);
            proxy1.start();
        }
        log.info("Proxy started.");

    }

    protected void stopProxies() throws Exception {
        log.info("Stop proxy...");
        if (proxy1 != null) {
            proxy1.stop();
            proxy1 = null;
        }

        log.info("Proxy stopped.");
    }
}
