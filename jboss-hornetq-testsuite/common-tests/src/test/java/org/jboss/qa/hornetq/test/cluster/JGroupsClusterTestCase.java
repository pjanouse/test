package org.jboss.qa.hornetq.test.cluster;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.*;
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
 * @tpChapter  Integration testing
 * @tpSubChapter JGROUPS CLUSTER - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-ipv6-tests/
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-qe-internal-ts-cluster-tests/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19042/activemq-artemis-integration#testcases
 * @tpTestCaseDetails This is the same as ClusterTestCase, JGroups is used for cluster nodes discovery.
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
public class JGroupsClusterTestCase extends ClusterTestCase {

    private static String JGROUPS_CONNECTION_FACTORY = "JGroupsConnectionFactory";


    public void prepareServers() {
        prepareServers(true);
    }

    public void prepareServers(boolean createDestinations) {

        prepareServer(container(1), createDestinations);
        prepareServer(container(2), createDestinations);
        prepareServer(container(3), createDestinations);
        prepareServer(container(4), createDestinations);
    }

    /**
     * Prepares server for topology.
     *
     * @param container          The container - defined in arquillian.xml
     * @param createDestinations Create destination topics and queues and topics if true, otherwise no.
     */
    private void prepareServer(Container container, boolean createDestinations) {

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

    @After
    @Before
    public void stopAllServers() {
        container(1).stop();
        container(2).stop();
        container(3).stop();
        container(4).stop();
    }

    // TODO un-ignore when bz https://bugzilla.redhat.com/show_bug.cgi?id=1132190 is fixed
    @Ignore
    @Test
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Category(FunctionalTests.class)
    public void testLookupOfConnectionFactoryWithJGroupsDiscoveryGroup() throws Exception {

        prepareServer(container(1), true);

        container(1).start();

        Context context = null;
        Connection connection = null;

        try {
            context = container(1).getContext();

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
            container(1).stop();
        }
    }
}