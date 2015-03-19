package org.jboss.qa.hornetq.test.bridges;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageVerifier;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;
import org.junit.Assert;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Miroslav Novak (mnovak@redhat.com)
 */
@RunWith(Arquillian.class)
public class NetworkFailuresJMSBridges extends NetworkFailuresBridgesAbstract {

    private static final Logger log = Logger.getLogger(NetworkFailuresJMSBridges.class);


    public void testNetworkFailure(long timeBetweenFails, MessageBuilder messageBuilder, int reconnectAttempts, int numberOfFails, boolean staysDisconnected)
            throws Exception {

        prepareServers(reconnectAttempts);

        startProxies();

        controller.start(CONTAINER2_NAME); // B1
        controller.start(CONTAINER1_NAME); // A1


        Thread.sleep(5000);
        // message verifier which detects duplicated or lost messages
        FinalTestMessageVerifier messageVerifier = new TextMessageVerifier();

        // A1 producer
        ProducerTransAck producer1 = new ProducerTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), relativeJndiInQueueName, NUMBER_OF_MESSAGES_PER_PRODUCER);
        producer1.setMessageVerifier(messageVerifier);
        if (messageBuilder != null) {
            messageBuilder.setAddDuplicatedHeader(true);
            producer1.setMessageBuilder(messageBuilder);
        }
        // B1 consumer
        ReceiverTransAck receiver1 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER2_NAME), getJNDIPort(


                CONTAINER2_NAME), relativeJndiInQueueName, (4 * timeBetweenFails) > 120000 ? (4 * timeBetweenFails) : 120000, 10, 10);
        receiver1.setTimeout(0);
        receiver1.setMessageVerifier(messageVerifier);

        log.info("Start producer and receiver.");
        producer1.start();
        receiver1.start();

        // Wait to send and receive some messages
        Thread.sleep(15 * 1000);

        executeNetworkFails(timeBetweenFails, numberOfFails);

        Thread.sleep(5 * 1000);

        producer1.stopSending();
        producer1.join();
        // Just prints lost or duplicated messages if there are any. This does not fail the test.
      

        if (staysDisconnected)  {
            stopProxies();
            receiver1.join();

            log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
            log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();
            JMSOperations adminOperations = container(1).getJmsOperations();
            int preparedTransactions= adminOperations.getNumberOfPreparedTransaction();
            adminOperations.close();
            if(preparedTransactions>0){
                log.info("There are unfinished transactions in journal, waiting for rollback");
                Thread.sleep(7*60000);
            }



            ReceiverTransAck receiver2 = new ReceiverTransAck(getCurrentContainerForTest(), getHostname(CONTAINER1_NAME), getJNDIPort(CONTAINER1_NAME), relativeJndiInQueueName, 10000, 10, 10);
            receiver2.start();
            receiver2.join();
            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(),
                    receiver1.getListOfReceivedMessages().size() + receiver2.getListOfReceivedMessages().size());
        } else {
            receiver1.setReceiveTimeOut(120000);
            receiver1.join();
            log.info("Number of sent messages: " + producer1.getListOfSentMessages().size());
            log.info("Number of received messages: " + receiver1.getListOfReceivedMessages().size());
            messageVerifier.verifyMessages();


            Assert.assertEquals("There is different number of sent and received messages.",
                    producer1.getListOfSentMessages().size(), receiver1.getListOfReceivedMessages().size());
        }
        stopServer(CONTAINER2_NAME);
        stopServer(CONTAINER1_NAME);


    }



    /**
     * Prepare servers.
     * <p/>
     */
    public void prepareServers(int reconnectAttempts) {

        prepareClusterServer(container(1), proxy12port, reconnectAttempts, true);
        prepareClusterServer(container(2), proxy12port, reconnectAttempts, false);

    }

    /**
     * Prepare servers.
     *
     * @param container             test container
     * @param proxyPortIn           proxy port for connector where to connect to proxy directing to this server,every can connect to this server through proxy on 127.0.0.1:proxyPortIn
     * @param reconnectAttempts     number of reconnects for cluster-connections
     */
    protected void prepareClusterServer(Container container, int proxyPortIn, int reconnectAttempts, boolean deployJmsBridge) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String inVmConnectionFactory = "InVmConnectionFactory";
        String connectionFactoryName = "BridgeConnectionFactory";

        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

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

        container.restart();

        jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.createConnectionFactory(connectionFactoryName, "java:jboss/exported/jms/" + connectionFactoryName, connectorToProxy);
        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, false);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, reconnectAttempts);
        jmsAdminOperations.setFactoryType(connectionFactoryName, "XA_GENERIC");

        jmsAdminOperations.setFactoryType(inVmConnectionFactory, "XA_GENERIC");

        jmsAdminOperations.createQueue(hornetqInQueueName, relativeJndiInQueueName, true);

        jmsAdminOperations.close();

        if (deployJmsBridge)    {
            deployBridge(container, reconnectAttempts, "jms/" + connectionFactoryName);
        }

        container.stop();
    }

    protected void deployBridge(Container container, int reconnetAttempts, String bridgeConnectionFactoryJndiName) {

        String bridgeName = "myBridge";
        String sourceConnectionFactory = "java:/ConnectionFactory";
        String sourceDestination = relativeJndiInQueueName;
//        Map<String,String> sourceContext = new HashMap<String, String>();
//        sourceContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
//        sourceContext.put("java.naming.provider.url", "remote://" + getHostname(containerName) + ":4447");

        String targetDestination = relativeJndiInQueueName;
        Map<String,String> targetContext = new HashMap<String, String>();
        targetContext.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        targetContext.put("java.naming.provider.url", "remote://" + getHostname(CONTAINER2_NAME) + ":" + getJNDIPort(
                CONTAINER2_NAME));
        String qualityOfService = "ONCE_AND_ONLY_ONCE";
        long failureRetryInterval = 1000;
        long maxBatchSize = 10;
        long maxBatchTime = 100;
        boolean addMessageIDInHeader = true;

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.createJMSBridge(bridgeName, sourceConnectionFactory, sourceDestination, null,
                bridgeConnectionFactoryJndiName, targetDestination, targetContext, qualityOfService, failureRetryInterval, reconnetAttempts,
                maxBatchSize, maxBatchTime, addMessageIDInHeader);

        jmsAdminOperations.close();
    }

    protected void startProxies() throws Exception {

        log.info("Start proxy...");
        if (proxy1 == null) {
            proxy1 = new SimpleProxyServer(getHostname(CONTAINER2_NAME), getHornetqPort(CONTAINER2_NAME), proxy12port);
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
