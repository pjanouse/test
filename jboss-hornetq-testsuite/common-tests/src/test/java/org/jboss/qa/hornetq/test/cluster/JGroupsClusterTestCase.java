package org.jboss.qa.hornetq.test.cluster;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;


/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p/>
 * This test also serves
 *
 * @author nziakova@redhat.com
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class JGroupsClusterTestCase extends ClusterTestCase {

    private static String JGROUPS_CONNECTION_FACTORY = "JGroupsConnectionFactory";


    public void prepareServers() {
        prepareServers(true);
    }

    public void prepareServers(boolean createDestinations) {

        prepareServer(CONTAINER1, createDestinations);
        prepareServer(CONTAINER2, createDestinations);
        prepareServer(CONTAINER3, createDestinations);
        prepareServer(CONTAINER4, createDestinations);
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName      Name of the container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    private void prepareServer(String containerName, boolean createDestinations) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

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

        // prepare connection factory
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
        controller.stop(containerName);
    }

    // TODO un-ignore when bz https://bugzilla.redhat.com/show_bug.cgi?id=1132190 is fixed
    @Ignore
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Category(FunctionalTests.class)
    public void testLookupOfConnectionFactoryWithJGroupsDiscoveryGroup() throws Exception {

        prepareServer(CONTAINER1, true);

        controller.start(CONTAINER1);

        Context context = null;
        Connection connection = null;

        try {
            context = getContext(CONTAINER1);

            Queue queue = (Queue) context.lookup(inQueueJndiNameForMdb);

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("jms/" + JGROUPS_CONNECTION_FACTORY);

            connection = connectionFactory.createConnection();

            connection.start();

            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage());

            session.commit();

            MessageConsumer consumer = session.createConsumer(queue);

            Message message = consumer.receive(3000);

            session.commit();

            Assert.assertNotNull("Message cannot be null", message);

        } finally {
            if (context != null)    {
                context.close();
            }
            if (connection != null) {
                connection.close();
            }
            stopServer(CONTAINER1);
        }
    }
}