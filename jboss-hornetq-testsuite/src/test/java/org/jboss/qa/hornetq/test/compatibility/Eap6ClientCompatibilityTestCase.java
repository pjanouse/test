package org.jboss.qa.hornetq.test.compatibility;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ContainerInfo;


/**
 * Test compatibility of older EAP 6 clients against EAP 6 server.
 *
 * Set eap6client property to version of the client you want to test against
 * the latest EAP6 server.
 *
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 * @author msvehla@redhat.com
 */
public class Eap6ClientCompatibilityTestCase extends ClientCompatibilityTestBase {

    private static final Logger log = Logger.getLogger(Eap6ClientCompatibilityTestCase.class);


    @Override
    protected int getLegacyClientJndiPort() {
        return this.getJNDIPort();
    }


    /**
     * Set all jms binding which will be needed for tests.
     */
    @Override
    protected void prepareContainer(ContainerInfo container) throws Exception {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";

        controller.start(CONTAINER1);

        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

        jmsAdminOperations.setInetAddress("public", CONTAINER1_IP);
        jmsAdminOperations.setInetAddress("unsecure", CONTAINER1_IP);
        jmsAdminOperations.setInetAddress("management", CONTAINER1_IP);

        jmsAdminOperations.setClustered(true);
        jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
        jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
        jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
        jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);

        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName,
                "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                connectorName);

        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        deployDestinations();
        jmsAdminOperations.close();

        stopServer(CONTAINER1);

    }


    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations() {
        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            jmsAdminOperations.createQueue(QUEUE_NAME_PREFIX + destinationNumber, QUEUE_JNDI_NAME_PREFIX
                    + destinationNumber, true);
            jmsAdminOperations.createTopic(TOPIC_NAME_PREFIX + destinationNumber, TOPIC_JNDI_NAME_PREFIX
                    + destinationNumber);
        }
        jmsAdminOperations.close();
    }

}
