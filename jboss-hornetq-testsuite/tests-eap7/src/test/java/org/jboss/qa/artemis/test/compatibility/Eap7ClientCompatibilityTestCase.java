package org.jboss.qa.artemis.test.compatibility;


import category.ClientBackwardCompatibility;
import category.ClientForwardCompatibility;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.compatibility.ClientCompatibilityTestBase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.jms.Session;


/**
 * Test compatibility of older EAP 7 clients against EAP 7 server.
 *
 * @tpChapter Backward compatibility testing
 * @tpSubChapter COMPATIBILITY OF JMS CLIENTS - TEST SCENARIOS
 * @tpJobLink https://jenkins.mw.lab.eng.bos.redhat.com/hudson/job/eap7-artemis-integration-client-compatibility-matrix/
 * @tpTcmsLink https://tcms.engineering.redhat.com/plan/19047/activemq-artemis-functional#testcases
 * @tpTestCaseDetails Test older EAP6 JMS client against latest EAP 7.x server, this test case implements tests from
 * ClientCompatibilityTestBase
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 * @author msvehla@redhat.com
 */
@Category({ClientBackwardCompatibility.class, ClientForwardCompatibility.class})
public class Eap7ClientCompatibilityTestCase extends ClientCompatibilityTestBase {

    private static final Logger LOG = Logger.getLogger(Eap7ClientCompatibilityTestCase.class);

    /**
     * Set all jms binding which will be needed for tests.
     */
    @Override
    protected void prepareContainer(Container container) throws Exception {
        prepareContainer(container, Constants.CONNECTOR_TYPE.HTTP_CONNECTOR);
    }

    /**
     * Set all jms binding which will be needed for tests.
     */
    protected void prepareContainer(Container container, Constants.CONNECTOR_TYPE connectorType) throws Exception {

        container.start();

        JMSOperations jmsAdminOperations = container.getJmsOperations();
        jmsAdminOperations.setBindingsDirectory(JOURNAL_DIR);
        jmsAdminOperations.setPagingDirectory(JOURNAL_DIR);
        jmsAdminOperations.setJournalDirectory(JOURNAL_DIR);
        jmsAdminOperations.setLargeMessagesDirectory(JOURNAL_DIR);
        jmsAdminOperations.setPersistenceEnabled(true);
        jmsAdminOperations.disableSecurity();
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        deployDestinations(jmsAdminOperations);

        if (Constants.CONNECTOR_TYPE.NETTY_NIO.equals(connectorType))   {
            String socketBindingMessaging = "messaging";
            int messsagingPort = 5445;
            String connectorName = "netty";
            String acceptorName = "netty";
            // setup messaging socket binding
            jmsAdminOperations.createSocketBinding(socketBindingMessaging, messsagingPort);

            // restart server so new socket binding will appear
            jmsAdminOperations.close();
            container.restart();
            jmsAdminOperations = container.getJmsOperations();

            // configure netty connector
            jmsAdminOperations.createRemoteConnector(connectorName, socketBindingMessaging, null);
            // configure netty acceptor
            jmsAdminOperations.createRemoteAcceptor(acceptorName, socketBindingMessaging, null);
            // set connector to remote connection factory
            jmsAdminOperations.setConnectorOnConnectionFactory(Constants.CONNECTION_FACTORY_EAP7, connectorName);
        }

        jmsAdminOperations.close();
        container.stop();
    }


    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations(JMSOperations jmsOperations) {

        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            jmsOperations.createQueue(QUEUE_NAME_PREFIX + destinationNumber, QUEUE_JNDI_NAME_PREFIX
                    + destinationNumber, true);
            jmsOperations.createTopic(TOPIC_NAME_PREFIX + destinationNumber, TOPIC_JNDI_NAME_PREFIX
                    + destinationNumber);
        }
    }

    /**
     * @tpTestDetails This test scenario tests whether is possible to send and receive messages from topic with older EAP clients
     * on latest EAP7 server. Netty remote-connectors/acceptors are used.
     * @tpProcedure <ul>
     *     <li>start EAP7 node</li>
     *     <li>start older clients (with SESSION_TRANSACTED) sessions sending and receiving messages from testTopic on EAP7 server.
     *     <li>stop producer and consumer</li>
     *     <li>verify messages</li>
     * </ul>
     * @tpPassCrit producer and receiver successfully sent and received messages
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testNettyTransAckTopic() throws Exception {
        testNettyClient(container(1), Session.SESSION_TRANSACTED, true);
    }

    protected void testNettyClient(final Container container, final int acknowledgeMode, final boolean isTopic)
            throws Exception {

        this.prepareContainer(container, Constants.CONNECTOR_TYPE.NETTY_NIO);
        container.start();

        Clients client = createClients(container, acknowledgeMode, isTopic);
        client.startClients();

        ClientUtils.waitForClientsToFinish(client, 300000);

        container.stop();

        Assert.assertTrue("There are failures detected by org.jboss.qa.hornetq.apps.clients. More information in log.", client.evaluateResults());
    }
}
