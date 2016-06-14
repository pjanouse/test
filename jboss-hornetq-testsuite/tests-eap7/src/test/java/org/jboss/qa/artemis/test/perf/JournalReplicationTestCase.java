package org.jboss.qa.artemis.test.perf;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.tools.CheckFileContentUtils;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.resourcemonitor.NetworkUsageMeasurement;
import org.jboss.qa.resourcemonitor.ResourceMonitor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by okalman on 9.6.16.
 */
@RunWith(Arquillian.class)
public class JournalReplicationTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(JournalReplicationTestCase.class);
    private static final String queueName= "testQueue";
    private static final String queueJndiName = "jms/queue/testQueue";
    private static final String replicationGroupName = "g1";
    private static final String clusterConnectionName = "my-cluster";
    //private static final long SIZE = 21474836480l;
    private static final long SIZE = 5368709120l/4;
    long messageSize=1024;


    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testJournalSyncPerformance() throws Exception {
        String protocol = ContainerUtils.isEAP6(container(1)) ? ResourceMonitor.Builder.JMX_URL_EAP6 : ResourceMonitor.Builder.JMX_URL_EAP7;
        prepareDedicatedReplicatedTopology();
        container(1).start();
        logger.info("Client started sending" + calculateNumberOfMessages(messageSize)+ " messages");
        sendMessages();
        logger.info("Client finished, now backup will be started");
        container(2).start();
        ResourceMonitor jmsServerMeasurement = new ResourceMonitor.Builder()
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(protocol)
                .processId(container(1).getProcessId())
                .outFileNamingPattern("live-server")
                .measurePeriod(10000)
                .generateCharts()
                .build();
        jmsServerMeasurement.start();

        ResourceMonitor networkMeasurement = new ResourceMonitor.Builder()
                .host(container(1).getHostname())
                .port(container(1).getPort())
                .protocol(protocol)
                .processId(container(1).getProcessId())
                .outFileNamingPattern("network-traffic")
                .measurePeriod(1000)
                .setMeasurables(NetworkUsageMeasurement.class)
                .generateCharts()
                .build();
        networkMeasurement.start();



        ResourceMonitor backupMeasurement = new ResourceMonitor.Builder()
                .host(container(2).getHostname())
                .port(container(2).getPort())
                .protocol(protocol)
                .processId(container(2).getProcessId())
                .outFileNamingPattern("backup-server")
                .measurePeriod(10000)
                .generateCharts()
                .build();
        backupMeasurement.start();


        logger.info("Waiting for synchronization... timeout is 1 hour");
        waitForSynchronization(container(2),3600000); //1 hour timeout
        jmsServerMeasurement.stopMeasuring();
        jmsServerMeasurement.join();
        networkMeasurement.stopMeasuring();
        networkMeasurement.join();
        backupMeasurement.stopMeasuring();
        backupMeasurement.join();
        container(1).stop();
        container(2).stop();


    }

    private void sendMessages() throws InterruptedException {
        List<ProducerTransAck> producers= new ArrayList<ProducerTransAck>();

        int messagesPerProducer = (int)calculateNumberOfMessages(messageSize)/10;
        for(int i=0 ; i<10; i++) {
            ProducerTransAck producerTransAck = new ProducerTransAck(container(1), queueJndiName, messagesPerProducer);
            producerTransAck.setCommitAfter(1000);
            producerTransAck.setTimeout(0);
            producerTransAck.setMessageBuilder(new TextMessageBuilder((int) messageSize));
            producerTransAck.start();
            producers.add(producerTransAck);
        }

        for(ProducerTransAck p: producers){
            p.join();
        }


    }


    private void waitForSynchronization(Container container, long timeout)throws Exception{
        StringBuilder pathToServerLogFile = new StringBuilder(container.getServerHome());
        pathToServerLogFile.append(File.separator).append("standalone").append(File.separator).append("log").append(File.separator).append("server.log");

        logger.info("Check server.log: " + pathToServerLogFile);

        File serverLog = new File(pathToServerLogFile.toString());

        String stringToFind = "synchronized with live-server";
        boolean sync=false;
        long start= System.currentTimeMillis();
        while (!sync && (System.currentTimeMillis()-start<timeout)) {
           sync = CheckFileContentUtils.checkThatFileContainsGivenString(serverLog, stringToFind);
            Thread.sleep(2000);
        }
        logger.info("Synchronization finished after: "+(System.currentTimeMillis()-start-2000)/1000 +"s");
    }


    private void prepareDedicatedReplicatedTopology(){
        prepareLiveServerEAP7(container(1), "ASYNCIO", Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
        prepareBackupServerEAP7(container(2), "ASYNCIO", Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);



    }

    protected void prepareLiveServerEAP7(Container container,  String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setJournalType(journalType);
        setConnectorForClientEAP7(container, connectorType);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addHAPolicyReplicationMaster(true, clusterConnectionName, replicationGroupName);
        jmsAdminOperations.addLoggerCategory("org.apache.activemq","INFO");
        jmsAdminOperations.createQueue(queueName,queueJndiName);

        jmsAdminOperations.close();
        container.stop();
    }

    protected void prepareBackupServerEAP7(Container container, String journalType, Constants.CONNECTOR_TYPE connectorType) {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        setConnectorForClientEAP7(container, connectorType);

        jmsAdminOperations.setJournalType(journalType);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 1024 * 1024, 0, 0, 512 * 1024);
        jmsAdminOperations.addLoggerCategory("org.apache.activemq","INFO");
        jmsAdminOperations.addHAPolicyReplicationSlave(true, clusterConnectionName, 5000, replicationGroupName, 60, true, false, null, null, null, null);

        jmsAdminOperations.createQueue(queueName,queueJndiName);

        jmsAdminOperations.close();

        container.stop();
    }

    protected void setConnectorForClientEAP7(Container container, Constants.CONNECTOR_TYPE connectorType) {

        String messagingGroupSocketBindingForConnector = "messaging";
        String nettyConnectorName = "netty";
        String nettyAcceptorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        int defaultPortForMessagingSocketBinding = 5445;
        String discoveryGroupName = "dg-group1";
        String jgroupsChannel = "activemq-cluster";
        String jgroupsStack = "udp";
        String broadcastGroupName = "bg-group1";

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        switch (connectorType) {
            case HTTP_CONNECTOR:
                break;
            case NETTY_BIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with BIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, null);
                // add acceptor wtih BIO
                Map<String, String> acceptorParams = new HashMap<String, String>();
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, null);
                jmsAdminOperations.setConnectorOnConnectionFactory(connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            case NETTY_NIO:
                jmsAdminOperations.createSocketBinding(messagingGroupSocketBindingForConnector, defaultPortForMessagingSocketBinding);
                jmsAdminOperations.close();
                container.stop();
                container.start();
                jmsAdminOperations = container.getJmsOperations();
                // add connector with NIO
                jmsAdminOperations.removeRemoteConnector(nettyConnectorName);
                Map<String, String> connectorParamsNIO = new HashMap<String, String>();
                connectorParamsNIO.put("use-nio", "true");
                connectorParamsNIO.put("use-nio-global-worker-pool", "true");
                jmsAdminOperations.createRemoteConnector(nettyConnectorName, messagingGroupSocketBindingForConnector, connectorParamsNIO);

                // add acceptor with NIO
                Map<String, String> acceptorParamsNIO = new HashMap<String, String>();
                acceptorParamsNIO.put("use-nio", "true");
                jmsAdminOperations.removeRemoteAcceptor(nettyAcceptorName);
                jmsAdminOperations.createRemoteAcceptor(nettyAcceptorName, messagingGroupSocketBindingForConnector, acceptorParamsNIO);
                jmsAdminOperations.setConnectorOnConnectionFactory(connectionFactoryName, nettyConnectorName);
                jmsAdminOperations.removeClusteringGroup(clusterConnectionName);
                jmsAdminOperations.setClusterConnections(clusterConnectionName, "jms", discoveryGroupName, false, 1, 1000, true, nettyConnectorName);
                jmsAdminOperations.removeBroadcastGroup(broadcastGroupName);
                jmsAdminOperations.setBroadCastGroup(broadcastGroupName, jgroupsStack, jgroupsChannel, 1000, nettyConnectorName);
                break;
            default:
                break;
        }

        // todo remote once you're done with testing
        jmsAdminOperations.setClusterConnectionCallTimeout(clusterConnectionName, 15000);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 1000, jgroupsStack, jgroupsChannel);

        jmsAdminOperations.close();
    }

    private long calculateNumberOfMessages(long messageSize){
        return SIZE/messageSize;
    }


}
