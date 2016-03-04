package org.jboss.qa.hornetq.test.cluster;


import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;

public class JGroupsMessageGroupingTestCase extends MessageGroupingTestCase {

    private static String JGROUPS_CONNECTION_FACTORY = "JGroupsConnectionFactory";

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    protected void prepareServer(Container container, boolean createDestinations) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = ContainerUtils.isEAP6(container) ? "netty" : "http-connector";
        String connectionFactoryName = "RemoteConnectionFactory";

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        if(ContainerUtils.isEAP6(container)){
            jmsAdminOperations.setClustered(true);
            jmsAdminOperations.setSharedStore(true);
        }

        if (JMSTools.isIpv6Address(container.getHostname())) {
            jmsAdminOperations.setMulticastAddressOnSocketBinding("modcluster", System.getenv("MCAST_ADDRV6"));
        }

        jmsAdminOperations.setPersistenceEnabled(true);


        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, "udp", "udp", 2000, connectorName);

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, 10000, "udp", "udp");

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        //  prepare connection factory
        jmsAdminOperations.createConnectionFactory(JGROUPS_CONNECTION_FACTORY, "java:jboss/exported/jms/" + JGROUPS_CONNECTION_FACTORY, connectorName);
        jmsAdminOperations.setDiscoveryGroupOnConnectionFactory(JGROUPS_CONNECTION_FACTORY, discoveryGroupName);


        jmsAdminOperations.disableSecurity();
//        jmsAdminOperations.setLoggingLevelForConsole("DEBUG");
//        jmsAdminOperations.addLoggerCategory("org.hornetq.core.client.impl.Topology", "DEBUG");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024, 0, 0, 1024);
        if (createDestinations) {
            for (int queueNumber = 0; queueNumber < NUMBER_OF_DESTINATIONS; queueNumber++) {
                jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, queueJndiNamePrefix + queueNumber, true);
            }

            for (int topicNumber = 0; topicNumber < NUMBER_OF_DESTINATIONS; topicNumber++) {
                jmsAdminOperations.createTopic(topicNamePrefix + topicNumber, topicJndiNamePrefix + topicNumber);
            }

            jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
            jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);
            jmsAdminOperations.createTopic(inTopicNameForMdb, inTopicJndiNameForMdb);
            jmsAdminOperations.createTopic(outTopicNameForMdb, outTopicJndiNameForMdb);
        }
        jmsAdminOperations.close();
        container.stop();
    }

}
