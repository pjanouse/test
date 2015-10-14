package org.jboss.qa.artemis.test.compatibility;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.compatibility.ClientCompatibilityTestBase;
import org.jboss.qa.hornetq.tools.JMSOperations;


/**
 * Test compatibility of older EAP 6 org.jboss.qa.hornetq.apps.clients against EAP 6 server.
 *
 * Set eap6client property to version of the client you want to test against
 * the latest EAP6 server.
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS CLIENTS - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-JMS/job/eap7-artemis-integration-client-compatability-EAP-6x-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpTestCaseDetails Test older EAP6 JMS client against latest EAP 7.x server, this test case implements tests from
 * ClientCompatibilityTestBase
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 * @author msvehla@redhat.com
 */
public class Eap6ClientCompatibilityTestCase extends ClientCompatibilityTestBase {

    private static final Logger log = Logger.getLogger(Eap6ClientCompatibilityTestCase.class);

    /**
     * Set all jms binding which will be needed for tests.
     */
    @Override
    protected void prepareContainer(Container container) throws Exception {
        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String messagingGroupSocketBindingName = "messaging-group";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";

        container(1).start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
        jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
        jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
        jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName, "");

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

        container(1).stop();

    }


    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations() {
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            jmsAdminOperations.createQueue(QUEUE_NAME_PREFIX + destinationNumber, QUEUE_JNDI_NAME_PREFIX
                    + destinationNumber, true);
            jmsAdminOperations.createTopic(TOPIC_NAME_PREFIX + destinationNumber, TOPIC_JNDI_NAME_PREFIX
                    + destinationNumber);
        }
        jmsAdminOperations.close();
    }

}